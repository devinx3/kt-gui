package io.github.devinx3.kt.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 子进程双流读取工具。
 * 启动两个守护线程分别读取 stdout 和 stderr，逐行回调，避免管道写满死锁。
 */
public final class StreamPump {

    private StreamPump() {
    }

    /** Windows → GBK，其他平台 → UTF-8 */
    public static String charsetName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? "GBK" : "UTF-8";
    }

    public static Charset charset() {
        return Charset.forName(charsetName());
    }

    /**
     * 启动 stdout/stderr 两个读取线程，逐行回调。
     *
     * @param process          子进程
     * @param onLine           行回调：(line, isError) — isError=true 表示来自 stderr
     * @param onSuccessKeyword 成功关键字命中回调（stdout 与 stderr 均检查；ktctl 成功日志走 stderr），可为 null
     * @return 两个线程引用 [stdoutThread, stderrThread]
     */
    public static Thread[] pump(Process process,
                                BiConsumer<String, Boolean> onLine,
                                Consumer<String> onSuccessKeyword) {
        Charset cs = charset();

        Thread stdoutThread = new Thread(() -> readStream(
                process.getInputStream(), cs, false, onLine, onSuccessKeyword),
                "kt-stdout-reader");
        stdoutThread.setDaemon(true);

        Thread stderrThread = new Thread(() -> readStream(
                process.getErrorStream(), cs, true, onLine, onSuccessKeyword),
                "kt-stderr-reader");
        stderrThread.setDaemon(true);

        stdoutThread.start();
        stderrThread.start();

        return new Thread[]{stdoutThread, stderrThread};
    }

    private static void readStream(InputStream stream,
                                   Charset charset,
                                   boolean isError,
                                   BiConsumer<String, Boolean> onLine,
                                   Consumer<String> onSuccessKeyword) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    onLine.accept(line, isError);
                } catch (Exception e) {
                    // 回调异常不中断读取
                    System.err.println("StreamPump onLine callback error: " + e.getMessage());
                }
                // stdout/stderr 都检查成功关键字（ktctl 的 logrus 成功日志通常走 stderr）
                if (onSuccessKeyword != null) {
                    try {
                        onSuccessKeyword.accept(line);
                    } catch (Exception e) {
                        System.err.println("StreamPump onSuccessKeyword callback error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            // 进程被终止导致流关闭，静默忽略
        }
    }
}
