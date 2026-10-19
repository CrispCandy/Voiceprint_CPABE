package org.tanshin.voiceprint.cp.abe.config;

import org.springframework.stereotype.Component;
import org.tanshin.voiceprint.cp.abe.engine.CpAneEngine;
import org.tanshin.voiceprint.cp.abe.parameter.*;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

@Component
public class SystemGuardian {
    private static final String KEY_PATH = "system_keys.dat";
    private SystemKey systemKey;
    private CpAneEngine engine;

    @PostConstruct
    public void init() throws Exception {
        this.engine = new CpAneEngine();
        File keyFile = new File(KEY_PATH);
        if (keyFile.exists()) {
            // 从本地磁盘恢复，保证重启后 PK, MSK 不变
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(keyFile))) {
                this.systemKey = (SystemKey) ois.readObject();
                System.out.println("✅ 系统密钥已从本地恢复，安全参数保持一致。");
            }
        } else {
            // 第一次运行，生成并持久化
            this.systemKey = SystemKey.build();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(keyFile))) {
                oos.writeObject(this.systemKey);
                System.out.println("🚀 初始系统密钥生成并已保存至本地。");
            }
        }
    }

    public PublicKey getPk() { return systemKey.getPublicKey(); }
    public MasterPrivateKey getMsk() { return systemKey.getMasterPrivateKey(); }
    public CpAneEngine getEngine() { return engine; }
}