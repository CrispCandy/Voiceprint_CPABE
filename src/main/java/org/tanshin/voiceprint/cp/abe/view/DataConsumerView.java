package org.tanshin.voiceprint.cp.abe.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.UIScope;
import io.ipfs.api.IPFS;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;
import it.unisa.dia.gas.jpbc.Element;
import org.tanshin.voiceprint.cp.abe.attribute.Attribute;
import org.tanshin.voiceprint.cp.abe.entity.AuditLog;
import org.tanshin.voiceprint.cp.abe.entity.DataResource;
import org.tanshin.voiceprint.cp.abe.parameter.PublicKey;
import org.tanshin.voiceprint.cp.abe.parameter.UserPrivateKey;
import org.tanshin.voiceprint.cp.abe.repository.AuditLogRepository;
import org.tanshin.voiceprint.cp.abe.repository.DataResourceRepository;
import org.tanshin.voiceprint.cp.abe.service.OfflinePoolManager;
import org.tanshin.voiceprint.cp.abe.structure.AccessTree;
import org.tanshin.voiceprint.cp.abe.structure.AccessTreeBuildModel;
import org.tanshin.voiceprint.cp.abe.text.CipherText;
import org.tanshin.voiceprint.cp.abe.text.MedicalCipherPackage;
import org.tanshin.voiceprint.cp.abe.text.PlainText;
import org.tanshin.voiceprint.cp.abe.text.SerializableCipherPackage;
import org.tanshin.voiceprint.cp.abe.util.AESUtil;
import org.tanshin.voiceprint.cp.abe.util.CipherJSONUtils;
import org.tanshin.voiceprint.cp.abe.util.ConvertUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value = "consumer", layout = MainView.class)
@UIScope
public class DataConsumerView extends VerticalLayout {

    // ✨ 新增：类级别的静态全局变量，用于存放系统启动时随机生成的 4 位数 Token
    private static final String CONSULT_TOKEN; // 会诊医生
    private static final String CHIEF_TOKEN;   // 主治医生

    // ✨ 新增：静态代码块，在类加载时自动生成 1000~9999 之间的随机数
    static {
        Random random = new Random();
        CONSULT_TOKEN = String.valueOf(random.nextInt(9000) + 1000);
        CHIEF_TOKEN = String.valueOf(random.nextInt(9000) + 1000);

        System.out.println("🔒 [KGC 动态授权中心] 启动成功，当前随机安全 Token 已更新：");
        System.out.println("👉 [主治医生] 授权码: " + CHIEF_TOKEN);
        System.out.println("👉 [会诊医生] 授权码: " + CONSULT_TOKEN);
        System.out.println("---------------------------------------------------------");
    }

    public static MedicalCipherPackage lastPackageTemplate;

    private final DataResourceRepository resourceRepository;
    private final AuditLogRepository auditLogRepo;
    private final OfflinePoolManager poolManager;

    private final TextField employeeIdField;
    private final PasswordField tokenField;
    private final Grid<DataResource> grid;

    public static class DecryptResult {
        public String text;
        public float[][] matrix;
        public DecryptResult(String text, float[][] matrix) {
            this.text = text;
            this.matrix = matrix;
        }
    }

