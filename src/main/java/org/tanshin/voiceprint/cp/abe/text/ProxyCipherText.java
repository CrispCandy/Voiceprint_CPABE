package org.tanshin.voiceprint.cp.abe.text;

import it.unisa.dia.gas.jpbc.Element;
import org.tanshin.voiceprint.cp.abe.parameter.PairingParameter;
import lombok.Data;
import java.io.*;

@Data
public class ProxyCipherText implements Serializable {
    private static final long serialVersionUID = 1L;

    private transient Element c_wave;
    private transient Element T;

    public ProxyCipherText(Element c_wave, Element T) {
        this.c_wave = (c_wave == null) ? null : c_wave.getImmutable();
        this.T = (T == null) ? null : T.getImmutable();
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

        byte[] b1 = (c_wave != null) ? c_wave.toBytes() : new byte[0];
        out.writeInt(b1.length);
        if (b1.length > 0) out.write(b1);

        byte[] b2 = (T != null) ? T.toBytes() : new byte[0];
        out.writeInt(b2.length);
        if (b2.length > 0) out.write(b2);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        it.unisa.dia.gas.jpbc.Pairing pairing = PairingParameter.getInstance().getPairing();

        int l1 = in.readInt();
        if (l1 > 0) {
            byte[] b1 = new byte[l1];
            in.readFully(b1);
            this.c_wave = pairing.getGT().newElementFromBytes(b1).getImmutable();
        }

        int l2 = in.readInt();
        if (l2 > 0) {
            byte[] b2 = new byte[l2];
            in.readFully(b2);
            // 盲化配对值 T 一定在 GT 群
            this.T = pairing.getGT().newElementFromBytes(b2).getImmutable();
        }
    }
}