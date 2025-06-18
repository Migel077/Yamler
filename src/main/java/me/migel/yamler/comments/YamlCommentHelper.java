package me.migel.yamler.comments;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ChatGPT 4.1
 */
public class YamlCommentHelper {

    private final Map<String, List<String>> commentMap = new HashMap<>();

    public void readComments(File yamlFile) throws IOException {
        commentMap.clear();
        List<String> pathStack = new ArrayList<>();
        List<String> buffer = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(yamlFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int indent = countLeadingSpaces(line);
                String trimmed = line.trim();
                if (trimmed.startsWith("#")) {
                    buffer.add(trimmed.substring(1).trim());
                    continue;
                }
                if (!trimmed.contains(":")) {
                    continue;
                }

                // Вычисляем YAML path (типа key1.key2.key3)
                String key = trimmed.substring(0, trimmed.indexOf(":")).replaceAll("[\"']", "");
                while (!pathStack.isEmpty() && indent <= getIndentOfStack(pathStack)) {
                    pathStack.remove(pathStack.size() - 1);
                }
                pathStack.add(key);
                String path = String.join(".", pathStack);

                if (!buffer.isEmpty()) {
                    commentMap.put(path, new ArrayList<>(buffer));
                    buffer.clear();
                }

            }
        }
    }

    // Получить комментарии для любого path (key1.key2.key3)
    public List<String> getComments(String path) {
        return commentMap.getOrDefault(path, List.of());
    }

    // Установить/обновить комментарии для любого path
    public void setComments(String path, List<String> comments) {
        if (comments == null || comments.isEmpty()) {
            commentMap.remove(path);
        } else {
            commentMap.put(path, new ArrayList<>(comments));
        }
    }

    // Сохранить YAML-файл с комментариями (работает только с плоскими конфигами или своей сериализацией!)
    // Для Spigot YamlConfiguration.save(...) комментарии НЕ сохранятся. Нужно делать свой save.
    public void saveWithComments(File file, Map<String, Object> data) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String path = entry.getKey();
                List<String> comm = commentMap.get(path);
                if (comm != null) {
                    for (String c : comm) {
                        writer.println("# " + c);
                    }
                }
                writer.println(path + ": " + entry.getValue());
            }
        }
    }

    public void saveWithComments(File file, FileConfiguration config) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writeSection(writer, config, "", 0);
        }
    }

    private void writeSection(PrintWriter writer, ConfigurationSection section, String pathPrefix, int indent) {
        String indentStr = "  ".repeat(indent);
        for (String key : section.getKeys(false)) {
            String fullPath = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
            List<String> comments = getComments(fullPath);
            if (comments != null && !comments.isEmpty()) {
                for (String comment : comments) {
                    writer.println(indentStr + "# " + comment);
                }
            }
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) {
                writer.println(indentStr + key + ":");
                writeSection(writer, (ConfigurationSection) value, fullPath, indent + 1);
            } else if (value instanceof List<?> list) {
                writer.println(indentStr + key + ":");
                for (Object item : list) {
                    String itemStr = nonNullString(String.valueOf(item));
                    if (itemStr.contains(":") || itemStr.contains("#") || itemStr.contains("\"") || itemStr.contains("{") || itemStr.contains("}")) {
                        writer.println(indentStr + "  - \"" + itemStr.replace("\"", "\\\"") + "\"");
                    } else {
                        writer.println(indentStr + "  - " + itemStr);
                    }
                }
            } else {
                // Экранируем строки если нужно
                if (value instanceof String strVal && (strVal.contains(":") || strVal.contains("#") || strVal.contains("\"") || strVal.contains("{") || strVal.contains("}"))) {
                    writer.println(indentStr + key + ": \"" + strVal.replace("\"", "\\\"") + "\"");
                } else {
                    writer.println(indentStr + key + ": " + value);
                }
            }

        }
    }

    public String nonNullString(String toBeNonNull) {
        if (toBeNonNull == null || toBeNonNull.isBlank()) return "\"\"";
        else return toBeNonNull;
    }

    // Вспомогательные
    private int countLeadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') count++;
        return count;
    }
    private int getIndentOfStack(List<String> stack) {
        // "root.child.child" = 2*2 = 4 пробела (наивно)
        return (stack.size() - 1) * 2;
    }
}
