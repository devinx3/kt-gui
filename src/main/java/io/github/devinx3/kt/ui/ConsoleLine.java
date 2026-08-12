package io.github.devinx3.kt.ui;

/**
 * 控制台输出行：text 为内容，error 表示是否来自 stderr（stderr 行使用淡红背景）
 */
public class ConsoleLine {
    public final String text;
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
