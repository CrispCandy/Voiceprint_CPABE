package org.tanshin.voiceprint.cp.abe.benchmark;

import it.unisa.dia.gas.jpbc.Element;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.tanshin.voiceprint.cp.abe.attribute.Attribute;
import org.tanshin.voiceprint.cp.abe.engine.CpAneEngine;
import org.tanshin.voiceprint.cp.abe.parameter.MasterPrivateKey;
import org.tanshin.voiceprint.cp.abe.parameter.PublicKey;
import org.tanshin.voiceprint.cp.abe.parameter.SystemKey;
import org.tanshin.voiceprint.cp.abe.parameter.UserPrivateKey;
import org.tanshin.voiceprint.cp.abe.structure.AccessTree;
import org.tanshin.voiceprint.cp.abe.structure.AccessTreeBuildModel;
import org.tanshin.voiceprint.cp.abe.text.CipherText;
import org.tanshin.voiceprint.cp.abe.text.PlainText;
import org.tanshin.voiceprint.cp.abe.text.ProxyCipherText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CP-ABE 与 代理重加密(PRE) 性能基准测试
 */
@BenchmarkMode(Mode.AverageTime) // 测试平均耗时
@OutputTimeUnit(TimeUnit.MILLISECONDS) // 结果单位：毫秒
@State(Scope.Benchmark) // 整个测试共享一个状态（单例）
@Warmup(iterations = 5, time = 1) // 预热 5 轮，让 JVM 做好即时编译
@Measurement(iterations = 50, time = 1) // 正式测试 50 轮
@Fork(1) // 开启 1 个独立进程测试，防止相互干扰
public class AbeJmhBenchmark {

    // 核心：设置变量参数，JMH 会自动分别取 5, 10, 20 进行三轮完整的测试
    @Param({"5", "10", "20"})
    private int attributeCount;

    private CpAneEngine engine;
    private PublicKey pk;
    private MasterPrivateKey msk;

    private PlainText dummyPlainText;
    private AccessTree accessTree;
    private UserPrivateKey userPrivateKey;
    private CipherText preEncryptedCipherText;

    // PRE 相关预备数据
    private UserPrivateKey tk;
    private Element rk;
    private ProxyCipherText preGeneratedProxyCipher;

    /**
     * 在每一轮测试开始前，初始化环境（不计入耗时）
     */
    @Setup(Level.Trial)
    public void setup() {
        engine = new CpAneEngine();
        SystemKey systemKey = SystemKey.build();
        pk = systemKey.getPublicKey();
        msk = systemKey.getMasterPrivateKey();

        // 1. 生成一个模拟的 AES 密钥元素（在 G1 群上）
        byte[] dummyAes = new byte[16];
        int g1Length = pk.getPairingParameter().getG1().getLengthInBytes();
        byte[] paddedKey = new byte[g1Length];
        System.arraycopy(dummyAes, 0, paddedKey, g1Length - 16, 16);
        Element mText = pk.getPairingParameter().getG1().newElementFromBytes(paddedKey).getImmutable();
        dummyPlainText = new PlainText(mText);

        // 2. 动态生成包含 N 个属性的访问树 (AND 门策略，要求满足所有属性，最耗时)
        accessTree = buildDynamicAndTree(pk, attributeCount);

        // 3. 生成包含 N 个属性的用户私钥
        List<Attribute> attributes = new ArrayList<>();
        for (int i = 1; i <= attributeCount; i++) {
            attributes.add(new Attribute("Attr_" + i, pk));
        }
        userPrivateKey = engine.keyGen(msk, attributes);

        // 4. 预先准备好密文，供解密测试使用
        preEncryptedCipherText = engine.encrypt(pk, dummyPlainText, accessTree);

        // 5. 预先准备好 PRE 参数，供后续测试使用
        Map<String, Object> proxyKeys = engine.generateProxyKeys(pk, userPrivateKey);
        tk = (UserPrivateKey) proxyKeys.get("TK");
        rk = (Element) proxyKeys.get("RK");
        preGeneratedProxyCipher = engine.proxyReEncrypt(pk, tk, preEncryptedCipherText);
    }

    /**
     * 测试 1：数据拥有者加密数据的耗时
     */
    @Benchmark
    public void test1_Encrypt() {
        engine.encrypt(pk, dummyPlainText, accessTree);
    }

    /**
     * 测试 2：授权用户直接解密的耗时
     */
    @Benchmark
    public void test2_Decrypt() {
        engine.decrypt(pk, userPrivateKey, preEncryptedCipherText);
    }

    /**
     * 测试 3：代理服务器执行重加密转换的耗时
     */
    @Benchmark
    public void test3_ProxyTransform() {
        engine.proxyReEncrypt(pk, tk, preEncryptedCipherText);
    }

    /**
     * 测试 4：被授权人(Delegatee)最终解密的耗时
     */
    @Benchmark
    public void test4_DelegateeDecrypt() {
        engine.delegateeDecrypt(preGeneratedProxyCipher, rk);
    }

    /**
     * 辅助方法：动态构建一棵要求全满足的 AND 访问树
     */
    private AccessTree buildDynamicAndTree(PublicKey publicKey, int attrNum) {
        AccessTreeBuildModel[] models = new AccessTreeBuildModel[attrNum + 1];
        // 根节点，门限值为 attrNum (即 AND 门，必须全部满足)
        models[0] = AccessTreeBuildModel.innerAccessTreeBuildModel(1, 1, attrNum, -1);

        // 挂载叶子节点
        for (int i = 1; i <= attrNum; i++) {
            // index, threshold=1, attrName, parentIndex=1
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