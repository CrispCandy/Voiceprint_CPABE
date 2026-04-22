package org.tanshin.voiceprint.cp.abe.text;

import it.unisa.dia.gas.jpbc.Element;
import lombok.Data;

/**
 * 代理重加密/转换后的密文结构
 * 代理服务器无法获取明文，只能计算出带有盲化因子的 T 值
 */
@Data
public class ProxyCipherText {
    private Element c_wave; // 原封不动保留
    private Element T;      // 代理计算出的盲化配对值：e(g,g)^{z * alpha * s}

    public ProxyCipherText(Element c_wave, Element T) {
        this.c_wave = (c_wave == null) ? null : c_wave.getImmutable();
        this.T = (T == null) ? null : T.getImmutable();
    }
}
