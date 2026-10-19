package org.tanshin.voiceprint.cp.abe.util;

import com.alibaba.fastjson2.JSON;
import org.tanshin.voiceprint.cp.abe.text.MedicalCipherPackage;
import org.tanshin.voiceprint.cp.abe.text.SerializableCipherPackage;
import org.tanshin.voiceprint.cp.abe.text.CipherText;

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

        // ✨ 新增的 IV 和长度字段直接赋值，Fastjson2 会自动处理 byte[]
        dto.textIv = pkg.getTextIv();
        dto.voiceIv = pkg.getVoiceIv();
        dto.textDataLength = pkg.getTextDataLength();
        dto.voiceDataLength = pkg.getVoiceDataLength();

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
     * 从 JSON 还原为 DTO 传输对象
     */
    public static SerializableCipherPackage jsonToDTO(String json) {
        // 这一步不用改，Fastjson2 自动映射新增字段
        return JSON.parseObject(json, SerializableCipherPackage.class);
    }

    /**
     * (可选) 将 DTO 直接还原为业务实体 MedicalCipherPackage
     * 需传入模板以恢复 CipherText 内部的群参数结构
     */
    public static MedicalCipherPackage dtoToPackage(SerializableCipherPackage dto, MedicalCipherPackage template) {
        MedicalCipherPackage pkg = new MedicalCipherPackage();
        pkg.setCaseId(dto.caseId);
        pkg.setEncText(dto.encText);
        pkg.setEncVoice(dto.encVoice);

        // ✨ 将 DTO 里的 IV 和长度恢复到实体中
        pkg.setTextIv(dto.textIv);
        pkg.setVoiceIv(dto.voiceIv);
        pkg.setTextDataLength(dto.textDataLength);
        pkg.setVoiceDataLength(dto.voiceDataLength);

        if (dto.headerTextBase64 != null && template != null && template.getHeaderText() != null) {
            byte[] raw = Base64.getDecoder().decode(dto.headerTextBase64);
            pkg.setHeaderText(CipherText.fromBytes(raw, template.getHeaderText()));
        }

        if (dto.headerVoiceBase64 != null && template != null && template.getHeaderVoice() != null) {
            byte[] raw = Base64.getDecoder().decode(dto.headerVoiceBase64);
            pkg.setHeaderVoice(CipherText.fromBytes(raw, template.getHeaderVoice()));
        }

        return pkg;
    }
}