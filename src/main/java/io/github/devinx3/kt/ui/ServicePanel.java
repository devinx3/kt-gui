package io.github.devinx3.kt.ui;

import io.github.devinx3.kt.core.CommandRunner;
import io.github.devinx3.kt.core.CtrlC;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 服务面板：标题行（服务 + 新增）+ 服务列表（每行 服务名 : 端口 + mesh/recover/stop/修改/删除/置顶 按钮靠右）
 * + 底部控制台（显示选中服务的输出）。由原"服务"与"Mesh/Recover"两个面板合并而来。
 */
public class ServicePanel implements MenuPanel {

    // STATUS_CONTROL_C_EXIT = 0xC000013A：进程因收到 Ctrl+C/Ctrl+Break 信号退出（用户主动终止，非异常）
    private static final int STATUS_CONTROL_C_EXIT = -1073741510;

    private final ToggleButton btn = new ToggleButton("服务");
    private final VBox serviceListBox = new VBox(5);         // 服务列表（每行一个服务，点击选中）
    private final Label consoleTitle = new Label("控制台");  // 底部控制台标题（显示当前服务名）
    private final StackPane consoleArea = new StackPane();   // 底部控制台容器
    private final ConsoleArea fallbackConsole = new ConsoleArea(); // 兜底控制台（未选中服务时）
    private final VBox pane = new VBox(5);
    private final ServiceStore store;

    private final Map<String, Process> serviceProcesses = new ConcurrentHashMap<>();  // 服务名 → 命令进程
    private final Map<String, String> serviceCommands = new ConcurrentHashMap<>();    // 服务名 → 当前执行的命令（mesh/recover）
    private final Map<String, Label> serviceNameLabels = new ConcurrentHashMap<>();   // 服务名 → 名称 Label（mesh 成功时绿色加粗）
    private final Map<String, Button[]> serviceActionButtons = new ConcurrentHashMap<>(); // 服务名 → [mesh, recover, stop, 修改, 删除, 置顶] 按钮（状态联动）
    private final Map<String, ConsoleArea> serviceConsoles = new ConcurrentHashMap<>(); // 服务名 → 独立控制台
    private final Map<String, Thread[]> serviceReaderThreads = new ConcurrentHashMap<>(); // 服务名 → [stdout, stderr] 读取线程（stop 时等待剩余输出排空）
    private final Set<String> meshSuccessServices = ConcurrentHashMap.newKeySet();  // mesh 成功的服务（服务名绿色加粗）
    private String selectedService;                      // 当前选中的服务（决定底部控制台显示内容）

    public ServicePanel(ServiceStore store) {
        this.store = store;

        // 标题行：全局操作（新增）放在最上边
        Button addBtn = new Button("新增");
        addBtn.setOnAction(e -> showAddServiceDialog());
        HBox titleRow = new HBox(10, new Label("服务"), addBtn);

        // 服务列表：每行按钮靠右（行内操作，无需固定宽度）
        ScrollPane listScroll = new ScrollPane(serviceListBox);
        listScroll.setFitToWidth(true);
        listScroll.setPrefHeight(280);  // 列表固定高度，剩余空间留给底部控制台
        HBox body = new HBox(listScroll);
        HBox.setHgrow(listScroll, Priority.ALWAYS);

        // 底部控制台：默认显示兜底控制台，选中服务后显示该服务的独立控制台
        consoleArea.getChildren().add(fallbackConsole.getListView());
        consoleTitle.setStyle("-fx-text-fill: #888888;");
        pane.getChildren().addAll(titleRow, body, consoleTitle, consoleArea);
        VBox.setVgrow(consoleArea, Priority.ALWAYS);
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
     * 刷新服务列表（每行：服务名 : 端口 + 修改/删除/置顶/mesh/recover/stop 按钮靠右，点击行选中该服务）
     */
    public void refreshServices() {
        Map<String, String> services = store.load();
        serviceListBox.getChildren().clear();
        serviceNameLabels.clear();
        serviceActionButtons.clear();
        serviceConsoles.clear();
        serviceReaderThreads.clear();
        for (Map.Entry<String, String> e : services.entrySet()) {
            final String service = e.getKey();
            final String port = e.getValue();
            Label nameLabel = new Label(service + " : " + port);
            Button editBtn = new Button("修改");
            Button delBtn = new Button("删除");
            Button pinBtn = new Button("置顶");
            Button meshBtn = new Button("mesh");
            Button recoverBtn = new Button("recover");
            Button stopBtn = new Button("stop");
            editBtn.setOnAction(ev -> showServiceDialog(service, port));
            delBtn.setOnAction(ev -> deleteService(service));
            pinBtn.setOnAction(ev -> pinService(service));
            meshBtn.setOnAction(ev -> startServiceCommand(service, port, "mesh"));
            recoverBtn.setOnAction(ev -> startServiceCommand(service, port, "recover"));
            stopBtn.setOnAction(ev -> stopService(service));
            // 按钮组右对齐：中间用 spacer 撑满
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(5, nameLabel, spacer, meshBtn, recoverBtn, stopBtn, editBtn, delBtn, pinBtn);
            // 行文字垂直居中，左侧留一点间距
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(0, 0, 0, 6));
            // 点击行任意位置（含按钮）选中该服务，底部控制台切换为其输出
            row.addEventFilter(MouseEvent.MOUSE_CLICKED, ev -> selectService(service));
            serviceNameLabels.put(service, nameLabel);
            serviceActionButtons.put(service, new Button[]{meshBtn, recoverBtn, stopBtn, editBtn, delBtn, pinBtn});
            // 每个服务独立的控制台
            serviceConsoles.put(service, new ConsoleArea());
            serviceListBox.getChildren().add(row);
        }
        // 选中服务已被删除时清除选中；否则保持选中并切换到底部控制台
        if (selectedService != null && !services.containsKey(selectedService)) {
            selectedService = null;
        }
        if (selectedService != null) {
            selectService(selectedService);
        } else {
            consoleTitle.setText("控制台");
            consoleArea.getChildren().clear();
            consoleArea.getChildren().add(fallbackConsole.getListView());
        }
        // 列表重建后恢复 mesh 成功服务的绿色加粗字体与按钮状态
        for (String s : meshSuccessServices) {
            updateServiceRowStyle(s);
        }
        updateSelectionStyle();
        updateActionButtons();
        updateMenuButton();  // 同步顶部按钮的绿色加粗与个数
    }

