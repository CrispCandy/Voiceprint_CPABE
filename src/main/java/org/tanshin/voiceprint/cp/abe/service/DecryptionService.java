package org.tanshin.voiceprint.cp.abe.service;

import it.unisa.dia.gas.jpbc.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tanshin.voiceprint.cp.abe.attribute.Attribute;
import org.tanshin.voiceprint.cp.abe.engine.CpAneEngine;
import org.tanshin.voiceprint.cp.abe.entity.DataResource;
import org.tanshin.voiceprint.cp.abe.parameter.PublicKey;
import org.tanshin.voiceprint.cp.abe.parameter.UserPrivateKey;
import org.tanshin.voiceprint.cp.abe.text.CipherText;
import org.tanshin.voiceprint.cp.abe.text.ProxyCipherText;
import org.tanshin.voiceprint.cp.abe.util.AESUtil;
import org.tanshin.voiceprint.cp.abe.util.ConvertUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DecryptionService {

    @Autowired
    private CpAneEngine engine;
    @Autowired
    private EncryptionService encryptionService; // 用于获取系统 PK/MSK

    /**
     * 方式 A: 标准 ABE 直接解密
     */
    public DecryptionResult decryptStandard(DataResource resource, Set<String> roles) throws Exception {
        PublicKey pk = encryptionService.getPublicKey();

        // 1. 模拟 KDC 根据属性生成私钥
        List<Attribute> attributes = roles.stream()
                .map(r -> new Attribute(r, pk))
                .collect(Collectors.toList());
        UserPrivateKey sk = engine.keyGen(encryptionService.getMsk(), attributes);

        // 2. 还原密文对象
        CipherText ctText = CipherText.fromBytes(Base64.getDecoder().decode(resource.getHeaderTextBase64()), pk);
        CipherText ctVoice = CipherText.fromBytes(Base64.getDecoder().decode(resource.getHeaderVoiceBase64()), pk);

        // 3. ABE 解密获取 Element
        Element mText = engine.decrypt(pk, sk, ctText);
        Element mVoice = engine.decrypt(pk, sk, ctVoice);

        if (mText == null || mVoice == null) throw new RuntimeException("权限不匹配，拒绝访问");

        return performFinalAesDecryption(resource, mText, mVoice);
    }

    /**
     * 方式 B: 代理重加密 (PRE) 流程
     * 模拟：代理服务器处理 TK，终端只需处理 RK
     */
    public DecryptionResult decryptViaProxy(DataResource resource, Set<String> delegatorRoles) throws Exception {
        PublicKey pk = encryptionService.getPublicKey();

        // 1. 授权者（Delegator）私钥
        List<Attribute> attributes = delegatorRoles.stream()
                .map(r -> new Attribute(r, pk)).toList();
        UserPrivateKey skDelegator = engine.keyGen(encryptionService.getMsk(), attributes);

        // 2. 生成代理密钥对 (TK, RK)
        Map<String, Object> proxyKeys = engine.generateProxyKeys(pk, skDelegator);
        UserPrivateKey tk = (UserPrivateKey) proxyKeys.get("TK");
        Element rk = (Element) proxyKeys.get("RK");

        // 3. 【代理端】执行重加密转换 (Proxy Transform)
        CipherText ctText = CipherText.fromBytes(Base64.getDecoder().decode(resource.getHeaderTextBase64()), pk);
        CipherText ctVoice = CipherText.fromBytes(Base64.getDecoder().decode(resource.getHeaderVoiceBase64()), pk);

        ProxyCipherText pctText = engine.proxyReEncrypt(pk, tk, ctText);
        ProxyCipherText pctVoice = engine.proxyReEncrypt(pk, tk, ctVoice);

        // 4. 【客户端】使用 RK 极速解密 (仅需一次除法)
        Element mText = engine.delegateeDecrypt(pctText, rk);
        Element mVoice = engine.delegateeDecrypt(pctVoice, rk);

        return performFinalAesDecryption(resource, mText, mVoice);
    }

    /**
     * 内部公共方法：从 Element 恢复 AES 密钥并解密明文
     */
    private DecryptionResult performFinalAesDecryption(DataResource resource, Element mText, Element mVoice) throws Exception {
        // 恢复 Text AES Key (16字节截取)
        byte[] rawT = mText.toBytes();
        SecretKey kText = new SecretKeySpec(rawT, rawT.length - 16, 16, "AES");
        String text = AESUtil.decrypt(resource.getEncText(), kText);

        // 恢复 Voice AES Key (16字节截取)
        byte[] rawV = mVoice.toBytes();
        SecretKey kVoice = new SecretKeySpec(rawV, rawV.length - 16, 16, "AES");
        byte[] matrixBytes = AESUtil.decryptBytes(Base64.getDecoder().decode(resource.getEncVoice()), kVoice);
        float[][] matrix = ConvertUtils.bytesToMatrix(matrixBytes);

        return new DecryptionResult(text, matrix);
    }

    // 内部类用于封装结果
    public record DecryptionResult(String text, float[][] matrix) {}
}