package org.tanshin.voiceprint.cp.abe.mfcc;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class SyncExtractionTest {

    public static void main(String[] args) {
        // 1. 设置 Windows 原生外观
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // 2. 弹出 Windows 文件选择器
        File selectedFile = openWindowsFileDialog();

        if (selectedFile == null) {
            System.out.println("未选择任何文件，测试退出。");
            return;
        }

        System.out.println("========================================");
        System.out.println("测试开始：正在处理文件 -> " + selectedFile.getAbsolutePath());
        System.out.println("请注意观察：程序会在此处‘卡住’一段时间，直到处理完全部流程...");
        System.out.println("========================================");

        try {
            long startTime = System.currentTimeMillis();

            // 3. 调用你的 MedicalRecord 全流程处理方法
            // 该方法内部依次执行了：转码 -> Python ASR -> MFCC提取
            MedicalRecord record = MedicalRecord.fullProcess(selectedFile);

            long endTime = System.currentTimeMillis();

            // 4. 验证结果
            System.out.println("\n===== 提取结果验证 =====");
            System.out.println("【耗时统计】: " + (endTime - startTime) / 1000.0 + " 秒");

            // 检查文本是否拿到了
            if (record.getMsg() != null && !record.getMsg().isEmpty()) {
                System.out.println("【ASR 文本】: " + record.getMsg());
            } else {
                System.err.println("【错误】: 未能提取到文本，请检查 Python 脚本输出。");
            }

            // 检查 MFCC 矩阵是否拿到了
//            float[][] matrix = record.getMfccMatrix();
//            if (matrix != null && matrix.length > 0) {
//                System.out.println("【MFCC 矩阵】: 成功提取 " + matrix.length + " 帧特征数据");
//                System.out.println("【特征维度】: 每帧包含 " + matrix[0].length + " 维特征向量");
//
//                // 打印前两帧看看数据
//                System.out.print("【数据样例】: [Frame 0]: ");
//                for (int i = 0; i < Math.min(5, matrix[0].length); i++) System.out.printf("%.2f ", matrix[0][i]);
//                System.out.println("...");
//            } else {
//                System.err.println("【错误】: MFCC 矩阵为空，请检查 TarsosDSP 提取逻辑。");
//            }

            // 检查 MFCC 矩阵是否拿到了
            float[][] matrix = record.getMfccMatrix();
            if (matrix != null && matrix.length > 0) {
                System.out.println("\n========== [完整 MFCC 矩阵数据开始] ==========");
                System.out.println("总帧数: " + matrix.length + " | 每帧维度: " + matrix[0].length);
                System.out.println("----------------------------------------------");

                // 外层循环：遍历每一帧 (每一行)
                for (int i = 0; i < 6; i++) {
                    System.out.printf("帧 [%04d]: ", i); // 打印帧编号，补零对齐

                    // 内层循环：遍历该帧的所有特征维度 (每一列)
                    for (int j = 0; j < matrix[i].length; j++) {
                        // 打印浮点数，保留 4 位小数，并用空格隔开
                        System.out.printf("%8.4f ", matrix[i][j]);
                    }
                    System.out.println(); // 换行，开始打印下一帧

//                    // 可选：如果数据实在太多，每打印 500 帧暂停一下，防止控制台卡死
//                    if (i > 0 && i % 500 == 0) {
//                        System.out.println("--- 已打印 500 帧，继续输出中... ---");
//                    }
                }

                System.out.println("----------------------------------------------");
                System.out.println("========== [完整 MFCC 矩阵数据结束] ==========\n");

            } else {
                System.err.println("【错误】: MFCC 矩阵为空，未提取到特征数据。");
            }
            JOptionPane.showMessageDialog(null, "数据同步提取成功！\n识别文本：" + record.getMsg() + "\n提取帧数：" + matrix.length);

        } catch (Exception e) {
            System.err.println("测试过程中发生异常：");
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "处理失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 调用 Windows 原生文件选择器
     */
    private static File openWindowsFileDialog() {
        Frame parent = new Frame();
        FileDialog fileDialog = new FileDialog(parent, "请选择一个音视频文件（测试同步提取）", FileDialog.LOAD);
        fileDialog.setFile("*.mp4;*.wav;*.mp3;*.m4a");
        fileDialog.setVisible(true);

        String directory = fileDialog.getDirectory();
        String filename = fileDialog.getFile();
        parent.dispose();

        if (filename == null) return null;
        return new File(directory, filename);
    }
}
