package io.github.devinx3.kt.ui;

import io.github.devinx3.kt.core.CommandRunner;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * 清理面板：执行 ktctl clean，带终止按钮
 */
public class CleanPanel implements MenuPanel {

    private final ToggleButton btn = new ToggleButton("清理");
    private final ConsoleArea cleanArea = new ConsoleArea();
    private final VBox pane = new VBox(5);
    private final CommandRunner runner;

    public CleanPanel(CommandRunner runner) {
        this.runner = runner;

        // 标题 + 终止按钮，与连接面板保持一致
        Button terminateBtn = new Button("终止");
        terminateBtn.setOnAction(e -> handleTerminate());
        HBox cleanHeader = new HBox(10, new Label("清理"), terminateBtn);
        pane.getChildren().addAll(cleanHeader, cleanArea.getListView());
        VBox.setVgrow(cleanArea.getListView(), Priority.ALWAYS);

        btn.setOnAction(e -> handleClean());
    }

    @Override
    public ToggleButton getButton() {
        return btn;
    }

    @Override
    public Node getPane() {
        return pane;
    }

    private void handleClean() {
        // 清理命令正在执行时，再次点击无需重复执行
        if (runner.isRunning("clean")) {
            cleanArea.append(Ui.timestamp() + " 清理命令正在执行中，请勿重复点击", false);
            return;
        }
        runner.runCommandTo(cleanArea.getListView(), "clean");
    }

    /**
     * 终止当前命令：与连接面板行为一致
     */
    private void handleTerminate() {
        if (!runner.isBusy()) {
            cleanArea.append(Ui.timestamp() + " 当前没有正在运行的命令", false);
            return;
        }
        cleanArea.append(Ui.timestamp() + " 已发送终止信号 (Ctrl+C)", false);
        runner.terminateCurrent();
    }
}
