package io.github.devinx3.kt.ui;

import javafx.scene.Node;
import javafx.scene.control.ToggleButton;

/**
 * 面板接口：左侧菜单按钮 + 右侧面板内容。
 */
public interface MenuPanel {

    /** 左侧菜单按钮（含选中样式/标题联动） */
    ToggleButton getButton();

    /** 右侧对应面板 */
    Node getPane();
}
