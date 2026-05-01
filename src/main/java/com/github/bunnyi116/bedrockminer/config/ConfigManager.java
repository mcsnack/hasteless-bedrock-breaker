package com.github.bunnyi116.bedrockminer.config;

import com.github.bunnyi116.bedrockminer.Debug;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.*;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.MOD_ID;

public class ConfigManager {
    private static volatile @Nullable ConfigManager INSTANCE;

    public static final File CONFIG_DIR = FabricLoader.getInstance().getConfigDir().toFile();
    public static final File MOD_FILE = new File(CONFIG_DIR, MOD_ID + ".json");

    private volatile @Nullable Config CONFIG;

    public Config loadConfig() {
        Config config = load(MOD_FILE, Config.class);
        if (config == null) {
            config = new Config();
        }
        CONFIG = config;
        return config;
    }

    public void saveConfig() {
        save(MOD_FILE, getConfig());
    }

    public static <T> @Nullable T load(File file, Class<T> clazz) {
        T config = null;
        Gson gson = new Gson();
        try (Reader reader = new FileReader(file)) {
            config = gson.fromJson(reader, clazz);
            Debug.alwaysWrite("Config file loaded successfully");
        } catch (Exception e) {
            if (file.exists()) {
                if (file.delete()) {
                    Debug.alwaysWrite("Failed to load config; deleted the config file");
                } else {
                    Debug.alwaysWrite("Failed to load config; could not delete the config file");
                }
            } else {
                Debug.alwaysWrite("Config file not found");
            }
        }

        try {
            if (config == null) {
                Debug.alwaysWrite("Using default config");
                config = clazz.getDeclaredConstructor().newInstance(); // Create instance via reflection
            }
            save(file, config);
            return config;
        } catch (Exception e) {
            Debug.alwaysWrite("Failed to create default config");
            return null;
        }
    }

    public static <T> void save(File file, T instance) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(instance, writer);
        } catch (IOException e) {
            Debug.alwaysWrite("Failed to save config file");
            e.printStackTrace();
        }
    }

    public Config getConfig() {
        if (CONFIG == null) {
            synchronized (ConfigManager.class) {
                if (CONFIG == null) {
                    Config config = load(MOD_FILE, Config.class);
                    if (config != null) {
                        CONFIG = config;
                    }
                }
            }
        }
        if (CONFIG == null) {
            CONFIG = new Config();
        }
        return CONFIG;
    }

    public static ConfigManager getInstance() {
        if (INSTANCE == null) {
            synchronized (ConfigManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ConfigManager();
                }
            }
        }
        return INSTANCE;
    }
}
