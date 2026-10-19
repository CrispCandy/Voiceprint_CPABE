package org.tanshin.voiceprint.cp.abe.parameter;

import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import lombok.Data;
import lombok.ToString;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * @BelongsProject: JPBC-ABE
 * @BelongsPackage: com.duwei.jpbc.cp.abe
 * @Author: duwei
 * @Date: 2022/7/21 16:22
 * @Description: 系统公钥
 */
@Data
@ToString
public class PublicKey extends Key implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * h = g ^ alpha (属于 G1 群)
     * 注意：加上 transient，阻止默认序列化
     */
    private transient Element h;

    /**
     * 公开的双线性对结果 (属于 GT 群)
     */
    private transient Element egg_a;

    /**
     * f = g ^ (1 / beta) (属于 G1 群)
     */
    private transient Element f;

    private PublicKey() {
    }

    private PublicKey(PairingParameter parameter) {
        super(parameter);
    }

    public static PublicKey build(PairingParameter parameter, MasterPrivateKey msk) {
        PublicKey publicKey = new PublicKey(parameter);
        publicKey.setH(parameter.getGenerator().powZn(msk.getBeta()).getImmutable());
        publicKey.setF(parameter.getGenerator().powZn(msk.getBeta().invert()).getImmutable());
        publicKey.setEgg_a((parameter.getPairing().pairing(parameter.getGenerator(), parameter.getGenerator()).mulZn(msk.getAlpha())).getImmutable());
        return publicKey;
    }

    // =========================================================
    // 核心魔法：自定义序列化处理
    // =========================================================
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject(); // 序列化普通属性 (父类的 parameter)

        // 保存 h
        byte[] hBytes = h.toBytes();
        out.writeInt(hBytes.length);
        out.write(hBytes);

        // 保存 egg_a
        byte[] eggaBytes = egg_a.toBytes();
        out.writeInt(eggaBytes.length);
        out.write(eggaBytes);

        // 保存 f
        byte[] fBytes = f.toBytes();
        out.writeInt(fBytes.length);
        out.write(fBytes);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject(); // 反序列化当前类的普通属性

        // 【最关键的修复】手动将父类因序列化丢失的 PairingParameter 补回来！
        this.setPairingParameter(PairingParameter.getInstance());

        // 获取 Pairing 引擎
        it.unisa.dia.gas.jpbc.Pairing pairing = this.getPairingParameter().getPairing();

        // 还原 h (G1群)
        int hLen = in.readInt();
        byte[] hBytes = new byte[hLen];
        in.readFully(hBytes);
        this.h = pairing.getG1().newElementFromBytes(hBytes).getImmutable();

        // 还原 egg_a (GT群)
        int eggaLen = in.readInt();
        byte[] eggaBytes = new byte[eggaLen];
        in.readFully(eggaBytes);
        this.egg_a = pairing.getGT().newElementFromBytes(eggaBytes).getImmutable();

        // 还原 f (G1群)
        int fLen = in.readInt();
        byte[] fBytes = new byte[fLen];
        in.readFully(fBytes);
        this.f = pairing.getG1().newElementFromBytes(fBytes).getImmutable();
    }
}