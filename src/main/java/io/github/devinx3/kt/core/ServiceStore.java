package io.github.devinx3.kt.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mesh 服务配置文件读写（服务名 → 端口 的 JSON），存放于 ~/.kt-gui/services.json
 */
public class ServiceStore {

    // Mesh 服务配置文件（服务名 → 端口 的 JSON）
    private static final String SERVICES_FILE = System.getProperty("user.home") + "/.ktgui/services.json";
    Map<String, String> services = null;
    /**
     * 从 JSON 文件加载服务列表（服务名 → 端口）
     */
    public Map<String, String> list() {
        if (this.services == null) {
            load();
        }
        return Collections.unmodifiableMap(this.services);
    }

    public Map<String, String> listForUpdate() {
        if (this.services == null) {
            load();
        }
        return new LinkedHashMap<>(this.services);
    }

    public Map<String, String> load() {
        this.services = loadFromFile();
        return Collections.unmodifiableMap(this.services);
    }

    private Map<String, String> loadFromFile() {
        Map<String, String> services = new LinkedHashMap<>();
        File f = new File(SERVICES_FILE);
        if (!f.exists()) {
            return services;
        }
        try {
            String content = Files.readString(f.toPath()).trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                String body = content.substring(1, content.length() - 1);
                for (String pair : body.split(",")) {
                    int colon = pair.indexOf(':');
                    if (colon > 0) {
                        String key = unquote(pair.substring(0, colon));
                        String value = unquote(pair.substring(colon + 1));
                        if (!key.isEmpty() && !value.isEmpty()) {
                            services.put(key, value);
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            // 读取失败时返回空列表
            System.err.println("load services error");
        }
        return services;
    }

    /**
     * 将服务列表写入 JSON 文件
     */
    public void save(Map<String, String> services) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : services.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":\"").append(escape(e.getValue())).append('"');
        }
        sb.append('}');
        try {
            File f = new File(SERVICES_FILE);
            File parent = f.getParentFile();
            if (parent != null) {
                parent.mkdirs(); // 目录不存在时自动创建
            }
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            System.err.println("save services error");
        }
        this.services = new LinkedHashMap<>(services);
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unquote(String s) {
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
