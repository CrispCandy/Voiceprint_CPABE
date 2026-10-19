package org.tanshin.voiceprint.cp.abe.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.UIScope;
import io.ipfs.api.IPFS;
import io.ipfs.api.NamedStreamable;
import it.unisa.dia.gas.jpbc.Element;
import org.tanshin.voiceprint.cp.abe.engine.CpAneEngine;
import org.tanshin.voiceprint.cp.abe.entity.AuditLog;
import org.tanshin.voiceprint.cp.abe.entity.DataResource;
import org.tanshin.voiceprint.cp.abe.mfcc.MedicalRecord;
import org.tanshin.voiceprint.cp.abe.parameter.MasterPrivateKey;
import org.tanshin.voiceprint.cp.abe.parameter.PublicKey;
import org.tanshin.voiceprint.cp.abe.parameter.SystemKey;
import org.tanshin.voiceprint.cp.abe.repository.AuditLogRepository;
import org.tanshin.voiceprint.cp.abe.repository.DataResourceRepository;
import org.tanshin.voiceprint.cp.abe.service.OfflinePoolManager;
import org.tanshin.voiceprint.cp.abe.structure.AccessTree;
import org.tanshin.voiceprint.cp.abe.structure.AccessTreeBuildModel;
import org.tanshin.voiceprint.cp.abe.text.CipherText;
import org.tanshin.voiceprint.cp.abe.text.MedicalCipherPackage;
import org.tanshin.voiceprint.cp.abe.text.PlainText;
import org.tanshin.voiceprint.cp.abe.util.AESUtil;
import org.tanshin.voiceprint.cp.abe.util.CipherJSONUtils;
import org.tanshin.voiceprint.cp.abe.util.ConvertUtils;

import javax.crypto.SecretKey;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

@Route(value = "owner", layout = MainView.class)
@UIScope
public class DataOwnerView extends VerticalLayout {

    // === 【全局 ABE 上下文】确保加解密使用同一套系统参数 ===
    public static class AbeContext {
        public static final SystemKey systemKey = SystemKey.build();
        public static final PublicKey pk = systemKey.getPublicKey();
        public static final MasterPrivateKey msk = systemKey.getMasterPrivateKey();
        public static final CpAneEngine engine = new CpAneEngine();
    }

    private File selectedFile;
    private MedicalRecord currentRecord;
    private final Div heatmapContainer = new Div();
    private final ProgressBar progressBar = new ProgressBar();

    private final DataResourceRepository resourceRepository;
    private final OfflinePoolManager poolManager;
    private final AuditLogRepository auditLogRepo;

