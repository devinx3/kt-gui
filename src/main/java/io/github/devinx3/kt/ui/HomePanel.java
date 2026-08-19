package io.github.devinx3.kt.ui;

import io.github.devinx3.kt.core.ServiceStore;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.Map;

/**
 * 主页面板：展示连接会话与服务 mesh 的运行情况
 */
public class HomePanel implements MenuPanel {

    private final ToggleButton btn = new ToggleButton("主页");
    private final VBox pane = new VBox(10);
    private final Label connectStateLabel = new Label();
    private final VBox serviceList = new VBox(5);
    private final ConnectPanel connectPanel;
    private final ServicePanel servicePanel;
    private final ServiceStore store;

    public HomePanel(ConnectPanel connectPanel, ServicePanel servicePanel, ServiceStore store) {
        this.connectPanel = connectPanel;
        this.servicePanel = servicePanel;
        this.store = store;

        pane.setPadding(new Insets(5));

        Label title = new Label("总览");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // 一键启动 / 全部关闭：点击后立即禁用 5s，防止重复触发
        Button startAllBtn = new Button("一键启动");
        Button stopAllBtn = new Button("全部关闭");
        HBox actionRow = new HBox(10, startAllBtn, stopAllBtn);
        startAllBtn.setOnAction(e -> {
            disableButtons(Duration.seconds(5), startAllBtn, stopAllBtn);
            connectPanel.execute(); // 等同点击连接面板的"执行"按钮
            timeout(Duration.millis(1300), ev -> servicePanel.startFirstServiceMesh()); // 执行服务列表第一个服务的 mesh
        });
        stopAllBtn.setOnAction(e -> {
            disableButtons(Duration.seconds(3), startAllBtn, stopAllBtn);
            // 逻辑与退出确认相同：先终止所有已连接会话
            servicePanel.stopAllActiveServices();
            connectPanel.disconnect();
            refresh();
        });

        HBox connectRow = new HBox(10, new Label("数据代理通道"), connectStateLabel);

        pane.getChildren().add(title);
        pane.getChildren().add(actionRow);
        pane.getChildren().add(new Label(" "));
        pane.getChildren().addAll(connectRow, serviceList);
        VBox.setVgrow(serviceList, Priority.ALWAYS);

        // 每秒刷新连接状态
        Timeline timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> refresh()));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.play();
    }

    /**
     * 临时禁用按钮几秒后恢复
     */
    private void disableButtons(Duration duration, Button... buttons) {
        for (Button b : buttons) {
            b.setDisable(true);
        }
        timeout(duration, ev -> {
            for (Button b : buttons) {
                b.setDisable(false);
            }
            refresh();
        });
    }

    public void timeout(Duration duration, EventHandler<ActionEvent> value) {
        PauseTransition pause = new PauseTransition(duration);
        pause.setOnFinished(value);
        pause.play();
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
     * 刷新连接与服务运行状态（主页显示时/状态变化时调用）
     */
    public void refresh() {
        if (connectPanel.isConnected()) {
            connectStateLabel.setText("已建立");
            connectStateLabel.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
        } else {
            connectStateLabel.setText(connectPanel.isConnecting() ? "正在建立" : "未建立");
            connectStateLabel.setStyle("-fx-text-fill: #888888;");
        }
        // 服务运行状态
        serviceList.getChildren().clear();
        Map<String, String> services = store.list();
        Map<String, String> running = servicePanel.getRunningServices();
        if (services.isEmpty()) {
            serviceList.getChildren().add(new Label("暂无服务"));
            return;
        }
        for (Map.Entry<String, String> e : services.entrySet()) {
            String name = e.getKey();
            String port = e.getValue();
            Label nameLabel = new Label(name + " : " + port);
            Label stateLabel = new Label();
            String cmd = running.get(name);
            boolean meshOk = servicePanel.getActiveMeshServices().contains(name);
            if (meshOk) {
                stateLabel.setText("mesh 已连接");
                stateLabel.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
            } else if ("mesh".equals(cmd)) {
                stateLabel.setText("mesh 执行中");
                stateLabel.setStyle("-fx-text-fill: #888888;");
            } else if ("recover".equals(cmd)) {
                stateLabel.setText("recover 执行中");
                stateLabel.setStyle("-fx-text-fill: #888888;");
            } else {
                stateLabel.setText("未运行");
                stateLabel.setStyle("-fx-text-fill: #888888;");
            }
            HBox row = new HBox(10, nameLabel, stateLabel);
            serviceList.getChildren().add(row);
        }
    }
}
