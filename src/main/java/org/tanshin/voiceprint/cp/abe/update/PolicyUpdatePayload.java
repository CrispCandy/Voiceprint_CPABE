package org.tanshin.voiceprint.cp.abe.update;

import org.tanshin.voiceprint.cp.abe.text.CipherText;
import java.io.Serializable;

public class PolicyUpdatePayload implements Serializable {
    private static final long serialVersionUID = 1L;

    // 异或增量 (Delta)
    private byte[] voiceDelta;
    // 采用新策略和新AES密钥加密的全新信封
    private CipherText newHeaderVoice;

    public PolicyUpdatePayload(byte[] voiceDelta, CipherText newHeaderVoice) {
        this.voiceDelta = voiceDelta;
        this.newHeaderVoice = newHeaderVoice;
    }

    public byte[] getVoiceDelta() { return voiceDelta; }
    public void setVoiceDelta(byte[] voiceDelta) { this.voiceDelta = voiceDelta; }
    public CipherText getNewHeaderVoice() { return newHeaderVoice; }
    public void setNewHeaderVoice(CipherText newHeaderVoice) { this.newHeaderVoice = newHeaderVoice; }
}