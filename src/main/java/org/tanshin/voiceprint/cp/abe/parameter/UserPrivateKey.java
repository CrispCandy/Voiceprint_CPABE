package org.tanshin.voiceprint.cp.abe.parameter;

import org.tanshin.voiceprint.cp.abe.attribute.Attribute;
import it.unisa.dia.gas.jpbc.Element;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @BelongsProject: JPBC-ABE
 * @BelongsPackage: com.duwei.jpbc.cp.abe.parameter
 * @Author: duwei
 * @Date: 2022/7/22 16:06
 * @Description: 用户私钥
 */
@Data
public class UserPrivateKey {
    private Element D;
    private PairingParameter pairingParameter;
    public Map<Attribute,Element> D_j_map;
    public Map<Attribute,Element> D_j_pie_map;
    private List<Attribute> userAttributes;

    private UserPrivateKey() {
        D_j_map = new HashMap<>();
        D_j_pie_map = new HashMap<>();
    }


    public void putDj(Attribute attribute,Element D_j){
        D_j_map.put(attribute,D_j);
    }

    public void putDjPie(Attribute attribute,Element D_j_pie){
        D_j_pie_map.put(attribute,D_j_pie);
    }


    public Element getDj(Attribute attribute){
        for (Attribute attribute1 : D_j_map.keySet()) {
            if (attribute1.equals(attribute)){
                return D_j_map.get(attribute1);
            }
        }
        return null;
    }

    public Element getDjPie(Attribute attribute){
        for (Attribute attribute1 : D_j_pie_map.keySet()) {
            if (attribute1.equals(attribute)){
                return D_j_pie_map.get(attribute1);
            }
        }
        return null;
    }

    public static UserPrivateKey build(MasterPrivateKey masterPrivateKey, List<Attribute> attributes){
        UserPrivateKey userPrivateKey = new UserPrivateKey();
        userPrivateKey.setPairingParameter(masterPrivateKey.getPairingParameter());
        userPrivateKey.setUserAttributes(attributes);
        Element r = masterPrivateKey.getPairingParameter().getZr().newRandomElement().getImmutable();
        Element alpha = masterPrivateKey.getAlpha();
        Element beta = masterPrivateKey.getBeta();
        Element g = masterPrivateKey.getPairingParameter().getGenerator();
        Element D = g.powZn((alpha.add(r)).div(beta)).getImmutable();
        userPrivateKey.setD(D);
        for (Attribute attribute : attributes){
            Element r_j = masterPrivateKey.getPairingParameter().getZr().newRandomElement().getImmutable();
            Element D_j = g.powZn(r).mul(masterPrivateKey.hash(attribute.getAttributeValue()).powZn(r_j)).getImmutable();
            Element D_j_pie = g.powZn(r_j).getImmutable();
            userPrivateKey.putDjPie(attribute,D_j_pie);
            userPrivateKey.putDj(attribute,D_j);
        }
        return userPrivateKey;
    }

    /**
     * 专门用于生成代理转换密钥 (TK) 的静态工厂方法
     * @param source 原私钥
     * @param z 盲化因子
     * @return 盲化后的新密钥对象
     */
    public static UserPrivateKey buildProxyKey(UserPrivateKey source, Element z) {
        UserPrivateKey tk = new UserPrivateKey();
        tk.setPairingParameter(source.getPairingParameter());
        tk.setUserAttributes(new ArrayList<>(source.getUserAttributes()));

        // 1. 盲化核心组件: D^z
        tk.setD(source.getD().powZn(z).getImmutable());

        // 2. 盲化属性组件: 利用你现有的 putDj/putDjPie 方法
        for (Attribute attr : source.getUserAttributes()) {
            Element dj_z = source.getDj(attr).powZn(z).getImmutable();
            Element djPie_z = source.getDjPie(attr).powZn(z).getImmutable();

            tk.putDj(attr, dj_z);
            tk.putDjPie(attr, djPie_z);
        }
        return tk;
    }

    /**
     * 实现深拷贝方法
     * 确保在多轮解密（如文本+声纹）中，原始私钥的数学状态不被底层运算修改
     */
    public UserPrivateKey duplicate() {
        UserPrivateKey copy = new UserPrivateKey();
        copy.setPairingParameter(this.pairingParameter);

        // 1. 复制核心元素 D (必须 duplicate)
        if (this.D != null) {
            copy.setD(this.D.duplicate());
        }

        // 2. 复制属性列表 (创建新 List)
        if (this.userAttributes != null) {
            copy.setUserAttributes(new ArrayList<>(this.userAttributes));
        }

        // 3. 深度复制 D_j_map
        for (Map.Entry<Attribute, Element> entry : this.D_j_map.entrySet()) {
            // 对每一个 Element 调用 duplicate()
            copy.putDj(entry.getKey(), entry.getValue().duplicate());
        }

        // 4. 深度复制 D_j_pie_map
        for (Map.Entry<Attribute, Element> entry : this.D_j_pie_map.entrySet()) {
            copy.putDjPie(entry.getKey(), entry.getValue().duplicate());
        }

        return copy;
    }
}
