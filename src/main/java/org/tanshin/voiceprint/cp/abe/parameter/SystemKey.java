package org.tanshin.voiceprint.cp.abe.parameter;

import lombok.Data;

import java.io.Serializable;

/**
 * @BelongsProject: CP-ABE
 * @BelongsPackage: com.duwei.cp.abe.parameter
 * @Author: duwei
 * @Date: 2022/7/22 17:29
 * @Description: 系统密钥，包含系统公钥和系统私钥
 */
@Data
public class SystemKey implements Serializable {
    private static final long serialVersionUID = 1L;
    private PublicKey publicKey;
    private MasterPrivateKey masterPrivateKey;

    private SystemKey() {

    }

    public static SystemKey build() {
        SystemKey systemKey = new SystemKey();
        PairingParameter pairingParameter = PairingParameter.getInstance();
        MasterPrivateKey masterPrivateKey = MasterPrivateKey.build(pairingParameter);
        PublicKey publicKey = PublicKey.build(pairingParameter, masterPrivateKey);
        systemKey.setPublicKey(publicKey);
        systemKey.setMasterPrivateKey(masterPrivateKey);
        return systemKey;
    }

}
