package org.tanshin.voiceprint.cp.abe.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "data_resource")
public class DataResource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String resourceName;
    private String ownerName;
    private String fileType;

    @Column(nullable = false, unique = true)
    private String ipfsCid;

    @Column(length = 1000)
    private String msgAccessPolicy;

    @Column(length = 1000)
    private String voiceAccessPolicy;

    // 【关键新增】用于存储和展示策略描述的字段
    @Column(length = 500)
    private String policyDesc;

    // ⭐ 新增：用于存储加密时建树模型的 JSON 图纸
    @Column(columnDefinition = "TEXT")
    private String msgPolicyModel;

    // ⭐ 新增：用于存储声纹特征建树模型的 JSON 图纸
    @Column(columnDefinition = "TEXT")
    private String voicePolicyModel;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime createTime;

    private String encryptedMsg;
    private String encryptedVoice;

    // Getters and Setters
    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }

    public String getIpfsCid() { return ipfsCid; }
    public void setIpfsCid(String ipfsCid) { this.ipfsCid = ipfsCid; }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    public String getPolicyDesc() { return policyDesc; }
    public void setPolicyDesc(String policyDesc) { this.policyDesc = policyDesc; }
}