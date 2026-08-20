package io.github.devinx3.kt;

import io.github.devinx3.kt.core.KtEventBus;
import io.github.devinx3.kt.core.ServiceStore;
import io.github.devinx3.kt.ui.CleanPanel;
import io.github.devinx3.kt.ui.ConfigPanel;
import io.github.devinx3.kt.ui.ConnectPanel;
import io.github.devinx3.kt.ui.HomePanel;
import io.github.devinx3.kt.ui.MenuPanel;
import io.github.devinx3.kt.ui.ServicePanel;
import io.github.devinx3.kt.ui.Ui;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

/**
 * kt GUI 主入口：装配主界面。
 */
public class KtApp extends Application {

    private ConnectPanel connectPanel;
    private ServicePanel servicePanel;
    private CleanPanel cleanPanel;
    private ConfigPanel configPanel;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("KT 客户端");
        primaryStage.getIcons().addAll(
                new Image(KtApp.class.getResourceAsStream("/favicon.png")),
                new Image(KtApp.class.getResourceAsStream("/favicon-256.png")));

        // 创建全局共享实例
        KtEventBus bus = new KtEventBus();
        ServiceStore store = new ServiceStore();

        // 构造五个面板（每个面板持有独立的 CommandRunner）
        connectPanel = new ConnectPanel(bus);
        cleanPanel = new CleanPanel(bus);
        configPanel = new ConfigPanel(bus);
        servicePanel = new ServicePanel(store, bus);
        HomePanel homePanel = new HomePanel(connectPanel, servicePanel, store, bus);
        // 服务列表增删改置顶后刷新主页
        servicePanel.setOnServicesChanged(homePanel::refresh);
        // 注册主窗口：所有 Alert 弹框继承其标题栏图标
        Ui.setPrimaryStage(primaryStage);

        MenuPanel[] panels = {homePanel, configPanel, connectPanel, servicePanel, cleanPanel};

        // 左侧按钮列
        VBox menuBox = new VBox(10);
        menuBox.setPadding(new javafx.geometry.Insets(10));
        menuBox.setPrefWidth(130);

        ToggleGroup commandGroup = new ToggleGroup();
        for (MenuPanel p : panels) {
            ToggleButton btn = p.getButton();
            btn.setToggleGroup(commandGroup);
            btn.setMaxWidth(Double.MAX_VALUE);
            menuBox.getChildren().add(btn);

            // 再次点击已选中按钮：吞事件
            btn.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (btn.isSelected()) {
                    e.consume();
                }
            });
        }

        // 右侧面板区
        StackPane contentArea = new StackPane();
        for (MenuPanel p : panels) {
            contentArea.getChildren().add(p.getPane());
        }

        // 面板切换
        commandGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            for (MenuPanel p : panels) {
                boolean visible = newToggle == p.getButton();
                p.getPane().setVisible(visible);
                p.getPane().setManaged(visible);
            }
        });

        HBox root = new HBox(10, menuBox, contentArea);
        HBox.setHgrow(contentArea, Priority.ALWAYS);

        VBox outerRoot = new VBox(10, root);
        outerRoot.setPadding(new javafx.geometry.Insets(10));

        Scene scene = new Scene(outerRoot, 960, 630);
        scene.getStylesheets().add(KtApp.class.getResource("/style.css").toExternalForm());
        primaryStage.setScene(scene);

        // 初始流程
        servicePanel.updateServiceState();
        servicePanel.refreshServices();

        // 默认选中主页
        homePanel.getButton().setSelected(true);
        homePanel.getPane().setVisible(true);
        homePanel.getPane().setManaged(true);
        for (MenuPanel p : panels) {
            if (p != homePanel) {
                p.getPane().setVisible(false);
                p.getPane().setManaged(false);
            }
        }
        homePanel.refresh();

        // 退出确认
        primaryStage.setOnCloseRequest(e -> {
            String message = buildExitMessage();
            if (message.isEmpty()) {
                // 无活动会话，直接关闭
                return;
            }
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("确认退出");
            alert.setHeaderText("存在未清理的活动会话");
            alert.setContentText(message + "确定要退出吗？确认后将自动终止上述活动会话。");
            Ui.initOwner(alert);
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                e.consume();
                return;
            }
            servicePanel.stopAllActiveServices();
            connectPanel.disconnect();
        });

        primaryStage.show();
    }

    private String buildExitMessage() {
        StringBuilder sb = new StringBuilder();
        if (connectPanel.isConnected()) {
            sb.append("连接会话仍处于活动状态\n");
        } else if (connectPanel.isConnecting()) {
            sb.append("连接命令正在执行中\n");
        }

        Map<String, String> runningServices = servicePanel.getRunningServices();
        LinkedHashSet<String> meshOk = servicePanel.getActiveMeshServices();

        // 并集
        LinkedHashSet<String> activeServiceNames = new LinkedHashSet<>();
        activeServiceNames.addAll(runningServices.keySet());
        activeServiceNames.addAll(meshOk);

        if (!activeServiceNames.isEmpty()) {
            sb.append("以下服务命令仍处于活动状态：\n");
            sb.append(String.join("，", activeServiceNames));
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    public void stop() {
        // 清理每个面板独立持有的执行器
        if (connectPanel != null) {
            connectPanel.shutdown();
        }
        if (cleanPanel != null) {
            cleanPanel.shutdown();
        }
        if (configPanel != null) {
            configPanel.shutdown();
        }
        if (servicePanel != null) {
            servicePanel.shutdown();
        }
    }
}
