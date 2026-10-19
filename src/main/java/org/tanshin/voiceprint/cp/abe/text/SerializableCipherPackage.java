package org.tanshin.voiceprint.cp.abe.text;

import java.io.Serializable;

/**
 * 这是一个为了序列化而设计的传输对象 (DTO)
 */
public class SerializableCipherPackage implements Serializable {
    public String caseId;

    public String encText;      // AES-CTR加密后的文本 (Base64)
    public String encVoice;     // AES-CTR加密后的声纹 (Base64)
    public String headerTextBase64;  // ABE密文头1 (Base64序列化后)
    public String headerVoiceBase64; // ABE密文头2 (Base64序列化后)

    // ✨ 新增：AES-CTR 模式必须持久化存储的初始化向量 (IV)
    public byte[] textIv;
    public byte[] voiceIv;

    // ✨ 新增：数据长度标识 (同态异或时，用于精确生成等长的伪随机流)
    public int textDataLength;
    public int voiceDataLength;
}