    public DataConsumerView(DataResourceRepository resourceRepository,
                            AuditLogRepository auditLogRepo,
                            OfflinePoolManager poolManager) {
        this.resourceRepository = resourceRepository;
        this.auditLogRepo = auditLogRepo;
        this.poolManager = poolManager;

        setSizeFull();
        setSpacing(true);

        add(new H3("🩺 医疗终端安全接入认证"));
        Span info = new Span("说明：系统基于ABE动态授权。输入工号模拟刷卡，输入特定授权码可获取'会诊医生'或'主治医生'权限。");
        info.getStyle().set("color", "gray").set("font-size", "0.9em");
        add(info);

        employeeIdField = new TextField("医护工号");
        employeeIdField.setPlaceholder("如: DOC-001, NUR-001, STU-001");
        employeeIdField.setWidth("250px");

        tokenField = new PasswordField("动态授权码");
        tokenField.setPlaceholder("需要高级权限时填写");
        tokenField.setWidth("250px");

        add(new HorizontalLayout(employeeIdField, tokenField));
        add(new Hr());

        add(new H3("📂 医疗病历数据资源池"));
        grid = new Grid<>(DataResource.class, false);

        grid.addColumn(DataResource::getResourceName).setHeader("资源名称").setAutoWidth(true);
        grid.addColumn(DataResource::getIpfsCid).setHeader("CID").setAutoWidth(true);
        grid.addColumn(r -> r.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).setHeader("创建时间");
        grid.addComponentColumn(this::createActionButtons).setHeader("操作").setWidth("300px");

        refreshGrid();
        add(grid);
    }

    private void refreshGrid() {
        grid.setItems(resourceRepository.findAll());
    }

    private Set<String> requestAttributesFromKGC(String empId, String token) {
        Set<String> attributes = new HashSet<>();
        if (empId == null || empId.trim().isEmpty()) return attributes;

        String upperId = empId.toUpperCase();
        if (upperId.startsWith("DOC")) {
            if (CONSULT_TOKEN.equals(token)) {
                attributes.add("会诊医生");
            } else if(CHIEF_TOKEN.equals(token)){
                attributes.add("主治医生");
            } else {
                attributes.add("在职医生");
            }
        } else if (upperId.startsWith("NUR")) {
            attributes.add("抓药护士");
        } else if (upperId.startsWith("STU")) {
            attributes.add("医学生");
        } else if (upperId.startsWith("AI")) {
            attributes.add("AI节点");
        } else if (upperId.startsWith("DEAN")) {
            attributes.add("院长");
        }
        return attributes;
    }

    private HorizontalLayout createActionButtons(DataResource item) {
        Button decryptBtn = new Button("请求授权解密");
        decryptBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        decryptBtn.addClickListener(e -> handleDecrypt(item, decryptBtn));

        Button updatePolicyBtn = new Button("修改策略");
        updatePolicyBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        updatePolicyBtn.addClickListener(e -> handlePolicyUpdate(item));

        return new HorizontalLayout(decryptBtn, updatePolicyBtn);
    }

