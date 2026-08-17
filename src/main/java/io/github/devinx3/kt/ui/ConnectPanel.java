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
 * 连接面板：点击"执行"才运行 ktctl connect，带终止按钮与成功状态（按钮变绿）
 */
public class ConnectPanel implements MenuPanel {

    private final ToggleButton btn = new ToggleButton("连接");
    private final ConsoleArea connectArea = new ConsoleArea();
    private final VBox pane = new VBox(5);
    private final CommandRunner runner;
    private boolean connectedOk;    // 连接是否成功建立（"连接"按钮为绿色时）

    public ConnectPanel(CommandRunner runner) {
        this.runner = runner;

        // 每个命令面板 = 标题 + 控制台输出区；执行按钮负责真正执行命令
        Button executeBtn = new Button("执行");
        executeBtn.setOnAction(e -> handleExecute());
        Button terminateBtn = new Button("终止");
        terminateBtn.setOnAction(e -> handleTerminate());
        HBox connectHeader = new HBox(10, new Label("连接"), executeBtn, terminateBtn);
        pane.getChildren().addAll(connectHeader, connectArea.getListView());
        VBox.setVgrow(connectArea.getListView(), Priority.ALWAYS);
    }

    @Override
    public ToggleButton getButton() {
        return btn;
    }

    @Override
    public Node getPane() {
        return pane;
    }

    /**
     * 点击"执行"：命令执行中或已连接成功时不重复执行
     */
    private void handleExecute() {
        // 连接命令正在执行时，再次点击无需重复执行
        if (runner.isRunning("connect")) {
            connectArea.append(Ui.timestamp() + " 连接命令正在执行中，请勿重复点击", false);
            return;
        }
        // 已连接成功（按钮为绿色）时，点击不重复执行连接命令
        if (connectedOk) {
            connectArea.append(Ui.timestamp() + " 已连接，无需重复执行 connect", false);
            return;
        }
        // 输出中出现成功提示时立即变绿（无需等命令结束）
        runner.runCommandTo(connectArea.getListView(), "all looks good", line -> markConnected(), "connect");
    }

    /**
     * 终止当前命令：提示 + 终止的是连接命令时清除绿色底色
     */
    private void handleTerminate() {
        if (!runner.isBusy()) {
            connectArea.append(Ui.timestamp() + " 当前没有正在运行的命令", false);
            return;
        }
        connectArea.append(Ui.timestamp() + " 已发送终止信号 (Ctrl+C)", false);
        // 终止的是连接命令时，清除连接成功的绿色底色
        if (runner.isRunning("connect")) {
            btn.setStyle("");
            connectedOk = false;
        }
        runner.terminateCurrent();
    }

    /**
     * connect 输出出现成功提示时：按钮变为绿色加粗字体（由 CommandRunner 在 UI 线程回调）
     */
    private void markConnected() {
        btn.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
        connectedOk = true;
    }

    /**
     * 连接会话是否仍活动（退出确认用）
     */
    public boolean isConnected() {
        return connectedOk;
    }
}