    /**
     * 选中服务：底部控制台切换到该服务的独立控制台
     */
    private void selectService(String service) {
        selectedService = service;
        consoleTitle.setText(service);
        consoleArea.getChildren().clear();
        ConsoleArea console = serviceConsoles.get(service);
        consoleArea.getChildren().add(console != null ? console.getListView() : fallbackConsole.getListView());
        updateSelectionStyle();
        updateActionButtons();
    }

    /**
     * 更新列表行的选中样式：选中的行浅蓝背景，mesh 成功的服务名绿色加粗
     */
    private void updateSelectionStyle() {
        for (Map.Entry<String, Label> e : serviceNameLabels.entrySet()) {
            Label l = e.getValue();
            String style = meshSuccessServices.contains(e.getKey())
                    ? "-fx-text-fill: #28a745; -fx-font-weight: bold; " : "";
            if (e.getKey().equals(selectedService)) {
                style += "-fx-background-color: #d0e8ff;";
            }
            l.setStyle(style);
        }
    }

    /**
     * 更新服务行样式：mesh 成功后服务名绿色加粗，同时刷新该行按钮状态
     */
    private void updateServiceRowStyle(String service) {
        updateSelectionStyle();
        updateRowButtons(service);
    }

    /**
     * 更新所有服务行的按钮状态（mesh 成功后禁用 mesh；命令执行中禁用 mesh/recover/修改/删除；
     * stop 仅在运行或 mesh 成功后可用）
     */
    private void updateActionButtons() {
        for (String service : serviceActionButtons.keySet()) {
            updateRowButtons(service);
        }
    }

    /**
     * 更新单个服务行按钮状态：[mesh, recover, stop, 修改, 删除] 按运行状态联动，置顶始终可用；
     * 命令执行中不允许修改/删除，stop 后恢复
     */
    private void updateRowButtons(String service) {
        Button[] btns = serviceActionButtons.get(service);
        if (btns == null) {
            return;
        }
        boolean running = serviceCommands.containsKey(service);
        boolean meshOk = meshSuccessServices.contains(service);
        btns[0].setDisable(running || meshOk);   // [0] = mesh：运行中或已成功后禁用
        btns[1].setDisable(running);             // [1] = recover：运行中禁用
        btns[2].setDisable(!running && !meshOk); // [2] = stop：仅在运行中或 mesh 成功后可用
        btns[3].setDisable(running);             // [3] = 修改：运行中禁用，stop 后恢复
        btns[4].setDisable(running);             // [4] = 删除：运行中禁用，stop 后恢复
    }

