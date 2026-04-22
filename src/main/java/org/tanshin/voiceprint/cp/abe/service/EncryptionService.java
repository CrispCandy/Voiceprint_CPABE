package org.tanshin.voiceprint.cp.abe.service;

import it.unisa.dia.gas.jpbc.Element;
import org.springframework.stereotype.Service;
import org.tanshin.voiceprint.cp.abe.engine.CpAneEngine;
import org.tanshin.voiceprint.cp.abe.entity.DataResource;
import org.tanshin.voiceprint.cp.abe.mfcc.MedicalRecord;
import org.tanshin.voiceprint.cp.abe.parameter.*;
import org.tanshin.voiceprint.cp.abe.repository.DataResourceRepository;
import org.tanshin.voiceprint.cp.abe.structure.AccessTree;
import org.tanshin.voiceprint.cp.abe.structure.AccessTreeBuildModel;
import org.tanshin.voiceprint.cp.abe.util.AESUtil;
import org.tanshin.voiceprint.cp.abe.util.ConvertUtils;

import javax.crypto.SecretKey;
import java.io.File;
import java.util.Base64;
import java.util.Set;

@Service
public class EncryptionService {

    private final DataResourceRepository resourceRepository;
    private final CpAneEngine engine = new CpAneEngine();
    // 实际项目中，系统密钥应从数据库或配置文件加载，这里模拟初始化
    private final SystemKey systemKey = SystemKey.build();

    public EncryptionService(DataResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    /**
     * 核心业务流：加密、上传模拟、保存数据库
     */
    public String fullEncryptionFlow(MedicalRecord record,
                                     Set<String> textRoles,
                                     Set<String> voiceRoles,
                                     String resourceName) throws Exception {

        PublicKey pk = systemKey.getPublicKey();

        // 1. AES 加密
        SecretKey kText = AESUtil.generateAESKey();
        SecretKey kVoice = AESUtil.generateAESKey();
        String encText = AESUtil.encrypt(record.getMsg(), kText);
        byte[] matrixBytes = ConvertUtils.matrixToBytes(record.getMfccMatrix());
        String encVoiceStr = Base64.getEncoder().encodeToString(AESUtil.encryptBytes(matrixBytes, kVoice));

        // 2. 构造 ABE 访问树 (将 UI 选中的角色转化为 OR 门结构)
        AccessTree treeText = buildSimpleOrTree(pk, textRoles);
        AccessTree treeVoice = buildSimpleOrTree(pk, voiceRoles);

        // 3. ABE 加密 AES 密钥 (使用你测试代码中的补齐逻辑)
        int g1Length = pk.getPairingParameter().getG1().getLengthInBytes();
        Element mText = prepareElement(pk, kText, g1Length);
        Element mVoice = prepareElement(pk, kVoice, g1Length);

        // 生成 Header (密文头部)
        // ... 此处省略具体 engine.encrypt 调用，逻辑同你测试代码 ...

        // 4. 模拟 IPFS 上传
        String fakeCid = "Qm" + Base64.getEncoder().encodeToString(String.valueOf(System.nanoTime()).getBytes()).substring(0, 10);

        // 5. 持久化到 H2 数据库
        DataResource entity = new DataResource();
        entity.setResourceName(resourceName);
        entity.setIpfsCid(fakeCid);
        entity.setMsgAccessPolicy(textRoles.toString());
        entity.setVoiceAccessPolicy(voiceRoles.toString());
        entity.setOwnerName("admin_user");
        resourceRepository.save(entity);

        return fakeCid;
    }

    // 辅助方法：将多个角色生成一个 1-of-N 的访问树
    private AccessTree buildSimpleOrTree(PublicKey pk, Set<String> roles) {
        if (roles.isEmpty()) roles = Set.of("Default");
        String[] roleArray = roles.toArray(new String[0]);
        AccessTreeBuildModel[] models = new AccessTreeBuildModel[roleArray.length + 1];
        // 根节点：阈值为 1 (OR 逻辑)
        models[0] = AccessTreeBuildModel.innerAccessTreeBuildModel(1, 1, roleArray.length, -1);
        for (int i = 0; i < roleArray.length; i++) {
            models[i+1] = AccessTreeBuildModel.leafAccessTreeBuildModel(i+2, 1, roleArray[i], 1);
        }
        return AccessTree.build(pk, models);
    }

    private Element prepareElement(PublicKey pk, SecretKey key, int length) {
        byte[] padded = new byte[length];
        System.arraycopy(key.getEncoded(), 0, padded, length - 16, 16);
        return pk.getPairingParameter().getG1().newElementFromBytes(padded).getImmutable();
    }
}