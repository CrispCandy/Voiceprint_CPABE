package org.tanshin.voiceprint.cp.abe.text;

import it.unisa.dia.gas.jpbc.Element;
import org.tanshin.voiceprint.cp.abe.parameter.PairingParameter;
import org.tanshin.voiceprint.cp.abe.structure.AccessTreeBuildModel; // 引入Model
import java.io.*;

public class OfflinePackage implements Serializable {
    private static final long serialVersionUID = 1L;

    private transient Element egg_a_s;
    private CipherText preCipherText;

    // ⭐ 新增：保存生成该包时的树模型图纸
    private AccessTreeBuildModel[] buildModels;

    public OfflinePackage(Element egg_a_s, CipherText preCipherText) {
        this.egg_a_s = egg_a_s;
        this.preCipherText = preCipherText;
    }

    public Element getEgg_a_s() { return egg_a_s; }
    public CipherText getPreCipherText() { return preCipherText; }

    // Getter & Setter
    public AccessTreeBuildModel[] getBuildModels() { return buildModels; }
    public void setBuildModels(AccessTreeBuildModel[] buildModels) { this.buildModels = buildModels; }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        byte[] b = (egg_a_s != null) ? egg_a_s.toBytes() : new byte[0];
        out.writeInt(b.length);
        if (b.length > 0) out.write(b);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        it.unisa.dia.gas.jpbc.Pairing pairing = PairingParameter.getInstance().getPairing();
        int l = in.readInt();
        if (l > 0) {
            byte[] b = new byte[l];
            in.readFully(b);
            this.egg_a_s = pairing.getGT().newElementFromBytes(b).getImmutable();
        }
    }
}