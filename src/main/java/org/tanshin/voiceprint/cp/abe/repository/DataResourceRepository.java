package org.tanshin.voiceprint.cp.abe.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tanshin.voiceprint.cp.abe.entity.DataResource;

import java.util.Optional;

@Repository
public interface DataResourceRepository extends JpaRepository<DataResource, Long> {

    // 核心功能：根据 IPFS 的 CID 查找资源，用于解密前的定位
    Optional<DataResource> findByIpfsCid(String ipfsCid);

    // 进阶功能：查找某个用户上传的所有资源
    java.util.List<DataResource> findByOwnerName(String ownerName);

    // 模糊查询：用于前端搜索脱敏后的资源名称
    java.util.List<DataResource> findByResourceNameContaining(String name);
}