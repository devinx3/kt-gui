package io.github.devinx3.kt.ui;

import io.github.devinx3.kt.core.CommandEvent;
import io.github.devinx3.kt.core.CommandRunner;
import io.github.devinx3.kt.core.KtCommand;
import io.github.devinx3.kt.core.KtEventBus;
import io.github.devinx3.kt.core.ServiceStore;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务面板：每服务一个独立 CommandRunner。
 * 服务名 → runner 正向映射（执行/终止），runner → 服务名 反向映射（事件按 source 归属）。
 */
public class ServicePanel implements MenuPanel {

    private final ToggleButton button = new ToggleButton("服务");
    private final VBox pane = new VBox(10);

    private final ServiceStore store;
    private final KtEventBus bus;

    // 每服务独立执行器（服务名 → runner）及其反向映射（事件 source → 服务名）
    private final ConcurrentHashMap<String, CommandRunner> serviceRunners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CommandRunner, String> runnerToService = new ConcurrentHashMap<>();

    // UI 映射
    private final VBox serviceListBox = new VBox(2);
    private final Label consoleTitle = new Label("控制台");
    private final StackPane consoleArea = new StackPane();
    private final ConsoleArea fallbackConsole = new ConsoleArea();

    private final ConcurrentHashMap<String, Label> serviceNameLabels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HBox> serviceActionButtons = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConsoleArea> serviceConsoles = new ConcurrentHashMap<>();

    /** mesh 成功的服务集合 */
    private final ConcurrentLinkedHashSet meshSuccessServices = new ConcurrentLinkedHashSet();

    private String selectedService = null;

    // 服务数据变更回调（主页刷新用）
    private Runnable onServicesChanged;

    /** 注册服务列表变更通知（如主页刷新） */
    public void setOnServicesChanged(Runnable onServicesChanged) {
        this.onServicesChanged = onServicesChanged;
    }

    // 按钮引用缓存（用于 updateActionButtons）
    private final ConcurrentHashMap<String, Button[]> serviceButtons = new ConcurrentHashMap<>();

