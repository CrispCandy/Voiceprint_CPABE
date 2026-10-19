package org.tanshin.voiceprint.cp.abe.text;

import java.io.Serializable;

public class MedicalCipherPackage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String caseId;
    private String encText;
    private String encVoice;
    private CipherText headerText;
    private CipherText headerVoice;

    // ================= 【新增】流密码更新所需字段 =================
    private byte[] textIv;           // 文本加密的 IV
    private int textDataLength;      // 文本明文的字节长度
    private byte[] voiceIv;          // 声纹加密的 IV
    private int voiceDataLength;     // 声纹明文的字节长度


    // 原有的 Getter 和 Setter
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getEncText() { return encText; }
    public void setEncText(String encText) { this.encText = encText; }
    public String getEncVoice() { return encVoice; }
    public void setEncVoice(String encVoice) { this.encVoice = encVoice; }
    public CipherText getHeaderText() { return headerText; }
    public void setHeaderText(CipherText headerText) { this.headerText = headerText; }
    public CipherText getHeaderVoice() { return headerVoice; }
    public void setHeaderVoice(CipherText headerVoice) { this.headerVoice = headerVoice; }

    // 新增的 Getter 和 Setter
    public byte[] getTextIv() { return textIv; }
    public void setTextIv(byte[] textIv) { this.textIv = textIv; }
    public int getTextDataLength() { return textDataLength; }
    public void setTextDataLength(int textDataLength) { this.textDataLength = textDataLength; }
    public byte[] getVoiceIv() { return voiceIv; }
    public void setVoiceIv(byte[] voiceIv) { this.voiceIv = voiceIv; }
    public int getVoiceDataLength() { return voiceDataLength; }
    public void setVoiceDataLength(int voiceDataLength) { this.voiceDataLength = voiceDataLength; }
}