    private void handleDecrypt(DataResource item, Button btn) {
// 如果这个 stream 和你解密出来的 encText 的 Keystream 不一致，那就是 Key 或 IV 传错了！
        String empId = employeeIdField.getValue();
        String token = tokenField.getValue();

        Set<String> roles = requestAttributesFromKGC(empId, token);
        if (roles.isEmpty()) {
            showError("无效工号，系统拒绝接入！");
            return;
        }

        var ui = btn.getUI().get();
        btn.setEnabled(false);

        Notification.show("KGC 动态授权属性: " + roles, 3000, Notification.Position.TOP_END);

        new Thread(() -> {
            boolean isSuccess = false;
            long start = System.currentTimeMillis();
            try {
                List<Attribute> userAttrs = new ArrayList<>();
                for (String role : roles) {
                    userAttrs.add(new Attribute(role, DataOwnerView.AbeContext.pk));
                }
                UserPrivateKey userPriv = DataOwnerView.AbeContext.engine.keyGen(DataOwnerView.AbeContext.msk, userAttrs);

                IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
                byte[] downloadedData = ipfs.cat(Multihash.fromBase58(item.getIpfsCid()));
                String downloadedJson = new String(downloadedData, StandardCharsets.UTF_8);

                SerializableCipherPackage dto = CipherJSONUtils.jsonToDTO(downloadedJson);
                CipherText resHeaderText = CipherText.fromBytes(Base64.getDecoder().decode(dto.headerTextBase64), lastPackageTemplate.getHeaderText());
                CipherText resVoiceHeader = CipherText.fromBytes(Base64.getDecoder().decode(dto.headerVoiceBase64), lastPackageTemplate.getHeaderVoice());

                Element eleText = DataOwnerView.AbeContext.engine.decrypt(DataOwnerView.AbeContext.pk, userPriv, resHeaderText);
                Element eleVoice = DataOwnerView.AbeContext.engine.decrypt(DataOwnerView.AbeContext.pk, userPriv, resVoiceHeader);

                String textResult = null;
                float[][] voiceResult = null;

                // ✨ 核心升级：替换掉原先的 ECB 模式，改用安全的 CTR 模式解密
                if (eleText != null) {
                    SecretKey skT = unpadElementToKey(eleText);
                    byte[] encTextBytes = Base64.getDecoder().decode(dto.encText);

                    // --- 🛠️ 插入调试代码 ---
                    System.out.println("--- 🔍 文本解密调试 ---");
                    System.out.println("1. 密文 (Base64): " + dto.encText);
                    System.out.println("2. 密文长度: " + encTextBytes.length);
                    System.out.println("3. IV (Base64): " + Base64.getEncoder().encodeToString(dto.textIv));
                    System.out.println("4. 密钥 (Base64): " + Base64.getEncoder().encodeToString(skT.getEncoded()));

                    // 尝试用底层原生 Cipher 验证一次
                    try {
                        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding");
                        c.init(javax.crypto.Cipher.DECRYPT_MODE, skT, new javax.crypto.spec.IvParameterSpec(dto.textIv));
                        byte[] test = c.doFinal(encTextBytes);
                        System.out.println("5. 解密结果预览: " + new String(test, StandardCharsets.UTF_8));
                    } catch (Exception e) {
                        System.err.println("❌ 调试解密失败: " + e.getMessage());
                    }
                    System.out.println("----------------------");
                    // --------------------------

                    // 使用 AESUtil.decryptBytesCTR 和 DTO 中存储的 IV
                    byte[] rawTextBytes = AESUtil.decryptBytesCTR(encTextBytes, skT, dto.textIv);
                    textResult = new String(rawTextBytes, StandardCharsets.UTF_8);
                }

                if (eleVoice != null) {
                    SecretKey skV = unpadElementToKey(eleVoice);
                    byte[] encVoiceBytes = Base64.getDecoder().decode(dto.encVoice);
                    // 使用 AESUtil.decryptBytesCTR 和 DTO 中存储的 IV
                    byte[] vBytes = AESUtil.decryptBytesCTR(encVoiceBytes, skV, dto.voiceIv);
                    voiceResult = ConvertUtils.bytesToMatrix(vBytes);
                }

                if (eleText == null && eleVoice == null) {
                    throw new RuntimeException("访问策略不匹配，无法获取任何解密密钥！");
                }

                long time = System.currentTimeMillis() - start;
                DecryptResult finalResult = new DecryptResult(textResult, voiceResult);

                ui.access(() -> {
                    btn.setEnabled(true);
                    showDualResultDialog(item, finalResult, time, roles);
                });
                isSuccess = true;
            } catch (Exception ex) {
                ui.access(() -> {
                    btn.setEnabled(true);
                    showError("解密失败：" + ex.getMessage());
                });
                ex.printStackTrace();
            } finally {
                AuditLog log = new AuditLog();
                log.setOpTime(LocalDateTime.now());
                log.setActionType("ABE_DECRYPT_ATTEMPT");
                log.setTargetCid(item.getIpfsCid());
                log.setOperatorName(empId + " (Roles: " + roles + ")");
                log.setSuccess(isSuccess);
                auditLogRepo.save(log);
            }
        }).start();
    }

