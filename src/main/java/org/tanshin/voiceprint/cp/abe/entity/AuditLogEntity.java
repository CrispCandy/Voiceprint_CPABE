package org.tanshin.voiceprint.cp.abe.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "system_audit_log")
public class AuditLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_time")
    private LocalDateTime time;

    @Column(name = "action_type") // e.g., ENCRYPT, DECRYPT, OFFLINE_GENERATE
    private String action;

    @Column(name = "target_cid")
    private String cid;

    @Column(name = "status")
    private String status;

    public AuditLogEntity() {}

    public AuditLogEntity(LocalDateTime time, String action, String cid, String status) {
        this.time = time;
        this.action = action;
        this.cid = cid;
        this.status = status;
    }
}