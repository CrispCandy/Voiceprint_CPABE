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
 * @Date: 2022/7/21 16:27
 * @Description: 系统主私钥
 */
@Data
@ToString
public class MasterPrivateKey extends Key implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * beta in Z_p (属于 Zr 群)
     */
    private transient Element beta;

    /**
     * g pow alpha (属于 G1 群)
     */
    private transient Element g_alpha;

    /**
     * alpha (属于 Zr 群)
     */
    private transient Element alpha;

    private MasterPrivateKey(){
    }

    private MasterPrivateKey(PairingParameter parameter){
        super(parameter);
    }

    public static MasterPrivateKey build(PairingParameter parameter){
        MasterPrivateKey masterPrivateKey = new MasterPrivateKey(parameter);
        masterPrivateKey.setBeta(parameter.getZr().newRandomElement().getImmutable());
        masterPrivateKey.setAlpha(parameter.getZr().newRandomElement().getImmutable());
        masterPrivateKey.setG_alpha((parameter.getGenerator().powZn(masterPrivateKey.getAlpha())).getImmutable());
        return masterPrivateKey;
    }

    // =========================================================
    // 核心魔法：自定义序列化处理
    // =========================================================
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

        // 保存 beta
        byte[] betaBytes = beta.toBytes();
        out.writeInt(betaBytes.length);
        out.write(betaBytes);

        // 保存 g_alpha
        byte[] gAlphaBytes = g_alpha.toBytes();
        out.writeInt(gAlphaBytes.length);
        out.write(gAlphaBytes);

        // 保存 alpha
        byte[] alphaBytes = alpha.toBytes();
        out.writeInt(alphaBytes.length);
        out.write(alphaBytes);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        // 【最关键的修复】手动将父类因序列化丢失的 PairingParameter 补回来！
        this.setPairingParameter(PairingParameter.getInstance());

        it.unisa.dia.gas.jpbc.Pairing pairing = this.getPairingParameter().getPairing();

        // 还原 beta (Zr群)
        int betaLen = in.readInt();
        byte[] betaBytes = new byte[betaLen];
        in.readFully(betaBytes);
        this.beta = pairing.getZr().newElementFromBytes(betaBytes).getImmutable();

        // 还原 g_alpha (G1群)
        int gAlphaLen = in.readInt();
        byte[] gAlphaBytes = new byte[gAlphaLen];
        in.readFully(gAlphaBytes);
        this.g_alpha = pairing.getG1().newElementFromBytes(gAlphaBytes).getImmutable();

        // 还原 alpha (Zr群)
        int alphaLen = in.readInt();
        byte[] alphaBytes = new byte[alphaLen];
        in.readFully(alphaBytes);
        this.alpha = pairing.getZr().newElementFromBytes(alphaBytes).getImmutable();
    }
}