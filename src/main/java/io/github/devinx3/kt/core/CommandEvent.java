package io.github.devinx3.kt.core;

import java.util.List;

/**
 * 命令生命周期事件（sealed interface + record，Java 17）。
 * <p>
 * 每个事件携带发布它的 {@link CommandRunner} 实例（source），订阅者据此区分来源，无需字符串 key。
 */
public sealed interface CommandEvent {

    /** 发布该事件的执行器（单会话，即命令归属） */
    CommandRunner source();

    /** 命令规格 */
    KtCommand command();

    /** 命令已启动 */
    record Started(CommandRunner source, KtCommand command) implements CommandEvent {
    }

    /** 输出行（预留：未来控制台行也走事件） */
    record Output(CommandRunner source, KtCommand command, String line, boolean error) implements CommandEvent {
    }

    /** 成功关键字命中 */
    record Success(CommandRunner source, KtCommand command, String matchedLine) implements CommandEvent {
    }

    /** 收集型命令结果 */
    record Collected(CommandRunner source, KtCommand command, List<String> lines) implements CommandEvent {
    }

    /** 命令完成（正常/异常退出统一） */
    record Completed(CommandRunner source, KtCommand command, int exitCode,
                      boolean stoppedByUser) implements CommandEvent {
    }

    /** 命令启动失败 */
    record Failed(CommandRunner source, KtCommand command, String message) implements CommandEvent {
    }
}
