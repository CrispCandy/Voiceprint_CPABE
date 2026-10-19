package org.tanshin.voiceprint.cp.abe.update;

import org.tanshin.voiceprint.cp.abe.text.MedicalCipherPackage;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class DynamicPolicyUpdater {

    /**
     * 生成策略更新增量包
     * @param oldVoiceKey 之前的 AES 密钥
     * @param newVoiceKey 你刚生成的新 AES 密钥
     * @param pkg 数据库中存的 MedicalCipherPackage (只需要里面的长度和IV)
     * @return 准备发给后端的异或增量包
     */
    public static byte[] generateVoiceDelta(SecretKey oldVoiceKey, SecretKey newVoiceKey, MedicalCipherPackage pkg) throws Exception {
        int length = pkg.getVoiceDataLength();
        byte[] iv = pkg.getVoiceIv();

        // 1. 生成一段全 0 的假明文，用于套取流密码的 KeyStream
        byte[] dummyZeroData = new byte[length];

        // 2. 生成旧的密钥流
        Cipher cipherOld = Cipher.getInstance("AES/CTR/NoPadding");
        cipherOld.init(Cipher.ENCRYPT_MODE, oldVoiceKey, new IvParameterSpec(iv));
        byte[] oldStream = cipherOld.doFinal(dummyZeroData);

        // 3. 生成新的密钥流
        Cipher cipherNew = Cipher.getInstance("AES/CTR/NoPadding");
        cipherNew.init(Cipher.ENCRYPT_MODE, newVoiceKey, new IvParameterSpec(iv));
        byte[] newStream = cipherNew.doFinal(dummyZeroData);

        // 4. 计算 Delta (旧流 XOR 新流)
        byte[] delta = new byte[length];
        for (int i = 0; i < length; i++) {
            delta[i] = (byte) (oldStream[i] ^ newStream[i]);
        }

        return delta;
    }
}