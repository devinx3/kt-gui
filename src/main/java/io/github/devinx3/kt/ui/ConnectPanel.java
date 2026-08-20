package io.github.devinx3.kt.ui;

import io.github.devinx3.kt.core.CommandEvent;
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
 * 连接面板：ktctl connect
 */
public class ConnectPanel implements MenuPanel {

    private final ToggleButton button = new ToggleButton("连接");
    private final VBox pane = new VBox(10);

    private final ConsoleArea connectArea = new ConsoleArea();
    private volatile boolean connectedOk = false;

    private final CommandRunner runner;
    private final Button executeBtn = new Button("执行");
    private final Button terminateBtn = new Button("终止");

    public ConnectPanel(KtEventBus bus) {
        this.runner = new CommandRunner(bus);

        // 布局：标题行 + 控制台
        HBox titleRow = new HBox(10, new Label("连接"), executeBtn, terminateBtn);
        pane.getChildren().addAll(titleRow, connectArea.getListView());
        VBox.setVgrow(connectArea.getListView(), Priority.ALWAYS);

        // 执行按钮
        executeBtn.setOnAction(e -> handleExecute());

        // 终止按钮
        terminateBtn.setOnAction(e -> handleTerminate());

        // 订阅本执行器的 Success 事件 → markConnected
        bus.subscribe(CommandEvent.Success.class, event -> {
            if (event.source() == runner) {
                markConnected();
            }
        });
    }

    /** 执行连接 */
    public void execute() {
        handleExecute();
    }

    private void handleExecute() {
        if (runner.isActive()) {
            connectArea.append(Ui.timestamp() + " 连接命令正在执行中，请勿重复点击", false);
            return;
        }
        if (connectedOk) {
            connectArea.append(Ui.timestamp() + " 已连接，无需重复执行 connect", false);
            return;
        }
        runner.runCommand(connectArea, KtCommand.CONNECT);
    }

    private void markConnected() {
        // 主菜单"连接"按钮变绿
        button.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
        connectedOk = true;
    }

    private void handleTerminate() {
        if (!runner.isActive()) {
            connectArea.append(Ui.timestamp() + " 当前没有正在运行的命令", false);
            return;
        }
        connectArea.append(Ui.timestamp() + " 已发送终止信号 (Ctrl+C)", false);
        // 清绿色
        button.setStyle(null);
        connectedOk = false;
        runner.terminate();
    }

    /** 断开连接（退出确认用） */
    public void disconnect() {
        if (runner.isActive()) {
            button.setStyle(null);
            connectedOk = false;
            runner.terminate();
        }
    }

    /** 退出清理 */
    public void shutdown() {
        runner.shutdown();
    }

    public boolean isConnected() {
        return connectedOk;
    }

    public boolean isConnecting() {
        return runner.isActive();
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