    public DataOwnerView(DataResourceRepository resourceRepository,
                         OfflinePoolManager poolManager,
                         AuditLogRepository auditLogRepo) {
        this.resourceRepository = resourceRepository;
        this.poolManager = poolManager;
        this.auditLogRepo = auditLogRepo;

        setSpacing(true);

        TextField nameField = new TextField("资源名称");
        TextArea resultText = new TextArea("提取结果");
        resultText.setWidthFull();

        MultiSelectComboBox<String> msgPolicy = new MultiSelectComboBox<>("文字访问策略");
        msgPolicy.setItems("主治医生", "会诊医生", "医学生", "抓药护士");

        MultiSelectComboBox<String> voicePolicy = new MultiSelectComboBox<>("声纹访问策略");
        voicePolicy.setItems("主治医生", "会诊医生", "医学生", "抓药护士", "科室主任", "AI节点");

        Button processBtn = new Button("提取声纹特征");
        processBtn.setEnabled(false);

        processBtn.addClickListener(e -> {
            if (selectedFile == null || !selectedFile.exists()) {
                Notification.show("请先上传视频文件！");
                return;
            }
            processBtn.setEnabled(false);
            processBtn.setText("正在分析声纹...");

            try {
                MedicalRecord record = MedicalRecord.fullProcess(selectedFile);
                this.currentRecord = record;
                renderHeatmap(record.getMfccMatrix());
                resultText.setValue("特征提取成功！矩阵帧数: " + record.getMfccMatrix().length);
                Notification.show("声纹特征提取完成，请设置访问策略并加密。");
            } catch (Exception ex) {
                Notification.show("特征提取失败: " + ex.getMessage());
                ex.printStackTrace();
            } finally {
                processBtn.setEnabled(true);
                processBtn.setText("提取声纹特征");
            }
        });

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("video/mp4", "audio/wav", "audio/mpeg");

        upload.addSucceededListener(event -> {
            try {
                this.selectedFile = saveToTempFile(buffer);
                Notification.show("文件上传成功，可以开始提取特征了。");
                processBtn.setEnabled(true);
            } catch (Exception ex) {
                Notification.show("文件保存失败: " + ex.getMessage());
            }
        });

        // --- 核心：混合加密并上传 IPFS (融合测试集 CTR 与 IV 机制) ---
        Button encryptBtn = new Button("执行 ABE 加密并存库", e -> {
            var ui = e.getSource().getUI().get();
            String resourceName = nameField.getValue();
            Set<String> msgRoles = msgPolicy.getValue();
            Set<String> voiceRoles = voicePolicy.getValue();

            if (currentRecord == null || msgRoles.isEmpty() || voiceRoles.isEmpty()) {
                Notification.show("请检查输入：特征未提取或策略未选择！");
                return;
            }

            e.getSource().setEnabled(false);

            new Thread(() -> {
                String tempCid = "FAILED";
                boolean isSuccess = false;
                try {
                    // 1. 生成 16 字节 AES 密钥与随机 IV 向量（对抗动态重置时的字节碎化问题）
                    SecretKey kText = AESUtil.generateAESKey();
                    SecretKey kVoice = AESUtil.generateAESKey();
                    byte[] ivText = AESUtil.generateIV();
                    byte[] ivVoice = AESUtil.generateIV();

                    // 2. 转换明文数据为字节数组
                    byte[] rawTextBytes = currentRecord.getMsg().getBytes(StandardCharsets.UTF_8);
                    byte[] rawVoiceBytes = ConvertUtils.matrixToBytes(currentRecord.getMfccMatrix());

                    // 3. 执行高级 CTR 模式流加密
                    byte[] encTextBytes = AESUtil.encryptBytesCTR(rawTextBytes, kText, ivText);
                    byte[] encVoiceBytes = AESUtil.encryptBytesCTR(rawVoiceBytes, kVoice, ivVoice);

                    String encTextStr = Base64.getEncoder().encodeToString(encTextBytes);
                    String encVoiceStr = Base64.getEncoder().encodeToString(encVoiceBytes);

                    // 4. 构建标准拓扑结构的门限/OR 树
                    AccessTree treeText = buildOrTree(AbeContext.pk, msgRoles);
                    AccessTree treeVoice = buildOrTree(AbeContext.pk, voiceRoles);

                    // 5. 将群空间元素进行高位对齐拉伸，映射至 G1 群空间
                    int g1Length = AbeContext.pk.getPairingParameter().getG1().getLengthInBytes();
                    Element mText = padKeyToElement(kText, g1Length);
                    Element mVoice = padKeyToElement(kVoice, g1Length);

                    // 6. 经由池化组件加速生成 ABE 数字信封头
                    CipherText headerText = poolManager.encryptWithPool(
                            AbeContext.engine, AbeContext.pk, new PlainText(mText), msgRoles, treeText
                    );
                    CipherText headerVoice = poolManager.encryptWithPool(
                            AbeContext.engine, AbeContext.pk, new PlainText(mVoice), voiceRoles, treeVoice
                    );

                    // 7. 组装契合【多身份演进协议】的密码学大对象
                    MedicalCipherPackage pkg = new MedicalCipherPackage();
                    pkg.setCaseId(UUID.randomUUID().toString());
                    pkg.setEncText(encTextStr);
                    pkg.setEncVoice(encVoiceStr);
                    pkg.setHeaderText(headerText);
                    pkg.setHeaderVoice(headerVoice);

                    // 💡 [关键注入]: 为解密端与动态演进策略提供必不可少的流空间参数
                    pkg.setTextIv(ivText);
                    pkg.setVoiceIv(ivVoice);
                    pkg.setTextDataLength(rawTextBytes.length);
                    pkg.setVoiceDataLength(rawVoiceBytes.length);

                    // 8. 序列化并存入星际文件系统 (IPFS)
                    String jsonForIpfs = CipherJSONUtils.packageToJSON(pkg);
                    IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
                    NamedStreamable.ByteArrayWrapper ipfsFile = new NamedStreamable.ByteArrayWrapper(
                            "medical_package.json", jsonForIpfs.getBytes(StandardCharsets.UTF_8));
                    tempCid = ipfs.add(ipfsFile).get(0).hash.toBase58();

                    // 9. 数据库元数据持久化
                    DataResource resource = new DataResource();
                    resource.setResourceName(resourceName.isEmpty() ? "未命名医疗数据" : resourceName);
                    resource.setIpfsCid(tempCid);
                    resource.setMsgAccessPolicy(String.join(" OR ", msgRoles));
                    resource.setVoiceAccessPolicy(String.join(" OR ", voiceRoles));
                    resource.setCreateTime(LocalDateTime.now());
                    resourceRepository.save(resource);

                    // 传递临时静态模板供消费端快速测试引用
                    DataConsumerView.lastPackageTemplate = pkg;
                    isSuccess = true;

                    final String finalCid = tempCid;
                    ui.access(() -> Notification.show("🚀 ABE混合加密完成！数据包成功发布至 IPFS. CID: " + finalCid, 3000, Notification.Position.MIDDLE));

                } catch (Exception ex) {
                    ex.printStackTrace();
                    ui.access(() -> Notification.show("加密处理发生致命异常: " + ex.getMessage()));
                } finally {
                    AuditLog log = new AuditLog();
                    log.setOpTime(LocalDateTime.now());
                    log.setActionType("ABE_HYBRID_ENCRYPT_UPLOAD");
                    log.setTargetCid(tempCid);
                    log.setOperatorName("DataOwner_Node");
                    log.setSuccess(isSuccess);
                    auditLogRepo.save(log);

                    ui.access(() -> e.getSource().setEnabled(true));
                }
            }).start();
        });

        add(new H3("声纹 ABE 细粒度隐私保护系统 - 数据所有者控制台"),
                nameField,
                new Span("第一步：采集患者生物声纹信号并上传"), upload,
                progressBar,
                processBtn,
                resultText,
                heatmapContainer,
                new Hr(),
                new Span("第二步：编排面向多身份的属性级策略树"), msgPolicy, voicePolicy,
                encryptBtn);
    }

