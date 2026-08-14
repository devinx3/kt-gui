package io.github.devinx3.kt.ui;

import io.github.devinx3.kt.core.ServiceStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 服务面板：Mesh 服务列表管理（新增/修改/删除/置顶）
 */
public class ServicePanel implements MenuPanel {

    private final ToggleButton btn = new ToggleButton("服务");
    private final VBox meshServiceBox = new VBox(5);   // 服务面板左侧服务列表
    private final VBox pane = new VBox(5);
    private final ServiceStore store;
    private final Consumer<String> onServiceDeleted;   // 删除服务后的联动回调（同步 Mesh/Recover 面板）

    public ServicePanel(ServiceStore store, Consumer<String> onServiceDeleted) {
        this.store = store;
        this.onServiceDeleted = onServiceDeleted;

        ScrollPane serviceScroll = new ScrollPane(meshServiceBox);
        serviceScroll.setFitToWidth(true);
        GridPane listGrid = new GridPane();
        ColumnConstraints listCol = new ColumnConstraints();
        listCol.setPrefWidth(Ui.SERVICE_LIST_WIDTH);  // 与 Mesh/Recover 面板服务列表等宽
        ColumnConstraints emptyCol = new ColumnConstraints();
        emptyCol.setHgrow(Priority.ALWAYS);
        listGrid.getColumnConstraints().addAll(listCol, emptyCol);
        listGrid.add(serviceScroll, 0, 0);
        Button addServiceBtn = new Button("新增");
        addServiceBtn.setOnAction(e -> showAddServiceDialog());
        HBox titleRow = new HBox(10, new Label("服务"), addServiceBtn);
        pane.getChildren().addAll(titleRow, listGrid);
        VBox.setVgrow(listGrid, Priority.ALWAYS);
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
     * 刷新服务列表（服务名 + 修改/删除/置顶按钮）
     */
    public void refreshServices() {
        meshServiceBox.getChildren().clear();
        for (Map.Entry<String, String> e : store.load().entrySet()) {
            final String service = e.getKey();
            final String port = e.getValue();
            Button editBtn = new Button("修改");
            Button delBtn = new Button("删除");
            Button pinBtn = new Button("置顶");
            editBtn.setOnAction(ev -> showEditServiceDialog(service, port));
            delBtn.setOnAction(ev -> deleteService(service));
            pinBtn.setOnAction(ev -> pinService(service));
            // 按钮组右对齐：中间用 spacer 撑满
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(5, new Label(service + " : " + port), spacer, editBtn, delBtn, pinBtn);
            // 服务行文字垂直居中，左侧留一点间距
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(0, 0, 0, 6));
            meshServiceBox.getChildren().add(row);
        }
    }

    private void showAddServiceDialog() {
        showServiceDialog(null, null);
    }

    private void showEditServiceDialog(String name, String port) {
        showServiceDialog(name, port);
    }

    /**
     * 服务弹窗通用逻辑：新增（oldName 为空）或修改（预填并更新）
     */
    private void showServiceDialog(String oldName, String oldPort) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(oldName == null ? "新增服务" : "修改服务");
        TextField nameField = new TextField(oldName == null ? "" : oldName);
        TextField portField = new TextField(oldPort == null ? "" : oldPort);
        nameField.setPromptText("服务名（必填）");
        portField.setPromptText("端口（必填）");
        GridPane gp = new GridPane();
        gp.setHgap(10);
        gp.setVgap(10);
        gp.add(new Label("服务名:"), 0, 0);
        gp.add(nameField, 1, 0);
        gp.add(new Label("端口:"), 0, 1);
        gp.add(portField, 1, 1);
        dialog.getDialogPane().setContent(gp);
        ButtonType saveType = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.showAndWait().ifPresent(btnType -> {
            if (btnType != saveType) {
                return;
            }
            String name = nameField.getText().trim();
            String port = portField.getText().trim();
            if (name.isEmpty()) {
                Ui.showAlert("服务名必填");
                return;
            }
            if (port.isEmpty()) {
                Ui.showAlert("端口不允许为空");
                return;
            }
            if (!Ui.isValidPort(port)) {
                Ui.showAlert("端口必须是 0-65535 之间的整数");
                return;
            }
            Map<String, String> services = store.load();
            if (oldName == null) {
                // 新增：服务名必须唯一
                if (services.containsKey(name)) {
                    Ui.showAlert("服务名已存在: " + name);
                    return;
                }
            } else {
                // 修改：改名时不能与其他服务重名
                if (!oldName.equals(name) && services.containsKey(name)) {
                    Ui.showAlert("服务名已存在: " + name);
                    return;
                }
                services.remove(oldName);
            }
            services.put(name, port);
            store.save(services);
            refreshServices();
        });
    }

    /**
     * 删除指定服务
     */
    private void deleteService(String name) {
        Map<String, String> services = store.load();
        if (services.remove(name) == null) {
            Ui.showAlert("服务不存在: " + name);
            return;
        }
        store.save(services);
        refreshServices();
        onServiceDeleted.accept(name);  // 同步刷新 Mesh/Recover 面板
    }

    /**
     * 将指定服务置顶（移到列表最前面）并保存
     */
    private void pinService(String name) {
        Map<String, String> services = store.load();
        String port = services.remove(name);
        if (port == null) {
            Ui.showAlert("服务不存在: " + name);
            return;
        }
        Map<String, String> reordered = new LinkedHashMap<>();
        reordered.put(name, port);   // 置顶服务放最前
        reordered.putAll(services);  // 其余保持原顺序
        store.save(reordered);
        refreshServices();
    }
}
