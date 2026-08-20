package io.github.devinx3.kt.core;

import javafx.application.Platform;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 事件总线：FX 线程感知派发。
 * <p>
 * 派发规则：
 * - 已在 JavaFX Application Thread → 同步直接派发给订阅者；
 * - 否则 → Platform.runLater 异步派发。
 * <p>
 * 该规则保证：
 * 1. Started 事件由面板按钮点击处理器（FX 线程）内同步发布，先于 process.start() 生效；
 * 2. 后台线程发布的事件自动切回 FX 线程，订阅者可直接操作 UI。
 */
public class KtEventBus {

    private final ConcurrentHashMap<Class<? extends CommandEvent>, CopyOnWriteArrayList<Consumer<CommandEvent>>> subscribers
            = new ConcurrentHashMap<>();

    /**
     * 按事件类型注册订阅者；同一类型多个订阅者按注册顺序执行。
     */
    public void subscribe(Class<? extends CommandEvent> type, Consumer<CommandEvent> listener) {
        subscribers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * 取消订阅。
     */
    public void unsubscribe(Class<? extends CommandEvent> type, Consumer<CommandEvent> listener) {
        List<Consumer<CommandEvent>> list = subscribers.get(type);
        if (list != null) {
            list.remove(listener);
        }
    }

    /**
     * 发布事件。已在 FX 线程则同步派发，否则 Platform.runLater 异步派发。
     */
    public void publish(CommandEvent event) {
        if (Platform.isFxApplicationThread()) {
            dispatch(event);
        } else {
            Platform.runLater(() -> dispatch(event));
        }
    }

    private void dispatch(CommandEvent event) {
        // 按事件精确类型派发
        CopyOnWriteArrayList<Consumer<CommandEvent>> list = subscribers.get(event.getClass());
        if (list != null) {
            for (Consumer<CommandEvent> listener : list) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    System.err.println("KtEventBus subscriber error: " + e.getMessage());
                }
            }
        }
    }
}
