package io.github.devinx3.kt.ui;

/**
 * 控制台行模型（不可变）。
 */
public class ConsoleLine {

    /** 行内容 */
    public final String text;

    /** 是否错误行（stderr 且匹配错误级别） */
    public final boolean error;

    public ConsoleLine(String text, boolean error) {
        this.text = text;
        this.error = error;
    }

    @Override
    public String toString() {
        return text;
    }
}
