package io.github.devinx3.kt.core;

import io.github.devinx3.kt.ui.ConsoleArea;
import io.github.devinx3.kt.ui.Ui;
import javafx.application.Platform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单会话命令执行器（事件发布者）。
 * 一个执行器只管理一个 ktctl 进程；每个命令/服务持有独立的 CommandRunner 实例，无 key 概念。
 * 完整命令行经 {@link KtCommand#command(String...)} 构造，额外参数由调用方以字符串传入。
 */
public class CommandRunner {

    private volatile CommandHandle handle;          // 当前进程句柄，null = 空闲
    private volatile KtCommand currentCommand;      // 当前执行的命令规格，null = 空闲
    private final AtomicBoolean active = new AtomicBoolean(false); // 同步防重入（进程启动前即置位）
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final KtEventBus bus;

    /** Ctrl+C 退出码（STATUS_CONTROL_C_EXIT） */
    private static final int CTRL_C_EXIT_CODE = -1073741510; // 0xC000013A

    public CommandRunner(KtEventBus bus) {
        this.bus = bus;
    }

    /**
     * 流式执行：本执行器空闲时启动，占用期间重复调用 → 拒绝并提示。
     *
     * @param target    目标控制台
     * @param cmd       命令规格（决定收集模式/终止方式/成功关键字）
     * @param cmdOptions 额外参数（如 "show"、"svc"、"--expose"、"8080"）
     */
    public void runCommand(ConsoleArea target, KtCommand cmd, String... cmdOptions) {
        // 同步防重入（UI 线程内立即生效）
        if (!active.compareAndSet(false, true)) {
            Platform.runLater(() ->
                    target.append(Ui.timestamp() + " 命令正在执行中，请勿重复点击", false));
            return;
        }
        currentCommand = cmd;

        // 经 KtCommand#command 构造完整命令行（含 ktctl 前缀）
        List<String> fullCommand = cmd.command(cmdOptions);

        // 先发布 Started 事件（同步派发，订阅者禁用按钮/刷新主页）
        bus.publish(new CommandEvent.Started(this, cmd));

        // 控制台先追加执行日志
        Platform.runLater(() ->
                target.append(Ui.timestamp() + " > 执行: " + String.join(" ", fullCommand), false));

        // 提交后台任务
        executor.submit(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(fullCommand);
                pb.redirectErrorStream(false);
                Process process = pb.start();

                CommandHandle handle = new CommandHandle(cmd, cmdOptions, process);
                this.handle = handle;

                // 启动双流读取
                Thread[] readers = StreamPump.pump(process,
                        (line, isError) -> {
                            // 写入控制台（UI 线程）
                            Platform.runLater(() ->
                                    target.append(line, isError && ConsoleArea.isErrorLine(line)));
                            // 收集型写入缓冲
                            if (cmd.isCollect()) {
                                handle.collected.add(line);
                            }
                            if (isError) {
                                handle.errLines.add(line);
                            }
                        },
                        cmd.getSuccessKeyword() != null ? (line) -> {
                            // 成功关键字命中
                            if (!handle.isStopped() && line.toLowerCase().contains(cmd.getSuccessKeyword().toLowerCase())) {
                                bus.publish(new CommandEvent.Success(this, cmd, line));
                            }
                        } : null
                );
                handle.readerThreads[0] = readers[0];
                handle.readerThreads[1] = readers[1];

                // 等待进程退出
                int exitCode = handle.waitExit();

                // 等待读取线程结束
                for (Thread t : handle.readerThreads) {
                    if (t != null) {
                        t.join(2000);
                    }
                }

                // 统一完成处理
                handleCompletion(handle, exitCode, target);

            } catch (IOException e) {
                // 启动异常（如 ktctl 不在 PATH）
                Platform.runLater(() ->
                        target.append(Ui.timestamp() + " 执行异常: " + e.getMessage(), true));
                bus.publish(new CommandEvent.Failed(this, cmd, e.getMessage()));
                reset();
                bus.publish(new CommandEvent.Completed(this, cmd, -1, false));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * 收集式执行：结果经 COLLECTED / FAILED 事件送达，无需回调参数。
     *
     * @param cmd       命令规格（决定收集模式/终止方式）
     * @param cmdOptions 额外参数（如 "show"）
     */
    public void runCollecting(KtCommand cmd, String... cmdOptions) {
        // 同步防重入
        if (!active.compareAndSet(false, true)) {
            return;
        }
        currentCommand = cmd;

        // 经 KtCommand#command 构造完整命令行（含 ktctl 前缀）
        List<String> fullCommand = cmd.command(cmdOptions);

        // 先发布 Started 事件
        bus.publish(new CommandEvent.Started(this, cmd));

        executor.submit(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(fullCommand);
                pb.redirectErrorStream(false);
                Process process = pb.start();

                CommandHandle handle = new CommandHandle(cmd, cmdOptions, process);
                this.handle = handle;

                // 启动双流读取（收集型不写控制台，只缓冲）
                Thread[] readers = StreamPump.pump(process,
                        (line, isError) -> {
                            if (!isError) {
                                handle.collected.add(line);
                            } else {
                                handle.errLines.add(line);
                            }
                        },
                        null
                );
                handle.readerThreads[0] = readers[0];
                handle.readerThreads[1] = readers[1];

                int exitCode = handle.waitExit();

                for (Thread t : handle.readerThreads) {
                    if (t != null) {
                        t.join(2000);
                    }
                }

                // 收集型完成处理
                if (handle.isStopped()) {
                    // 用户终止
                    bus.publish(new CommandEvent.Completed(this, cmd, exitCode, true));
                } else if (exitCode == 0) {
                    bus.publish(new CommandEvent.Collected(this, cmd, new ArrayList<>(handle.collected)));
                    bus.publish(new CommandEvent.Completed(this, cmd, exitCode, false));
                } else {
                    String errMsg = handle.errLines.isEmpty() ? "退出码: " + exitCode : String.join("\n", handle.errLines);
                    bus.publish(new CommandEvent.Failed(this, cmd, errMsg));
                    bus.publish(new CommandEvent.Completed(this, cmd, exitCode, false));
                }

                reset();

            } catch (IOException e) {
                bus.publish(new CommandEvent.Failed(this, cmd, e.getMessage()));
                reset();
                bus.publish(new CommandEvent.Completed(this, cmd, -1, false));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * 终止当前命令（按 KtCommand 的 TerminateMode 决定 Ctrl+C 或 destroy）。
     */
    public void terminate() {
        CommandHandle h = handle;
        if (h == null) return;

        h.terminate();

        // 3s 未退出 → destroyForcibly（守护线程内等待，不阻塞 UI）
        Thread forceKillThread = new Thread(() -> {
            try {
                boolean exited = processWaitFor(h.process, 3000);
                if (!exited) {
                    h.process.destroyForcibly();
                }
            } catch (Exception e) {
                // 忽略
            }
        }, "kt-force-kill");
        forceKillThread.setDaemon(true);
        forceKillThread.start();
    }

    /** 当前是否有命令在执行（同步判定，含已提交未启动窗口） */
    public boolean isActive() {
        return active.get();
    }

    /** 当前正在执行的命令规格；空闲时返回 null */
    public KtCommand activeCommand() {
        return currentCommand;
    }

    /** 应用退出：terminate + shutdownNow */
    public void shutdown() {
        CommandHandle h = handle;
        if (h != null) {
            h.terminate();
            try {
                h.process.destroyForcibly();
            } catch (Exception e) {
                // 忽略
            }
        }
        reset();
        executor.shutdownNow();
    }

    // ---- 内部方法 ----

    /** 释放占用状态（进程已结束/启动失败后调用） */
    private void reset() {
        handle = null;
        currentCommand = null;
        active.set(false);
    }

    private void handleCompletion(CommandHandle handle, int exitCode, ConsoleArea target) {
        boolean stoppedByUser = handle.isStopped() || exitCode == CTRL_C_EXIT_CODE;

        if (stoppedByUser) {
            Platform.runLater(() ->
                    target.append(Ui.timestamp() + " 已通过 Ctrl+C 正常终止", false));
        } else {
            boolean isError = exitCode != 0;
            Platform.runLater(() ->
                    target.append(Ui.timestamp() + " 命令完成，退出码: " + exitCode, isError));
        }

        // 释放占用状态
        reset();

        // 发布 Completed 事件
        bus.publish(new CommandEvent.Completed(this, handle.command, exitCode, stoppedByUser));
    }

    /** 等待进程退出，带超时（ms），返回是否已退出 */
    private boolean processWaitFor(Process process, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException e) {
                // 进程尚未退出
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
