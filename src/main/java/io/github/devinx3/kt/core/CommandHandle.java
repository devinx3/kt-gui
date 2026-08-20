package io.github.devinx3.kt.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个命令会话句柄。
 * 持有进程引用、输出缓冲与终止标记。
 */
class CommandHandle {

    final KtCommand command;
    final String[] options;
    final Process process;
    final List<String> collected;       // 收集型命令的输出缓存
    final List<String> errLines;        // stderr 缓存
    final Thread[] readerThreads;       // stdout/stderr 读取线程
    volatile boolean stopped;           // 是否已被用户终止

    CommandHandle(KtCommand command, String[] options, Process process) {
        this.command = command;
        this.options = options;
        this.process = process;
        this.collected = Collections.synchronizedList(new ArrayList<>());
        this.errLines = Collections.synchronizedList(new ArrayList<>());
        this.readerThreads = new Thread[2];
        this.stopped = false;
    }

    /** 标记为已停止（抑制迟到的成功事件） */
    void markStopped() {
        stopped = true;
    }

    /** 按 KtCommand 的终止方式终止进程 */
    void terminate() {
        markStopped();
        if (command.getTerminate() == KtCommand.TerminateMode.CTRL_C) {
            if (!CtrlC.send(process)) {
                process.destroy();
            }
        } else {
            process.destroy();
        }
    }

    /** 等待进程退出，返回退出码 */
    int waitExit() throws InterruptedException {
        return process.waitFor();
    }

    /** 是否已停止 */
    boolean isStopped() {
        return stopped;
    }
}
