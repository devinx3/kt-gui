package io.github.devinx3.kt.core;

import io.github.devinx3.kt.ui.ConsoleArea;
import io.github.devinx3.kt.ui.ConsoleLine;
import io.github.devinx3.kt.ui.Ui;
import javafx.application.Platform;
import javafx.scene.control.ListView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * ktctl 命令执行器：负责启动进程、读取输出、收集结果与终止命令。
 * 面板通过 runCommandTo / runCollecting 发起命令，通过回调感知状态变化。
 */
public class CommandRunner {

    // STATUS_CONTROL_C_EXIT = 0xC000013A：进程因收到 Ctrl+C/Ctrl+Break 信号退出（用户主动终止，非异常）
    private static final int STATUS_CONTROL_C_EXIT = -1073741510;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile Process currentProcess;  // 当前正在执行的进程，供"终止"按钮使用
    private volatile String currentCommand;   // 当前执行的命令名（args[0]），用于终止后联动

    private Runnable onStateChanged = () -> {};          // 命令开始/结束时的状态联动（如禁用/恢复 Mesh/Recover）
    private Consumer<String> onCollectError = msg -> {}; // 收集型命令失败时回调（如配置加载失败提示）

    /**
     * 命令执行状态变化回调（开始/结束时触发）
     */
    public void setOnStateChanged(Runnable onStateChanged) {
        this.onStateChanged = onStateChanged;
    }

    /**
     * 收集型命令（runCollecting）执行异常时的回调
     */
    public void setOnCollectError(Consumer<String> onCollectError) {
        this.onCollectError = onCollectError;
    }

    /**
     * 当前是否有命令正在执行
     */
    public boolean isBusy() {
        return currentProcess != null;
    }

    /**
     * 指定的命令是否正在执行（如 "connect"、"clean"）
     */
    public boolean isRunning(String command) {
        return command.equals(currentCommand);
    }

    /**
     * 当前正在执行的命令名（args[0]），无命令时为 null
     */
    public String getCurrentCommand() {
        return currentCommand;
    }

    /**
     * 执行命令，输出到指定控制台
     */
    public void runCommandTo(ListView<ConsoleLine> target, String... args) {
        runInternal(args, target, null, null, null);
    }

    /**
     * 执行命令，输出到指定控制台；输出行出现 successKeyword 时回调 onSuccess（如 connect 成功变绿）
     */
    public void runCommandTo(ListView<ConsoleLine> target, String successKeyword, Consumer<String> onSuccess, String... args) {
        runInternal(args, target, null, successKeyword, onSuccess);
    }

    /**
     * 执行命令，收集全部输出行后回调（如 config show）
     */
    public void runCollecting(String[] args, Consumer<List<String>> onCollected) {
        runInternal(args, null, onCollected, null, null);
    }

