package me.migel.yamler;

import com.google.errorprone.annotations.ForOverride;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;
import me.migel.yamler.comments.YamlCommentHelper;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public abstract class YamlFile {

    @NonNull @Getter
    protected final String name;

    @Nullable
    private File yamlFile;

    @Nullable
    private FileConfiguration configuration;

    private final YamlCommentHelper COMMENT_HELPER = new YamlCommentHelper();

    public YamlFile(@NonNull String name) {
        this.name = name;
    }

    public void reload() {
        loadFromFiles(Yamler.getPlugin());
    }


    public void save() {
        Arrays.stream(this.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Field.class))
                .forEach(field -> {
                    val annotation = field.getAnnotation(Field.class);
                    field.setAccessible(true);

                    try {
                        val name = (annotation.customName().isBlank() ? field.getName() : annotation.customName()).replace("_", "-");
                        val path = annotation.prefix() + (annotation.prefix().endsWith(".") ? "" : ".") + name;

                        val o = field.get(this);

                        if (o instanceof Serializable<?> serializable) {
                            serializable.serializeInto(configuration.createSection(path));
                        } else {
                            configuration.set(path, o);
                        }

                        val comments = Arrays.stream(annotation.comment().split("\\n")).toList();

                        if (!annotation.comment().isBlank() || !(comments.size() == 1 && comments.get(0).isBlank())){
                            if (!comments.equals(COMMENT_HELPER.getComments(path))) {
                                COMMENT_HELPER.setComments(path, comments);
                            }
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
        try {
            COMMENT_HELPER.saveWithComments(yamlFile, configuration);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void loadFromFiles(@NonNull JavaPlugin plugin) {
        yamlFile = new File(plugin.getDataFolder(), name);
        configuration = new YamlConfiguration();
        try {
            if (!yamlFile.exists()){
                yamlFile.getParentFile().mkdirs();
                onFirstLoad();
                save();
                return;
            }
            configuration.load(yamlFile);
            COMMENT_HELPER.readComments(yamlFile);
            Arrays.stream(this.getClass().getDeclaredFields()).filter(field -> field.isAnnotationPresent(Field.class))
                    .forEach(field -> {
                        val annotation = field.getAnnotation(Field.class);
                        val name = (annotation.customName().isBlank() ? field.getName() : annotation.customName()).replace("_", "-");
                        val path = annotation.prefix() + (annotation.prefix().endsWith(".") ? "" : ".") + name;
                        val value = Optional.ofNullable(configuration.get(path));
                        try {
                            field.setAccessible(true);
                            if (value.isPresent()) {
                                if (Serializable.class.isAssignableFrom(field.getType())) {
                                    ConfigurationSection section = configuration.getConfigurationSection(path);
                                    if (section != null) {
                                        Object serializableObject = ((Serializable<?>) field.get(this)).getObject(section);
                                        field.set(this, serializableObject);
                                    }
                                } else if (value.get() instanceof ConfigurationSection section) {
                                    field.set(this, readConfigurationSection(section));
                                } else {
                                    if (field.getType().equals(String.class) && value.get() instanceof Number number) {
                                        field.set(this, String.valueOf(number));
                                    } else field.set(this, value.get());
                                }
                            } else {
                                configuration.set(path, field.get(this));
                            }
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    });
            onEndLoadFields();
            save();
        } catch (IOException | InvalidConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<Object, Object> readConfigurationSection(@NonNull ConfigurationSection section) {
        HashMap<Object, Object> data_result = new HashMap<>();
        section.getValues(false).forEach((s, o) -> {
           if (o instanceof MemorySection memorySection) {
               data_result.put(s, readConfigurationSection(memorySection));
           } else data_result.put(s, o);
        });
        return data_result;
    }

    public void setComments(@NonNull String path, @NonNull List<String> comments) {
        COMMENT_HELPER.setComments(path, comments);
    }

    @ForOverride
    protected void onFirstLoad() {}
    @ForOverride
    protected void onEndLoadFields() {}

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YamlFile yamlFile)) return false;

        return name.equals(yamlFile.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