    private void handlePolicyUpdate(DataResource item) {
        String empId = employeeIdField.getValue();
        String token = tokenField.getValue();

        Set<String> roles = requestAttributesFromKGC(empId, token);

        if (!roles.contains("主治医生")) {
            showError("权限拒绝：仅【主治医生】有权限动态修改访问策略！");
            return;
        }

        Dialog policyDialog = new Dialog();
        policyDialog.setHeaderTitle("⚙️ 动态修改访问策略树 (密文同态)");
        policyDialog.setWidth("500px");

        VerticalLayout layout = new VerticalLayout();
        layout.add(new Span("当前操作身份：主治医生 (" + empId + ")"));
        layout.add(new Span("系统将采用同态盲差 (Delta-XOR) 技术完成密钥轮换，无需传输原明文。"));
        layout.add(new Hr());

        MultiSelectComboBox<String> msgPolicyBox = new MultiSelectComboBox<>("新文字访问策略");
        msgPolicyBox.setItems("主治医生", "会诊医生", "院长", "医学生", "抓药护士", "AI节点");
        msgPolicyBox.setWidthFull();
        if (item.getMsgAccessPolicy() != null) {
            msgPolicyBox.setValue(Set.of(item.getMsgAccessPolicy().split(" OR ")));
        }

        MultiSelectComboBox<String> voicePolicyBox = new MultiSelectComboBox<>("新声纹访问策略");
        voicePolicyBox.setItems("主治医生", "会诊医生", "院长", "医学生", "抓药护士", "AI节点");
        voicePolicyBox.setWidthFull();
        if (item.getVoiceAccessPolicy() != null) {
            voicePolicyBox.setValue(Set.of(item.getVoiceAccessPolicy().split(" OR ")));
        }

        layout.add(msgPolicyBox, voicePolicyBox);
        policyDialog.add(layout);

        Button saveBtn = new Button("执行同态重加密", e -> {
            Set<String> newMsgRoles = msgPolicyBox.getValue();
            Set<String> newVoiceRoles = voicePolicyBox.getValue();

            if (newMsgRoles.isEmpty() || newVoiceRoles.isEmpty()) {
                showError("策略不能为空！");
                return;
            }

            policyDialog.close();
            executeReEncryption(item, empId, roles, newMsgRoles, newVoiceRoles);
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelBtn = new Button("取消", e -> policyDialog.close());

        policyDialog.getFooter().add(cancelBtn, saveBtn);
        policyDialog.open();
    }

    // ✨ 核心升级：实现前向安全的同态盲差重加密 (Forward Secrecy via Delta-XOR)
    // ✨ 核心升级：实现前向安全的同态盲差重加密 (Key 与 IV 双重轮换)
    private void executeReEncryption(DataResource item, String empId, Set<String> roles, Set<String> newMsgRoles, Set<String> newVoiceRoles) {
        var ui = getUI().get();
        Notification.show("正在计算密钥与IV演进增量并执行同态异或，请稍候...", 4000, Notification.Position.TOP_CENTER);

        if (lastPackageTemplate == null) {
            showError("模板缺失！请先在 Owner 页面执行一次加密后再尝试重加密。");
            return;
        }

        new Thread(() -> {
            boolean isSuccess = false;
            String updatedCid = item.getIpfsCid();
            try {
                // 1. 生成主治医生私钥
                List<Attribute> userAttrs = new ArrayList<>();
                for (String role : roles) {
                    userAttrs.add(new Attribute(role, DataOwnerView.AbeContext.pk));
                }
                UserPrivateKey userPriv = DataOwnerView.AbeContext.engine.keyGen(DataOwnerView.AbeContext.msk, userAttrs);

                // 2. 从 IPFS 拉取原密文结构
                IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
                byte[] downloadedData = ipfs.cat(Multihash.fromBase58(item.getIpfsCid()));
                SerializableCipherPackage oldDto = CipherJSONUtils.jsonToDTO(new String(downloadedData, StandardCharsets.UTF_8));

                boolean policyChanged = false;

                MedicalCipherPackage newPkg = new MedicalCipherPackage();
                newPkg.setCaseId(UUID.randomUUID().toString());
                newPkg.setTextDataLength(oldDto.textDataLength);
                newPkg.setVoiceDataLength(oldDto.voiceDataLength);
                newPkg.setTextIv(oldDto.textIv);
                newPkg.setVoiceIv(oldDto.voiceIv);

                // ================== [ 📄 文字策略重加密 ] ==================
                String newMsgPolicyStr = String.join(" OR ", newMsgRoles);
                if (!newMsgPolicyStr.equals(item.getMsgAccessPolicy())) {
                    CipherText oldHeader = CipherText.fromBytes(Base64.getDecoder().decode(oldDto.headerTextBase64), lastPackageTemplate.getHeaderText());
                    Element eleText = DataOwnerView.AbeContext.engine.decrypt(DataOwnerView.AbeContext.pk, userPriv, oldHeader);
                    if (eleText == null) throw new RuntimeException("权限不足：无法解开原文本密钥！");

                    SecretKey oldKText = unpadElementToKey(eleText);

                    // ✨【核心修复】: 先从 G1 群生成合法的随机元素，再衍生出 AES 密钥，确保 100% 自洽
                    Element randElementText = DataOwnerView.AbeContext.pk.getPairingParameter().getG1().newRandomElement().getImmutable();
                    SecretKey newKText = unpadElementToKey(randElementText);
                    byte[] newTextIv = generateNewIv();

                    // 执行同态盲差异或
                    byte[] oldEncTextBytes = Base64.getDecoder().decode(oldDto.encText);
                    byte[] newEncTextBytes = computeHomomorphicXOR(oldEncTextBytes, oldKText, newKText, oldDto.textIv, newTextIv);

                    newPkg.setEncText(Base64.getEncoder().encodeToString(newEncTextBytes));
                    newPkg.setTextIv(newTextIv);

                    // 包装新密钥树
                    AccessTree newTree = buildOrTree(DataOwnerView.AbeContext.pk, newMsgRoles);
                    CipherText newHeader = poolManager.encryptWithPool(
                            DataOwnerView.AbeContext.engine, DataOwnerView.AbeContext.pk,
                            new PlainText(randElementText), // ✨【核心修复】: 直接封装合法的群元素
                            newMsgRoles, newTree
                    );
                    newPkg.setHeaderText(newHeader);
                    item.setMsgAccessPolicy(newMsgPolicyStr);
                    policyChanged = true;
                } else {
                    newPkg.setEncText(oldDto.encText);
                    newPkg.setHeaderText(CipherText.fromBytes(Base64.getDecoder().decode(oldDto.headerTextBase64), lastPackageTemplate.getHeaderText()));
                }

                // ================== [ 🎙️ 声纹策略重加密 ] ==================
                String newVoicePolicyStr = String.join(" OR ", newVoiceRoles);
                if (!newVoicePolicyStr.equals(item.getVoiceAccessPolicy())) {
                    CipherText oldHeader = CipherText.fromBytes(Base64.getDecoder().decode(oldDto.headerVoiceBase64), lastPackageTemplate.getHeaderVoice());
                    Element eleVoice = DataOwnerView.AbeContext.engine.decrypt(DataOwnerView.AbeContext.pk, userPriv, oldHeader);
                    if (eleVoice == null) throw new RuntimeException("权限不足：无法解开原声纹密钥！");

                    SecretKey oldKVoice = unpadElementToKey(eleVoice);

                    // ✨【核心修复】: 声纹也做同样的自洽变换
                    Element randElementVoice = DataOwnerView.AbeContext.pk.getPairingParameter().getG1().newRandomElement().getImmutable();
                    SecretKey newKVoice = unpadElementToKey(randElementVoice);
                    byte[] newVoiceIv = generateNewIv();

                    // 执行同态盲差异或
                    byte[] oldEncVoiceBytes = Base64.getDecoder().decode(oldDto.encVoice);
                    byte[] newEncVoiceBytes = computeHomomorphicXOR(oldEncVoiceBytes, oldKVoice, newKVoice, oldDto.voiceIv, newVoiceIv);

                    newPkg.setEncVoice(Base64.getEncoder().encodeToString(newEncVoiceBytes));
                    newPkg.setVoiceIv(newVoiceIv);

                    // 包装新密钥树
                    AccessTree newTree = buildOrTree(DataOwnerView.AbeContext.pk, newVoiceRoles);
                    CipherText newHeader = poolManager.encryptWithPool(
                            DataOwnerView.AbeContext.engine, DataOwnerView.AbeContext.pk,
                            new PlainText(randElementVoice), // ✨【核心修复】: 直接封装合法的群元素
                            newVoiceRoles, newTree
                    );
                    newPkg.setHeaderVoice(newHeader);
                    item.setVoiceAccessPolicy(newVoicePolicyStr);
                    policyChanged = true;
                } else {
                    newPkg.setEncVoice(oldDto.encVoice);
                    newPkg.setHeaderVoice(CipherText.fromBytes(Base64.getDecoder().decode(oldDto.headerVoiceBase64), lastPackageTemplate.getHeaderVoice()));
                }

                // 更新 IPFS 和 H2 数据库状态
                if (policyChanged) {
                    String jsonForIpfs = CipherJSONUtils.packageToJSON(newPkg);
                    NamedStreamable.ByteArrayWrapper ipfsFile = new NamedStreamable.ByteArrayWrapper(
                            "medical_updated.json", jsonForIpfs.getBytes(StandardCharsets.UTF_8));
                    updatedCid = ipfs.add(ipfsFile).get(0).hash.toBase58();

                    item.setIpfsCid(updatedCid);
                    item.setCreateTime(LocalDateTime.now());
                    resourceRepository.save(item);
                    lastPackageTemplate = newPkg;
                    isSuccess = true;

                    final String finalCid = updatedCid;
                    ui.access(() -> {
                        Notification n = Notification.show("🚀 策略同态重加密完成 (密钥与代数状态完全自洽)！", 5000, Notification.Position.MIDDLE);
                        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                        refreshGrid();
                    });
                } else {
                    ui.access(() -> Notification.show("检测到策略无变化，无需执行轮换。"));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                ui.access(() -> showError("同态重加密失败：" + ex.getMessage()));
            } finally {
                AuditLog log = new AuditLog();
                log.setOpTime(LocalDateTime.now());
                log.setActionType("ABE_POLICY_RE_ENCRYPT");
                log.setTargetCid(updatedCid);
                log.setOperatorName(empId + " (Roles: " + roles + ")");
                log.setSuccess(isSuccess);
                auditLogRepo.save(log);
            }
        }).start();
    }

    // ✨ 核心机制：CTR 模式下的同态异或增量计算工具
    // C_new = C_old ⊕ Keystream_old ⊕ Keystream_new
    // ✨ 核心机制：CTR 模式下的同态异或增量计算工具 (已修复纯净流提取)
    // C_new = C_old ⊕ Keystream_old ⊕ Keystream_new
    // ✨ 核心机制：CTR 模式下的同态异或增量计算工具 (双 IV 安全升级版)
    // C_new = C_old ⊕ Keystream_old ⊕ Keystream_new
    private byte[] computeHomomorphicXOR(byte[] oldCiphertext, SecretKey oldKey, SecretKey newKey, byte[] oldIv, byte[] newIv) throws Exception {

        // 1. 生成旧密钥流（使用旧 IV）
        javax.crypto.Cipher cipherOld = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding");
        cipherOld.init(javax.crypto.Cipher.ENCRYPT_MODE, oldKey, new javax.crypto.spec.IvParameterSpec(oldIv));

        // 2. 生成新密钥流（使用全新 IV）
        javax.crypto.Cipher cipherNew = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding");
        cipherNew.init(javax.crypto.Cipher.ENCRYPT_MODE, newKey, new javax.crypto.spec.IvParameterSpec(newIv));

        // 3. 构建与密文等长的全 0 虚拟明文
        byte[] zeros = new byte[oldCiphertext.length];

        // 4. 提取纯净伪随机流
        byte[] oldKeystream = cipherOld.doFinal(zeros);
        byte[] newKeystream = cipherNew.doFinal(zeros);

        // 5. 计算同态盲差并直接叠加于旧密文
        byte[] newCiphertext = new byte[oldCiphertext.length];
        for (int i = 0; i < oldCiphertext.length; i++) {
            newCiphertext[i] = (byte) (oldCiphertext[i] ^ oldKeystream[i] ^ newKeystream[i]);
        }

        return newCiphertext;
    }

    // --- 安全加固后的拓扑控制树构建方法 (与 Owner 页面同步) ---
    private AccessTree buildOrTree(PublicKey pk, Set<String> roles) {
        int totalRoles = roles.size();
        if (totalRoles == 0) {
            AccessTreeBuildModel[] fallback = new AccessTreeBuildModel[2];
            fallback[0] = AccessTreeBuildModel.innerAccessTreeBuildModel(1, 1, 1, -1);
            fallback[1] = AccessTreeBuildModel.leafAccessTreeBuildModel(2, 1, "DENY_ALL", 1);
            return AccessTree.build(pk, fallback);
        }

        AccessTreeBuildModel[] models = new AccessTreeBuildModel[totalRoles + 1];
        models[0] = AccessTreeBuildModel.innerAccessTreeBuildModel(1, 1, totalRoles, -1);

        int id = 2;
        var roleList = new ArrayList<>(roles);
        for (int i = 0; i < totalRoles; i++) {
            models[id - 1] = AccessTreeBuildModel.leafAccessTreeBuildModel(id, 1, roleList.get(i), 1);
            id++;
        }
        return AccessTree.build(pk, models);
    }

    private Element padKeyToElement(SecretKey key, int len) {
        byte[] padded = new byte[len];
        System.arraycopy(key.getEncoded(), 0, padded, len - 16, 16);
        return DataOwnerView.AbeContext.pk.getPairingParameter().getG1().newElementFromBytes(padded).getImmutable();
    }

    private SecretKey unpadElementToKey(Element e) {
        byte[] raw = e.toBytes();
        byte[] keyBytes = new byte[16];
        System.arraycopy(raw, raw.length - 16, keyBytes, 0, 16);
        return new SecretKeySpec(keyBytes, "AES");
    }

    private void showDualResultDialog(DataResource item, DecryptResult result, long ms, Set<String> currentRoles) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("🔓 双策略解密结果");
        dialog.setWidth("500px");

        VerticalLayout layout = new VerticalLayout();
        layout.add(new Span("👤 授权身份: " + currentRoles));
        layout.add(new Span("⏱️ 运算耗时: " + ms + " ms"));
        layout.add(new Hr());

        layout.add(new H4("📄 文本策略: " + item.getMsgAccessPolicy()));
        if (result.text != null) {
            Span text = new Span(result.text);
            text.getStyle().set("color", "green");
            layout.add(text);
        } else {
            Span fail = new Span("❌ 权限不足，无法访问文本");
            fail.getStyle().set("color", "red");
            layout.add(fail);
        }

        layout.add(new H4("🎙️ 声纹策略: " + item.getVoiceAccessPolicy()));
        if (result.matrix != null) {
            layout.add(new Span("✔ 成功还原（展示前2帧）"));
            for (int i = 0; i < Math.min(2, result.matrix.length); i++) {
                layout.add(new Span(Arrays.toString(result.matrix[i])));
            }
        } else {
            Span fail = new Span("❌ 权限不足，无法访问声纹");
            fail.getStyle().set("color", "red");
            layout.add(fail);
        }

        dialog.add(layout);
        dialog.getFooter().add(new Button("关闭", e -> dialog.close()));
        dialog.open();
    }

    private void showError(String msg) {
        Notification n = Notification.show(msg, 4000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    // ✨ 新增：用于在密钥轮换时生成全新的随机 IV
    private byte[] generateNewIv() {
        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);
        return iv;
    }
}