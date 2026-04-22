package org.tanshin.voiceprint.cp.abe.benchmark;

import it.unisa.dia.gas.jpbc.Element;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.tanshin.voiceprint.cp.abe.attribute.Attribute;
import org.tanshin.voiceprint.cp.abe.engine.CpAneEngine;
import org.tanshin.voiceprint.cp.abe.parameter.*;
import org.tanshin.voiceprint.cp.abe.structure.AccessTree;
import org.tanshin.voiceprint.cp.abe.structure.AccessTreeBuildModel;
import org.tanshin.voiceprint.cp.abe.text.CipherText;
import org.tanshin.voiceprint.cp.abe.text.OfflinePackage;
import org.tanshin.voiceprint.cp.abe.text.PlainText;
import org.tanshin.voiceprint.cp.abe.text.ProxyCipherText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CP-ABE 性能基准测试 v2.0
 * 涵盖：传统加密、轻量化离线/在线加密、授权解密、代理重加密
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS) // 使用微秒，以便更精确地观察 Online 加密性能
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class OffAbeJmhBenchmark {

    // 参数化：测试不同属性规模下的性能波动
    @Param({"5", "10", "20"})
    private int attributeCount;

    private CpAneEngine engine;
    private PublicKey pk;
    private MasterPrivateKey msk;

    private PlainText dummyPlainText;
    private AccessTree accessTree;
    private UserPrivateKey userPrivateKey;
    private CipherText preEncryptedCipherText;

    // 离线/在线模式预备
    private OfflinePackage preGeneratedOfflinePkg;

    // PRE 代理重加密预备
    private UserPrivateKey tk;
    private Element rk;
    private ProxyCipherText preGeneratedProxyCipher;

    @Setup(Level.Trial)
    public void setup() {
        engine = new CpAneEngine();
        SystemKey systemKey = SystemKey.build();
        pk = systemKey.getPublicKey();
        msk = systemKey.getMasterPrivateKey();

        // 模拟 AES 密钥处理（匹配最新测试类的末尾 16 字节对齐逻辑）
        int g1Length = pk.getPairingParameter().getG1().getLengthInBytes();
        byte[] dummyAes = new byte[16]; // 模拟 128-bit AES Key
        byte[] paddedKey = new byte[g1Length];
        System.arraycopy(dummyAes, 0, paddedKey, g1Length - 16, 16);
        Element mElement = pk.getPairingParameter().getG1().newElementFromBytes(paddedKey).getImmutable();
        dummyPlainText = new PlainText(mElement);

        // 构建全满足的 AND 访问策略（最坏情况测试）
        accessTree = buildDynamicAndTree(pk, attributeCount);

        // 生成用户私钥
        List<Attribute> attributes = new ArrayList<>();
        for (int i = 1; i <= attributeCount; i++) {
            attributes.add(new Attribute("Attr_" + i, pk));
        }
        userPrivateKey = engine.keyGen(msk, attributes);

        // 预生成数据：用于测试解密耗时
        preEncryptedCipherText = engine.encrypt(pk, dummyPlainText, accessTree);

        // 预生成离线包：用于测试 Online 加密耗时
        preGeneratedOfflinePkg = engine.offlineEncrypt(pk, accessTree);

        // 预生成 PRE 参数
        Map<String, Object> proxyKeys = engine.generateProxyKeys(pk, userPrivateKey);
        tk = (UserPrivateKey) proxyKeys.get("TK");
        rk = (Element) proxyKeys.get("RK");
        preGeneratedProxyCipher = engine.proxyReEncrypt(pk, tk, preEncryptedCipherText);
    }

    // --- [1] 加密性能对比 ---

    /**
     * 传统模式：实时执行所有模幂和双线性配对运算
     */
    @Benchmark
    public void traditionalEncrypt() {
        engine.encrypt(pk, dummyPlainText, accessTree);
    }

    /**
     * 轻量化-离线阶段：预计算与消息无关的群元素转换
     */
    @Benchmark
    public void offlineStage() {
        engine.offlineEncrypt(pk, accessTree);
    }

    /**
     * 轻量化-在线阶段：仅执行简单的群元素乘法，不涉及复杂运算
     * (这是 IoT 设备在传感器采集数据后真正感知的延迟)
     */
    @Benchmark
    public void onlineStage() {
        engine.onlineEncrypt(dummyPlainText, preGeneratedOfflinePkg);
    }

    // --- [2] 解密与转换性能 ---

    /**
     * 基础 ABE 解密性能
     */
    @Benchmark
    public void standardDecrypt() {
        engine.decrypt(pk, userPrivateKey, preEncryptedCipherText);
    }

    /**
     * 代理服务器执行重加密转换的性能（通常在云端执行）
     */
    @Benchmark
    public void proxyTransform() {
        engine.proxyReEncrypt(pk, tk, preEncryptedCipherText);
    }

    /**
     * 被授权人利用 RK 还原数据的性能（极轻量）
     */
    @Benchmark
    public void delegateeDecrypt() {
        engine.delegateeDecrypt(preGeneratedProxyCipher, rk);
    }

    // --- [3] 辅助方法 ---

    private AccessTree buildDynamicAndTree(PublicKey publicKey, int attrNum) {
        AccessTreeBuildModel[] models = new AccessTreeBuildModel[attrNum + 1];
        // 根节点：门限等于属性总数 (AND 逻辑)
        models[0] = AccessTreeBuildModel.innerAccessTreeBuildModel(1, 1, attrNum, -1);
        for (int i = 1; i <= attrNum; i++) {
            models[i] = AccessTreeBuildModel.leafAccessTreeBuildModel(i + 1, 1, "Attr_" + i, 1);
        }
        return AccessTree.build(publicKey, models);
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(AbeJmhBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
