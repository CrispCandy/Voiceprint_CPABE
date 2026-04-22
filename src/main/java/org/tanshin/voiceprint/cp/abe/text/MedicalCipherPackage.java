package org.tanshin.voiceprint.cp.abe.text;

import java.io.Serializable;

public class MedicalCipherPackage implements Serializable {
    private String caseId;
    private String encText;   // AES 加密后的文本 (Base64)
    private String encVoice;  // AES 加密后的 MFCC 矩阵 (Base64)
    private CipherText headerText;  // CP-ABE 加密后的 AES Key 1
    private CipherText headerVoice; // CP-ABE 加密后的 AES Key 2

    // Getter 和 Setter
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
}
