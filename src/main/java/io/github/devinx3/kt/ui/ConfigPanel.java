package io.github.devinx3.kt.ui;

import io.github.devinx3.kt.core.CommandRunner;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * 配置面板：展示 ktctl config show 输出（名称/值 两列表格）
 */
public class ConfigPanel implements MenuPanel {

    private final ToggleButton btn = new ToggleButton("配置");
    private final TableView<String[]> configTable = new TableView<>();
    private final Label configStatusLabel = new Label();
    private final VBox pane = new VBox(5);
    private final CommandRunner runner;

    public ConfigPanel(CommandRunner runner) {
        this.runner = runner;

        configTable.setPrefHeight(250);
        configTable.setPlaceholder(new Label("无配置内容"));
        TableColumn<String[], String> nameCol = new TableColumn<>("配置名称");
        nameCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue()[0]));
        nameCol.setPrefWidth(220);
        TableColumn<String[], String> valueCol = new TableColumn<>("配置值");
        valueCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue()[1]));
        valueCol.setPrefWidth(380);
        configTable.getColumns().addAll(List.of(nameCol, valueCol));
        // 两列自动填满面板宽度，避免右侧出现多余的空白区域
        configTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        configStatusLabel.setStyle("-fx-text-fill: #888888;");
        pane.getChildren().addAll(new Label("配置"), configStatusLabel, configTable);
        VBox.setVgrow(configTable, Priority.ALWAYS);

        btn.setOnAction(e -> runner.runCollecting(new String[]{"config", "show"}, this::showConfig));
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
     * 解析 ktctl config show 输出：每行一个配置，按等号分割为"名称 / 值"两列展示
     */
    private void showConfig(List<String> lines) {
        configTable.getItems().clear();
        int count = 0;
        for (String line : lines) {
            int idx = line.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String name = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            configTable.getItems().add(new String[]{name, value});
            count++;
        }
        if (count == 0) {
            configStatusLabel.setText("未获取到配置内容");
            configStatusLabel.setStyle("-fx-text-fill: #c0392b;");
        } else {
            configStatusLabel.setText("已加载 " + count + " 项配置");
            configStatusLabel.setStyle("-fx-text-fill: #888888;");
        }
    }

    /**
     * 配置收集失败时在配置面板提示，避免静默失败（由 CommandRunner 回调）
     */
    public void showLoadError(String msg) {
        configStatusLabel.setText("加载配置失败: " + msg);
        configStatusLabel.setStyle("-fx-text-fill: #c0392b;");
    }
}
