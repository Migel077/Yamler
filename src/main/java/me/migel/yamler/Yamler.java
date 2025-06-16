package me.migel.yamler;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Optional;

@SuppressWarnings("unchecked")
@UtilityClass
public class Yamler {

    @Getter
    private static JavaPlugin plugin;

    public static void init(JavaPlugin plugin) {
        Yamler.plugin = plugin;
    }

    private static final HashSet<YamlFile> INSTANCES = new HashSet<>();

    public static <YamlFileClass extends YamlFile> Optional<YamlFileClass> initialize(@NonNull Class<YamlFileClass> clazz) {
        YamlFileClass result;
        try {
            val constructor = clazz.getConstructors()[0];
            constructor.setAccessible(true);

            result = (YamlFileClass) constructor.newInstance();

            val previous = INSTANCES.stream().filter(yamlFile -> yamlFile.getName().equals(result.getName())).findAny();

            if (previous.isPresent()) {
                previous.get().reload();
                return Optional.of(((YamlFileClass) previous.get()));
            }

            result.loadFromFiles(plugin);

            INSTANCES.add(result);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        return Optional.of(result);
    }

    public static <YamlFileClass extends YamlFile> Optional<YamlFileClass> getConfig(@NonNull Class<YamlFileClass> clazz) {
        return INSTANCES.stream().filter(yamlFile -> yamlFile.getClass().equals(clazz)).map(yamlFile -> ((YamlFileClass) yamlFile)).findAny();
    }

    public <YamlFileClass extends YamlFile> Optional<YamlFileClass> getConfigOrInitialize(@NonNull Class<YamlFileClass> clazz) {
        return getConfig(clazz).or(() -> initialize(clazz));
    }

    public static <YamlFileClass extends YamlFile> Optional<YamlFileClass> reloadConfig(@NonNull Class<YamlFileClass> clazz) {
        val optional = getConfig(clazz);
        optional.ifPresent(yamlFileClass -> yamlFileClass.reload());
        return optional;
    }

}
