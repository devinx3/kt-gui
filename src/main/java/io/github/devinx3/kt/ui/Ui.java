package io.github.devinx3.kt.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * UI 工具类：时间戳/提示框/端口校验。
 */
public final class Ui {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 所有 Alert 弹框的 owner（主窗口），由应用启动时注册 */
    private static Window primaryStage;

    private Ui() {
    }

    /** 注册主窗口，使所有 Alert 弹框继承其标题栏图标并居中 */
    public static void setPrimaryStage(Stage primaryStage) {
        Ui.primaryStage = primaryStage;
    }

    /** 给 Alert 设置 owner（已注册时），继承主窗口图标并作为模态子窗口显示 */
    public static void initOwner(Alert alert) {
        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }
    }

    public static void initOwner(Dialog<?> dialog) {
        if (primaryStage != null) {
            dialog.initOwner(primaryStage);
        }
    }

    /** [yyyy-MM-dd HH:mm:ss] */
    public static String timestamp() {
        return "[" + LocalDateTime.now().format(FORMATTER) + "]";
    }

    /** WARNING 弹框，标题"提示"，无 header */
    public static void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        initOwner(alert);
        alert.showAndWait();
    }

    /** 0-65535 整数（含两端），非数字返回 false */
    public static boolean isValidPort(String port) {
        if (port == null || port.isEmpty()) return false;
        try {
            int p = Integer.parseInt(port);
            return p >= 0 && p <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
