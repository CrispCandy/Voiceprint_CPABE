package org.tanshin.voiceprint.cp.abe.text;

import org.tanshin.voiceprint.cp.abe.parameter.PublicKey;
import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Field;
import lombok.Data;

import java.nio.charset.StandardCharsets;

/**
 * @BelongsProject: JPBC-ABE
 * @BelongsPackage: com.duwei.jpbc.cp.abe
 * @Author: duwei
 * @Date: 2022/7/21 16:42
 * @Description: 明文
 *  明文空间 - G1
 */
@Data
public class PlainText {
    private Element messageValue;
    private String messageStr;

    public PlainText(String messageStr, PublicKey publicKey) {
        this(messageStr, publicKey.getPairingParameter().getG1());
    }

    private PlainText(String messageStr, Field G1) {
        this.messageStr = messageStr;
        this.messageValue = G1.newElementFromBytes(messageStr.getBytes(StandardCharsets.UTF_8)).getImmutable();
    }

    /**
     * 新增构造函数：支持直接传入 Element
     * 用于混合加密中对 AES 密钥生成的 Element 进行封装
     */
    public PlainText(Element messageValue) {
        this.messageValue = messageValue.getImmutable();
        // 由于是加密密钥字节，通常不设置 messageStr，或者设置为十六进制/Base64
        this.messageStr = "AES_KEY_ELEMENT";
    }
}
