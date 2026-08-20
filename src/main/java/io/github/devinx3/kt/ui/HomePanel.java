package io.github.devinx3.kt.ui;

import io.github.devinx3.kt.core.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.LinkedHashSet;
import java.util.Map;

/**
 * 主页/总览面板。
 * 一键启动/全部关闭/状态展示。
 */
public class HomePanel implements MenuPanel {

    private final ToggleButton button = new ToggleButton("主页");
    private final BorderPane pane = new BorderPane();

    private final ConnectPanel connectPanel;
    private final ServicePanel servicePanel;
    private final ServiceStore store;

    private final Button oneClickStartBtn = new Button("一键启动");
    private final Button closeAllBtn = new Button("全部关闭");

    private final Label connectStateLabel = new Label();
    private final VBox serviceList = new VBox(5);

    public HomePanel(ConnectPanel connectPanel, ServicePanel servicePanel,
                     ServiceStore store, KtEventBus bus) {
        this.connectPanel = connectPanel;
        this.servicePanel = servicePanel;
        this.store = store;

        // 标题 + 帮助按钮（消息提醒图标放在"总览"后面）
        Label titleLabel = new Label("总览");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Button helpBtn = new Button("?");
        helpBtn.setStyle("-fx-background-color: transparent; -fx-padding: 2;");
        helpBtn.setOnAction(e -> showHelpDialog());
        HBox titleRow = new HBox(8, titleLabel, helpBtn);

        // 按钮行
        HBox buttonRow = new HBox(10, oneClickStartBtn, closeAllBtn);

        // 空行
        Label emptyLine = new Label();

        // 连接状态行
        HBox connectRow = new HBox(10, new Label("(*) 数据代理通道"), connectStateLabel);

        // 服务列表滚动区（内容超出时使用滚动）
        ScrollPane serviceScroll = new ScrollPane(serviceList);
        serviceScroll.setFitToWidth(true);
        serviceScroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(serviceScroll, Priority.ALWAYS);

        // 内容区（center，serviceScroll 撑满）
        VBox content = new VBox(10, titleRow, buttonRow, emptyLine, connectRow, serviceScroll);
        BorderPane.setAlignment(content, Pos.TOP_LEFT);

        pane.setCenter(content);

        // 一键启动
        oneClickStartBtn.setOnAction(e -> {
            connectPanel.execute();
            // 1.1s 后启动第一个服务 mesh
            handleTimeout(ev -> servicePanel.startFirstServiceMesh(), Duration.millis(1100));
        });

        // 全部关闭
        closeAllBtn.setOnAction(e -> {
            servicePanel.stopAllActiveServices();
            connectPanel.disconnect();
            refresh();
        });

        // 订阅事件 → refresh（含 Success：连接/mesh 成功时主页状态需即时更新）
        bus.subscribe(CommandEvent.Started.class, this::refreshListener);
        bus.subscribe(CommandEvent.Success.class, this::refreshListener);
        bus.subscribe(CommandEvent.Completed.class, this::refreshListener);
        bus.subscribe(CommandEvent.Failed.class, this::refreshListener);
    }

    public void refreshListener(CommandEvent event) {
        if (event.command().equals(KtCommand.MESH) || event.command().equals(KtCommand.CONNECT) || event.command().equals(KtCommand.RECOVER)) {
            refresh();
        }
    }

    public void handleTimeout(EventHandler<ActionEvent>  onFinished, Duration duration) {
        PauseTransition pt = new PauseTransition(duration);
        pt.setOnFinished(onFinished);
        pt.play();
    }

