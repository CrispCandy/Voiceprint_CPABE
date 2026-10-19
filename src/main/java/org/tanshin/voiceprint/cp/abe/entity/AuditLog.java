package org.tanshin.voiceprint.cp.abe.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String operatorName;  // 谁操作的
    private String actionType;    // 操作类型：上传、解密声纹、解密文字
    private String targetCid;     // 针对哪个 IPFS 资源
    private String detail;
    private boolean success;      // 是否解密成功 (ABE 属性匹配结果)
    @Column(length = 500)
    private String failReason;    // 如果失败，记录原因：如 "属性不匹配"

    private LocalDateTime opTime = LocalDateTime.now();
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}