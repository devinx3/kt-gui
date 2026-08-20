package io.github.devinx3.kt.ui;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.paint.Color;

import java.util.regex.Pattern;

/**
 * 基于 ListView&lt;ConsoleLine&gt; 的控制台控件。
 */
public class ConsoleArea {

    /** 错误行判定正则：\b(ERR|FAT)\b（CASE_INSENSITIVE，logrus 级别格式） */
    private static final Pattern ERROR_PATTERN = Pattern.compile("\\b(ERR|FAT)\\b", Pattern.CASE_INSENSITIVE);

    private final ListView<ConsoleLine> listView;

    public ConsoleArea() {
        this.listView = new ListView<>();
        setupCellFactory();
        setupContextMenu();
        setupAutoScroll();
    }

    public ListView<ConsoleLine> getListView() {
        return listView;
    }

    /** 追加到自身 list（UI 线程） */
    public void append(String text, boolean error) {
        appendLine(listView, new ConsoleLine(text, error));
    }

    /** 追加+滚动到底部（必须 UI 线程调用） */
    public static void appendLine(ListView<ConsoleLine> list, ConsoleLine line) {
        list.getItems().add(line);
        int size = list.getItems().size();
        if (size > 0) {
            list.scrollTo(size - 1);
        }
    }

    /** 内容变化后自动滚动到底部（双重延迟等布局完成，并显式对齐垂直滚动条） */
    private void setupAutoScroll() {
        listView.getItems().addListener((ListChangeListener<ConsoleLine>) change -> {
            // 双重 runLater：确保 ListView 完成布局（cell 高度已知）后再滚动
            Platform.runLater(() -> Platform.runLater(this::scrollToBottom));
        });
    }

    /** 滚动到最底部：scrollTo 最后一项 + 显式把垂直滚动条拉到最大值 */
    private void scrollToBottom() {
        int size = listView.getItems().size();
        if (size == 0) {
            return;
        }
        listView.scrollTo(size - 1);
        for (Node node : listView.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL) {
                bar.setValue(bar.getMax());
            }
        }
    }

    /** 错误级别判定 */
    public static boolean isErrorLine(String line) {
        return line != null && ERROR_PATTERN.matcher(line).find();
    }

    private void setupCellFactory() {
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ConsoleLine item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle(null);
                    setGraphic(null);
                } else {
                    if (item.error) {
                        setText("[ERR] " + item.text);
                        setStyle("-fx-background-color: #ffe3e3;");
                    } else {
                        setText(item.text);
                        setStyle(null);
                    }
                }
            }
        });
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("复制");
        copyItem.setOnAction(e -> {
            StringBuilder sb = new StringBuilder();
            for (ConsoleLine line : listView.getSelectionModel().getSelectedItems()) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append(line.text);
            }
            ClipboardContent content = new ClipboardContent();
            content.putString(sb.toString());
            Clipboard.getSystemClipboard().setContent(content);
        });

        MenuItem selectAllItem = new MenuItem("全选");
        selectAllItem.setOnAction(e -> listView.getSelectionModel().selectAll());

        MenuItem clearItem = new MenuItem("清空");
        clearItem.setOnAction(e -> listView.getItems().clear());

        contextMenu.getItems().addAll(copyItem, selectAllItem, clearItem);
        listView.setContextMenu(contextMenu);
    }
}
