package org.tanshin.voiceprint.cp.abe.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tanshin.voiceprint.cp.abe.entity.SystemUser;

import java.util.Optional;

@Repository
public interface SystemUserRepository extends JpaRepository<SystemUser, Long> {

    // 核心功能：根据用户名查找用户，用于模拟登录和提取 ABE 属性
    Optional<SystemUser> findByUsername(String username);

    // 检查用户名是否存在
    boolean existsByUsername(String username);
}