    /** 刷新总览 */
    public void refresh() {
        // 连接状态
        if (connectPanel.isConnected()) {
            connectStateLabel.setText("已建立");
            connectStateLabel.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
        } else if (connectPanel.isConnecting()) {
            connectStateLabel.setText("正在建立");
            connectStateLabel.setStyle("-fx-text-fill: #888888; -fx-font-weight: bold;");
        } else {
            connectStateLabel.setText("未建立");
            connectStateLabel.setStyle("-fx-text-fill: #888888;");
        }

        // 服务列表
        serviceList.getChildren().clear();
        Map<String, String> services = store.list();
        if (services.isEmpty()) {
            serviceList.getChildren().add(new Label("暂无服务"));
        } else {
            LinkedHashSet<String> meshOk = servicePanel.getActiveMeshServices();
            Map<String, String> active = servicePanel.getRunningServices();

            boolean first = true;
            for (Map.Entry<String, String> entry : services.entrySet()) {
                String service = entry.getKey();
                String port = entry.getValue();

                // 第一个服务（一键启动的默认目标）加 (*) 前缀标记，其余服务空三个空格对齐
                Label nameLabel = new Label((first ? "(*) " : "     ") + service + " : " + port);
                first = false;
                Label stateLabel = new Label();

                if (meshOk.contains(service)) {
                    stateLabel.setText("mesh 已连接");
                    stateLabel.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                } else if (active.containsKey(service)) {
                    String cmd = active.get(service);
                    stateLabel.setText(cmd + " 执行中");
                    stateLabel.setStyle("-fx-text-fill: #888888; -fx-font-weight: bold;");
                } else {
                    stateLabel.setText("未运行");
                    stateLabel.setStyle("-fx-text-fill: #888888;");
                }

                HBox row = new HBox(10, nameLabel, stateLabel);
                serviceList.getChildren().add(row);
            }
        }

        // 按钮可用性随状态刷新
        updateButtonStates();
    }

    /** 逻辑计算"一键启动/全部关闭"的可用状态 */
    private void updateButtonStates() {
        boolean hasService = !store.list().isEmpty();
        boolean connected = connectPanel.isConnected();
        boolean connecting = connectPanel.isConnecting();
        boolean anyServiceRunning = !servicePanel.getRunningServices().isEmpty();
        boolean anyMeshOk = !servicePanel.getActiveMeshServices().isEmpty();

        // 一键启动：有服务、无进行中操作，且（未连接 或 存在待 mesh 的服务）
        boolean canStart = hasService && !connecting && !anyServiceRunning
                && (!connected || !anyMeshOk);
        oneClickStartBtn.setDisable(!canStart);

        // 全部关闭：存在任何活动（已连接/连接中/服务运行中/mesh 成功）
        boolean anyActive = connected || connecting || anyServiceRunning || anyMeshOk;
        closeAllBtn.setDisable(!anyActive);
    }

    /** 帮助图标：圆圈 + 问号（Material help 风格） */
    private static Node createHelpIcon() {
        SVGPath icon = new SVGPath();
        icon.setContent("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z"
                + "m1 17h-2v-2h2v2zm2.07-7.75l-.9.92C13.45 12.9 13 13.5 13 15h-2v-.5"
                + "c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41 0-1.1-.9-2-2-2"
                + "s-2 .9-2 2H8c0-2.21 1.79-4 4-4s4 1.79 4 4c0 .88-.36 1.68-.93 2.25z");
        icon.setFill(Color.web("#666666"));
        icon.setScaleX(0.5);
        icon.setScaleY(0.5);
        return icon;
    }

    /** 主页帮助弹框：说明各按钮/状态区/服务列表的用法 */
    private void showHelpDialog() {
        // 延迟到当前事件/动画处理结束后再显示，避免 showAndWait 被拒绝
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("帮助");
            // 去掉默认的信息图标（感叹号）
            alert.setGraphic(null);
            // 关联主窗口：继承其标题栏图标，并作为模态子窗口居中显示
            Ui.initOwner(alert);
            alert.setHeaderText(null);
            alert.setContentText("""
                    【主页】
                        【一键启动】自动建立数据代理通道和连接第一个服务（名称前带 (*) 标记）。
                        【全部关闭】终止所有正在运行的服务命令，并断开数据代理通道。
                    
                    【配置】
                    查看当前工具已生效的配置
                    
                    【连接】
                    本地机器与集群内网之间建立双向网络通道
                    
                    【服务】
                    维护服务名和端口, 并支持对服务进行 mesh 和 recover
                    
                    【清理】
                    清理在集群中残留的临时资源
                    """);
            alert.getDialogPane().setPrefWidth(500);
            alert.showAndWait();
        });
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
