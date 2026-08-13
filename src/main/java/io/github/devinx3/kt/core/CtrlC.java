package io.github.devinx3.kt.core;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.win32.StdCallLibrary;

/**
 * Windows 平台发送真正 Ctrl+C 信号（GenerateConsoleCtrlEvent）的工具类。
 * 非 Windows 平台或发送失败时返回 false，由调用方回退到 Process.destroy()。
 */
public final class CtrlC {

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    // GenerateConsoleCtrlEvent 的事件类型：CTRL_C_EVENT = 0
    private static final int CTRL_C_EVENT = 0;

    // JNA 5.14.0 已从 Wincon/Kernel32 中移除 SetConsoleCtrlHandler，这里自行声明。
    // handler 传 null 表示忽略（TRUE）或恢复（FALSE）本进程对 Ctrl+C 的默认处理。
    private interface Kernel32Ex extends StdCallLibrary {
        Kernel32Ex INSTANCE = Native.load("kernel32", Kernel32Ex.class);

        boolean SetConsoleCtrlHandler(Callback handler, boolean add);
    }

    private CtrlC() {
    }

    /**
     * 向目标进程发送 Ctrl+C 信号。
     * 经典实现（MSDN/社区共识）：FreeConsole → AttachConsole → 屏蔽自身 Ctrl+C →
     * GenerateConsoleCtrlEvent(CTRL_C, 0) → 保持附加直到目标进程退出 → FreeConsole → 恢复。
     * 注意：CTRL_C 只能以 group 0 广播给共享调用进程控制台的所有进程；GenerateConsoleCtrlEvent
     * 的投递是异步的，必须在目标退出前保持附加，过早 FreeConsole 会导致事件丢失。
     *
     * @param process 目标进程
     * @return true 表示已成功发送 Ctrl+C；false 表示非 Windows 或发送失败（调用方应回退 destroy）
     */
    public static boolean send(Process process) {
        if (!IS_WINDOWS || process == null) {
            return false;
        }
        int pid = (int) process.pid();
        Kernel32 k32 = Kernel32.INSTANCE;
        // 先释放自身控制台：若调用方已有控制台，AttachConsole 会返回 ERROR_ACCESS_DENIED(5) 而失败
        // （开发时从 IDE/终端启动即属于此情况）
        k32.FreeConsole();
        if (!k32.AttachConsole(pid)) {
            return false;
        }
        // 屏蔽自身对 Ctrl+C 的响应，避免广播信号波及本程序（须在 Generate 之前生效）
        Kernel32Ex.INSTANCE.SetConsoleCtrlHandler(null, true);
        // 0 = 发送给与调用者共享控制台（即刚附加的目标进程控制台）的所有进程
        boolean sent = k32.GenerateConsoleCtrlEvent(CTRL_C_EVENT, 0);
        // 保持附加直到目标进程退出：事件投递是异步的，过早 FreeConsole 会丢失事件；
        // 轮询等待目标退出，上限 2s，避免长时间阻塞调用方（UI 线程）
        try {
            long deadline = System.currentTimeMillis() + 2000;
            while (process.isAlive() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        k32.FreeConsole();
        // 恢复自身 Ctrl+C 处理（该忽略属性会被子进程继承，必须恢复，否则后续 ktctl 收不到 Ctrl+C）
        Kernel32Ex.INSTANCE.SetConsoleCtrlHandler(null, false);
        return sent;
    }
}
