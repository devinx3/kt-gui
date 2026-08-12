package io.github.devinx3.kt.ui;

import io.github.devinx3.kt.core.CommandRunner;
import io.github.devinx3.kt.core.ServiceStore;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Mesh/Recover 面板：左侧服务列表（mesh/recover/stop 按钮）+ 右侧服务独立控制台
 */
public class MeshRecoverPanel implements MenuPanel {

    private final ToggleButton btn = new ToggleButton("Mesh/Recover");
    private final VBox meshRecoverServiceBox = new VBox(5);   // 面板左侧服务列表
    private final Label mrConsoleTitle = new Label("请选择服务");  // 右侧控制台标题（显示当前服务名）
    private final StackPane mrConsoleArea = new StackPane();       // 右侧控制台容器
    private final ConsoleArea meshArea = new ConsoleArea();        // 兜底控制台
    private final VBox pane = new VBox(5);
    private final ServiceStore store;

    private final Map<String, Process> serviceProcesses = new ConcurrentHashMap<>();  // 服务名 → 命令进程
    private final Map<String, String> serviceCommands = new ConcurrentHashMap<>();   // 服务名 → 当前执行的命令（mesh/recover）
    private final Map<String, HBox> serviceRows = new ConcurrentHashMap<>();         // 服务名 → 列表行（用于成功背景色）
    private final Map<String, Button[]> serviceMeshButtons = new ConcurrentHashMap<>(); // 服务名 → [mesh, recover] 按钮（mesh 成功时同步浅绿）
    private final Map<String, ConsoleArea> serviceConsoles = new ConcurrentHashMap<>(); // 服务名 → 独立控制台
    private final Set<String> meshSuccessServices = ConcurrentHashMap.newKeySet();  // mesh 成功的服务（行背景浅绿）
    private String selectedService;                      // 当前选中的服务

