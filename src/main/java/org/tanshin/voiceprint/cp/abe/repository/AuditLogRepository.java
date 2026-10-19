package org.tanshin.voiceprint.cp.abe.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tanshin.voiceprint.cp.abe.entity.AuditLog;
import org.tanshin.voiceprint.cp.abe.entity.AuditLogEntity;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // 按时间倒序排列：让前端审计页面优先显示最新的操作
    List<AuditLog> findAllByOrderByOpTimeDesc();

    // 查找针对某个特定 CID 的所有操作历史
    List<AuditLog> findByTargetCid(String targetCid);

    // 查找某个用户的所有操作记录
    List<AuditLog> findByOperatorName(String operatorName);

    // 查找所有失败的操作（用于安全预警，比如有人非法尝试解密）
    List<AuditLog> findBySuccessFalse();
}