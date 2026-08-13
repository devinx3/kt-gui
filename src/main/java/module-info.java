/**
 * kt-gui：ktctl 桌面客户端。
 * 主类 KtApp 所在的根包导出用于启动器加载；JavaFX 通过反射实例化 Application 子类，需开放该包。
 */
module io.github.devinx3.kt {
    requires javafx.controls;
    requires com.sun.jna;
    requires com.sun.jna.platform;

    exports io.github.devinx3.kt;

    opens io.github.devinx3.kt to javafx.graphics;
}
