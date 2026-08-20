package io.github.devinx3.kt.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ktctl 命令规格枚举。
 * 集中声明每个命令的参数前缀、终止方式、成功关键字与输出模式。
 */
public enum KtCommand {

    CONNECT("connect", false, TerminateMode.CTRL_C, "all looks good"),
    CLEAN("clean", false, TerminateMode.DESTROY, null),
    CONFIG("config", true, TerminateMode.DESTROY, null),
    MESH("mesh", false, TerminateMode.CTRL_C, "Now you can access your service by header"),
    RECOVER("recover", false, TerminateMode.DESTROY, null);

    public enum TerminateMode {
        CTRL_C, DESTROY
    }

    private final String subCommand;
    private final boolean collect;
    private final TerminateMode terminate;
    private final String successKeyword;

    KtCommand(String subCommand, boolean collect, TerminateMode terminate, String successKeyword) {
        this.subCommand = subCommand;
        this.collect = collect;
        this.terminate = terminate;
        this.successKeyword = successKeyword;
    }

    /**
     * 构造完整命令行（含 ktctl 前缀与子命令）。
     * 额外参数由调用方从外部传入，本方法不做命令特定拼装。
     *
     * @param args 子命令后的参数（如 "show"、服务名、"--expose"、端口等）
     */
    public List<String> command(String... args) {
        List<String> result = new ArrayList<>();
        result.add("ktctl");
        result.add(this.subCommand);
        result.addAll(Arrays.asList(args));
        return result;
    }

    public boolean isCollect() {
        return collect;
    }

    public TerminateMode getTerminate() {
        return terminate;
    }

    public String getSuccessKeyword() {
        return successKeyword;
    }
}
