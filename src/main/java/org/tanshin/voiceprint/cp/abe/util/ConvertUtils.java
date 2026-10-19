package org.tanshin.voiceprint.cp.abe.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;

/**
 * @BelongsProject: CP-ABE
 * @BelongsPackage: com.duwei.cp.abe.util
 * @Author: duwei
 * @Date: 2022/7/26 10:54
 * @Description: byte和str转换
 */
public class ConvertUtils {
    /**
     * 比特数组去掉前后的0转字符串
     * @param bytes
     * @return
     */
    public static String byteToStr(byte[] bytes){
        int startIndex = 0;
        int endIndex = bytes.length;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != 0){
                startIndex = i;
                break;
            }
        }

        for (int i = bytes.length - 1; i >= 0; i--) {
            if (bytes[i] != 0){
                endIndex = i;
                break;
            }
        }
        return new String(bytes,startIndex,endIndex - startIndex + 1, Charset.defaultCharset());
    }

    //矩阵序列化
    public static byte[] matrixToBytes(float[][] matrix) throws Exception {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(matrix);
            return bos.toByteArray();
        }
    }

    public static float[][] bytesToMatrix(byte[] data) throws Exception {
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
        return (float[][]) ois.readObject();
    }

}
