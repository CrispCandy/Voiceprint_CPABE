package org.tanshin.voiceprint.cp.abe.text;

import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import org.tanshin.voiceprint.cp.abe.attribute.Attribute;
import org.tanshin.voiceprint.cp.abe.parameter.PairingParameter;
import org.tanshin.voiceprint.cp.abe.structure.AccessTree;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class CipherText implements Serializable {
    // 【关键1】加上 transient 避开默认序列化机制
    private transient Element c;
    private transient Element c_wave;
    private transient Map<Attribute, Element> c_y_map;
    private transient Map<Attribute, Element> c_y_pie_map;

    // 【关键2】策略树不参与序列化，解密时根据数据库策略重新 build 即可，大大减小 IPFS 文件体积
    private transient AccessTree accessTree;

    public CipherText() {
        c_y_map = new HashMap<>();
        c_y_pie_map = new HashMap<>();
    }

    public void setC(Element c) { this.c = (c == null) ? null : c.getImmutable(); }
    public Element getC() { return c; }
    public void setC_wave(Element c_wave) { this.c_wave = (c_wave == null) ? null : c_wave.getImmutable(); }
    public Element getC_wave() { return c_wave; }

    public void setAccessTree(AccessTree accessTree) { this.accessTree = accessTree; }
    public AccessTree getAccessTree() { return accessTree; }

    public void putCy(Attribute attribute, Element cy) { c_y_map.put(attribute, cy.getImmutable()); }
    public void putCyPie(Attribute attribute, Element cy_pie) { c_y_pie_map.put(attribute, cy_pie.getImmutable()); }
    public Map<Attribute, Element> getCyMap() { return this.c_y_map; }
    public Map<Attribute, Element> getCyPieMap() { return this.c_y_pie_map; }
    public Element getCy(Attribute attribute) { return c_y_map.get(attribute); }
    public Element getCyPie(Attribute attribute) { return c_y_pie_map.get(attribute); }

    public byte[] toBytes() {
        if (c == null || c_wave == null) return new byte[0];

        byte[] bC = c.toBytes();
        byte[] bWave = c_wave.toBytes();

        return ByteBuffer.allocate(8 + bC.length + bWave.length)
                .putInt(bC.length)
                .put(bC)
                .putInt(bWave.length)
                .put(bWave)
                .array();
    }

    /**
     * 【终极修复】动态克隆模板的群空间，彻底杜绝 Mismatch
     */
    public static CipherText fromBytes(byte[] data, CipherText template) {
        if (data == null || data.length == 0 || template == null) return null;
        ByteBuffer buffer = ByteBuffer.wrap(data);

        // 1. 获取 C 真正的数学群 (getField)，并还原
        int l1 = buffer.getInt();
        byte[] b1 = new byte[l1];
        buffer.get(b1);
        Element restoredC = template.getC().getField().newElementFromBytes(b1).getImmutable();

        // 2. 获取 C_wave 真正的数学群，并还原
        int l2 = buffer.getInt();
        byte[] b2 = new byte[l2];
        buffer.get(b2);
        Element restoredWave = template.getC_wave().getField().newElementFromBytes(b2).getImmutable();

        // 3. 构造并挂载所有依赖
        CipherText target = new CipherText();
        target.setC(restoredC);
        target.setC_wave(restoredWave);

        target.setAccessTree(template.getAccessTree());
        if (template.getCyMap() != null) target.getCyMap().putAll(template.getCyMap());
        if (template.getCyPieMap() != null) target.getCyPieMap().putAll(template.getCyPieMap());

        return target;
    }

    // ================= 手动序列化逻辑 =================
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

        // 1. 写入 C
        byte[] bC = (c != null) ? c.toBytes() : new byte[0];
        out.writeInt(bC.length);
        if (bC.length > 0) out.write(bC);

        // 2. 写入 C_wave
        byte[] bWave = (c_wave != null) ? c_wave.toBytes() : new byte[0];
        out.writeInt(bWave.length);
        if (bWave.length > 0) out.write(bWave);

        // 3. 写入 c_y_map
        out.writeInt(c_y_map.size());
        for (Map.Entry<Attribute, Element> entry : c_y_map.entrySet()) {
            out.writeObject(entry.getKey()); // Attribute 类也需要实现 Serializable!
            byte[] val = entry.getValue().toBytes();
            out.writeInt(val.length);
            out.write(val);
        }

        // 4. 写入 c_y_pie_map
        out.writeInt(c_y_pie_map.size());
        for (Map.Entry<Attribute, Element> entry : c_y_pie_map.entrySet()) {
            out.writeObject(entry.getKey());
            byte[] val = entry.getValue().toBytes();
            out.writeInt(val.length);
            out.write(val);
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        Pairing pairing = PairingParameter.getInstance().getPairing();

        // 1. 还原 C (假设在 G1 群，如果你的算法是在 GT 群，请改为 getGT())
        int lenC = in.readInt();
        if (lenC > 0) {
            byte[] bC = new byte[lenC];
            in.readFully(bC);
            this.c = pairing.getG1().newElementFromBytes(bC).getImmutable();
        }

        // 2. 还原 C_wave (根据你的明文类看，如果是明文乘配对，通常在 GT 群。如报错请尝试 G1)
        int lenWave = in.readInt();
        if (lenWave > 0) {
            byte[] bWave = new byte[lenWave];
            in.readFully(bWave);
            this.c_wave = pairing.getGT().newElementFromBytes(bWave).getImmutable();
        }

        // 3. 还原 c_y_map
        this.c_y_map = new HashMap<>();
        int size1 = in.readInt();
        for (int i = 0; i < size1; i++) {
            Attribute attr = (Attribute) in.readObject();
            int vLen = in.readInt();
            byte[] vBytes = new byte[vLen];
            in.readFully(vBytes);
            this.c_y_map.put(attr, pairing.getG1().newElementFromBytes(vBytes).getImmutable());
        }

        // 4. 还原 c_y_pie_map
        this.c_y_pie_map = new HashMap<>();
        int size2 = in.readInt();
        for (int i = 0; i < size2; i++) {
            Attribute attr = (Attribute) in.readObject();
            int vLen = in.readInt();
            byte[] vBytes = new byte[vLen];
            in.readFully(vBytes);
            this.c_y_pie_map.put(attr, pairing.getG1().newElementFromBytes(vBytes).getImmutable());
        }
    }
}