package org.tanshin.voiceprint.cp.abe.engine;

import org.tanshin.voiceprint.cp.abe.mfcc.MedicalRecord;
import org.tanshin.voiceprint.cp.abe.parameter.PublicKey;
import org.tanshin.voiceprint.cp.abe.structure.AccessTree;
import org.tanshin.voiceprint.cp.abe.text.CipherText;
import org.tanshin.voiceprint.cp.abe.text.MedicalCipherPackage;
import org.tanshin.voiceprint.cp.abe.text.PlainText;
import org.tanshin.voiceprint.cp.abe.util.AESUtil;
import org.tanshin.voiceprint.cp.abe.util.ConvertUtils;
import it.unisa.dia.gas.jpbc.Element;
import javax.crypto.SecretKey;
import java.util.Base64;

public class HybridCryptoService {
    private CpAneEngine abeEngine = new CpAneEngine();

    public MedicalCipherPackage encryptAll(MedicalRecord record,
                                           AccessTree textPolicy,
                                           AccessTree voicePolicy,
                                           PublicKey pk) throws Exception {

        // 1. 生成 AES 密钥
        SecretKey kText = AESUtil.generateAESKey();
        SecretKey kVoice = AESUtil.generateAESKey();

        // 2. AES 加密大数据
        // 加密文本
        String encText = AESUtil.encrypt(record.getMsg(), kText);
        // 加密声纹矩阵
        byte[] matrixBytes = ConvertUtils.matrixToBytes(record.getMfccMatrix());
        byte[] encVoiceBytes = AESUtil.encryptBytes(matrixBytes, kVoice);
        String encVoice = Base64.getEncoder().encodeToString(encVoiceBytes);

        // 3. CP-ABE 加密 AES 密钥
        // 转换密钥为 GT Element
        Element mText = pk.getPairingParameter().getGT().newElementFromBytes(kText.getEncoded()).getImmutable();
        Element mVoice = pk.getPairingParameter().getGT().newElementFromBytes(kVoice.getEncoded()).getImmutable();

        CipherText headerText = abeEngine.encrypt(pk, new PlainText(mText), textPolicy);
        CipherText headerVoice = abeEngine.encrypt(pk, new PlainText(mVoice), voicePolicy);

        // 4. 组装包
        MedicalCipherPackage pkg = new MedicalCipherPackage();
        pkg.setCaseId("P-" + System.currentTimeMillis());
        pkg.setEncText(encText);
        pkg.setEncVoice(encVoice);
        pkg.setHeaderText(headerText);
        pkg.setHeaderVoice(headerVoice);

        return pkg;
    }
}