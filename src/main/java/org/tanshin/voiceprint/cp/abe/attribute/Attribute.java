package org.tanshin.voiceprint.cp.abe.attribute;

import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Field;
import org.tanshin.voiceprint.cp.abe.parameter.PairingParameter;
import org.tanshin.voiceprint.cp.abe.parameter.PublicKey;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class Attribute implements Serializable {
    private static final long serialVersionUID = 1L;

    private String attributeName;
    private transient Element attributeValue; // 不参与序列化

    public Attribute(String attributeName, PublicKey publicKey){
        this(attributeName, publicKey.getPairingParameter().getG0());
    }

    public Attribute(String attributeName, Field G0){
        this.attributeName = attributeName != null ? attributeName.trim() : "";
        initElement(G0);
    }

    private void initElement(Field G0) {
        // 保证 attributeValue 的生成逻辑在全系统唯一
        this.attributeValue = G0.newElementFromBytes(this.attributeName.getBytes(StandardCharsets.UTF_8)).getImmutable();
    }

    public String getAttributeName() { return attributeName; }
    public Element getAttributeValue() { return attributeValue; }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // 反序列化后，必须从当前的单例 PairingParameter 中恢复数学元素
        Field G0 = PairingParameter.getInstance().getPairing().getG1();
        initElement(G0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Attribute)) return false;
        Attribute that = (Attribute) o;
        return Objects.equals(attributeName, that.attributeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attributeName);
    }

    @Override
    public String toString() { return attributeName; }
}