    /**
     * 更新顶部"服务"按钮：存在 mesh 成功的绿色服务时，按钮绿色加粗并显示服务个数
     */
    private void updateMenuButton() {
        int count = meshSuccessServices.size();
        if (count > 0) {
            btn.setText("服务 (" + count + ")");
            btn.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
        } else {
            btn.setText("服务");
            btn.setStyle("");
        }
    }

    /**
     * 命令状态联动（由 CommandRunner 回调）：恢复服务面板按钮的可用状态
     */
    public void updateServiceState() {
        btn.setDisable(false);
        updateActionButtons();
    }

    /**
     * mesh 运行中的服务集合（退出确认用）
     */
    public Set<String> getActiveMeshServices() {
        return meshSuccessServices;
    }

    /**
     * 停止所有正在运行的服务命令（mesh/recover），退出确认后调用
     */
    public void stopAllActiveServices() {
        for (String service : new ArrayList<>(serviceProcesses.keySet())) {
            stopService(service);
        }
    }

    private void showAddServiceDialog() {
        showServiceDialog(null, null);
    }

    /**
     * 服务弹窗通用逻辑：新增（oldName 为空）或修改（预填并更新）。
     * 校验失败（如端口为空）时弹框不关闭：提示后重新弹出，保留已输入内容继续修改。
     */
    private void showServiceDialog(String oldName, String oldPort) {
        String currentName = oldName == null ? "" : oldName;
        String currentPort = oldPort == null ? "" : oldPort;
        while (true) {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(oldName == null ? "新增服务" : "修改服务");
            TextField nameField = new TextField(currentName);
            TextField portField = new TextField(currentPort);
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
            // 取消或关闭弹框：终止操作
            if (dialog.showAndWait().map(t -> t != saveType).orElse(true)) {
                return;
            }
            currentName = nameField.getText().trim();
            currentPort = portField.getText().trim();
            if (currentName.isEmpty()) {
                Ui.showAlert("服务名必填");
                continue; // 重新弹出弹框继续修改
            }
            if (currentPort.isEmpty()) {
                Ui.showAlert("端口不允许为空");
                continue;
            }
            if (!Ui.isValidPort(currentPort)) {
                Ui.showAlert("端口必须是 0-65535 之间的整数");
                continue;
            }
            Map<String, String> services = store.load();
            if (oldName == null) {
                // 新增：服务名必须唯一
                if (services.containsKey(currentName)) {
                    Ui.showAlert("服务名已存在: " + currentName);
                    continue;
                }
            } else {
                // 修改：改名时不能与其他服务重名
                if (!oldName.equals(currentName) && services.containsKey(currentName)) {
                    Ui.showAlert("服务名已存在: " + currentName);
                    continue;
                }
                services.remove(oldName);
            }
            services.put(currentName, currentPort);
            store.save(services);
            // 修改的是当前选中服务时，改名后保持选中
            if (oldName != null && oldName.equals(selectedService)) {
                selectedService = currentName;
            }
            refreshServices();
            return;
        }
    }

