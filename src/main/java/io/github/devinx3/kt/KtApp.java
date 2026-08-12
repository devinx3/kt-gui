package io.github.devinx3.kt;

import io.github.devinx3.kt.core.CommandRunner;
import io.github.devinx3.kt.core.ServiceStore;
import io.github.devinx3.kt.ui.CleanPanel;
import io.github.devinx3.kt.ui.ConfigPanel;
import io.github.devinx3.kt.ui.ConnectPanel;
import io.github.devinx3.kt.ui.MenuPanel;
import io.github.devinx3.kt.ui.MeshRecoverPanel;
import io.github.devinx3.kt.ui.ServicePanel;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Set;

/**
 * kt GUI 主入口：组装主菜单五个面板（配置/连接/服务/Mesh-Recover/清理）与共享执行器
 */
public class KtApp extends Application {

    private CommandRunner runner;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("KT 客户端");
        // 设置应用图标（源自根目录 favicon.ico / favicon.svg，resources 中为转换后的 PNG）
        primaryStage.getIcons().addAll(
                new Image(KtApp.class.getResourceAsStream("/favicon.png")),       // 32x32，源自 favicon.ico
                new Image(KtApp.class.getResourceAsStream("/favicon-256.png")));  // 256x256，源自 favicon.svg

        // 共享依赖
        ServiceStore store = new ServiceStore();
        runner = new CommandRunner();

        // 主菜单五个面板（按钮 → 面板）
        ConfigPanel configPanel = new ConfigPanel(runner);
        ConnectPanel connectPanel = new ConnectPanel(runner);
        CleanPanel cleanPanel = new CleanPanel(runner);
        MeshRecoverPanel meshRecoverPanel = new MeshRecoverPanel(store);
        ServicePanel servicePanel = new ServicePanel(store, meshRecoverPanel::onServiceDeleted);

        // 命令执行联动：执行期间禁用 Mesh/Recover；配置收集失败时在配置面板提示
        runner.setOnStateChanged(meshRecoverPanel::updateMeshRecoverState);
        runner.setOnCollectError(configPanel::showLoadError);

        MenuPanel[] panels = {configPanel, connectPanel, servicePanel, meshRecoverPanel, cleanPanel};

        // 布局：左侧按钮列 + 右侧当前选中按钮的面板
        // 左侧：按钮垂直排列，点击后显示选中状态（ToggleGroup 单选）
        VBox buttonPane = new VBox(10);
        buttonPane.setPadding(new Insets(10));
        buttonPane.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1;");
        buttonPane.setPrefWidth(130);
        ToggleGroup commandGroup = new ToggleGroup();
        for (MenuPanel p : panels) {
            p.getButton().setMaxWidth(Double.MAX_VALUE);
            p.getButton().setToggleGroup(commandGroup);
            buttonPane.getChildren().add(p.getButton());
        }

        // 右侧：面板区，只展示当前选中按钮对应的面板
        StackPane panelArea = new StackPane();
        for (MenuPanel p : panels) {
            panelArea.getChildren().add(p.getPane());
        }

        // 点击按钮：切换右侧面板显示
        commandGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            for (MenuPanel p : panels) {
                boolean show = newToggle == p.getButton();
                p.getPane().setVisible(show);
                p.getPane().setManaged(show);
            }
        });

        HBox body = new HBox(10);
        body.getChildren().addAll(buttonPane, panelArea);
        HBox.setHgrow(panelArea, Priority.ALWAYS);

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.getChildren().addAll(body);
        VBox.setVgrow(body, Priority.ALWAYS);

        meshRecoverPanel.updateMeshRecoverState(); // 初始状态：Mesh/Recover 可用

        servicePanel.refreshServices(); // 加载已保存的 Mesh 服务列表
        meshRecoverPanel.refreshMeshRecoverServices(); // 加载 Mesh/Recover 面板服务列表

        // 默认不选中任何按钮，点击后才显示对应面板
        for (MenuPanel p : panels) {
            p.getPane().setVisible(false);
            p.getPane().setManaged(false);
        }

        // 退出时校验：连接/服务 mesh 会话仍活动则确认后再退出
        primaryStage.setOnCloseRequest(e -> {
            StringBuilder msg = new StringBuilder();
            if (connectPanel.isConnected()) {
                msg.append("连接会话仍处于活动状态（连接按钮为绿色）\n");
            }
            Set<String> activeMesh = meshRecoverPanel.getActiveMeshServices();
            if (!activeMesh.isEmpty()) {
                msg.append("以下服务 mesh 会话仍处于活动状态：\n")
                        .append(String.join(", ", activeMesh)).append('\n');
            }
            if (msg.length() > 0) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("确认退出");
                alert.setHeaderText("存在未清理的活动会话");
                alert.setContentText(msg + "\n确定要退出吗？");
                if (alert.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
                    e.consume(); // 用户取消，阻止退出
                }
            }
        });

        Scene scene = new Scene(root, 1150, 720);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (runner != null) {
            runner.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