    private void runInternal(String[] args, ListView<ConsoleLine> target, Consumer<List<String>> onCollected,
                             String successKeyword, Consumer<String> onSuccess) {
        onStateChanged.run(); // 执行期间禁用 Mesh/Recover
        if (target != null) {
            ConsoleArea.appendLine(target, new ConsoleLine(Ui.timestamp() + " > 执行: ktctl " + String.join(" ", args), false));
        }

        executor.submit(() -> {
            try {
                List<String> command = new ArrayList<>();
                command.add("ktctl");
                command.addAll(List.of(args));

                ProcessBuilder pb = new ProcessBuilder(command);

                Process process = pb.start();
                currentProcess = process; // 记录进程，供"终止"按钮使用
                currentCommand = args.length > 0 ? args[0] : null;

                // 根据操作系统选择字符集，解决中文乱码
                String charsetName = System.getProperty("os.name").toLowerCase().contains("win") ? "GBK" : "UTF-8";
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), Charset.forName(charsetName)));
                     BufferedReader errorReader = new BufferedReader(
                             new InputStreamReader(process.getErrorStream(), Charset.forName(charsetName)))) {

                    List<String> collected = new ArrayList<>();
                    List<String> errLines = new ArrayList<>();

                    // 并发读取 stdout/stderr，避免单流顺序读取时管道写满导致死锁、输出不全
                    Thread outReader = new Thread(() -> readOutput(reader, target, false, collected, successKeyword, onSuccess));
                    Thread errReader = new Thread(() -> readOutput(errorReader, target, true, errLines, successKeyword, onSuccess));
                    outReader.start();
                    errReader.start();

                    int exitCode = process.waitFor();
                    outReader.join();
                    errReader.join();

                    final List<String> result = onCollected != null ? new ArrayList<>(collected) : null;
                    // 权限错误以 stderr 内容为准：ktctl 权限失败时退出码可能为 0
                    final boolean permissionIssue = errLines.stream()
                            .anyMatch(l -> l.toLowerCase().contains("permission"));
                    Platform.runLater(() -> {
                        currentProcess = null;
                        currentCommand = null;
                        if (result != null) {
                            onCollected.accept(result);
                        }
                        if (target != null) {
                            ConsoleArea.appendLine(target, new ConsoleLine(Ui.timestamp() + " 命令完成，退出码: " + exitCode, false));
                            // 0xC000013A = STATUS_CONTROL_C_EXIT：进程因收到 Ctrl+C/Ctrl+Break 信号退出（用户主动终止，非异常）
                            if (exitCode == STATUS_CONTROL_C_EXIT) {
                                ConsoleArea.appendLine(target, new ConsoleLine(Ui.timestamp() + " 已通过 Ctrl+C 正常终止", false));
                            }
                            if (permissionIssue) {
                                // 权限不足：ktctl 需要管理员权限（修改 hosts、创建虚拟网卡等）
                                ConsoleArea.appendLine(target, new ConsoleLine(Ui.timestamp() + " 权限不足：请以管理员身份运行本程序后重试。", true));
                            }
                        }
                        onStateChanged.run();
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    currentProcess = null;
                    currentCommand = null;
                    if (target != null) {
                        ConsoleArea.appendLine(target, new ConsoleLine(Ui.timestamp() + " 执行异常: " + e.getMessage(), true));
                    }
                    // 收集型命令失败时回调（如配置面板提示），避免静默失败
                    if (onCollected != null) {
                        onCollectError.accept(e.getMessage());
                    }
                    onStateChanged.run();
                });
            }
        });
    }

    /**
     * 终止当前正在执行的进程：仅 connect 命令发送真正的 Ctrl+C（Windows），其余命令（clean 等）直接 destroy；
     * 超时未退出则强制终止。面板负责在此前后输出提示信息与联动（如清除连接成功底色）。
     */
    public void terminateCurrent() {
        Process p = currentProcess;
        if (p == null) {
            return;
        }
        // 仅 connect 命令需要优雅断开（发送真正的 Ctrl+C）；clean 等其余命令直接终止即可
        if ("connect".equals(currentCommand)) {
            if (!CtrlC.send(p)) {
                p.destroy();
            }
        } else {
            p.destroy();
        }
        executor.submit(() -> {
            try {
                if (!p.waitFor(3, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                p.destroyForcibly();
            }
        });
    }

    /**
     * 读取一个输出流的所有行：追加到控制台并收集到 lines（后台线程调用）
     */
    public static void readOutput(BufferedReader reader, ListView<ConsoleLine> target,
                           boolean isError, List<String> lines,
                           String successKeyword, Consumer<String> onSuccess) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lines != null) {
                    lines.add(line);
                }
                if (target != null) {
                    final String out = line;
                    // 只有 ERROR 级别日志才淡红背景，INFO 等仍用白底
                    final boolean err = isError && ConsoleArea.isErrorLine(out);
                    Platform.runLater(() -> ConsoleArea.appendLine(target, new ConsoleLine(out, err)));
                }
                // 自定义成功关键字检测（如 connect 成功提示）
                if (successKeyword != null && onSuccess != null
                        && line.toLowerCase().contains(successKeyword.toLowerCase())) {
                    final String match = line;
                    Platform.runLater(() -> onSuccess.accept(match));
                }
            }
        } catch (IOException e) {
            // 进程被终止等导致流关闭时忽略
        }
    }

    /**
     * 关闭执行器（应用退出时调用）
     */
    public void shutdown() {
        executor.shutdownNow();
    }
}