    public ServicePanel(ServiceStore store, KtEventBus bus) {
        this.store = store;
        this.bus = bus;

        // 标题行
        Button addBtn = new Button("新增");
        addBtn.setOnAction(e -> showServiceDialog(null, null));
        HBox titleRow = new HBox(10, new Label("服务"), addBtn);

        // 服务列表
        ScrollPane scrollPane = new ScrollPane(serviceListBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(155);

        // 底部控制台
        consoleArea.getChildren().add(fallbackConsole.getListView());

        pane.getChildren().addAll(titleRow, scrollPane, consoleTitle, consoleArea);
        VBox.setVgrow(consoleArea, Priority.ALWAYS);

        // 订阅事件：只响应本面板服务 runner 的事件（按 source 归属过滤）
        bus.subscribe(CommandEvent.Started.class, event -> {
            if (runnerToService.containsKey(event.source())) {
                updateActionButtons();
            }
        });

        bus.subscribe(CommandEvent.Completed.class, event -> {
            if (runnerToService.containsKey(event.source())) {
                updateActionButtons();
                updateMenuButton();
            }
        });

        bus.subscribe(CommandEvent.Failed.class, event -> {
            if (runnerToService.containsKey(event.source())) {
                updateActionButtons();
                updateMenuButton();
            }
        });

        bus.subscribe(CommandEvent.Success.class, event -> {
            String service = runnerToService.get(event.source());
            if (service != null && event.command() == KtCommand.MESH) {
                markMeshSuccess(service);
            }
        });
    }

    // ---- 命令生命周期 ----

    /** 获取或创建服务的独立执行器 */
    private CommandRunner runnerFor(String service) {
        return serviceRunners.computeIfAbsent(service, s -> {
            CommandRunner runner = new CommandRunner(bus);
            runnerToService.put(runner, s);
            return runner;
        });
    }

    /** 移除服务执行器（服务删除时调用） */
    private void removeRunner(String service) {
        CommandRunner runner = serviceRunners.remove(service);
        if (runner != null) {
            runnerToService.remove(runner);
            runner.shutdown();
        }
    }

    /** 获取或创建服务独立控制台 */
    private ConsoleArea serviceConsole(String service) {
        return serviceConsoles.computeIfAbsent(service, k -> new ConsoleArea());
    }

    /** 开始服务命令（mesh 或 recover） */
    private void startServiceCommand(KtCommand cmd, String service, String port) {
        CommandRunner runner = runnerFor(service);
        if (runner.isActive()) {
            serviceConsole(service).append(
                    Ui.timestamp() + " " + service + " 的命令正在执行中，请先点击 stop 终止", false);
            return;
        }
        updateActionButtons();
        switch (cmd) {
            case MESH -> runner.runCommand(serviceConsole(service), cmd, service, "--expose", port);
            case RECOVER -> runner.runCommand(serviceConsole(service), cmd, service);
            default -> runner.runCommand(serviceConsole(service), cmd);
        }
    }

    /** 停止服务命令 */
    private void stopService(String service) {
        CommandRunner runner = serviceRunners.get(service);
        if (runner == null || !runner.isActive()) {
            serviceConsole(service).append(Ui.timestamp() + " " + service + " 未在运行", false);
            return;
        }
        serviceConsole(service).append(Ui.timestamp() + " 已发送终止信号: " + service, false);
        meshSuccessServices.remove(service);
        updateServiceRowStyle(service);
        updateMenuButton();
        runner.terminate();
    }

    /** mesh 成功标记 */
    private void markMeshSuccess(String service) {
        meshSuccessServices.add(service);
        updateServiceRowStyle(service);
        updateMenuButton();
    }

    // ---- 按钮状态机 ----

    private void updateActionButtons() {
        for (String service : store.list().keySet()) {
            Button[] btns = serviceButtons.get(service);
            if (btns == null) continue;
            // btns: [mesh, recover, stop, edit, del, pin]
            CommandRunner runner = serviceRunners.get(service);
            boolean running = runner != null && runner.isActive();
            boolean meshOk = meshSuccessServices.contains(service);

            btns[0].setDisable(running || meshOk); // mesh
            btns[1].setDisable(running);            // recover
            btns[2].setDisable(!running && !meshOk); // stop
            btns[3].setDisable(running);            // edit
            btns[4].setDisable(running);            // delete
            // pin always enabled
        }
    }

    private void updateServiceRowStyle(String service) {
        Label nameLabel = serviceNameLabels.get(service);
        if (nameLabel == null) return;

        StringBuilder style = new StringBuilder();
        if (service.equals(selectedService)) {
            style.append("-fx-background-color: #d0e8ff;");
        }
        if (meshSuccessServices.contains(service)) {
            style.append("-fx-text-fill: #28a745; -fx-font-weight: bold;");
        }
        nameLabel.setStyle(style.length() > 0 ? style.toString() : null);
    }

    private void updateSelectionStyle() {
        for (String service : store.list().keySet()) {
            updateServiceRowStyle(service);
        }
    }

    private void updateMenuButton() {
        int n = meshSuccessServices.size();
        if (n > 0) {
            button.setText("服务 (" + n + ")");
            button.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
        } else {
            button.setText("服务");
            button.setStyle(null);
        }
    }

    private void selectService(String service) {
        this.selectedService = service;
        consoleTitle.setText(service != null ? service : "控制台");
        consoleArea.getChildren().clear();
        if (service != null) {
            consoleArea.getChildren().add(serviceConsole(service).getListView());
        } else {
            consoleArea.getChildren().add(fallbackConsole.getListView());
        }
        updateSelectionStyle();
        updateActionButtons();
    }

    // ---- 增删改置顶 ----

    private void showServiceDialog(String oldName, String oldPort) {
        boolean isEdit = oldName != null;

        while (true) {
            Dialog<ButtonType> dialog = new Dialog<>();
            Ui.initOwner(dialog);
            dialog.setTitle(isEdit ? "修改服务" : "新增服务");
            dialog.setHeaderText(null);

            ButtonType saveButtonType = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField nameField = new TextField();
            nameField.setPromptText("服务名（必填）");
            if (oldName != null) nameField.setText(oldName);

            TextField portField = new TextField();
            portField.setPromptText("端口（必填）");
            if (oldPort != null) portField.setText(oldPort);

            grid.add(new Label("服务名:"), 0, 0);
            grid.add(nameField, 1, 0);
            grid.add(new Label("端口:"), 0, 1);
            grid.add(portField, 1, 1);

            dialog.getDialogPane().setContent(grid);

            // 请求焦点
            Platform.runLater(nameField::requestFocus);

            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
                return;
            }

            String newName = nameField.getText().trim();
            String newPort = portField.getText().trim();

            if (newName.isEmpty()) {
                Ui.showAlert("服务名必填");
                continue;
            }
            if (newPort.isEmpty()) {
                Ui.showAlert("端口不允许为空");
                continue;
            }
            if (!Ui.isValidPort(newPort)) {
                Ui.showAlert("端口必须是 0-65535 之间的整数");
                continue;
            }

            Map<String, String> services = store.listForUpdate();

            // 检查重名
            if (isEdit) {
                services.remove(oldName);
            }
            if (services.containsKey(newName)) {
                Ui.showAlert("服务名已存在: " + newName);
                continue;
            }

            services.put(newName, newPort);
            store.save(services);

            if (isEdit && oldName.equals(selectedService)) {
                selectedService = newName;
            }

            refreshServices();
            return;
        }
    }

