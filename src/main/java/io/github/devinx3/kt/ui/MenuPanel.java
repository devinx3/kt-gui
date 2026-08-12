package io.github.devinx3.kt.ui;

import javafx.scene.Node;
import javafx.scene.control.ToggleButton;

/**
 * 主菜单面板接口：左侧按钮 + 右侧对应面板
 */
public interface MenuPanel {

    ToggleButton getButton();

    Node getPane();
}
