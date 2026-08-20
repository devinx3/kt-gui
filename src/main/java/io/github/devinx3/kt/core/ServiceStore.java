package io.github.devinx3.kt.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务列表 JSON 持久化。
 * 文件路径：~/.ktgui/services.json
 * 格式：{"服务名":"端口", ...}
 */
public class ServiceStore {

    private static final Path STORE_PATH = Paths.get(
            System.getProperty("user.home"), ".ktgui", "services.json");

    private Map<String, String> services; // 内存缓存，懒加载

    public ServiceStore() {
    }

    /** 懒加载，返回不可修改视图 */
    public Map<String, String> list() {
        if (services == null) {
            services = loadFromFile();
        }
        return Collections.unmodifiableMap(services);
    }

    /** 懒加载，返回 LinkedHashMap 副本（供增删改） */
    public Map<String, String> listForUpdate() {
        if (services == null) {
            services = loadFromFile();
        }
        return new LinkedHashMap<>(services);
    }

    /** 强制从文件重新加载 */
    public Map<String, String> load() {
        services = loadFromFile();
        return Collections.unmodifiableMap(services);
    }

    /** 写盘并更新内存缓存 */
    public void save(Map<String, String> services) {
        try {
            Path parent = STORE_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = mapToJson(services);
            Files.write(STORE_PATH, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            this.services = new LinkedHashMap<>(services);
        } catch (IOException e) {
            System.err.println("save services error");
        }
    }

    // ---- 内部方法 ----

    private Map<String, String> loadFromFile() {
        if (!Files.exists(STORE_PATH)) {
            return new LinkedHashMap<>();
        }
        try {
            String content = Files.readString(STORE_PATH, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (content.isEmpty() || !content.startsWith("{") || !content.endsWith("}")) {
                return new LinkedHashMap<>();
            }
            // 去掉首尾花括号
            content = content.substring(1, content.length() - 1).trim();
            if (content.isEmpty()) {
                return new LinkedHashMap<>();
            }

            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            // 按逗号拆分键值对（简单解析，不支持值中含逗号）
            String[] pairs = content.split(",");
            for (String pair : pairs) {
                pair = pair.trim();
                if (pair.isEmpty()) continue;
                // 按首个冒号拆分
                int colonIdx = pair.indexOf(':');
                if (colonIdx <= 0) continue;
                String key = unquote(pair.substring(0, colonIdx).trim());
                String value = unquote(pair.substring(colonIdx + 1).trim());
                if (!key.isEmpty()) {
                    result.put(key, value);
                }
            }
            return result;
        } catch (IOException e) {
            System.err.println("load services error");
            return new LinkedHashMap<>();
        }
    }

    private String mapToJson(Map<String, String> map) {
        if (map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /** 转义：\ → \\，" → \" */
    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 剥掉首尾引号并反向还原 */
    private String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
