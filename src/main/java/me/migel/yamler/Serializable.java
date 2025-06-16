package me.migel.yamler;

import org.bukkit.configuration.ConfigurationSection;

public interface Serializable<T extends Serializable<?>> {

    void serializeInto(ConfigurationSection section);

    T getObject(ConfigurationSection section);

}