    /**
     * 删除指定服务（二次确认后执行）
     */
    private void deleteService(String name) {
        Map<String, String> services = store.load();
        if (!services.containsKey(name)) {
            Ui.showAlert("服务不存在: " + name);
            return;
        }
        // 二次确认：防止误删
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认删除");
        confirm.setHeaderText(null);
        confirm.setContentText("确定要删除服务 " + name + " 吗？");
        if (confirm.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return; // 用户取消，不删除
        }
        services.remove(name);
        store.save(services);
        // 删除的是当前选中服务时，清除选中状态
        if (name.equals(selectedService)) {
            selectedService = null;
        }
        refreshServices();
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

    /**
     * 获取服务的控制台（不存在时兜底用 fallbackConsole）
     */
    private ConsoleArea serviceConsole(String service) {
        ConsoleArea c = serviceConsoles.get(service);
        return c != null ? c : fallbackConsole;
    }

    /**
     * 启动指定服务的 mesh/recover 命令（独立线程，输出到对应服务控制台）
     */
    private void startServiceCommand(String service, String port, String cmd) {
        if (serviceCommands.containsKey(service)) {
            // 命令执行中：不允许再次执行（含 mesh/recover 互斥），需先点击 stop 终止
            serviceConsole(service).append(Ui.timestamp() + " " + service + " 的 " + serviceCommands.get(service)
                    + " 命令正在执行中，请先点击 stop 终止", false);
            return;
        }
        updateActionButtons(); // 命令执行期间禁用该服务的 mesh/recover 按钮
        Thread t = new Thread(() -> {
            try {
                List<String> command = new ArrayList<>();
                command.add("ktctl");
                command.add(cmd);
                command.add(service);
                if ("mesh".equals(cmd)) {
                    command.add("--expose");
                    command.add(port);
                }
                ProcessBuilder pb = new ProcessBuilder(command);
                Process process = pb.start();
                serviceProcesses.put(service, process);
                serviceCommands.put(service, cmd);
                String charsetName = System.getProperty("os.name").toLowerCase().contains("win") ? "GBK" : "UTF-8";
                Platform.runLater(() -> serviceConsole(service).append(
                        Ui.timestamp() + " > 执行: " + String.join(" ", command), false));
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), Charset.forName(charsetName)));
                     BufferedReader errorReader = new BufferedReader(
                             new InputStreamReader(process.getErrorStream(), Charset.forName(charsetName)))) {
                    // mesh 命令成功（出现 "Now you can access your service by header"）时服务名变绿并禁用 mesh 按钮
                    String keyword = "mesh".equals(cmd) ? "Now you can access your service by header" : null;
                    Consumer<String> onSuccess = "mesh".equals(cmd) ? line -> markMeshSuccess(service) : null;
                    Thread outT = new Thread(() -> CommandRunner.readOutput(reader, serviceConsole(service).getListView(), false, null, keyword, onSuccess));
                    Thread errT = new Thread(() -> CommandRunner.readOutput(errorReader, serviceConsole(service).getListView(), true, null, keyword, onSuccess));
                    serviceReaderThreads.put(service, new Thread[]{outT, errT});
                    outT.start();
                    errT.start();
                    int exitCode = process.waitFor();
                    outT.join();
                    errT.join();
                    Platform.runLater(() -> {
                        // 0xC000013A = STATUS_CONTROL_C_EXIT：进程因收到 Ctrl+C/Ctrl+Break 信号退出（用户主动终止，非异常）
                        if (exitCode == STATUS_CONTROL_C_EXIT) {
                            serviceConsole(service).append(
                                    Ui.timestamp() + " " + cmd + " " + service + " 已通过 Ctrl+C 正常终止", false);
                        } else {
                            serviceConsole(service).append(
                                    Ui.timestamp() + " " + cmd + " " + service + " 结束，退出码: " + exitCode, exitCode != 0);
                        }
                        updateActionButtons();  // 命令结束后恢复按钮状态
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    serviceConsole(service).append(
                            Ui.timestamp() + " " + cmd + " " + service + " 启动失败: " + e.getMessage(), true);
                    updateActionButtons();
                });
            } finally {
                serviceProcesses.remove(service);
                serviceCommands.remove(service);
                serviceReaderThreads.remove(service);
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /**
     * 终止指定服务的命令进程
     */
    private void stopService(String service) {
        Process p = serviceProcesses.get(service);
        if (p == null) {
            serviceConsole(service).append(Ui.timestamp() + " " + service + " 未在运行", false);
            return;
        }
        serviceConsole(service).append(Ui.timestamp() + " 已发送终止信号: " + service, false);
        // 立即从运行表移除并清除 mesh 成功状态：迟到的成功回调不再变绿，服务名与按钮恢复默认状态
        serviceProcesses.remove(service);
        meshSuccessServices.remove(service);
        updateServiceRowStyle(service);
        updateMenuButton();  // 顶部按钮恢复默认（无绿色服务时）
        // 仅 mesh 命令需要优雅退出（发送真正的 Ctrl+C）；recover 直接终止即可
        if ("mesh".equals(serviceCommands.get(service))) {
            if (!CtrlC.send(p)) {
                p.destroy();
            }
        } else {
            p.destroy();
        }
        Thread t = new Thread(() -> {
            try {
                // 等待输出读取线程把进程已产生的剩余输出读完并打印（如 mesh/recover 的结束日志），
                // 之后再强制终止兜底，避免 stop 后输出丢失
                Thread[] readers = serviceReaderThreads.get(service);
                if (readers != null) {
                    for (Thread r : readers) {
                        r.join(3000);
                    }
                }
                if (!p.waitFor(3, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                p.destroyForcibly();
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /**
     * mesh 命令成功：服务名变绿加粗，并禁用该服务的 mesh 按钮
     */
    private void markMeshSuccess(String service) {
        // 已 stop 的服务不再变绿：避免终止后迟到的成功输出（异步回调）把服务名与按钮重新上色
        if (!serviceProcesses.containsKey(service)) {
            return;
        }
        meshSuccessServices.add(service);
        updateServiceRowStyle(service);
        updateMenuButton();  // 顶部按钮绿色加粗并显示个数
    }
}
