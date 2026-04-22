package org.tanshin.voiceprint.cp.abe.util;

import com.alibaba.fastjson2.JSON;
import org.tanshin.voiceprint.cp.abe.text.CipherText;
import org.tanshin.voiceprint.cp.abe.text.MedicalCipherPackage;
import org.tanshin.voiceprint.cp.abe.text.SerializableCipherPackage;

import java.util.Base64;

public class CipherJSONUtils {

    /**
     * 将复杂的 ABE 加密包转换为可存储的 JSON 字符串
     */
    public static String packageToJSON(MedicalCipherPackage pkg) {
        SerializableCipherPackage dto = new SerializableCipherPackage();
        dto.caseId = pkg.getCaseId();
        dto.encText = pkg.getEncText();
        dto.encVoice = pkg.getEncVoice();

        // 【关键】将 CipherText 对象序列化
        // 注意：这里假设你的 CipherText 类实现了 toBytes() 方法
        // 如果没有，你需要根据 CipherText 内部结构手动获取 byte[]
        if (pkg.getHeaderText() != null) {
            byte[] headerBytes = pkg.getHeaderText().toBytes();
            dto.headerTextBase64 = Base64.getEncoder().encodeToString(headerBytes);
        }

        if (pkg.getHeaderVoice() != null) {
            byte[] headerVoiceBytes = pkg.getHeaderVoice().toBytes();
            dto.headerVoiceBase64 = Base64.getEncoder().encodeToString(headerVoiceBytes);
        }

        return JSON.toJSONString(dto, "PrettyFormat"); // 格式化输出，方便论文展示
    }

    /**
     * 从 JSON 还原对象 (用于解密前读取)
     * 注意：还原 CipherText 需要 PublicKey 来初始化群信息
     */
    public static SerializableCipherPackage jsonToDTO(String json) {
        return JSON.parseObject(json, SerializableCipherPackage.class);
    }
}