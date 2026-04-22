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
import org.tanshin.voiceprint.cp.abe.util.AESUtil;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 10, time = 1) // 为了防止纯CP-ABE跑太久，这里设为10轮
@Fork(1)
public class SchemeComparisonBenchmark {

    @Param({"5", "10", "20"})
    private int attributeCount;

    private CpAneEngine engine;
    private PublicKey pk;
    private MasterPrivateKey msk;
    private AccessTree accessTree;

    // 模拟 10KB 的声纹特征矩阵
    private byte[] rawData;
    // JPBC 椭圆曲线群 G1 的可用字节长度（通常为 128 字节）
    private int g1Length;

    @Setup(Level.Trial)
    public void setup() {
        engine = new CpAneEngine();
        SystemKey systemKey = SystemKey.build();
        pk = systemKey.getPublicKey();
        msk = systemKey.getMasterPrivateKey();
        g1Length = pk.getPairingParameter().getG1().getLengthInBytes();

        // 1. 初始化 10KB 的测试数据 (模拟 float[][] 转化的 byte[])
        rawData = new byte[10 * 1024];

        // 2. 动态生成测试用访问树
        accessTree = buildDynamicAndTree(pk, attributeCount);
    }

    /**
     * 选手A：纯 AES 方案 (无细粒度权限，仅对数据进行一次对称加密)
     */
    @Benchmark
    public void test1_PureAES() throws Exception {
        SecretKey key = AESUtil.generateAESKey();
        AESUtil.encryptBytes(rawData, key);
    }

    /**
     * 选手B：混合加密方案 (本文方案：AES 加密数据 + ABE 加密 AES 密钥)
     */
    @Benchmark
    public void test2_HybridScheme() throws Exception {
        // 1. AES 加密实体数据
        SecretKey key = AESUtil.generateAESKey();
        AESUtil.encryptBytes(rawData, key);

        // 2. 将 16 字节 AES 密钥映射到 G1 群元素上并用 ABE 加密
        byte[] paddedKey = new byte[g1Length];
        System.arraycopy(key.getEncoded(), 0, paddedKey, g1Length - 16, 16);
        Element mKey = pk.getPairingParameter().getG1().newElementFromBytes(paddedKey).getImmutable();
        engine.encrypt(pk, new PlainText(mKey), accessTree);
    }

    /**
     * 选手C：纯 CP-ABE 方案 (为了加密 10KB，必须将数据切分成多个 G1 块循环加密)
     * 注意：这个方法会极其耗时，正是为了证明纯 ABE 的不可行性
     */
    @Benchmark
    public void test3_PureCPABE() {
        // 计算需要分多少块 (10KB / 128B 约等于 80 块)
        int chunks = (int) Math.ceil((double) rawData.length / g1Length);

        for (int i = 0; i < chunks; i++) {
            byte[] block = new byte[g1Length];
            int length = Math.min(g1Length, rawData.length - i * g1Length);
            System.arraycopy(rawData, i * g1Length, block, 0, length);

            Element mBlock = pk.getPairingParameter().getG1().newElementFromBytes(block).getImmutable();
            engine.encrypt(pk, new PlainText(mBlock), accessTree);
        }
    }

    private AccessTree buildDynamicAndTree(PublicKey publicKey, int attrNum) {
        AccessTreeBuildModel[] models = new AccessTreeBuildModel[attrNum + 1];
        models[0] = AccessTreeBuildModel.innerAccessTreeBuildModel(1, 1, attrNum, -1);
        for (int i = 1; i <= attrNum; i++) {
            models[i] = AccessTreeBuildModel.leafAccessTreeBuildModel(i + 1, 1, "Attr_" + i, 1);
        }
        return AccessTree.build(publicKey, models);
    }

    // ================= 空间复杂度测算逻辑 =================
    public static void calculateSpaceOverhead() {
        System.out.println("==============================================");
        System.out.println("========= 空间存储开销理论测算 (10KB 数据) =========");
        System.out.println("==============================================");
        int dataSize = 10 * 1024; // 10240 Bytes
        int g1Size = 128; // JPBC 中 G1 元素一般占 128 字节
        int gtSize = 128; // GT 元素通常也是 128 字节

        int[] attrCounts = {5, 10, 20};

        for (int attrs : attrCounts) {
            System.out.println("【当前属性个数】: " + attrs);

            // 1. 纯 AES：密文大小约等于明文大小
            int aesSize = dataSize + 16; // 加上 16 字节 IV 偏移
            System.out.println("纯 AES 方案占用空间: " + aesSize + " 字节");

            // 2. CP-ABE Header 大小：C(GT) + C'(G1) + N * (Cy(G1) + CyPie(G1))
            int abeHeaderSize = gtSize + g1Size + attrs * (g1Size + g1Size);

            // 3. 混合加密：AES 数据体 + 一个 ABE Header
            int hybridSize = aesSize + abeHeaderSize;
            System.out.println("本文混合方案占用空间: " + hybridSize + " 字节 (膨胀倍数: " + String.format("%.2f", (double)hybridSize/dataSize) + "x)");

            // 4. 纯 CP-ABE：数据被切分成 80 块，每块产生一个独立的 ABE 密文结构
            int chunks = (int) Math.ceil((double) dataSize / g1Size);
            int pureAbeSize = chunks * abeHeaderSize;
            System.out.println("纯 CP-ABE 方案占用空间: " + pureAbeSize + " 字节 (膨胀倍数: " + String.format("%.2f", (double)pureAbeSize/dataSize) + "x)");
            System.out.println("----------------------------------------------");
        }
    }

    public static void main(String[] args) throws Exception {
        // 1. 先打印空间复杂度测算结果
        calculateSpaceOverhead();

        // 2. 启动 JMH 时间性能压测
        System.out.println("\n>>> 开始执行 JMH 时间性能压测，请耐心等待纯 CP-ABE 的执行...");
        Options opt = new OptionsBuilder()
                .include(SchemeComparisonBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
