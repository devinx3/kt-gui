package io.github.devinx3.kt.ui;

import io.github.devinx3.kt.core.CommandRunner;
import io.github.devinx3.kt.core.KtCommand;
import io.github.devinx3.kt.core.KtEventBus;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * 清理面板：ktctl clean
 */
public class CleanPanel implements MenuPanel {

    private final ToggleButton button = new ToggleButton("清理");
    private final VBox pane = new VBox(10);

    private final ConsoleArea cleanArea = new ConsoleArea();
    private final CommandRunner runner;
    private final Button executeBtn = new Button("执行");
    private final Button terminateBtn = new Button("终止");

    public CleanPanel(KtEventBus bus) {
        this.runner = new CommandRunner(bus);

        // 布局：标题行 + 控制台
        HBox titleRow = new HBox(10, new Label("清理"), executeBtn, terminateBtn);
        pane.getChildren().addAll(titleRow, cleanArea.getListView());
        VBox.setVgrow(cleanArea.getListView(), Priority.ALWAYS);

        executeBtn.setOnAction(e -> handleExecute());
        terminateBtn.setOnAction(e -> handleTerminate());
    }

    private void handleExecute() {
        if (runner.isActive()) {
            cleanArea.append(Ui.timestamp() + " 清理命令正在执行中，请勿重复点击", false);
            return;
        }
        runner.runCommand(cleanArea, KtCommand.CLEAN);
    }

    private void handleTerminate() {
        if (!runner.isActive()) {
            cleanArea.append(Ui.timestamp() + " 当前没有正在运行的命令", false);
            return;
        }
        cleanArea.append(Ui.timestamp() + " 已发送终止信号", false);
        runner.terminate();
    }

    /** 退出清理 */
    public void shutdown() {
        runner.shutdown();
    }

    @Override
    public ToggleButton getButton() {
        return button;
    }

    @Override
    public Node getPane() {
        return pane;
    }
}
