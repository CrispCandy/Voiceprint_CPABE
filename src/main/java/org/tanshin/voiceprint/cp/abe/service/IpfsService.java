package org.tanshin.voiceprint.cp.abe.service;

import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class IpfsService {

    // 连接本地 IPFS 节点 (默认端口 5001)
    private final IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");

    /**
     * 【新增】直接上传 JSON 字符串
     */
    public String uploadJson(String json) throws IOException {
        return upload(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 【新增】下载并返回 JSON 字符串
     */
    public String downloadJson(String cid) throws IOException {
        byte[] data = download(cid);
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * 上传原始字节数据到 IPFS
     * @return 文件的 CID (Content Identifier)
     */
    public String upload(byte[] data) throws IOException {
        NamedStreamable.ByteArrayWrapper file = new NamedStreamable.ByteArrayWrapper(data);
        // 执行上传，IPFS 会对数据进行分块并生成 Merkle Tree
        MerkleNode addResult = ipfs.add(file).get(0);
        String cid = addResult.hash.toString();

        // 建议：上传后默认执行一次 Pin，防止被垃圾回收
        pin(cid);
        return cid;
    }

    /**
     * 根据 CID 从 IPFS 下载数据
     * 对应 DecryptionService 中的调用
     */
    public byte[] download(String cid) throws IOException {
        Multihash filePointer = Multihash.fromBase58(cid);
        // 使用 cat 方法获取文件流的字节内容
        return ipfs.cat(filePointer);
    }

    /**
     * 固定（Pin）数据
     * 作用：明确告诉本地节点“这个数据很重要”，不要在执行垃圾回收（GC）时删掉它
     */
    public void pin(String cid) throws IOException {
        Multihash filePointer = Multihash.fromBase58(cid);
        ipfs.pin.add(filePointer);
        System.out.println(">>> [IPFS] 已固定资源: " + cid);
    }

    /**
     * 取消固定（Unpin）
     * 作用：允许该数据被垃圾回收。注意，这不会立即从网络删除，只是你的节点不再强制保留。
     */
    public void unpin(String cid) throws IOException {
        Multihash filePointer = Multihash.fromBase58(cid);
        ipfs.pin.rm(filePointer);
        System.out.println(">>> [IPFS] 已取消固定资源: " + cid);
    }

    /**
     * 检查节点连接状态（健康检查）
     */
    public boolean isOnline() {
        try {
            return ipfs.refs.local() != null;
        } catch (IOException e) {
            return false;
        }
    }
}