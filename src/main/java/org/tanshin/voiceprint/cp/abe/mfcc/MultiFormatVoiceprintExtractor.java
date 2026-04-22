package org.tanshin.voiceprint.cp.abe.mfcc;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.mfcc.MFCC;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;


public class UniversalVoiceprintExtractor {

    public static void main(String[] args) {
        // 设置 UI 风格为系统原生，看起来更美观
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}

        // 1. 打开文件选择器
        File selectedFile = showFileChooser();

        if (selectedFile != null) {
            System.out.println("正在处理文件: " + selectedFile.getAbsolutePath());
            processAudio(selectedFile);
        } else {
            System.out.println("操作已取消。");
        }
    }

    private static File showFileChooser() {
        JFileChooser chooser = new JFileChooser();
        // 同时支持 wav 和 mp3
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "音频文件 (*.wav, *.mp3)", "wav", "mp3");
        chooser.setFileFilter(filter);
        chooser.setDialogTitle("选择声纹采样音频");

        int returnVal = chooser.showOpenDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    private static void processAudio(File file) {
        // 参数设置：
        // 采样率 16000Hz 是声纹识别的标准，能过滤高频噪声
        float sampleRate = 16000;
        int bufferSize = 1024;
        int bufferOverlap = 512;

        // 存储特征矩阵：每一行是一个 13 维的 MFCC 向量
        List<float[]> mfccMatrix = new ArrayList<>();

        try {
            // AudioDispatcherFactory.fromFile 会根据文件后缀自动调用对应的解码器 (SPI)
            AudioDispatcher dispatcher = AudioDispatcherFactory.fromFile(file, bufferSize, bufferOverlap);

            // MFCC 构造参数：bufferSize, sampleRate, 系数个数, 滤波器组数, 最低频率, 最高频率
            final MFCC mfcc = new MFCC(bufferSize, sampleRate, 13, 40, 133.33f, 8000f);

            dispatcher.addAudioProcessor(new AudioProcessor() {
                @Override
                public boolean process(AudioEvent audioEvent) {
                    mfcc.process(audioEvent);
                    float[] coefficients = mfcc.getMFCC();
                    // 深度拷贝数据并存入矩阵
                    mfccMatrix.add(coefficients.clone());
                    return true;
                }

                @Override
                public void processingFinished() {
                    System.out.println("--- 提取成功 ---");
                    System.out.println("总计提取帧数: " + mfccMatrix.size());
                    System.out.println("每帧特征维度: " + (mfccMatrix.isEmpty() ? 0 : mfccMatrix.get(0).length));

                    // 打印前 5 帧作为示例
                    System.out.println("前5帧矩阵数据预览：");
                    for (int i = 0; i < Math.min(5, mfccMatrix.size()); i++) {
                        System.out.print("Frame " + i + ": ");
                        for (float val : mfccMatrix.get(i)) System.out.printf("%8.2f ", val);
                        System.out.println();
                    }
                }
            });

            // 启动处理
            dispatcher.run();

        } catch (Exception e) {
            System.err.println("错误：无法解析该音频文件。请检查依赖是否包含 mp3spi。");
            e.printStackTrace();
        }
    }
}
