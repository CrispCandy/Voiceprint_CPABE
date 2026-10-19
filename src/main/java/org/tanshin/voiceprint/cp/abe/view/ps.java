//package org.tanshin.voiceprint.cp.abe.view;
//
//import com.vaadin.flow.component.button.Button;
//import com.vaadin.flow.component.button.ButtonVariant;
//import com.vaadin.flow.component.checkbox.CheckboxGroup;
//import com.vaadin.flow.component.dialog.Dialog;
//import com.vaadin.flow.component.grid.Grid;
//import com.vaadin.flow.component.html.H3;
//import com.vaadin.flow.component.html.H4;
//import com.vaadin.flow.component.html.Hr;
//import com.vaadin.flow.component.html.Span;
//import com.vaadin.flow.component.notification.Notification;
//import com.vaadin.flow.component.notification.NotificationVariant;
//import com.vaadin.flow.component.orderedlayout.VerticalLayout;
//import com.vaadin.flow.router.Route;
//import com.vaadin.flow.spring.annotation.UIScope;
//import io.ipfs.api.IPFS;
//import io.ipfs.multihash.Multihash;
//import it.unisa.dia.gas.jpbc.Element;
//import org.springframework.stereotype.Component;
//import org.tanshin.voiceprint.cp.abe.attribute.Attribute;
//import org.tanshin.voiceprint.cp.abe.entity.AuditLog;
//import org.tanshin.voiceprint.cp.abe.entity.DataResource;
//import org.tanshin.voiceprint.cp.abe.parameter.UserPrivateKey;
//import org.tanshin.voiceprint.cp.abe.repository.AuditLogRepository;
//import org.tanshin.voiceprint.cp.abe.repository.DataResourceRepository;
//import org.tanshin.voiceprint.cp.abe.text.CipherText;
//import org.tanshin.voiceprint.cp.abe.text.MedicalCipherPackage;
//import org.tanshin.voiceprint.cp.abe.text.SerializableCipherPackage;
//import org.tanshin.voiceprint.cp.abe.util.CipherJSONUtils;
//import org.tanshin.voiceprint.cp.abe.util.ConvertUtils;
//
//import javax.crypto.SecretKey;
//import javax.crypto.spec.SecretKeySpec;
//import java.nio.charset.StandardCharsets;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Base64;
//import java.util.List;
//import java.util.Set;
//
//@Route(value = "consumer", layout = MainView.class)
//@UIScope
//public class DataConsumerView extends VerticalLayout {
//
//    // 内存模板：由于 CipherText.fromBytes 需要群参数上下文，我们临时利用 Owner 留下的 pkg
//    public static MedicalCipherPackage lastPackageTemplate;
//
//    private final DataResourceRepository resourceRepository;
//    private final CheckboxGroup<String> attributeSelector;
//    private final AuditLogRepository auditLogRepo;
//
//    // 自定义解密结果类（替代原先被废弃的 Service）
//    public static class DecryptResult {
//        public String text;
//        public float[][] matrix;
//
//        public DecryptResult(String text, float[][] matrix) {
//            this.text = text;
//            this.matrix = matrix;
//        }
//    }
//
//    public DataConsumerView(DataResourceRepository resourceRepository, AuditLogRepository auditLogRepo) {
//        this.resourceRepository = resourceRepository;
//        this.auditLogRepo = auditLogRepo;
//
//        setSizeFull();
//        setSpacing(true);
//
//        add(new H3("🧪 终端权限模拟"));
//
//        attributeSelector = new CheckboxGroup<>();
//        attributeSelector.setLabel("当前持有属性 (私钥)");
//        attributeSelector.setItems("主治医生", "医学生", "会诊医生", "抓药护士", "院长", "AI节点");
//        attributeSelector.select("医学生");
//        add(attributeSelector);
//
//        add(new H3("📂 可访问资源列表"));
//
//        Grid<DataResource> grid = new Grid<>(DataResource.class, false);
//        grid.addColumn(DataResource::getResourceName).setHeader("资源名称");
//        grid.addColumn(DataResource::getIpfsCid).setHeader("CID");
//        grid.addColumn(r -> r.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).setHeader("时间");
//        grid.addComponentColumn(this::createDecryptButton).setHeader("操作");
//        grid.setItems(resourceRepository.findAll());
//
//        add(grid);
//    }
//
//    private Button createDecryptButton(DataResource item) {
//        Button btn = new Button("双策略解密");
//        btn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
//
//        btn.addClickListener(e -> {
//            Set<String> roles = attributeSelector.getValue();
//            if (roles.isEmpty()) {
//                showError("请先选择属性！");
//                return;
//            }
//
//            var ui = e.getSource().getUI().get();
//            e.getSource().setEnabled(false);
//
//            new Thread(() -> {
//                boolean isSuccess = false;
//                long start = System.currentTimeMillis();
//
//                try {
//                    // 1. 根据选中属性，向系统(PKG)申请用户私钥
//                    List<Attribute> userAttrs = new ArrayList<>();
//                    for (String role : roles) {
//                        userAttrs.add(new Attribute(role, DataOwnerView.AbeContext.pk));
//                    }
//                    UserPrivateKey userPriv = DataOwnerView.AbeContext.engine.keyGen(DataOwnerView.AbeContext.msk, userAttrs);
//
//                    // 2. 从 IPFS 下载密文
//                    IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
//                    byte[] downloadedData = ipfs.cat(Multihash.fromBase58(item.getIpfsCid()));
//                    String downloadedJson = new String(downloadedData, StandardCharsets.UTF_8);
//
//                    // 3. 反序列化 SerializableCipherPackage
//                    SerializableCipherPackage dto = CipherJSONUtils.jsonToDTO(downloadedJson);
//                    CipherText resHeaderText = CipherText.fromBytes(Base64.getDecoder().decode(dto.headerTextBase64), lastPackageTemplate.getHeaderText());
//                    CipherText resVoiceHeader = CipherText.fromBytes(Base64.getDecoder().decode(dto.headerVoiceBase64), lastPackageTemplate.getHeaderVoice());
//
//                    // 4. ABE 解密提取 AES 密钥
//                    Element eleText = DataOwnerView.AbeContext.engine.decrypt(DataOwnerView.AbeContext.pk, userPriv, resHeaderText);
//                    Element eleVoice = DataOwnerView.AbeContext.engine.decrypt(DataOwnerView.AbeContext.pk, userPriv, resVoiceHeader);
//
//                    String textResult = null;
//                    float[][] voiceResult = null;
//
//                    // 5. 如果文本权限校验通过，执行 AES 解密
//                    if (eleText != null) {
//                        SecretKey skT = unpadElementToKey(eleText);
//                        textResult = decryptAES(dto.encText, skT);
//                    }
//
//                    // 6. 如果声纹权限校验通过，执行 AES 解密
//                    if (eleVoice != null) {
//                        SecretKey skV = unpadElementToKey(eleVoice);
//                        byte[] vBytes = decryptBytesAES(Base64.getDecoder().decode(dto.encVoice), skV);
//                        voiceResult = ConvertUtils.bytesToMatrix(vBytes);
//                    }
//
//                    long time = System.currentTimeMillis() - start;
//                    DecryptResult finalResult = new DecryptResult(textResult, voiceResult);
//
//                    ui.access(() -> {
//                        e.getSource().setEnabled(true);
//                        showDualResultDialog(item, finalResult, time);
//                    });
//
//                    isSuccess = true;
//
//                } catch (Exception ex) {
//                    ui.access(() -> {
//                        e.getSource().setEnabled(true);
//                        showError("解密失败：" + ex.getMessage());
//                        ex.printStackTrace();
//                    });
//                } finally {
//                    // --- 记录解密审计日志 ---
//                    AuditLog log = new AuditLog();
//                    log.setOpTime(LocalDateTime.now());
//                    log.setActionType("ABE_DECRYPT_ATTEMPT");
//                    log.setTargetCid(item.getIpfsCid());
//                    log.setOperatorName("Consumer_Roles:" + roles);
//                    log.setSuccess(isSuccess);
//                    auditLogRepo.save(log);
//
//                    ui.access(() -> e.getSource().setEnabled(true));
//                }
//            }).start();
//        });
//
//        return btn;
//    }
//
//    // --- 工具与 UI 弹窗保持不变 ---
//    private SecretKey unpadElementToKey(Element e) {
//        byte[] raw = e.toBytes();
//        byte[] keyBytes = new byte[16];
//        System.arraycopy(raw, raw.length - 16, keyBytes, 0, 16);
//        return new SecretKeySpec(keyBytes, "AES");
//    }
//
//    private String decryptAES(String data, SecretKey key) throws Exception {
//        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
//        c.init(javax.crypto.Cipher.DECRYPT_MODE, key);
//        return new String(c.doFinal(Base64.getDecoder().decode(data)), StandardCharsets.UTF_8);
//    }
//
//    private byte[] decryptBytesAES(byte[] data, SecretKey key) throws Exception {
//        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
//        c.init(javax.crypto.Cipher.DECRYPT_MODE, key);
//        return c.doFinal(data);
//    }
//
//    private void showDualResultDialog(DataResource item, DecryptResult result, long ms) {
//        Dialog dialog = new Dialog();
//        dialog.setHeaderTitle("🔓 双策略解密结果");
//
//        VerticalLayout layout = new VerticalLayout();
//        layout.add(new Span("⏱️ 总耗时: " + ms + " ms"));
//
//        layout.add(new H4("📄 文本访问策略结果"));
//        if (result.text != null) {
//            Span text = new Span(result.text);
//            text.getStyle().set("color", "green");
//            layout.add(text);
//        } else {
//            Span fail = new Span("❌ 无权限访问文本 (属性不匹配)");
//            fail.getStyle().set("color", "red");
//            layout.add(fail);
//        }
//
//        layout.add(new H4("🎙️ 声纹访问策略结果"));
//        if (result.matrix != null) {
//            layout.add(new Span("✔ 已解密（展示前3帧）"));
//            for (int i = 0; i < Math.min(3, result.matrix.length); i++) {
//                layout.add(new Span(Arrays.toString(result.matrix[i])));
//            }
//        } else {
//            Span fail = new Span("❌ 无权限访问声纹 (属性不匹配)");
//            fail.getStyle().set("color", "red");
//            layout.add(fail);
//        }
//
//        layout.add(new Hr());
//        layout.add(new Span("📜 文本策略: " + item.getMsgAccessPolicy()));
//        layout.add(new Span("📜 声纹策略: " + item.getVoiceAccessPolicy()));
//
//        dialog.add(layout);
//        dialog.getFooter().add(new Button("关闭", e -> dialog.close()));
//        dialog.open();
//    }
//
//    private void showError(String msg) {
//        Notification n = Notification.show(msg, 3000, Notification.Position.TOP_CENTER);
//        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
//    }
//}