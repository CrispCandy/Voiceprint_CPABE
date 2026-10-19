package org.tanshin.voiceprint.cp.abe.mfcc;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.mfcc.MFCC;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MultiFormatVoiceprintExtractor {

    /**
     * 【核心修改】同步提取 MFCC。
     * 使用 dispatcher.run() 阻塞主线程，直到文件处理完并返回 List。
     */
    public static List<float[]> extractMFCCSync(File wavFile) throws Exception {
        int sampleRate = 16000;
        int bufferSize = 1024;
        int bufferOverlap = 512;
        final List<float[]> result = new ArrayList<>();

        AudioDispatcher dispatcher = AudioDispatcherFactory.fromFile(wavFile, bufferSize, bufferOverlap);
        final MFCC mfccProcessor = new MFCC(bufferSize, sampleRate, 13, 40, 133.33f, 8000f);

        dispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                mfccProcessor.process(audioEvent);
                // 必须 clone，否则 list 里存的都是最后一块内存的引用
                result.add(mfccProcessor.getMFCC().clone());
                return true;
            }

            @Override
            public void processingFinished() {
                // 处理完成的回调
            }
        });

        // 注意：这里使用 run() 而不是开启新线程，保证方法能同步返回数据
        dispatcher.run();
        return result;
    }

    /**
     * 将任意音视频格式转为标准采样率的 WAV
     */
    public static File convertToStandardWav(File source) throws Exception {
        File target = File.createTempFile("voiceprint_temp_", ".wav");
        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("pcm_s16le");
        audio.setBitRate(128000);
        audio.setChannels(1);
        audio.setSamplingRate(16000);

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("wav");
        attrs.setAudioAttributes(audio);

        new Encoder().encode(new MultimediaObject(source), target, attrs);
        return target;
    }

    /**
     * 调用 Python 脚本进行语音转文字
     */
    public static String speechToText(File wavFile) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "C:\\Users\\Lenovo\\AppData\\Local\\Programs\\Python\\Python311\\python.exe",
                "-X", "utf8",
                "src/main/java/org/tanshin/voiceprint/cp/abe/script/speech_to_text.py",
                wavFile.getAbsolutePath()
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }
        process.waitFor();
        return result.toString().trim();
    }
}