    // --- 安全加固后的拓扑控制树构建方法 ---
    private AccessTree buildOrTree(PublicKey pk, Set<String> roles) {
        int totalRoles = roles.size();
        // 健壮性预防：如果没勾选角色，提供一个兜底节点，防止数组越界
        if (totalRoles == 0) {
            AccessTreeBuildModel[] fallback = new AccessTreeBuildModel[2];
            fallback[0] = AccessTreeBuildModel.innerAccessTreeBuildModel(1, 1, 1, -1);
            fallback[1] = AccessTreeBuildModel.leafAccessTreeBuildModel(2, 1, "DENY_ALL", 1);
            return AccessTree.build(pk, fallback);
        }

        AccessTreeBuildModel[] models = new AccessTreeBuildModel[totalRoles + 1];
        // 根节点：1-out-of-N (即 OR 门逻辑门限)，父节点标为 -1
        models[0] = AccessTreeBuildModel.innerAccessTreeBuildModel(1, 1, totalRoles, -1);

        int id = 2;
        var roleList = new ArrayList<>(roles);
        for (int i = 0; i < totalRoles; i++) {
            // 所有叶子角色的父节点都严格指向根节点 id: 1
            models[id - 1] = AccessTreeBuildModel.leafAccessTreeBuildModel(id, 1, roleList.get(i), 1);
            id++;
        }
        return AccessTree.build(pk, models);
    }

    private Element padKeyToElement(SecretKey key, int len) {
        byte[] padded = new byte[len];
        System.arraycopy(key.getEncoded(), 0, padded, len - 16, 16);
        return AbeContext.pk.getPairingParameter().getG1().newElementFromBytes(padded).getImmutable();
    }

    private void renderHeatmap(float[][] matrix) {
        heatmapContainer.removeAll();
        heatmapContainer.add(new H5("13维标量 Mel 频率倒谱系数(MFCC) 特征矩阵可视化（前300帧）："));
        Div grid = new Div();
        grid.getStyle().set("display", "grid").set("grid-template-rows", "repeat(13, 10px)")
                .set("grid-auto-columns", "5px").set("grid-auto-flow", "column")
                .set("overflow-x", "auto").set("background", "#1a1a1a")
                .set("padding", "10px").set("border-radius", "8px");

        int frames = Math.min(matrix.length, 300);
        for (int c = 0; c < frames; c++) {
            for (int r = 0; r < 13; r++) {
                float val = matrix[c][r];
                float norm = Math.max(0, Math.min(1, (val + 20) / 80));
                Div cell = new Div();
                cell.getStyle().set("background-color", String.format("rgb(%d, 50, %d)", (int) (norm * 255), (int) ((1 - norm) * 255)));
                grid.add(cell);
            }
        }
        heatmapContainer.add(grid);
    }

    private File saveToTempFile(MemoryBuffer buffer) throws Exception {
        File tempFile = File.createTempFile("voiceprint_stream_", ".tmp");
        try (var in = buffer.getInputStream(); var out = new FileOutputStream(tempFile)) {
            in.transferTo(out);
        }
        tempFile.deleteOnExit();
        return tempFile;
    }
}