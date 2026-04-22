package org.tanshin.voiceprint.cp.abe.text;

import it.unisa.dia.gas.jpbc.Element;

/**
 * 离线预计算数据包容器
 * 用于 IoT 设备在空闲时预先生成的半成品密文组件
 */
public class OfflinePackage {
    // 预先算好的 e(g,g)^{alpha * s}，在线阶段只需要和明文做乘法
    private Element egg_a_s;

    // 预先算好的、已经包含了所有树节点耗时指数运算的“半成品密文”
    private CipherText preCipherText;

    public OfflinePackage(Element egg_a_s, CipherText preCipherText) {
        this.egg_a_s = egg_a_s;
        this.preCipherText = preCipherText;
    }

    public Element getEgg_a_s() {
        return egg_a_s;
    }

    public CipherText getPreCipherText() {
        return preCipherText;
    }
}