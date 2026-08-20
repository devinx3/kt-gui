package io.github.devinx3.kt.ui;

import io.github.devinx3.kt.core.CommandEvent;
import io.github.devinx3.kt.core.CommandRunner;
import io.github.devinx3.kt.core.KtCommand;
import io.github.devinx3.kt.core.KtEventBus;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * 配置面板：ktctl config show
 * 输出 name=value 行，解析成两列表格。
 */
public class ConfigPanel implements MenuPanel {

    private final ToggleButton button = new ToggleButton("配置");
    private final VBox pane = new VBox(10);

    private final Label configStatusLabel = new Label();
    private final TableView<String[]> tableView = new TableView<>();

    private final CommandRunner runner;

    public ConfigPanel(KtEventBus bus) {
        this.runner = new CommandRunner(bus);

        // 状态标签初始灰色
        configStatusLabel.setStyle("-fx-text-fill: #888888;");

        // 表格：两列
        TableColumn<String[], String> nameCol = new TableColumn<>("配置名称");
        nameCol.setPrefWidth(220);
        nameCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue() != null && data.getValue().length > 0 ? data.getValue()[0] : ""));

        TableColumn<String[], String> valueCol = new TableColumn<>("配置值");
        valueCol.setPrefWidth(380);
        valueCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue() != null && data.getValue().length > 1 ? data.getValue()[1] : ""));

        // 用 Collection 重载避免 varargs 泛型数组（TableColumn<String[], ?>[]）的 unchecked 警告
        tableView.getColumns().addAll(List.of(nameCol, valueCol));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableView.setPrefHeight(250);
        tableView.setPlaceholder(new Label("无配置内容"));

        pane.getChildren().addAll(new Label("配置"), configStatusLabel, tableView);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        // 菜单按钮点击 → 执行 config show
        button.setOnAction(e -> {
            runner.runCollecting(KtCommand.CONFIG, "show");
        });

        // 订阅本执行器的 Collected 事件 → showConfig
        bus.subscribe(CommandEvent.Collected.class, event -> {
            CommandEvent.Collected collected = (CommandEvent.Collected) event;
            if (collected.source() == runner) {
                showConfig(collected.lines());
            }
        });

        // 订阅本执行器的 Failed 事件 → showLoadError
        bus.subscribe(CommandEvent.Failed.class, event -> {
            CommandEvent.Failed failed = (CommandEvent.Failed) event;
            if (failed.source() == runner) {
                showLoadError(failed.message());
            }
        });
    }

    private void showConfig(List<String> lines) {
        tableView.getItems().clear();
        int count = 0;
        for (String line : lines) {
            int idx = line.indexOf('=');
            if (idx <= 0) continue;
            String name = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            tableView.getItems().add(new String[]{name, value});
            count++;
        }
        if (count > 0) {
            configStatusLabel.setText("已加载 " + count + " 项配置");
            configStatusLabel.setStyle("-fx-text-fill: #888888;");
        } else {
            configStatusLabel.setText("未获取到配置内容");
            configStatusLabel.setStyle("-fx-text-fill: #c0392b;");
        }
    }

    private void showLoadError(String msg) {
        configStatusLabel.setText("加载配置失败: " + msg);
        configStatusLabel.setStyle("-fx-text-fill: #c0392b;");
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
