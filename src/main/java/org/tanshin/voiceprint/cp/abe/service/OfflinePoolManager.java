package org.tanshin.voiceprint.cp.abe.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.tanshin.voiceprint.cp.abe.engine.CpAneEngine;
import org.tanshin.voiceprint.cp.abe.parameter.PublicKey;
import org.tanshin.voiceprint.cp.abe.structure.AccessTree;
import org.tanshin.voiceprint.cp.abe.structure.AccessTreeBuildModel;
import org.tanshin.voiceprint.cp.abe.text.CipherText;
import org.tanshin.voiceprint.cp.abe.text.OfflinePackage;

import jakarta.annotation.PostConstruct;
import org.tanshin.voiceprint.cp.abe.text.PlainText;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class OfflinePoolManager {

    private final Map<String, Queue<OfflinePackage>> multiPool = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> replenishingFlags = new ConcurrentHashMap<>();

    private final int MAX_POOL_SIZE = 50;
    private final int THRESHOLD = 10;

    private final CpAneEngine engine = new CpAneEngine();
    private final SystemKeyManager keyManager;

    public OfflinePoolManager(SystemKeyManager keyManager) {
        this.keyManager = keyManager;
    }

    private String generatePolicyKey(Set<String> roles) {
        if (roles == null || roles.isEmpty()) return "Default";
        return roles.stream().sorted().collect(Collectors.joining("_"));
    }

    public OfflinePackage getOfflinePackage(Set<String> roles) {
        String policyKey = generatePolicyKey(roles);
        multiPool.putIfAbsent(policyKey, new ConcurrentLinkedQueue<>());
        replenishingFlags.putIfAbsent(policyKey, new AtomicBoolean(false));

        Queue<OfflinePackage> pool = multiPool.get(policyKey);
        OfflinePackage pkg = pool.poll();

        if (pool.size() <= THRESHOLD) {
            replenishPool(roles, policyKey);
        }
        return pkg;
    }

    @Async
    public void replenishPool(Set<String> roles, String policyKey) {
        AtomicBoolean isReplenishing = replenishingFlags.get(policyKey);
        if (!isReplenishing.compareAndSet(false, true)) {
            return;
        }

        try {
            Queue<OfflinePackage> pool = multiPool.get(policyKey);
            if (pool.size() >= MAX_POOL_SIZE) return;

            PublicKey pk = keyManager.getSystemKey().getPublicKey();

            // ⭐ 核心拆分：先生成图纸，再建树
            AccessTreeBuildModel[] models = generateModels(roles);
            AccessTree specificTree = AccessTree.build(pk, models);

            while (pool.size() < MAX_POOL_SIZE) {
                OfflinePackage pkg = engine.offlineEncrypt(pk, specificTree);
                // ⭐ 将图纸绑定到离线包上
                pkg.setBuildModels(models);
                pool.offer(pkg);
            }
        } catch (Exception e) {
            System.err.println("❌ 预计算失败: " + e.getMessage());
        } finally {
            isReplenishing.set(false);
        }
    }

    // ⭐ 新增开放方法：纯粹生成树图纸模型
    public AccessTreeBuildModel[] generateModels(Set<String> roles) {
        String[] roleArray = (roles == null || roles.isEmpty()) ?
                new String[]{"Default"} : roles.stream().sorted().toArray(String[]::new);

        AccessTreeBuildModel[] models = new AccessTreeBuildModel[roleArray.length + 1];
        models[0] = AccessTreeBuildModel.innerAccessTreeBuildModel(1, 1, roleArray.length, -1);

        for (int i = 0; i < roleArray.length; i++) {
            models[i + 1] = AccessTreeBuildModel.leafAccessTreeBuildModel(i + 2, i + 1, roleArray[i], 1);
        }
        return models;
    }

    public int getTotalSize() {
        return multiPool.values().stream().mapToInt(Queue::size).sum();
    }
    public int getMaxSize() {
        return multiPool.isEmpty() ? 100 : multiPool.size() * MAX_POOL_SIZE;
    }
    public int getPoolCount() {
        return multiPool.size();
    }

    @PostConstruct
    public void initDefaultPool() {
        Set<String> defaultRoles = Set.of("Doctor", "Admin");
        String policyKey = generatePolicyKey(defaultRoles);
        multiPool.putIfAbsent(policyKey, new ConcurrentLinkedQueue<>());
        replenishingFlags.putIfAbsent(policyKey, new AtomicBoolean(false));
        replenishPool(defaultRoles, policyKey);
    }

    public void forceReplenishAll() {
        if (multiPool.isEmpty()) {
            initDefaultPool();
        } else {
            for (String policyKey : multiPool.keySet()) {
                Set<String> roles = policyKey.equals("Default") ?
                        java.util.Collections.emptySet() :
                        java.util.Arrays.stream(policyKey.split("_")).collect(Collectors.toSet());
                replenishPool(roles, policyKey);
            }
        }
    }

    /**
     * 核心方法：尝试从离线池加速加密
     * @param engine ABE引擎
     * @param pk 公钥
     * @param plaintext 明文（AES密钥映射的群元素）
     * @param roles 当前选择的属性集合（用于匹配池）
     * @param fallbackTree 如果池为空，降级使用标准加密所需的完整策略树
     * @return 密文结果
     */
    // 在 OfflinePoolManager.java 中修改该方法
    public CipherText encryptWithPool(CpAneEngine engine, PublicKey pk, PlainText plaintext,
                                      Set<String> roles, AccessTree fallbackTree) {
        // 1. 尝试获取离线包
        OfflinePackage pkg = getOfflinePackage(roles);

        if (pkg != null) {
            try {
                // ⭐ 修正处：根据报错信息，去掉 pk，并将 plaintext 放在第一位
                // 需要: PlainText, OfflinePackage
                return engine.onlineEncrypt(plaintext, pkg);
            } catch (Exception e) {
                System.err.println("❌ 在线加密失败，降级至标准加密: " + e.getMessage());
            }
        }

        // 2. 备选方案：池空或报错，执行标准全量加密 (这个方法的参数通常是 pk, plaintext, tree)
        return engine.encrypt(pk, plaintext, fallbackTree);
    }
}