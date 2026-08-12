package io.github.devinx3.kt.ui;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.regex.Pattern;

/**
 * 控制台输出区：基于 ListView，支持按行着色（stderr 错误行淡红背景）与右键菜单
 */
public class ConsoleArea {

    // 错误级别日志标记（logrus 格式：ERR/FAT），用于 stderr 行淡红背景判定
    private static final Pattern ERROR_PATTERN = Pattern.compile("\\b(ERR|FAT)\\b", Pattern.CASE_INSENSITIVE);

    private final ListView<ConsoleLine> list = new ListView<>();

    public ConsoleArea() {
        init();
    }

    public ListView<ConsoleLine> getListView() {
        return list;
    }

    /**
     * 向控制台追加一行并滚动到底部（须在 UI 线程调用）
     */
    public void append(String text, boolean error) {
        appendLine(list, new ConsoleLine(text, error));
    }

    /**
     * 向指定控制台追加一行并滚动到底部（须在 UI 线程调用）
     */
    public static void appendLine(ListView<ConsoleLine> list, ConsoleLine line) {
        list.getItems().add(line);
        list.scrollTo(list.getItems().size() - 1);
    }

    /**
     * 判断是否为错误级别日志行（logrus 格式：ERR/FAT）
     */
    public static boolean isErrorLine(String line) {
        return ERROR_PATTERN.matcher(line).find();
    }

    private void init() {
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ConsoleLine item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.error ? "[ERR] " + item.text : item.text);
                    setStyle(item.error ? "-fx-background-color: #ffe3e3;" : "");
                }
            }
        });
        ContextMenu menu = new ContextMenu();
        MenuItem copyItem = new MenuItem("复制");
        copyItem.setOnAction(e -> copySelection());
        MenuItem selectAllItem = new MenuItem("全选");
        selectAllItem.setOnAction(e -> list.getSelectionModel().selectAll());
        MenuItem clearItem = new MenuItem("清空");
        clearItem.setOnAction(e -> list.getItems().clear());
        menu.getItems().addAll(copyItem, selectAllItem, clearItem);
        list.setContextMenu(menu);
    }

    /**
     * 复制控制台选中行到剪贴板
     */
    private void copySelection() {
        StringBuilder sb = new StringBuilder();
        for (ConsoleLine line : list.getSelectionModel().getSelectedItems()) {
            sb.append(line.text).append('\n');
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }
}
