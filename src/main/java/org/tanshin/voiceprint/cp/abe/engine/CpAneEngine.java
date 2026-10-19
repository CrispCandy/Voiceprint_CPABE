package org.tanshin.voiceprint.cp.abe.engine;

import org.tanshin.voiceprint.cp.abe.attribute.Attribute;
import org.tanshin.voiceprint.cp.abe.parameter.*;
import org.tanshin.voiceprint.cp.abe.polynomial.Polynomial;
import org.tanshin.voiceprint.cp.abe.structure.*;
import org.tanshin.voiceprint.cp.abe.text.CipherText;
import org.tanshin.voiceprint.cp.abe.text.OfflinePackage;
import org.tanshin.voiceprint.cp.abe.text.PlainText;
import org.tanshin.voiceprint.cp.abe.text.ProxyCipherText;
import org.tanshin.voiceprint.cp.abe.util.ConvertUtils;
import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Field;
import it.unisa.dia.gas.jpbc.Pairing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CpAneEngine {

    public UserPrivateKey keyGen(MasterPrivateKey masterPrivateKey, List<Attribute> attributes) {
        return UserPrivateKey.build(masterPrivateKey, attributes);
    }

    public CipherText encrypt(PublicKey pk, PlainText plainText, AccessTree accessTree) {
        AccessTreeNode root = accessTree.getRoot();
        Element s = getRandomElementInZr(pk);
        root.setSecretNumber(s);

        CipherText cipherText = new CipherText();

        Element c_ware = (plainText.getMessageValue().mul(pk.getEgg_a().powZn(s).getImmutable())).getImmutable();
        cipherText.setC_wave(c_ware);

        Element c = pk.getH().powZn(s).getImmutable();
        cipherText.setC(c);

        compute(root, pk, cipherText);

        cipherText.setAccessTree(accessTree);
        return cipherText;
    }

    private Element getRandomElementInZr(PublicKey publicKey) {
        return publicKey.getPairingParameter().getZr().newRandomElement().getImmutable();
    }

    private void compute(AccessTreeNode node, PublicKey publicKey, CipherText cipherText) {
        Field z_r = publicKey.getPairingParameter().getZr();
        Element secretNumber = node.getSecretNumber();

        if (node.getAccessTreeNodeType() == AccessTreeNodeType.INNER_NODE) {
            Polynomial polynomial = new Polynomial(((InnerAccessTreeNode)node).getThreshold() - 1, secretNumber, z_r);
            for (AccessTreeNode child : node.getChildren()) {
                int index = child.getIndex();
                Element childSecret = polynomial.getValue(z_r.newElement(index).getImmutable());
                child.setParent(node);
                child.setSecretNumber(childSecret);
                compute(child, publicKey, cipherText);
            }
        }

        if (node.getAccessTreeNodeType() == AccessTreeNodeType.LEAF_NODE) {
            LeafAccessTreeNode leafNode = (LeafAccessTreeNode) node;
            Attribute attribute = leafNode.getAttribute();
            Element attributeValue = attribute.getAttributeValue();

            Element c_y = (publicKey.getPairingParameter().getGenerator().powZn(leafNode.getSecretNumber())).getImmutable();

            // 【核心修复1：严格保证先 Hash 再模幂，与私钥生成对齐】
            Element c_y_pie = publicKey.hash(attributeValue).powZn(leafNode.getSecretNumber()).getImmutable();

            cipherText.putCy(attribute, c_y);
            cipherText.putCyPie(attribute, c_y_pie);
        }
    }

    public String decryptToStr(PublicKey publicKey, UserPrivateKey userPrivateKey, CipherText cipherText){
        Element decrypt = decrypt(publicKey, userPrivateKey, cipherText);
        if (decrypt != null){
            return new String(ConvertUtils.byteToStr(decrypt.toBytes()));
        }
        return null;
    }

    public Element decrypt(PublicKey publicKey, UserPrivateKey userPrivateKey, CipherText cipherText) {
        System.out.println("开始解密，用户持有的属性: " + userPrivateKey.getUserAttributes());

        Element decryptNode = decryptNode(publicKey, userPrivateKey, cipherText,
                cipherText.getAccessTree().getRoot(),
                userPrivateKey.getUserAttributes());

        if (decryptNode == null) {
            System.err.println("❌ [权限不足]: 用户属性不满足密文访问策略树！");
            return null;
        }

        try {
            Element D = userPrivateKey.getD();
            Element C = cipherText.getC();
            Element c_wave = cipherText.getC_wave();
            Pairing pairing = publicKey.getPairingParameter().getPairing();

            Element result = c_wave.div(pairing.pairing(C, D).div(decryptNode)).getImmutable();
            System.out.println("✅ [数学计算成功]: 明文元索已还原。");
            return result;
        } catch (Exception e) {
            System.err.println("❌ [数学崩溃]: 属性匹配但计算乱码，请删除旧密钥文件并重试！");
            return null;
        }
    }

    public Element decryptNode(PublicKey publicKey, UserPrivateKey userPrivateKey, CipherText cipherText, AccessTreeNode x, List<Attribute> attributes) {

        if (x.getAccessTreeNodeType() == AccessTreeNodeType.LEAF_NODE) {
            LeafAccessTreeNode leafNode = ((LeafAccessTreeNode) x);
            String leafName = leafNode.getAttribute().getAttributeName();

            System.out.println("DEBUG: 正在匹配叶子节点 [" + leafName + "]");

            // 优化日志：只打印名字，不打印对象的 toString()
            List<String> attrNames = attributes.stream().map(Attribute::getAttributeName).collect(Collectors.toList());
            System.out.println("DEBUG: 用户持有属性列表 " + attrNames);

            // --- 核心调试 ---
            boolean found = false;
            for (Attribute userAttr : attributes) {
                if (userAttr.getAttributeName().equals(leafName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.out.println("DEBUG: ❌ 匹配失败！树要求 [" + leafName + "]，但用户没有。");
            } else {
                System.out.println("DEBUG: ✅ 找到匹配属性！");
            }

            Attribute treeAttr = leafNode.getAttribute();

            // ⭐ 核心修复：手动遍历比对名称，解决对象实例不一致问题
            Attribute matchedUserAttr = null;
            for (Attribute userAttr : attributes) {
                if (userAttr.getAttributeName().equals(treeAttr.getAttributeName())) {
                    matchedUserAttr = userAttr;
                    break;
                }
            }

            if (matchedUserAttr != null) {
                // 从密文 Map 和私钥 Map 中提取元素
                // 注意：由于 CipherText 从 IPFS 回来，其 Map 的 Key 也是 Attribute 对象
                // 如果 Map.get(treeAttr) 为空，说明序列化 Key 的 hashCode 对不上

                Element cy = cipherText.getCy(treeAttr);
                if (cy == null) {
                    // 兜底逻辑：如果对象 Key 匹配不上，尝试按名称从 Map 找
                    cy = cipherText.getCyMap().entrySet().stream()
                            .filter(e -> e.getKey().getAttributeName().equals(treeAttr.getAttributeName()))
                            .map(Map.Entry::getValue).findFirst().orElse(null);
                }

                Element cyPie = cipherText.getCyPie(treeAttr);
                if (cyPie == null) {
                    cyPie = cipherText.getCyPieMap().entrySet().stream()
                            .filter(e -> e.getKey().getAttributeName().equals(treeAttr.getAttributeName()))
                            .map(Map.Entry::getValue).findFirst().orElse(null);
                }

                Element dj = userPrivateKey.getDj(matchedUserAttr);
                Element djPie = userPrivateKey.getDjPie(matchedUserAttr);

                if (cy == null || cyPie == null || dj == null || djPie == null) {
                    System.err.println("❌ [数学缺失]: 找到属性 " + treeAttr.getAttributeName() + " 但密文/私钥组件缺失！");
                    return null;
                }

                Pairing pairing = userPrivateKey.getPairingParameter().getPairing();
                return pairing.pairing(dj, cy).div(pairing.pairing(djPie, cyPie)).getImmutable();
            } else {
                return null;
            }
        } else {
            // ... INNER_NODE 逻辑保持不变，但确保使用 GT 群初始化 ...
            InnerAccessTreeNode innerNode = ((InnerAccessTreeNode) x);
            int threshold = innerNode.getThreshold();
            int satisfyCount = 0;

            Map<Element, Element> indexFzMap = new HashMap<>();
            for (AccessTreeNode child : innerNode.getChildren()) {
                Element decryptNode = decryptNode(publicKey, userPrivateKey, cipherText, child, attributes);
                if (decryptNode != null) {
                    satisfyCount++;
                    Element index = publicKey.getPairingParameter().getZr().newElement(child.getIndex()).getImmutable();
                    indexFzMap.put(index, decryptNode);
                }
            }
            if (satisfyCount < threshold) {
                return null;
            }

            // 【核心修复2：必须使用 GT 群来初始化累乘器】
            Element result = publicKey.getPairingParameter().getPairing().getGT().newOneElement();
            Element zero = publicKey.getPairingParameter().getZr().newZeroElement().getImmutable();
            List<Element> Sx = new ArrayList<>(indexFzMap.keySet());

            for (Map.Entry<Element, Element> entry : indexFzMap.entrySet()) {
                Element curIndex = entry.getKey();
                Element curFz = entry.getValue();
                Element powZn = Polynomial.lagrangeCoefficient(curIndex, Sx, zero, publicKey.getPairingParameter().getZr());
                result.mul((curFz.powZn(powZn)));
            }
            return result.getImmutable();
        }
    }

    public Map<String, Object> generateProxyKeys(PublicKey publicKey, UserPrivateKey userPrivateKey) {
        Field z_r = publicKey.getPairingParameter().getZr();
        Element z = z_r.newRandomElement().getImmutable();
        Element z_inv = z.invert().getImmutable();

        UserPrivateKey tk = UserPrivateKey.buildProxyKey(userPrivateKey, z);

        Map<String, Object> keys = new HashMap<>();
        keys.put("TK", tk);
        keys.put("RK", z_inv);
        return keys;
    }

    public ProxyCipherText proxyReEncrypt(PublicKey publicKey, UserPrivateKey tk, CipherText cipherText) {
        Element decryptNode = decryptNode(publicKey, tk, cipherText, cipherText.getAccessTree().getRoot(), tk.getUserAttributes());
        if (decryptNode != null) {
            Element D_z = tk.getD();
            Element C = cipherText.getC();
            Pairing pairing = publicKey.getPairingParameter().getPairing();

            Element T = pairing.pairing(C, D_z).div(decryptNode).getImmutable();

            return new ProxyCipherText(cipherText.getC_wave(), T);
        }
        return null;
    }

    public Element delegateeDecrypt(ProxyCipherText proxyCipherText, Element rk) {
        if (proxyCipherText == null || proxyCipherText.getT() == null) return null;

        Element T_recovered = proxyCipherText.getT().powZn(rk).getImmutable();
        return proxyCipherText.getC_wave().div(T_recovered).getImmutable();
    }

    public OfflinePackage offlineEncrypt(PublicKey pk, AccessTree accessTree) {
        AccessTreeNode root = accessTree.getRoot();
        Element s = getRandomElementInZr(pk);
        root.setSecretNumber(s);

        CipherText preCipherText = new CipherText();

        Element egg_a_s = pk.getEgg_a().powZn(s).getImmutable();
        Element c = pk.getH().powZn(s).getImmutable();
        preCipherText.setC(c);

        compute(root, pk, preCipherText);

        preCipherText.setAccessTree(accessTree);

        return new OfflinePackage(egg_a_s, preCipherText);
    }

    public CipherText onlineEncrypt(PlainText plainText, OfflinePackage offlinePackage) {
        CipherText finalCipherText = offlinePackage.getPreCipherText();
        Element c_wave = plainText.getMessageValue().mul(offlinePackage.getEgg_a_s()).getImmutable();
        finalCipherText.setC_wave(c_wave);
        return finalCipherText;
    }
}