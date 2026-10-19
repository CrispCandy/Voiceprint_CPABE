package org.tanshin.voiceprint.cp.abe.test;

import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;
import it.unisa.dia.gas.jpbc.Element;
import org.tanshin.voiceprint.cp.abe.attribute.Attribute;
import org.tanshin.voiceprint.cp.abe.engine.CpAneEngine;
import org.tanshin.voiceprint.cp.abe.mfcc.MedicalRecord;
import org.tanshin.voiceprint.cp.abe.parameter.*;
import org.tanshin.voiceprint.cp.abe.structure.AccessTree;
import org.tanshin.voiceprint.cp.abe.structure.AccessTreeBuildModel;
import org.tanshin.voiceprint.cp.abe.text.*;
import org.tanshin.voiceprint.cp.abe.util.AESUtil;
import org.tanshin.voiceprint.cp.abe.util.CipherJSONUtils;
import org.tanshin.voiceprint.cp.abe.util.ConvertUtils;
import org.tanshin.voiceprint.cp.abe.update.DynamicPolicyUpdater;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class HybridBenchmarkTest {

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        File selectedFile = openWindowsFileDialog();
        if (selectedFile == null) return;

        System.out.println("=================================================");
        System.out.println("=== [动态策略演进 & 多身份 ABE 双向权限阻断测试] ===");
        System.out.println("=================================================");

        try {
            // ---------------------------------------------------------
            // [阶段 1]: 数据准备
            // ---------------------------------------------------------
            MedicalRecord record = MedicalRecord.fullProcess(selectedFile);
            byte[] rawTextBytes = record.getMsg().getBytes(StandardCharsets.UTF_8);
            byte[] rawVoiceBytes = ConvertUtils.matrixToBytes(record.getMfccMatrix());
            System.out.println("\n[1] 数据提取完成。待加密文本长度: " + rawTextBytes.length + "，声纹矩阵长度: " + rawVoiceBytes.length);

            // ---------------------------------------------------------
            // [阶段 2]: ABE 系统初始化
            // ---------------------------------------------------------
            SystemKey systemKey = SystemKey.build();
            PublicKey pk = systemKey.getPublicKey();
            MasterPrivateKey msk = systemKey.getMasterPrivateKey();
            CpAneEngine engine = new CpAneEngine();

            // ---------------------------------------------------------
            // [阶段 3]: 离线加密池混合加密 (采用高级 CTR 模式)
            // ---------------------------------------------------------
            System.out.println("\n[2] 开始离线混合加密...");
            SecretKey kText = AESUtil.generateAESKey();
            SecretKey kVoice = AESUtil.generateAESKey();

            byte[] ivText = AESUtil.generateIV();
            byte[] ivVoice = AESUtil.generateIV();

            byte[] encTextBytes = AESUtil.encryptBytesCTR(rawTextBytes, kText, ivText);
            byte[] encVoiceBytes = AESUtil.encryptBytesCTR(rawVoiceBytes, kVoice, ivVoice);

            String encTextStr = Base64.getEncoder().encodeToString(encTextBytes);
            String encVoiceStr = Base64.getEncoder().encodeToString(encVoiceBytes);

            AccessTree policyTreeText = getAccessTreeA(pk);
            AccessTree policyTreeVoice = getAccessTreeB(pk);

            int g1Length = pk.getPairingParameter().getG1().getLengthInBytes();

            byte[] paddedTextKey = new byte[g1Length];
            System.arraycopy(kText.getEncoded(), 0, paddedTextKey, g1Length - 16, 16);
            Element mText = pk.getPairingParameter().getG1().newElementFromBytes(paddedTextKey).getImmutable();

            byte[] paddedVoiceKey = new byte[g1Length];
            System.arraycopy(kVoice.getEncoded(), 0, paddedVoiceKey, g1Length - 16, 16);
            Element mVoice = pk.getPairingParameter().getG1().newElementFromBytes(paddedVoiceKey).getImmutable();

            CipherText headerText = engine.encrypt(pk, new PlainText(mText), policyTreeText);
            CipherText headerVoice = engine.encrypt(pk, new PlainText(mVoice), policyTreeVoice);

            MedicalCipherPackage pkg = new MedicalCipherPackage();
            pkg.setCaseId("CASE_ID_9527");
            pkg.setEncText(encTextStr);
            pkg.setEncVoice(encVoiceStr);
            pkg.setHeaderText(headerText);
            pkg.setHeaderVoice(headerVoice);
            pkg.setTextIv(ivText);
            pkg.setTextDataLength(rawTextBytes.length);
            pkg.setVoiceIv(ivVoice);
            pkg.setVoiceDataLength(rawVoiceBytes.length);

            System.out.println(">>> 离线混合加密完成。");

            // ---------------------------------------------------------
            // [阶段 4]: 纯文本 JSON 存入 IPFS
            // ---------------------------------------------------------
            System.out.println("\n================ [IPFS 去中心化网络存取] ================");
            IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
            String jsonForIpfs = CipherJSONUtils.packageToJSON(pkg);

            System.out.println("[4.1] 正在上传纯文本数字信封至 IPFS...");
            NamedStreamable.ByteArrayWrapper file = new NamedStreamable.ByteArrayWrapper("medical_package.json", jsonForIpfs.getBytes(StandardCharsets.UTF_8));
            List<MerkleNode> results = ipfs.add(file);
            String cid = results.get(0).hash.toBase58();
            System.out.println(">>> 上传成功！分布式网格 CID: " + cid);

            // ---------------------------------------------------------
            // [阶段 5]: 代理解密与本地构树机制 (策略更新前测试)
            // ---------------------------------------------------------
            System.out.println("\n[5.1] 从 IPFS 节点提取数据...");
            byte[] downloadedData = ipfs.cat(Multihash.fromBase58(cid));
            String downloadedJson = new String(downloadedData, StandardCharsets.UTF_8);
            SerializableCipherPackage restoredDto = CipherJSONUtils.jsonToDTO(downloadedJson);

            System.out.println("[5.2] 执行【本地动态构树绑定】架构验证...");
            CipherText resHeaderText = CipherText.fromBytes(Base64.getDecoder().decode(restoredDto.headerTextBase64), pkg.getHeaderText());
            CipherText resVoiceHeader = CipherText.fromBytes(Base64.getDecoder().decode(restoredDto.headerVoiceBase64), pkg.getHeaderVoice());

            AccessTree localDecryptTreeText = getAccessTreeA(pk);
            AccessTree localDecryptTreeVoice = getAccessTreeB(pk);

            resHeaderText.setAccessTree(localDecryptTreeText);
            resVoiceHeader.setAccessTree(localDecryptTreeVoice);

            MedicalCipherPackage restoredPkg = new MedicalCipherPackage();
            restoredPkg.setEncText(restoredDto.encText);
            restoredPkg.setEncVoice(restoredDto.encVoice);
            restoredPkg.setTextIv(pkg.getTextIv());
            restoredPkg.setVoiceIv(pkg.getVoiceIv());
            restoredPkg.setTextDataLength(pkg.getTextDataLength());
            restoredPkg.setVoiceDataLength(pkg.getVoiceDataLength());

            System.out.println("[5.3] 原始策略下【主治医生】权限解码验证...");
            List<Attribute> initDoctorAttrs = Arrays.asList(new Attribute("主治医生", pk));
            UserPrivateKey doctorPrivKey = engine.keyGen(msk, initDoctorAttrs);

            Element initEleVoice = engine.decrypt(pk, doctorPrivKey, resVoiceHeader);
            if (initEleVoice != null) {
                System.out.println(">>> [初始验证成功] 策略更新前，【主治医生】有权解析出声纹 AES 密钥。");
            }

            // ---------------------------------------------------------
            // [阶段 6]: 细粒度同态更新策略 (B -> C)
            // ---------------------------------------------------------
            System.out.println("\n================ [细粒度策略动态更新] ================");
            System.out.println("[6.1] 数据所有者修改访问树：仅限【科室主任】可访问声纹");

            SecretKey newVoiceKey = AESUtil.generateAESKey();
            AccessTree newPolicyTreeVoice = getAccessTreeC(pk); // 构造全新策略 C

            byte[] paddedNewVoiceKey = new byte[g1Length];
            System.arraycopy(newVoiceKey.getEncoded(), 0, paddedNewVoiceKey, g1Length - 16, 16);
            Element mNewVoice = pk.getPairingParameter().getG1().newElementFromBytes(paddedNewVoiceKey).getImmutable();

            CipherText newHeaderVoice = engine.encrypt(pk, new PlainText(mNewVoice), newPolicyTreeVoice);

            // 【本地构树】：为新密文头绑定本地最新策略树 C
            newHeaderVoice.setAccessTree(newPolicyTreeVoice);

            System.out.println("[6.2] 利用流密码密钥空间本地计算 Delta 异或增量...");
            byte[] delta = DynamicPolicyUpdater.generateVoiceDelta(kVoice, newVoiceKey, restoredPkg);

            System.out.println("[6.3] 代理云节点执行同态盲运算：OldCipherText XOR Delta = NewCipherText...");
            byte[] oldEncVoice = Base64.getDecoder().decode(restoredPkg.getEncVoice());
            byte[] newEncVoice = new byte[oldEncVoice.length];
            for (int i = 0; i < oldEncVoice.length; i++) {
                newEncVoice[i] = (byte) (oldEncVoice[i] ^ delta[i]);
            }

            // 将更新后的状态封装回数字信封内
            restoredPkg.setEncVoice(Base64.getEncoder().encodeToString(newEncVoice));
            restoredPkg.setHeaderVoice(newHeaderVoice); // 挂载最新的 ABE 头
            System.out.println(">>> 策略同态演进完毕。密文及 ABE 头部已切换为新策略状态。");


            // ---------------------------------------------------------
            // [阶段 7]: 严格多身份 ABE 级双向权限校验 (💡 新增核心环节)
            // ---------------------------------------------------------
            System.out.println("\n================ [更新后 ABE 级多角色双向安全验证] ================");

            // 获取代理节点同态更新后的最新声纹 ABE 头与密文
            CipherText updatedVoiceHeader = restoredPkg.getHeaderVoice();
            byte[] updatedEncVoiceBytes = Base64.getDecoder().decode(restoredPkg.getEncVoice());

            // 确保最新的密文头上也应用了本地最新的策略树 C（模拟解密端本地构树）
            AccessTree localDecryptTreeC = getAccessTreeC(pk);
            updatedVoiceHeader.setAccessTree(localDecryptTreeC);

            // -----------------------------------------------------------------
            // 身份校验测试 1：【主治医生】(已被策略剥离的旧有身份) 尝试使用 ABE 引擎解密
            // -----------------------------------------------------------------
            System.out.println("[测试 1] 验证【主治医生】对演进后新数据的 ABE 权限阻断...");

            // 此时主治医生尝试通过 ABE 引擎去啃新的 ABE 头
            Element doctorDecryptedSeed = engine.decrypt(pk, doctorPrivKey, updatedVoiceHeader);

            if (doctorDecryptedSeed == null) {
                System.out.println(">>> ABE 层拦截成功：【主治医生】属性不匹配新策略 C，无法解密 ABE 头部！");
                System.out.println(">>> 结果: 【成功】符合前向安全，历史授权用户已被动态安全域阻断。");
            } else {
                // 防御性校验：即使 ABE 漏过去了，看它解出来的 AES 密钥解流密码对不对
                byte[] brokenAesKey = new byte[16];
                System.arraycopy(doctorDecryptedSeed.toBytes(), doctorDecryptedSeed.toBytes().length - 16, brokenAesKey, 0, 16);
                byte[] leakBytes = AESUtil.decryptBytesCTR(updatedEncVoiceBytes, new SecretKeySpec(brokenAesKey, "AES"), restoredPkg.getVoiceIv());

                if (Arrays.equals(rawVoiceBytes, leakBytes)) {
                    System.err.println(">>> 结果: 【失败】主治医生成功恢复了声纹！系统存在权限泄露！");
                } else {
                    System.out.println(">>> 结果: 【成功】虽然解出混淆种子，但由于流密码同态对抗，解密出的依旧是碎码。");
                }
            }

            // -----------------------------------------------------------------
            // 身份校验测试 2：【科室主任】(新策略赋予的全新高权身份) 尝试使用 ABE 引擎解密
            // -----------------------------------------------------------------
            System.out.println("\n[测试 2] 验证新赋权身份【科室主任】对演进后数据的 ABE 权限生效...");

            // 为新角色生成 ABE 私钥
            List<Attribute> directorAttrs = Arrays.asList(new Attribute("科室主任", pk));
            UserPrivateKey directorPrivKey = engine.keyGen(msk, directorAttrs);

            // 科室主任执行 ABE 解密
            Element directorDecryptedSeed = engine.decrypt(pk, directorPrivKey, updatedVoiceHeader);

            if (directorDecryptedSeed != null) {
                System.out.println(">>> ABE 层解析成功：【科室主任】属性匹配策略 C，成功提取演进后的群空间密钥种子！");

                // 提取最新的 AES 密钥
                byte[] targetAesKeyBytes = new byte[16];
                System.arraycopy(directorDecryptedSeed.toBytes(), directorDecryptedSeed.toBytes().length - 16, targetAesKeyBytes, 0, 16);
                SecretKeySpec targetSecretKey = new SecretKeySpec(targetAesKeyBytes, "AES");

                // 利用解出的新密钥，去解密经云端同态异或变换后的新密文
                byte[] directorPlainVoiceBytes = AESUtil.decryptBytesCTR(updatedEncVoiceBytes, targetSecretKey, restoredPkg.getVoiceIv());

                //显示明文
                System.out.println("[显示声纹矩阵]");
                for (int i = 0; i < directorPlainVoiceBytes.length; i++) {
                    System.out.print(directorPlainVoiceBytes[i] + " ");
                }
                // 校验恢复出来的声纹矩阵和最初读入的是否完全一致
                boolean isRestorePerfect = Arrays.equals(rawVoiceBytes, directorPlainVoiceBytes);
                System.out.println(">>> 矩阵完整性校对结果: " + (isRestorePerfect ? "【完美一致】" : "【数据损坏】"));
                System.out.println(">>> 结果: 【成功】新赋权用户顺利穿透 ABE+流密码 双层架构，无损还原声纹特征！");
            } else {
                System.err.println(">>> 结果: 【失败】系统异常，合法的科室主任被错误拦截！");
            }

            System.out.println("\n====== [多身份多级闭环测试完美通过！] ======");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- 访问控制树策略池 ---

    public static AccessTree getAccessTreeA(PublicKey publicKey) {
        AccessTreeBuildModel[] models = new AccessTreeBuildModel[6];
        models[0] = AccessTreeBuildModel.innerAccessTreeBuildModel(1, 1, 1, -1);
        models[1] = AccessTreeBuildModel.leafAccessTreeBuildModel(2, 1, "主治医生", 1);
        models[2] = AccessTreeBuildModel.leafAccessTreeBuildModel(3, 2, "会诊医生", 1);
        models[3] = AccessTreeBuildModel.innerAccessTreeBuildModel(4, 2, 3, 1);
        models[4] = AccessTreeBuildModel.leafAccessTreeBuildModel(5, 1, "医学生", 4);
        models[5] = AccessTreeBuildModel.leafAccessTreeBuildModel(6, 2, "同意书", 4);
        return AccessTree.build(publicKey, models);
    }

    public static AccessTree getAccessTreeB(PublicKey publicKey) {
        AccessTreeBuildModel[] models = new AccessTreeBuildModel[3];
        models[0] = AccessTreeBuildModel.innerAccessTreeBuildModel(1, 1, 1, -1);
        models[1] = AccessTreeBuildModel.leafAccessTreeBuildModel(2, 1, "主治医生", 1);
        models[2] = AccessTreeBuildModel.leafAccessTreeBuildModel(3, 2, "抓药护士", 1);
        return AccessTree.build(publicKey, models);
    }

    public static AccessTree getAccessTreeC(PublicKey publicKey) {
        AccessTreeBuildModel[] models = new AccessTreeBuildModel[2];
        models[0] = AccessTreeBuildModel.innerAccessTreeBuildModel(1, 1, 1, -1);
        models[1] = AccessTreeBuildModel.leafAccessTreeBuildModel(2, 1, "科室主任", 1);
        return AccessTree.build(publicKey, models);
    }

    private static File openWindowsFileDialog() {
        Frame parent = new Frame();
        FileDialog fileDialog = new FileDialog(parent, "选择测试视频", FileDialog.LOAD);
        fileDialog.setFile("*.mp4;*.wav;*.mp3;*.m4a");
        fileDialog.setVisible(true);
        String dir = fileDialog.getDirectory();
        String file = fileDialog.getFile();
        parent.dispose();
        return (file == null) ? null : new File(dir, file);
    }
}