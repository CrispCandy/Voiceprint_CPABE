package org.tanshin.voiceprint.cp.abe.text;

import java.io.Serializable;
import java.util.Base64;
import java.util.Map;
import java.util.HashMap;

/**
 * 这是一个为了序列化而设计的传输对象 (DTO)
 */
public class SerializableCipherPackage implements Serializable {
    public String caseId;
    public String encText;      // AES加密后的文本 (Base64)
    public String encVoice;     // AES加密后的声纹 (Base64)
    public String headerTextBase64;  // ABE密文头1 (Base64序列化后)
    public String headerVoiceBase64; // ABE密文头2 (Base64序列化后)

    // 你也可以直接在这个类里加一个转JSON的方法
}