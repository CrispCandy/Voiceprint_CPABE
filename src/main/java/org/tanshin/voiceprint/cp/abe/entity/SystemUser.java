package org.tanshin.voiceprint.cp.abe.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "app_user")
public class SystemUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String username;

    // 用户拥有的属性集合，用逗号分隔，如 "Hospital:A, Dept:Tech, Role:Admin"
    // 在实际加解密时，你会根据这些属性去生成 ABE 私钥
    private String attributes;

    private String role; // 前端权限控制：ADMIN, DATA_OWNER, CONSUMER
}
