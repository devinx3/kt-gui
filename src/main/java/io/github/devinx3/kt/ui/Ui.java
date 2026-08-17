package io.github.devinx3.kt.ui;

import javafx.scene.control.Alert;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 通用 UI 工具类：时间戳、提示框、端口校验等
 */
public final class Ui {

    private Ui() {
    }

    /**
     * 当前时间戳，格式如 [2025-08-11 17:30:05]
     */
    public static String timestamp() {
        return new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]").format(new Date());
    }

    /**
     * 简单提示框
     */
    public static void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    /**
     * 校验端口是否为 0-65535 之间的整数（含两端）
     */
    public static boolean isValidPort(String port) {
        try {
            int p = Integer.parseInt(port);
            return p >= 0 && p <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
