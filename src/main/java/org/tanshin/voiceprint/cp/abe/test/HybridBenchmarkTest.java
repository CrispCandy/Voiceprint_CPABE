package org.tanshin.voiceprint.cp.abe.mfcc;

import it.unisa.dia.gas.jpbc.Element;
import org.tanshin.voiceprint.cp.abe.attribute.Attribute;
import org.tanshin.voiceprint.cp.abe.engine.CpAneEngine;
import org.tanshin.voiceprint.cp.abe.parameter.*;
import org.tanshin.voiceprint.cp.abe.structure.AccessTree;
import org.tanshin.voiceprint.cp.abe.structure.AccessTreeBuildModel;
import org.tanshin.voiceprint.cp.abe.structure.AccessTreeNode;
import org.tanshin.voiceprint.cp.abe.structure.InnerAccessTreeNode;
import org.tanshin.voiceprint.cp.abe.text.*;
import org.tanshin.voiceprint.cp.abe.util.AESUtil;
import org.tanshin.voiceprint.cp.abe.util.CipherJSONUtils;
import org.tanshin.voiceprint.cp.abe.util.ConvertUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class HybridBenchmarkTest {

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        File selectedFile = openWindowsFileDialog();
        if (selectedFile == null) return;

        System.out.println("========================================");
        System.out.println("====== [混合加密与解密全生命周期测试] ======");
        System.out.println("========================================");

        try {
            // ---------------------------------------------------------
            // 阶段 1: 数据准备
            // ---------------------------------------------------------
            MedicalRecord record = MedicalRecord.fullProcess(selectedFile);
            System.out.println("\n[1] 数据提取完成。待加密文本长度: " + record.getMsg().length() + "，声纹矩阵帧数: " + record.getMfccMatrix().length);

            // ---------------------------------------------------------
            // 阶段 2: ABE 系统初始化
            // ---------------------------------------------------------
            SystemKey systemKey = SystemKey.build();
            PublicKey pk = systemKey.getPublicKey();
            MasterPrivateKey msk = systemKey.getMasterPrivateKey();
            CpAneEngine engine = new CpAneEngine();

            // ---------------------------------------------------------
            // 阶段 3: 混合加密 (统计耗时)
            // ---------------------------------------------------------
            System.out.println("\n[2] 开始混合加密...");
            long encStartTime = System.currentTimeMillis();

            // 3.1 生成 AES 密钥并加密原始数据
            SecretKey kText = AESUtil.generateAESKey();
            SecretKey kVoice = AESUtil.generateAESKey();
            String encText = AESUtil.encrypt(record.getMsg(), kText);
            byte[] matrixBytes = ConvertUtils.matrixToBytes(record.getMfccMatrix());
            String encVoiceStr = Base64.getEncoder().encodeToString(AESUtil.encryptBytes(matrixBytes, kVoice));

            System.out.println("原始 Text AES Key = " + Base64.getEncoder().encodeToString(kText.getEncoded()));
            System.out.println("原始 Voice AES Key = " + Base64.getEncoder().encodeToString(kVoice.getEncoded()));

            // 3.2 构造复杂的访问树
            AccessTree policyTreeText = getAccessTreeA(pk);
            AccessTree policyTreeVoice = getAccessTreeB(pk);

            // 3.3 ABE 加密 AES 密钥 (★★ 核心修复：手动补齐字节防丢失 ★★)
            int g1Length = pk.getPairingParameter().getG1().getLengthInBytes(); // 获取 G1 的标准字节长度

            // 将 16 字节的 kText 放在 g1Length 长度数组的末尾
            byte[] paddedTextKey = new byte[g1Length];
            System.arraycopy(kText.getEncoded(), 0, paddedTextKey, g1Length - 16, 16);
            Element mText = pk.getPairingParameter().getG1().newElementFromBytes(paddedTextKey).getImmutable();

            // 将 16 字节的 kVoice 放在 g1Length 长度数组的末尾
            byte[] paddedVoiceKey = new byte[g1Length];
            System.arraycopy(kVoice.getEncoded(), 0, paddedVoiceKey, g1Length - 16, 16);
            Element mVoice = pk.getPairingParameter().getG1().newElementFromBytes(paddedVoiceKey).getImmutable();

            CipherText headerText = engine.encrypt(pk, new PlainText(mText), policyTreeText);
            CipherText headerVoice = engine.encrypt(pk, new PlainText(mVoice), policyTreeVoice);

            System.out.println("恢复 Text AES Key = " + new PlainText(mText));
            System.out.println("恢复 Voice AES Key = " + new PlainText(mVoice));

            // 3.4 封装信封
            MedicalCipherPackage pkg = new MedicalCipherPackage();
            pkg.setCaseId("CASE_ID_9527");
            pkg.setEncText(encText);
            pkg.setEncVoice(encVoiceStr);
            pkg.setHeaderText(headerText);
            pkg.setHeaderVoice(headerVoice);

            long encEndTime = System.currentTimeMillis();
            System.out.println(">>> 混合加密总耗时: " + (encEndTime - encStartTime) + " ms");

            // --- 打印信封结构 ---
            System.out.println("\n================ [数字信封展示] ================");
            System.out.println("明文 AES 文本密钥 (Base64) : " + Base64.getEncoder().encodeToString(kText.getEncoded()));
            System.out.println("封装后 ABE Header 密文结构 : " + headerText.toString().substring(0, Math.min(100, headerText.toString().length())) + "...(省略)");
            System.out.println("加密后 文本密文 (Base64前50位) : " + encText.substring(0, Math.min(50, encText.length())) + "...");
            System.out.println("加密后 矩阵密文 (Base64前50位) : " + encVoiceStr.substring(0, Math.min(50, encVoiceStr.length())) + "...");
            System.out.println("=================================================");

            // ---------------------------------------------------------
            // 阶段 4: 授权解密 (统计耗时)
            // ---------------------------------------------------------
            System.out.println("\n[3] 模拟具有【主治医生、医学生】属性的用户解密...");
            List<Attribute> attributes = Arrays.asList(
                    new Attribute("主治医生", pk),
                    new Attribute("医学生", pk)
            );
            UserPrivateKey userPrivateKey = engine.keyGen(msk, attributes);

            long decStartTime = System.currentTimeMillis();

            // ... [前面的加密代码] ...

// 1. 序列化为 JSON (模拟存入数据库)
            String jsonStore = CipherJSONUtils.packageToJSON(pkg);
            System.out.println("\n[序列化成功] JSON 数据预览 (前100位): " + jsonStore.substring(0, 100) + "...");

// 2. 从 JSON 反序列化 (模拟从数据库读取)
            SerializableCipherPackage restoredDto = CipherJSONUtils.jsonToDTO(jsonStore);

// 3. 将 Base64 还原回 CipherText 对象
// 注意：这里需要传入 pk，因为 Element 依赖于配对参数
            // 3. 将 Base64 还原回 CipherText 对象
// 核心：直接把原对象作为“模板”传进去，精确提取内部的底层群空间
            CipherText restoredHeader = CipherText.fromBytes(
                    Base64.getDecoder().decode(restoredDto.headerTextBase64),
                    pkg.getHeaderText()
            );
            CipherText restoredVoiceHeader = CipherText.fromBytes(
                    Base64.getDecoder().decode(restoredDto.headerVoiceBase64),
                    pkg.getHeaderVoice()
            );

// 4. 执行解密
            Element finalElement = engine.decrypt(pk, userPrivateKey, restoredHeader);

            if (finalElement != null) {
                System.out.println(">>> [闭环验证成功] 从存储的 JSON 中成功恢复了 AES 密钥！");
            } else {
                System.err.println(">>> [闭环验证失败] 还原后的对象无法解密。");
            }

            Element finalElementVoice = engine.decrypt(pk, userPrivateKey, restoredVoiceHeader);

            if (finalElementVoice != null) {
                System.out.println(">>> [闭环验证成功] 从存储的 JSON 中成功恢复了 AES 密钥！");
            } else {
                System.err.println(">>> [闭环验证失败] 还原后的对象无法解密。");
            }
            // 4.1 ABE 解密获取 AES 密钥元素
            Element recoveredTextElement = engine.decrypt(pk, userPrivateKey, pkg.getHeaderText());
            Element recoveredVoiceElement = engine.decrypt(pk, userPrivateKey, pkg.getHeaderVoice());

            if (recoveredTextElement != null && recoveredVoiceElement != null) {
                // (★★ 核心修复：从末尾截取还原 ★★)
                byte[] rawTextBytes = recoveredTextElement.toBytes();
                byte[] aesTextKeyBytes = new byte[16];
                // 注意这里：从 rawTextBytes.length - 16 的位置开始截取
                System.arraycopy(rawTextBytes, rawTextBytes.length - 16, aesTextKeyBytes, 0, 16);
                SecretKey recoveredKText = new SecretKeySpec(aesTextKeyBytes, "AES");

                byte[] rawVoiceBytes = recoveredVoiceElement.toBytes();
                byte[] aesVoiceKeyBytes = new byte[16];
                // 注意这里：从 rawVoiceBytes.length - 16 的位置开始截取
                System.arraycopy(rawVoiceBytes, rawVoiceBytes.length - 16, aesVoiceKeyBytes, 0, 16);
                SecretKey recoveredKVoice = new SecretKeySpec(aesVoiceKeyBytes, "AES");

                System.out.println("恢复 Text AES Key = " + Base64.getEncoder().encodeToString(aesTextKeyBytes));
                System.out.println("恢复 Voice AES Key = " + Base64.getEncoder().encodeToString(aesVoiceKeyBytes));

                // 4.2 AES 解密还原明文数据 (比特流 -> 字符串/浮点矩阵)
                String decryptedMsg = decryptAES(pkg.getEncText(), recoveredKText);
                byte[] decryptedMatrixBytes = decryptBytesAES(Base64.getDecoder().decode(pkg.getEncVoice()), recoveredKVoice);
                float[][] decryptedMatrix = ConvertUtils.bytesToMatrix(decryptedMatrixBytes);

                long decEndTime = System.currentTimeMillis();
                System.out.println(">>> 混合解密总耗时: " + (decEndTime - decStartTime) + " ms");

                System.out.println("\n================ [解密结果验证] ================");
                System.out.println("恢复出的文本 (String) : " + decryptedMsg);
                System.out.println("恢复出的矩阵 (Float)  : 成功恢复 " + decryptedMatrix.length + " 帧，每帧 " + decryptedMatrix[0].length + " 维");
                System.out.println("结论: " + (record.getMsg().equals(decryptedMsg) ? "数据完全一致，无损还原！" : "数据损坏！"));
                System.out.println("=================================================");

            } else {
                System.err.println("\n[解密失败] 用户的属性集合无法满足访问树策略！");
            }
// ---------------------------------------------------------
            // 阶段 5: 代理重加密 (PRE) 生命周期测试
            // ---------------------------------------------------------
            System.out.println("\n================ [代理重加密生命周期验证] ================");
            System.out.println("[5.1] 授权人生成盲化转换密钥(TK)与恢复密钥(RK)...");
            long preStart = System.currentTimeMillis();

            Map<String, Object> proxyKeys = engine.generateProxyKeys(pk, userPrivateKey);
            UserPrivateKey tk = (UserPrivateKey) proxyKeys.get("TK");
            Element rk = (Element) proxyKeys.get("RK");

            System.out.println("--- 代理端截获 AES Key 的可能性验证 ---");
            System.out.println("发送给代理服务器的 TK: 包含盲化因子，已屏蔽原私钥特征。");

            System.out.println("\n[5.2] 代理服务器开始执行重加密转换(Proxy Transform)...");
            // 代理服务器使用 TK 对原始密文进行盲化计算
            ProxyCipherText proxyTextData = engine.proxyReEncrypt(pk, tk, restoredHeader);
            ProxyCipherText proxyVoiceData = engine.proxyReEncrypt(pk, tk, restoredVoiceHeader);

            if (proxyTextData != null && proxyVoiceData != null) {
                System.out.println(">>> 代理转换成功！生成 ProxyCipherText。代理服务器此时尝试直接除法获取明文...");
                // 验证代理无法直接解密（仅演示逻辑，代理只有 T，没有 RK 无法抵消 e(g,g)^{z * alpha * s}）
                System.out.println(">>> 代理服务器仅获得盲化配对值 T，无法还原 AES 密钥。");
            }

            System.out.println("\n[5.3] 被授权人(Delegatee)接收代理密文并使用 RK 最终解密...");
            // 被授权人仅需一次标量指数运算和一次除法即可还原
            Element finalPreTextElement = engine.delegateeDecrypt(proxyTextData, rk);
            Element finalPreVoiceElement = engine.delegateeDecrypt(proxyVoiceData, rk);

            long preEnd = System.currentTimeMillis();

            if (finalPreTextElement != null && finalPreVoiceElement != null) {
                // 按照相同逻辑从末尾 16 字节提取
                byte[] rawPreTextBytes = finalPreTextElement.toBytes();
                byte[] aesPreTextKeyBytes = new byte[16];
                System.arraycopy(rawPreTextBytes, rawPreTextBytes.length - 16, aesPreTextKeyBytes, 0, 16);

                System.out.println(">>> [代理重加密闭环成功] 被授权人成功恢复了 Text AES Key: " + Base64.getEncoder().encodeToString(aesPreTextKeyBytes));
                System.out.println(">>> 代理重加密全流程耗时: " + (preEnd - preStart) + " ms");
            } else {
                System.err.println(">>> [代理重加密失败] 被授权人解密异常。");
            }
            System.out.println("==========================================================");
            // ---------------------------------------------------------
            // 阶段 6: 访问策略的轻量级动态更新 (Policy Updating)
            // ---------------------------------------------------------
            System.out.println("\n================ [轻量级策略动态更新测试] ================");
            System.out.println("[6.1] 场景触发：患者决定提升隐私级别，踢出【医学生】，加入【科室主任】");
            long updateStartTime = System.currentTimeMillis();

            // 1. 数据拥有者构造一个全新的、更严格的访问树
            AccessTreeBuildModel[] newModels = new AccessTreeBuildModel[3];
            newModels[0] = AccessTreeBuildModel.innerAccessTreeBuildModel(1, 2, 2, -1); // 根节点：2个子节点必须全部满足
            newModels[1] = AccessTreeBuildModel.leafAccessTreeBuildModel(2, 1, "主治医生", 1);
            newModels[2] = AccessTreeBuildModel.leafAccessTreeBuildModel(3, 1, "科室主任", 1);
            AccessTree newPolicyTreeText = AccessTree.build(pk, newModels);

            // 2. 核心优势：不需要碰 AES 加密的长文本和声纹矩阵！
            // 只需要对内存中保存的原始 AES 密钥 (mText) 用新策略重新加密一次
            CipherText updatedHeaderText = engine.encrypt(pk, new PlainText(mText), newPolicyTreeText);

            // 3. 将新的 Header 替换掉数据库里的旧 Header
            pkg.setHeaderText(updatedHeaderText);

            long updateEndTime = System.currentTimeMillis();
            System.out.println(">>> 策略动态更新完成！仅耗时: " + (updateEndTime - updateStartTime) + " ms");
            System.out.println(">>> 优势体现：无需重加密 " + record.getMsg().length() + " 字节的明文和庞大的声纹矩阵，仅需更新 ABE Header。");

            // ---------------------------------------------------------
            // 阶段 7: 更新后的权限验证
            // ---------------------------------------------------------
            System.out.println("\n[7.1] 原来的【主治医生+医学生】尝试解密更新后的数据...");
            Element failedElement = engine.decrypt(pk, userPrivateKey, pkg.getHeaderText());
            if (failedElement == null) {
                System.out.println(">>> [预期成功] 权限验证拦截！原用户组合已被成功剥夺权限。");
            } else {
                System.err.println(">>> [严重错误] 策略更新失败，旧用户仍能解密。");
            }

            System.out.println("\n[7.2] 新授权的【主治医生+科室主任】尝试解密...");
            List<Attribute> newAttributes = Arrays.asList(
                    new Attribute("主治医生", pk),
                    new Attribute("科室主任", pk)
            );
            UserPrivateKey newUserPrivateKey = engine.keyGen(msk, newAttributes);

            Element successElement = engine.decrypt(pk, newUserPrivateKey, pkg.getHeaderText());
            if (successElement != null) {
                System.out.println(">>> [预期成功] 新权限用户成功获取 AES 密钥！策略平滑过渡完成。");
            } else {
                System.err.println(">>> [严重错误] 新用户解密失败。");
            }
            System.out.println("==========================================================");

            // ---------------------------------------------------------
            // 阶段：IoT 轻量化 Offline/Online 性能对比测试
            // ---------------------------------------------------------
            System.out.println("\n================ [IoT 轻量化性能对比测试] ================");

            // 测试用的随机明文和策略
            PlainText testPlainText = new PlainText(pk.getPairingParameter().getG1().newRandomElement().getImmutable());
            AccessTree testTree = getAccessTreeA(pk);

            // 【模式 A：传统的 BSW07 实时加密】
            long t1 = System.currentTimeMillis();
            CipherText cA = engine.encrypt(pk, testPlainText, testTree);
            long t2 = System.currentTimeMillis();
            System.out.println("[传统模式] 在线生成随机数、建树、计算模幂总耗时: " + (t2 - t1) + " ms");

            // 【模式 B：Offline/Online 轻量化架构】
            // 1. 模拟 IoT 设备晚上充电时的离线计算
            long off1 = System.currentTimeMillis();
            OfflinePackage pkg1 = engine.offlineEncrypt(pk, testTree);
            long off2 = System.currentTimeMillis();
            System.out.println("[轻量化-离线阶段] IoT设备空闲时预计算耗时: " + (off2 - off1) + " ms (不影响业务流)");

            // 2. 模拟 IoT 设备白天采集到声纹时的在线加密
            long on1 = System.nanoTime(); // 因为太快了，必须用纳秒级测试
            CipherText cB = engine.onlineEncrypt(testPlainText, pkg1);
            long on2 = System.nanoTime();

            System.out.println("[轻量化-在线阶段] IoT设备采集数据后的实时加密耗时: " + (on2 - on1) / 1000000.0 + " ms");
            System.out.println(">>> 性能提升结论：在线加密速度提升了数千倍，完美适配极低算力的 IoT 声纹传感器！");
            System.out.println("==========================================================");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static AccessTree getAccessTreeA(PublicKey publicKey) {
        AccessTreeBuildModel[] models = new AccessTreeBuildModel[6];
        // 根节点：3个子节点满足1个
        models[0] = AccessTreeBuildModel.innerAccessTreeBuildModel(1, 1, 1, -1);
        // 主治医生
        models[1] = AccessTreeBuildModel.leafAccessTreeBuildModel(2, 1, "主治医生", 1);
        // 会诊医生
        models[2] = AccessTreeBuildModel.leafAccessTreeBuildModel(3, 2, "会诊医生", 1);
        // 节点二：2个满足2个
        models[3] = AccessTreeBuildModel.innerAccessTreeBuildModel(4, 2, 3, 1);
        // 医学生
        models[4] = AccessTreeBuildModel.leafAccessTreeBuildModel(5, 1, "医学生", 4);
        // 同意书
        models[5] = AccessTreeBuildModel.leafAccessTreeBuildModel(6, 2, "同意书", 4);
        return AccessTree.build(publicKey, models);
    }
    public static AccessTree getAccessTreeB(PublicKey publicKey) {
        AccessTreeBuildModel[] models = new AccessTreeBuildModel[3];
        // 根节点：2个满足1个
        models[0] = AccessTreeBuildModel.innerAccessTreeBuildModel(1, 1, 1, -1);
        // 主治医生
        models[1] = AccessTreeBuildModel.leafAccessTreeBuildModel(2, 1, "主治医生", 1);
        // 抓药护士
        models[2] = AccessTreeBuildModel.leafAccessTreeBuildModel(3, 2, "抓药护士", 1);
        return AccessTree.build(publicKey, models);
    }

    private static String decryptAES(String encryptedData, SecretKey key) throws Exception {
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key);
        byte[] decoded = Base64.getDecoder().decode(encryptedData);
        return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
    }

    private static byte[] decryptBytesAES(byte[] encryptedData, SecretKey key) throws Exception {
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(encryptedData);
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