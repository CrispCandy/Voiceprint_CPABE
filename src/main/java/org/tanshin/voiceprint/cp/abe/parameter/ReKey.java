package org.tanshin.voiceprint.cp.abe.parameter;

import it.unisa.dia.gas.jpbc.Element;
import org.tanshin.voiceprint.cp.abe.structure.AccessTree;

public class ReKey {
    // 转换因子 (通常是 GT 元素，用于修改密文的 C_wave 部分)
    private Element k_tran;
    // 新的访问树策略 (目标用户 B 需要满足的策略)
    private AccessTree newTree;

    public ReKey(Element k_tran, AccessTree newTree) {
        this.k_tran = k_tran.getImmutable();
        this.newTree = newTree;
    }

    public Element getK_tran() { return k_tran; }
    public AccessTree getNewTree() { return newTree; }
}