    private void deleteService(String name) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText(null);
        alert.setContentText("确定要删除服务 " + name + " 吗？");
        Ui.initOwner(alert);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        Map<String, String> services = store.listForUpdate();
        if (!services.containsKey(name)) {
            Ui.showAlert("服务不存在: " + name);
            return;
        }
        services.remove(name);
        store.save(services);

        removeRunner(name);

        if (name.equals(selectedService)) {
            selectedService = null;
        }
        refreshServices();
    }

    private void pinService(String name) {
        Map<String, String> services = store.listForUpdate();
        if (!services.containsKey(name)) {
            Ui.showAlert("服务不存在: " + name);
            return;
        }
        // 取出该项放最前
        String port = services.remove(name);
        LinkedHashMap<String, String> reordered = new LinkedHashMap<>();
        reordered.put(name, port);
        reordered.putAll(services);
        store.save(reordered);
        refreshServices();
    }

    // ---- refreshServices ----

    /** 重建列表 */
    public void refreshServices() {
        serviceListBox.getChildren().clear();
        serviceNameLabels.clear();
        serviceActionButtons.clear();
        serviceButtons.clear();
        // 注意：不清除 serviceConsoles，保留控制台历史

        Map<String, String> services = store.list();
        for (Map.Entry<String, String> entry : services.entrySet()) {
            String service = entry.getKey();
            String port = entry.getValue();
            serviceListBox.getChildren().add(createServiceRow(service, port));
        }

        // 恢复选中
        if (selectedService != null && services.containsKey(selectedService)) {
            selectService(selectedService);
        } else {
            selectedService = null;
            consoleTitle.setText("控制台");
            consoleArea.getChildren().clear();
            consoleArea.getChildren().add(fallbackConsole.getListView());
        }

        // 恢复 mesh 成功样式
        for (String service : meshSuccessServices) {
            updateServiceRowStyle(service);
        }
        updateSelectionStyle();
        updateActionButtons();
        updateMenuButton();

        // 通知外部（主页）服务列表已变化
        if (onServicesChanged != null) {
            onServicesChanged.run();
        }
    }

    private HBox createServiceRow(String service, String port) {
        Label nameLabel = new Label(service + " : " + port);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button meshBtn = new Button("mesh");
        Button recoverBtn = new Button("recover");
        Button stopBtn = new Button("stop");
        Button editBtn = new Button("修改");
        Button delBtn = new Button("删除");
        Button pinBtn = new Button("置顶");

        // 缓存按钮引用
        serviceButtons.put(service, new Button[]{meshBtn, recoverBtn, stopBtn, editBtn, delBtn, pinBtn});
        serviceNameLabels.put(service, nameLabel);

        // 按钮动作
        meshBtn.setOnAction(e -> startServiceCommand(KtCommand.MESH, service, port));
        recoverBtn.setOnAction(e -> startServiceCommand(KtCommand.RECOVER, service, port));
        stopBtn.setOnAction(e -> stopService(service));
        editBtn.setOnAction(e -> showServiceDialog(service, port));
        delBtn.setOnAction(e -> deleteService(service));
        pinBtn.setOnAction(e -> pinService(service));

        HBox row = new HBox(5, nameLabel, spacer, meshBtn, recoverBtn, stopBtn, editBtn, delBtn, pinBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(0, 0, 0, 6));

        // 行上注册 MOUSE_CLICKED 事件过滤器 → selectService
        row.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, ev ->
                selectService(service));

        serviceActionButtons.put(service, row);
        return row;
    }

    // ---- 公开方法 ----

    public void updateServiceState() {
        button.setDisable(false);
        updateActionButtons();
    }

    public LinkedHashSet<String> getActiveMeshServices() {
        return meshSuccessServices.copy();
    }

    /** 运行中的服务命令映射 */
    public LinkedHashMap<String, String> getRunningServices() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, CommandRunner> entry : serviceRunners.entrySet()) {
            CommandRunner runner = entry.getValue();
            if (runner.isActive() && runner.activeCommand() != null) {
                KtCommand cmd = runner.activeCommand();
                if (cmd == KtCommand.MESH || cmd == KtCommand.RECOVER) {
                    result.put(entry.getKey(), cmd == KtCommand.MESH ? "mesh" : "recover");
                }
            }
        }
        return result;
    }

    /** 启动第一个服务 mesh（主页"一键启动"调用） */
    public void startFirstServiceMesh() {
        Map<String, String> services = store.list();
        if (services.isEmpty()) return;
        Map.Entry<String, String> first = services.entrySet().iterator().next();
        String service = first.getKey();
        String port = first.getValue();
        CommandRunner runner = serviceRunners.get(service);
        if ((runner != null && runner.isActive()) || meshSuccessServices.contains(service)) {
            return;
        }
        startServiceCommand(KtCommand.MESH, service, port);
    }

    /** 停止所有活动服务（退出确认后调用） */
    public void stopAllActiveServices() {
        for (String service : serviceRunners.keySet()) {
            stopService(service);
        }
    }

    /** 退出清理：关闭全部服务执行器 */
    public void shutdown() {
        for (CommandRunner runner : serviceRunners.values()) {
            runner.shutdown();
        }
        serviceRunners.clear();
        runnerToService.clear();
    }

    @Override
    public ToggleButton getButton() {
        return button;
    }

    @Override
    public Node getPane() {
        return pane;
    }

    // ---- 内部工具类 ----

    /** 并发安全的 LinkedHashSet */
    private static class ConcurrentLinkedHashSet implements Iterable<String> {
        private final LinkedHashSet<String> set = new LinkedHashSet<>();

        synchronized boolean add(String s) {
            return set.add(s);
        }

        synchronized boolean remove(String s) {
            return set.remove(s);
        }

        synchronized boolean contains(String s) {
            return set.contains(s);
        }

        synchronized int size() {
            return set.size();
        }

        synchronized LinkedHashSet<String> copy() {
            return new LinkedHashSet<>(set);
        }

        @Override
        public synchronized java.util.Iterator<String> iterator() {
            return new LinkedHashSet<>(set).iterator();
        }
    }
}
