package org.tanshin.voiceprint.cp.abe.service;

import org.springframework.stereotype.Component;
import org.tanshin.voiceprint.cp.abe.parameter.MasterPrivateKey;
import org.tanshin.voiceprint.cp.abe.parameter.PublicKey;
import org.tanshin.voiceprint.cp.abe.parameter.SystemKey;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

@Component
public class SystemKeyManager {

    private SystemKey systemKey;
    private static final String KEY_PATH = "data/abe_master_keys.ser";

    @PostConstruct
    public void init() {
        System.out.println("\n===========================================");
        System.out.println("🛡️ ABE 密钥管理中心初始化中...");
        try {
            File file = new File(KEY_PATH);
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();

            if (file.exists()) {
                loadKeyFromFile();
            } else {
                generateAndSaveNewKey();
            }
            printKeyFingerprint();
        } catch (Exception e) {
            System.err.println("❌ 密钥系统初始化崩溃: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("===========================================\n");
    }

    private void generateAndSaveNewKey() throws IOException {
        System.out.println(">>> [密钥生成] 正在生成全新主密钥...");
        this.systemKey = SystemKey.build();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(KEY_PATH))) {
            oos.writeObject(this.systemKey);
        }
    }

    private void loadKeyFromFile() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(KEY_PATH))) {
            this.systemKey = (SystemKey) ois.readObject();
            System.out.println(">>> [密钥加载] 成功从本地文件恢复已有主密钥。");
        } catch (Exception e) {
            System.err.println(">>> [警告] 密钥加载失败，强制重新生成...");
            try {
                Files.move(Paths.get(KEY_PATH), Paths.get(KEY_PATH + ".bak_" + System.currentTimeMillis()));
                generateAndSaveNewKey();
            } catch (IOException ex) { ex.printStackTrace(); }
        }
    }

    private void printKeyFingerprint() {
        if (systemKey != null) {
            byte[] hBytes = systemKey.getPublicKey().getH().toBytes();
            String fingerprint = Base64.getEncoder().encodeToString(hBytes);
            System.out.println(">>> [当前系统密钥指纹]: " + fingerprint.substring(0, 12));
        }
    }

    public SystemKey getSystemKey() { return systemKey; }
    public PublicKey getPublicKey() { return systemKey.getPublicKey(); }
    public MasterPrivateKey getMasterPrivateKey() { return systemKey.getMasterPrivateKey(); }
}