    public MeshRecoverPanel(ServiceStore store) {
        this.store = store;

        ScrollPane mrScroll = new ScrollPane(meshRecoverServiceBox);
        mrScroll.setPrefWidth(Ui.SERVICE_LIST_WIDTH);  // 与服务面板服务列表等宽
        mrScroll.setFitToWidth(true);
        VBox mrRight = new VBox(5, mrConsoleTitle, mrConsoleArea);
        VBox.setVgrow(mrConsoleArea, Priority.ALWAYS);
        HBox mrBody = new HBox(10, mrScroll, mrRight);
        HBox.setHgrow(mrRight, Priority.ALWAYS);
        Button mrRefreshBtn = new Button("刷新");
        mrRefreshBtn.setOnAction(e -> refreshMeshRecoverList());
        HBox mrTitle = new HBox(10, new Label("Mesh/Recover"), mrRefreshBtn);
        pane.getChildren().addAll(mrTitle, mrBody);
        VBox.setVgrow(mrBody, Priority.ALWAYS);
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
     * 刷新左侧服务列表（每行：服务名 + mesh/recover/stop 按钮）
     */
    public void refreshMeshRecoverServices() {
        meshRecoverServiceBox.getChildren().clear();
        serviceRows.clear();
        serviceMeshButtons.clear();
        serviceConsoles.clear();
        for (Map.Entry<String, String> e : store.load().entrySet()) {
            final String service = e.getKey();
            final String port = e.getValue();
            Button meshRunBtn = new Button("mesh");
            Button recoverRunBtn = new Button("recover");
            Button stopRunBtn = new Button("stop");
            meshRunBtn.setOnAction(ev -> startServiceCommand(service, port, "mesh"));
            recoverRunBtn.setOnAction(ev -> startServiceCommand(service, port, "recover"));
            stopRunBtn.setOnAction(ev -> stopService(service));
            // 服务行不展示端口（mesh 命令仍使用端口参数）；按钮组右对齐
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(5, new Label(service), spacer, meshRunBtn, recoverRunBtn, stopRunBtn);
            serviceRows.put(service, row);
            serviceMeshButtons.put(service, new Button[]{meshRunBtn, recoverRunBtn});
            // 每个服务独立的控制台
            ConsoleArea console = new ConsoleArea();
            serviceConsoles.put(service, console);
            // 点击服务行任意位置（含按钮）切换右侧控制台
            row.addEventFilter(MouseEvent.MOUSE_CLICKED, ev -> selectService(service));
            meshRecoverServiceBox.getChildren().add(row);
        }
        // 列表重建后恢复 mesh 成功服务的浅绿底色
        for (String s : meshSuccessServices) {
            updateServiceRowStyle(s);
        }
    }

    /**
     * 刷新 Mesh/Recover 服务列表：无服务 mesh 运行中（绿色）时重新加载，否则提示
     */
    private void refreshMeshRecoverList() {
        if (meshSuccessServices.isEmpty()) {
            refreshMeshRecoverServices();
        } else {
            Ui.showAlert("以下服务 mesh 正在运行中，请先执行 stop 后再刷新：\n" + String.join(", ", meshSuccessServices));
        }
    }

    /**
     * 服务被删除后的联动：清除选中状态并刷新列表（由 ServicePanel 删除时回调）
     */
    public void onServiceDeleted(String name) {
        // 删除的是当前选中服务时，清除选中状态与右侧控制台
        if (name.equals(selectedService)) {
            selectedService = null;
            mrConsoleTitle.setText("请选择服务");
            mrConsoleArea.getChildren().clear();
        }
        refreshMeshRecoverServices();
    }

    /**
     * 切换右侧控制台：标题显示服务名，内容为该服务独立控制台
     */
    private void selectService(String service) {
        selectedService = service;
        mrConsoleTitle.setText(service);
        mrConsoleArea.getChildren().clear();
        ConsoleArea console = serviceConsoles.get(service);
        if (console != null) {
            mrConsoleArea.getChildren().add(console.getListView());
        }
    }

    /**
     * 更新服务行样式：mesh 成功浅绿 > 默认（不再使用选中浅蓝）
     */
    private void updateServiceRowStyle(String service) {
        HBox row = serviceRows.get(service);
        if (row == null) {
            return;
        }
        if (meshSuccessServices.contains(service)) {
            row.setStyle("-fx-background-color: #d4edda;");  // mesh 成功浅绿
        } else {
            row.setStyle("");
        }
        // mesh/recover 按钮背景与行背景同步：mesh 成功时按钮同样为浅绿
        Button[] buttons = serviceMeshButtons.get(service);
        if (buttons != null) {
            for (Button b : buttons) {
                b.setStyle(meshSuccessServices.contains(service) ? "-fx-background-color: #d4edda;" : "");
            }
        }
    }

    /**
     * 获取服务的控制台（不存在时兜底用 meshArea）
     */
    private ConsoleArea serviceConsole(String service) {
        ConsoleArea c = serviceConsoles.get(service);
        return c != null ? c : meshArea;
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
                        Ui.timestamp() + " > 执行: ktctl " + String.join(" ", command), false));
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), Charset.forName(charsetName)));
                     BufferedReader errorReader = new BufferedReader(
                             new InputStreamReader(process.getErrorStream(), Charset.forName(charsetName)))) {
                    // mesh 命令成功（出现 "Now you can access your service by header"）时服务行变浅绿
                    String keyword = "mesh".equals(cmd) ? "Now you can access your service by header" : null;
                    Consumer<String> onSuccess = "mesh".equals(cmd) ? line -> markMeshSuccess(service) : null;
                    Thread outT = new Thread(() -> CommandRunner.readOutput(reader, serviceConsole(service).getListView(), false, null, keyword, onSuccess));
                    Thread errT = new Thread(() -> CommandRunner.readOutput(errorReader, serviceConsole(service).getListView(), true, null, keyword, onSuccess));
                    outT.start();
                    errT.start();
                    int exitCode = process.waitFor();
                    outT.join();
                    errT.join();
                    Platform.runLater(() -> serviceConsole(service).append(
                            Ui.timestamp() + " " + cmd + " " + service + " 结束，退出码: " + exitCode, exitCode != 0));
                }
            } catch (Exception e) {
                Platform.runLater(() -> serviceConsole(service).append(
                        Ui.timestamp() + " " + cmd + " " + service + " 启动失败: " + e.getMessage(), true));
            } finally {
                serviceProcesses.remove(service);
                serviceCommands.remove(service);
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
        // 立即从运行表移除并清除 mesh 成功状态：迟到的成功回调不再变绿，行与按钮恢复默认色
        serviceProcesses.remove(service);
        meshSuccessServices.remove(service);
        updateServiceRowStyle(service);
        p.destroy();
        Thread t = new Thread(() -> {
            try {
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
     * mesh 命令成功：服务行背景变为浅绿色
     */
    private void markMeshSuccess(String service) {
        // 已 stop 的服务不再变绿：避免终止后迟到的成功输出（异步回调）把行与按钮重新上色
        if (!serviceProcesses.containsKey(service)) {
            return;
        }
        meshSuccessServices.add(service);
        updateServiceRowStyle(service);
    }

    /**
     * mesh 运行中的服务集合（退出确认用）
     */
    public Set<String> getActiveMeshServices() {
        return meshSuccessServices;
    }

    /**
     * 更新 Mesh/Recover 按钮的可用状态：不依赖任何操作，始终可用。
     */
    public void updateMeshRecoverState() {
        btn.setDisable(false);
    }
}
