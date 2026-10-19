package org.tanshin.voiceprint.cp.abe.service;

import com.fasterxml.jackson.databind.ObjectMapper; // 引入 Jackson 工具
import it.unisa.dia.gas.jpbc.Element;
import org.springframework.stereotype.Service;
import org.tanshin.voiceprint.cp.abe.attribute.Attribute;
import org.tanshin.voiceprint.cp.abe.engine.CpAneEngine;
import org.tanshin.voiceprint.cp.abe.entity.AuditLog;
import org.tanshin.voiceprint.cp.abe.entity.DataResource;
import org.tanshin.voiceprint.cp.abe.parameter.PublicKey;
import org.tanshin.voiceprint.cp.abe.parameter.UserPrivateKey;
import org.tanshin.voiceprint.cp.abe.repository.AuditLogRepository;
import org.tanshin.voiceprint.cp.abe.repository.DataResourceRepository;
import org.tanshin.voiceprint.cp.abe.structure.AccessTree;
import org.tanshin.voiceprint.cp.abe.structure.AccessTreeBuildModel;
import org.tanshin.voiceprint.cp.abe.text.MedicalCipherPackage;
import org.tanshin.voiceprint.cp.abe.util.ConvertUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.stream.Stream;

@Service
public class DecryptionService {

    private final DataResourceRepository resourceRepository;
    private final SystemKeyManager keyManager;
    private final IpfsService ipfsService;
    private final AuditLogRepository auditLogRepo;
    private final CpAneEngine engine = new CpAneEngine();

    // 实例化 JSON 工具
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DecryptionService(DataResourceRepository resourceRepository,
                             SystemKeyManager keyManager,
                             IpfsService ipfsService,
                             AuditLogRepository auditLogRepo) {
        this.resourceRepository = resourceRepository;
        this.keyManager = keyManager;
        this.ipfsService = ipfsService;
        this.auditLogRepo = auditLogRepo;
    }

    public record DecryptionResult(String text, float[][] matrix) {}

    public DecryptionResult fullDecryptionFlow(String cid, Set<String> roles, String operatorName) throws Exception {
        PublicKey pk = keyManager.getSystemKey().getPublicKey();

        DataResource resource = resourceRepository.findAll().stream()
                .filter(r -> r.getIpfsCid().equals(cid))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("数据库中找不到该CID对应的资源记录"));

        MedicalCipherPackage pkg = downloadPackage(cid);

        // ⭐ 照图施工：直接根据数据库取出的 JSON 图纸重建树
        AccessTree textTree = rebuildTreeFromJson(pk, resource.getMsgPolicyModel());
        pkg.getHeaderText().setAccessTree(textTree);

        AccessTree voiceTree = rebuildTreeFromJson(pk, resource.getVoicePolicyModel());
        pkg.getHeaderVoice().setAccessTree(voiceTree);

        // 清洗属性 (仅用于生成私钥)
        Set<String> superCleanRoles = roles.stream()
                .flatMap(r -> {
                    if (r == null) return Stream.empty();
                    String clean = r.replace("[", "").replace("]", "").trim();
                    return Arrays.stream(clean.split("[,，_\\s]+"));
                })
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());

        List<Attribute> attributes = superCleanRoles.stream()
                .map(name -> new Attribute(name, pk))
                .collect(Collectors.toList());

        UserPrivateKey userPrivKey = engine.keyGen(keyManager.getSystemKey().getMasterPrivateKey(), attributes);

        String decryptedText = null;
        float[][] decryptedMatrix = null;
        boolean anySuccess = false;

        try {
            Element recText = engine.decrypt(pk, userPrivKey.duplicate(), pkg.getHeaderText());
            if (recText != null) {
                SecretKey kText = deriveAESKeyFromElement(recText);
                decryptedText = decryptAES(pkg.getEncText(), kText);
                anySuccess = true;
            }
        } catch (Exception e) {
            System.err.println("文本解密数学计算出错: " + e.getMessage());
        }

        try {
            Element recVoice = engine.decrypt(pk, userPrivKey.duplicate(), pkg.getHeaderVoice());
            if (recVoice != null) {
                SecretKey kVoice = deriveAESKeyFromElement(recVoice);
                byte[] mBytes = decryptBytesAES(Base64.getDecoder().decode(pkg.getEncVoice()), kVoice);
                decryptedMatrix = ConvertUtils.bytesToMatrix(mBytes);
                anySuccess = true;
            }
        } catch (Exception e) {
            System.err.println("声纹解密数学计算出错: " + e.getMessage());
        }

        if (anySuccess) {
            recordAuditLog(cid, operatorName, "DECRYPT", true,
                    (decryptedText != null ? "[Text OK] " : "") + (decryptedMatrix != null ? "[Voice OK]" : ""));
        } else {
            recordAuditLog(cid, operatorName, "DECRYPT", false, "属性策略完全不匹配");
            throw new RuntimeException("权限不足：您的属性无法解密该资源中的任何内容");
        }

        return new DecryptionResult(decryptedText, decryptedMatrix);
    }

    // ⭐ 终极解决方案：反序列化 JSON 图纸，原汁原味还原模型！
    private AccessTree rebuildTreeFromJson(PublicKey pk, String policyModelJson) throws Exception {
        if (policyModelJson == null || policyModelJson.isBlank()) {
            throw new RuntimeException("数据异常：数据库中缺失访问树的模型图纸！");
        }
        // 直接将其转回加密时的原始数组结构
        AccessTreeBuildModel[] models = objectMapper.readValue(policyModelJson, AccessTreeBuildModel[].class);
        return AccessTree.build(pk, models);
    }

    private SecretKey deriveAESKeyFromElement(Element e) throws Exception {
        byte[] rawBytes = e.toBytes();
        byte[] aesKey = new byte[16];

        if (rawBytes.length < 16) {
            throw new RuntimeException("还原的 Element 字节长度异常，小于16字节！");
        }

        System.arraycopy(rawBytes, rawBytes.length - 16, aesKey, 0, 16);
        return new SecretKeySpec(aesKey, "AES");
    }

    private String decryptAES(String data, SecretKey key) throws Exception {
        Cipher c = Cipher.getInstance("AES");
        c.init(Cipher.DECRYPT_MODE, key);
        return new String(c.doFinal(Base64.getDecoder().decode(data)), StandardCharsets.UTF_8);
    }

    private byte[] decryptBytesAES(byte[] data, SecretKey key) throws Exception {
        Cipher c = Cipher.getInstance("AES");
        c.init(Cipher.DECRYPT_MODE, key);
        return c.doFinal(data);
    }

    private MedicalCipherPackage downloadPackage(String cid) throws Exception {
        byte[] data = ipfsService.download(cid);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (MedicalCipherPackage) ois.readObject();
        }
    }

    private void recordAuditLog(String cid, String op, String type, boolean ok, String msg) {
        AuditLog log = new AuditLog();
        log.setOpTime(LocalDateTime.now());
        log.setActionType(type);
        log.setTargetCid(cid);
        log.setOperatorName(op);
        log.setSuccess(ok);
        log.setDetail(msg);
        auditLogRepo.save(log);
    }
}