package org.tanshin.voiceprint.cp.abe.service;

import com.fasterxml.jackson.databind.ObjectMapper; // 引入 Jackson 工具
import it.unisa.dia.gas.jpbc.Element;
import org.springframework.stereotype.Service;
import org.tanshin.voiceprint.cp.abe.engine.CpAneEngine;
import org.tanshin.voiceprint.cp.abe.entity.AuditLog;
import org.tanshin.voiceprint.cp.abe.entity.DataResource;
import org.tanshin.voiceprint.cp.abe.mfcc.MedicalRecord;
import org.tanshin.voiceprint.cp.abe.parameter.*;
import org.tanshin.voiceprint.cp.abe.repository.AuditLogRepository;
import org.tanshin.voiceprint.cp.abe.repository.DataResourceRepository;
import org.tanshin.voiceprint.cp.abe.structure.AccessTree;
import org.tanshin.voiceprint.cp.abe.structure.AccessTreeBuildModel;
import org.tanshin.voiceprint.cp.abe.text.CipherText;
import org.tanshin.voiceprint.cp.abe.text.MedicalCipherPackage;
import org.tanshin.voiceprint.cp.abe.text.OfflinePackage;
import org.tanshin.voiceprint.cp.abe.text.PlainText;
import org.tanshin.voiceprint.cp.abe.util.AESUtil;
import org.tanshin.voiceprint.cp.abe.util.ConvertUtils;

import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.Set;
import java.time.LocalDateTime;

@Service
public class EncryptionService {

    private final DataResourceRepository resourceRepository;
    private final SystemKeyManager keyManager;
    private final CpAneEngine engine = new CpAneEngine();
    private final IpfsService ipfsService;
    private final AuditLogRepository auditLogRepo;
    private final OfflinePoolManager poolManager;

    // 实例化 JSON 工具
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EncryptionService(DataResourceRepository resourceRepository,
                             SystemKeyManager keyManager,
                             IpfsService ipfsService,
                             AuditLogRepository auditLogRepo,
                             OfflinePoolManager poolManager) {
        this.resourceRepository = resourceRepository;
        this.keyManager = keyManager;
        this.ipfsService = ipfsService;
        this.auditLogRepo = auditLogRepo;
        this.poolManager = poolManager;
    }

    public String fullEncryptionFlow(MedicalRecord record, Set<String> textRoles, Set<String> voiceRoles, String resourceName) throws Exception {
        PublicKey pk = keyManager.getSystemKey().getPublicKey();

        SecretKey kText = AESUtil.generateAESKey();
        SecretKey kVoice = AESUtil.generateAESKey();
        Element mText = embedKeyToElement(pk, kText);
        Element mVoice = embedKeyToElement(pk, kVoice);

        String encText = AESUtil.encrypt(record.getMsg(), kText);
        byte[] matrixBytes = ConvertUtils.matrixToBytes(record.getMfccMatrix());
        String encVoiceStr = Base64.getEncoder().encodeToString(AESUtil.encryptBytes(matrixBytes, kVoice));

        OfflinePackage textPkg = poolManager.getOfflinePackage(textRoles);
        OfflinePackage voicePkg = poolManager.getOfflinePackage(voiceRoles);

        // ⭐ 提取图纸：如果离线包有就用离线包的，如果没有就在线生成
        AccessTreeBuildModel[] textModels = (textPkg != null && textPkg.getBuildModels() != null) ?
                textPkg.getBuildModels() : poolManager.generateModels(textRoles);

        AccessTreeBuildModel[] voiceModels = (voicePkg != null && voicePkg.getBuildModels() != null) ?
                voicePkg.getBuildModels() : poolManager.generateModels(voiceRoles);

        // 使用图纸直接建树
        CipherText headerText = (textPkg != null) ?
                engine.onlineEncrypt(new PlainText(mText), textPkg) :
                engine.encrypt(pk, new PlainText(mText), AccessTree.build(pk, textModels));

        CipherText headerVoice = (voicePkg != null) ?
                engine.onlineEncrypt(new PlainText(mVoice), voicePkg) :
                engine.encrypt(pk, new PlainText(mVoice), AccessTree.build(pk, voiceModels));

        MedicalCipherPackage pkg = new MedicalCipherPackage();
        pkg.setCaseId("CASE_" + System.currentTimeMillis());
        pkg.setEncText(encText);
        pkg.setEncVoice(encVoiceStr);
        pkg.setHeaderText(headerText);
        pkg.setHeaderVoice(headerVoice);

        byte[] pkgBytes;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(pkg);
            pkgBytes = bos.toByteArray();
        }
        String realCid = this.ipfsService.upload(pkgBytes);

        // ⭐ 将图纸序列化为 JSON
        String textModelJson = objectMapper.writeValueAsString(textModels);
        String voiceModelJson = objectMapper.writeValueAsString(voiceModels);

        saveMetadataAndLog(resourceName, realCid, textRoles.toString(), voiceRoles.toString(),
                textModelJson, voiceModelJson, textPkg != null, voicePkg != null);

        return realCid;
    }

    private Element embedKeyToElement(PublicKey pk, SecretKey key) {
        int g1Length = pk.getPairingParameter().getG1().getLengthInBytes();
        byte[] paddedKey = new byte[g1Length];
        System.arraycopy(key.getEncoded(), 0, paddedKey, g1Length - 16, 16);
        return pk.getPairingParameter().getG1().newElementFromBytes(paddedKey).getImmutable();
    }

    // 更新保存逻辑，新增接收 JSON 格式的模型数据
    private void saveMetadataAndLog(String name, String cid, String tR, String vR,
                                    String textModelJson, String voiceModelJson, boolean tH, boolean vH) {
        DataResource entity = new DataResource();
        entity.setResourceName(name);
        entity.setIpfsCid(cid);
        entity.setMsgAccessPolicy(tR);
        entity.setVoiceAccessPolicy(vR);
        // ⭐ 保存图纸到数据库
        entity.setMsgPolicyModel(textModelJson);
        entity.setVoicePolicyModel(voiceModelJson);
        entity.setCreateTime(LocalDateTime.now());
        resourceRepository.save(entity);

        AuditLog log = new AuditLog();
        log.setOpTime(LocalDateTime.now());
        log.setActionType("ENCRYPT");
        log.setTargetCid(cid);
        log.setSuccess(true);
        log.setDetail(String.format("TextHit:%b, VoiceHit:%b", tH, vH));
        auditLogRepo.save(log);
    }
}