package org.tanshin.voiceprint.cp.abe.mfcc;

import java.io.File;
import java.util.List;

/**
 * 医疗记录类：封装提取后的原始数据
 */
public class MedicalRecord {
    private String patientId;
    private String msg;           // ASR 识别出的文本
    private float[][] mfccMatrix; // 提取出的 MFCC 特征矩阵

    public MedicalRecord() {}

    // Getter 和 Setter
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public float[][] getMfccMatrix() { return mfccMatrix; }
    public void setMfccMatrix(float[][] mfccMatrix) { this.mfccMatrix = mfccMatrix; }

    /**
     * 全流程处理方法：将视频/音频文件转化为 MedicalRecord 对象
     */
    public static MedicalRecord fullProcess(File videoFile) throws Exception {
        System.out.println(">>> 启动全流程处理: " + videoFile.getName());

        // 1. 转码为标准 WAV (调用 Extractor 的静态方法)
        File wavFile = MultiFormatVoiceprintExtractor.convertToStandardWav(videoFile);

        // 2. ASR 提取文本
        String resultMsg = MultiFormatVoiceprintExtractor.speechToText(wavFile);

        // 3. MFCC 提取 (使用同步方法获取数据)
        List<float[]> matrixList = MultiFormatVoiceprintExtractor.extractMFCCSync(wavFile);

        // 4. 组装对象
        MedicalRecord record = new MedicalRecord();
        record.setPatientId("PATIENT_" + System.currentTimeMillis());
        record.setMsg(resultMsg);
        record.setMfccMatrix(convertListToArray(matrixList));

        // 5. 临时文件清理
        if (wavFile.exists()) wavFile.delete();

        System.out.println(">>> 处理完成。文本长度: " + resultMsg.length() + ", MFCC帧数: " + matrixList.size());
        return record;
    }

    private static float[][] convertListToArray(List<float[]> list) {
        if (list == null || list.isEmpty()) return new float[0][0];
        float[][] array = new float[list.size()][list.get(0).length];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}