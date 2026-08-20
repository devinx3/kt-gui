package io.github.devinx3.kt.core;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef;

/**
 * Windows 平台层：向子进程发送真实的 Ctrl+C 信号。
 * <p>
 * 流程：
 * 1. 判定 os.name 含 "win"，否则直接返回 false。
 * 2. FreeConsole() 释放自身控制台。
 * 3. AttachConsole(pid) 附加到目标进程控制台；失败返回 false。
 * 4. SetConsoleCtrlHandler(null, true) 屏蔽自身 Ctrl+C 响应。
 * 5. GenerateConsoleCtrlEvent(CTRL_C_EVENT=0, 0) 广播。
 * 6. 保持附加直到目标退出（上限 2s，20ms 轮询）。
 * 7. FreeConsole()，再 SetConsoleCtrlHandler(null, false) 恢复。
 * 8. 异常路径恢复中断位。
 */
public final class CtrlC {

    private static final int CTRL_C_EVENT = 0;

    private CtrlC() {
    }

    /**
     * 向指定进程发送 Ctrl+C 信号。
     *
     * @param process 目标进程
     * @return true=已发送；false=非Windows/失败（调用方回退 destroy）
     */
    public static boolean send(Process process) {
        if (!Platform.isWindows()) {
            return false;
        }

        try {
            long pid = process.pid();

            // 1. FreeConsole - 释放自身控制台
            Kernel32.INSTANCE.FreeConsole();

            // 2. AttachConsole - 附加到目标进程控制台
            Function attachConsole = Function.getFunction("kernel32", "AttachConsole");
            int attachResult = attachConsole.invokeInt(new Object[]{new WinDef.DWORD(pid)});
            if (attachResult == 0) {
                // 附加失败，尝试恢复
                Kernel32.INSTANCE.FreeConsole();
                tryAttachParentConsole();
                return false;
            }

            // 3. 屏蔽自身 Ctrl+C 响应
            Function setHandler = Function.getFunction("kernel32", "SetConsoleCtrlHandler");
            setHandler.invokeInt(new Object[]{null, Boolean.TRUE});

            // 4. 发送 Ctrl+C 事件
            Function generateEvent = Function.getFunction("kernel32", "GenerateConsoleCtrlEvent");
            int result = generateEvent.invokeInt(new Object[]{CTRL_C_EVENT, 0});

            if (result == 0) {
                // 发送失败，恢复并返回
                Kernel32.INSTANCE.FreeConsole();
                tryAttachParentConsole();
                setHandler.invokeInt(new Object[]{null, Boolean.FALSE});
                return false;
            }

            // 5. 保持附加直到目标退出（上限 2s，20ms 轮询）
            long deadline = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    process.exitValue();
                    break; // 进程已退出
                } catch (IllegalThreadStateException e) {
                    // 进程尚未退出
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // 6. 恢复
            Kernel32.INSTANCE.FreeConsole();
            tryAttachParentConsole();
            setHandler.invokeInt(new Object[]{null, Boolean.FALSE});

            return true;

        } catch (Exception e) {
            System.err.println("CtrlC.send error: " + e.getMessage());
            // 尝试恢复
            try {
                Kernel32.INSTANCE.FreeConsole();
                tryAttachParentConsole();
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    /** 尝试重新附加到父控制台（AttachConsole(null) 即附加到父进程控制台） */
    private static void tryAttachParentConsole() {
        try {
            Function attachConsole = Function.getFunction("kernel32", "AttachConsole");
            attachConsole.invokeInt(new Object[]{null});
        } catch (Exception ignored) {
        }
    }
}
