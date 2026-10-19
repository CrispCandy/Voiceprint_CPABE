package org.tanshin.voiceprint.cp.abe.service;

import org.springframework.stereotype.Service;
import org.tanshin.voiceprint.cp.abe.attribute.Attribute;
import org.tanshin.voiceprint.cp.abe.parameter.UserPrivateKey;
import org.tanshin.voiceprint.cp.abe.text.CipherText;
import org.tanshin.voiceprint.cp.abe.view.DataOwnerView; // 引入原先的上下文，或在此处直接初始化

import java.util.ArrayList;
import java.util.List;

@Service
public class KgcService {

    // 💡 动态缓存群参数上下文密文模板，彻底废弃不安全的跨视图静态变量引用
    private CipherText textTemplate;
    private CipherText voiceTemplate;

    /**
     * 核心职责 1：根据终端选择的属性角色，由 KGC 安全地签发用户 ABE 私钥
     * 避免了在消费端（Consumer）直接暴露或使用 msk 主密钥
     */
    public UserPrivateKey generateUserPrivateKey(List<String> roles) {
        // 💡 提示：如果你的 AbeContext 已经迁移到了 KGC，请把下面的 DataOwnerView.AbeContext 替换为本地变量
        var engine = DataOwnerView.AbeContext.engine;
        var msk = DataOwnerView.AbeContext.msk;
        var pk = DataOwnerView.AbeContext.pk;

        if (engine == null || msk == null || pk == null) {
            throw new IllegalStateException("KGC 尚未初始化，请先访问数据拥有者界面初始化 ABE 上下文！");
        }

        List<Attribute> userAttrs = new ArrayList<>();
        for (String role : roles) {
            userAttrs.add(new Attribute(role, pk));
        }

        // 调用 ABE 引擎生成私钥
        return engine.keyGen(msk, userAttrs);
    }

    /**
     * 核心职责 2：提供全局统一的密文反序列化群参数模板
     */
    public synchronized void registerTemplates(CipherText textTemplate, CipherText voiceTemplate) {
        this.textTemplate = textTemplate;
        this.voiceTemplate = voiceTemplate;
    }

    public synchronized CipherText getTextTemplate() {
        return textTemplate;
    }

    public synchronized CipherText getVoiceTemplate() {
        return voiceTemplate;
    }

    // 方便消费端获取公钥和引擎
    public Object getPk() {
        return DataOwnerView.AbeContext.pk;
    }

    public Object getEngine() {
        return DataOwnerView.AbeContext.engine;
    }
}