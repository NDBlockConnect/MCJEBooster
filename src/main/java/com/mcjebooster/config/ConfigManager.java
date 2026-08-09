/*
 * MCJEBooster - Minecraft Java Edition Multi-Core Optimization Engine
 * Copyright (C) 2026 StarsailsClover
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 */

package com.mcjebooster.config;

import com.mcjebooster.util.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Configuration Manager for MCJEBooster.
 * 
 * Supports YAML configuration files with hot-reload capability.
 * Provides preset templates for different performance profiles.
 * 
 * Configuration locations (in priority order):
 * 1. ./config/mcjebooster.yml
 * 2. ~/.mcjebooster/config.yml
 * 3. /etc/mcjebooster/config.yml (Linux/Mac)
 * 4. Embedded default configuration
 * 
 * @author StarsailsClover
 * @version 26.7-20260726
 */
public class ConfigManager {
    
    /** Singleton instance */
    private static volatile ConfigManager INSTANCE;
    
    /** Current configuration */
    private volatile Config currentConfig;
    
    /** Configuration file path */
    private Path configPath;
    
    /** Configuration file watcher */
    private WatchService watchService;
    
    /** Last modification time */
    private final AtomicLong lastModified = new AtomicLong(0);
    
    /** Configuration change listeners */
    private final List<ConfigChangeListener> listeners = new ArrayList<>();
    
    /** Default configuration values */
    private static final Map<String, Object> DEFAULTS = new ConcurrentHashMap<>();
    
    static {
        // Performance settings
        DEFAULTS.put("performance.workerCount", Runtime.getRuntime().availableProcessors() - 1);
        DEFAULTS.put("performance.regionSize", 16);
        DEFAULTS.put("performance.tickTimeout", 45);
        DEFAULTS.put("performance.rebalanceInterval", 100);
        
        // Monitoring settings
        DEFAULTS.put("monitoring.enabled", true);
        DEFAULTS.put("monitoring.statsInterval", 1200);
        DEFAULTS.put("monitoring.snapshotInterval", 20);
        DEFAULTS.put("monitoring.hotspotInterval", 10);
        DEFAULTS.put("monitoring.jmxEnabled", false);
        
        // Health check settings
        DEFAULTS.put("health.minTPS", 5.0);
        DEFAULTS.put("health.maxConsecutiveFailures", 5);
        DEFAULTS.put("health.deadlockCheck", true);
        DEFAULTS.put("health.autoRollback", true);
        
        // Logging settings
        DEFAULTS.put("logging.level", "INFO");
        DEFAULTS.put("logging.file", "logs/mcjebooster.log");
        DEFAULTS.put("logging.maxFileSize", "10MB");
        DEFAULTS.put("logging.maxFiles", 5);
        
        // Hotspot tasks
        DEFAULTS.put("hotspot.entityDensity.enabled", true);
        DEFAULTS.put("hotspot.chunkActivity.enabled", true);
        DEFAULTS.put("hotspot.redstoneCircuit.enabled", false);
        DEFAULTS.put("hotspot.fluidSimulation.enabled", false);
        
        // Experimental features
        DEFAULTS.put("experimental.parallelTick", false);
        DEFAULTS.put("experimental.mlLoadPrediction", false);
        DEFAULTS.put("experimental.adaptiveRegions", false);
    }
    
    /**
     * Configuration change listener interface
     */
    public interface ConfigChangeListener {
        void onConfigChanged(Config oldConfig, Config newConfig);
    }
    
    /**
     * Private constructor
     */
    private ConfigManager() {
    }
    
    /**
     * Gets the singleton instance
     */
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
    
    /**
     * Initializes the configuration manager
     */
    public void initialize() {
        // Find configuration file
        configPath = findConfigFile();
        
        if (configPath != null && Files.exists(configPath)) {
            Logger.info("Loading configuration from: " + configPath);
            loadConfig();
        } else {
            Logger.info("No configuration file found, using defaults");
            currentConfig = createDefaultConfig();
        }
        
        // Start file watcher for hot-reload
        startFileWatcher();
        
        Logger.info("ConfigManager initialized");
    }
    
    /**
     * Finds the configuration file in standard locations
     */
    private Path findConfigFile() {
        // 1. ./config/mcjebooster.yml
        Path path = Paths.get("config", "mcjebooster.yml");
        if (Files.exists(path)) {
            return path;
        }
        
        // 2. ~/.mcjebooster/config.yml
        path = Paths.get(System.getProperty("user.home"), ".mcjebooster", "config.yml");
        if (Files.exists(path)) {
            return path;
        }
        
        // 3. /etc/mcjebooster/config.yml (Linux/Mac)
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            path = Paths.get("/etc", "mcjebooster", "config.yml");
            if (Files.exists(path)) {
                return path;
            }
        }
        
        return null;
    }
    
    /**
     * Loads configuration from file
     */
    private void loadConfig() {
        try {
            Config newConfig = parseConfigFile(configPath);
            Config oldConfig = currentConfig;
            currentConfig = newConfig;
            lastModified.set(System.currentTimeMillis());
            
            // Notify listeners
            notifyListeners(oldConfig, newConfig);
            
            Logger.info("Configuration loaded successfully");
        } catch (Exception e) {
            Logger.error("Failed to load configuration: " + e.getMessage());
            if (currentConfig == null) {
                currentConfig = createDefaultConfig();
            }
        }
    }
    
    /**
     * Parses a YAML configuration file
     * 
     * Note: This is a simple parser. For production, consider using SnakeYAML library.
     */
    private Config parseConfigFile(Path path) throws IOException {
        Map<String, Object> values = new HashMap<>(DEFAULTS);
        
        List<String> lines = Files.readAllLines(path);
        String currentSection = "";
        
        for (String line : lines) {
            line = line.trim();
            
            // Skip comments and empty lines
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            // Section header
            if (line.endsWith(":") && !line.contains(" ")) {
                currentSection = line.substring(0, line.length() - 1);
                continue;
            }
            
            // Key-value pair
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                String key = parts[0].trim();
                String value = parts[1].trim();
                
                String fullKey = currentSection.isEmpty() ? key : currentSection + "." + key;
                values.put(fullKey, parseValue(value));
            }
        }
        
        return new Config(values);
    }
    
    /**
     * Parses a configuration value
     */
    private Object parseValue(String value) {
        // Remove quotes
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        if (value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }
        
        // Boolean
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        
        // Number
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            // Not a number, return as string
        }
        
        return value;
    }
    
    /**
     * Creates default configuration
     */
    private Config createDefaultConfig() {
        return new Config(new HashMap<>(DEFAULTS));
    }
    
    /**
     * Starts file watcher for hot-reload
     */
    private void startFileWatcher() {
        if (configPath == null || !Files.exists(configPath)) {
            return;
        }
        
        Thread watcherThread = new Thread(() -> {
            try {
                watchService = FileSystems.getDefault().newWatchService();
                Path dir = configPath.getParent();
                dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
                
                Logger.info("Configuration file watcher started");
                
                while (true) {
                    WatchKey key = watchService.take();
                    
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changed = (Path) event.context();
                        if (changed.equals(configPath.getFileName())) {
                            // Wait a bit to ensure file write is complete
                            Thread.sleep(500);
                            
                            Logger.info("Configuration file changed, reloading...");
                            loadConfig();
                        }
                    }
                    
                    key.reset();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Logger.error("File watcher error: " + e.getMessage());
            }
        }, "MCJEBooster-ConfigWatcher");
        
        watcherThread.setDaemon(true);
        watcherThread.start();
    }
    
    /**
     * Gets the current configuration
     */
    public Config getConfig() {
        if (currentConfig == null) {
            initialize();
        }
        return currentConfig;
    }
    
    /**
     * Reloads configuration from file
     */
    public void reload() {
        loadConfig();
    }
    
    /**
     * Registers a configuration change listener
     */
    public void addListener(ConfigChangeListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Notifies all listeners of configuration change
     */
    private void notifyListeners(Config oldConfig, Config newConfig) {
        for (ConfigChangeListener listener : listeners) {
            try {
                listener.onConfigChanged(oldConfig, newConfig);
            } catch (Exception e) {
                Logger.error("Error notifying config listener: " + e.getMessage());
            }
        }
    }
    
    /**
     * Saves current configuration to file
     */
    public void saveConfig() throws IOException {
        if (configPath == null) {
            configPath = Paths.get("config", "mcjebooster.yml");
        }
        
        Files.createDirectories(configPath.getParent());
        
        try (BufferedWriter writer = Files.newBufferedWriter(configPath)) {
            writer.write("# MCJEBooster Configuration File\n");
            writer.write("# Version: 26.7-20260726\n");
            writer.write("# Generated: " + new Date() + "\n\n");
            
            writeSection(writer, "performance", currentConfig.values);
            writeSection(writer, "monitoring", currentConfig.values);
            writeSection(writer, "health", currentConfig.values);
            writeSection(writer, "logging", currentConfig.values);
            writeSection(writer, "hotspot", currentConfig.values);
            writeSection(writer, "experimental", currentConfig.values);
        }
        
        Logger.info("Configuration saved to: " + configPath);
    }
    
    /**
     * Writes a configuration section
     */
    private void writeSection(BufferedWriter writer, String section, Map<String, Object> values) throws IOException {
        writer.write(section + ":\n");
        
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getKey().startsWith(section + ".")) {
                String key = entry.getKey().substring(section.length() + 1);
                Object value = entry.getValue();
                
                if (value instanceof String) {
                    writer.write("  " + key + ": \"" + value + "\"\n");
                } else {
                    writer.write("  " + key + ": " + value + "\n");
                }
            }
        }
        
        writer.write("\n");
    }
    
    /**
     * Shuts down the configuration manager
     */
    public void shutdown() {
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            Logger.error("Error closing watch service: " + e.getMessage());
        }
    }
    
    /**
     * Configuration holder class
     */
    public static class Config {
        private final Map<String, Object> values;
        
        public Config(Map<String, Object> values) {
            this.values = new ConcurrentHashMap<>(values);
        }
        
        public int getInt(String key) {
            Object value = values.get(key);
            if (value instanceof Integer) {
                return (Integer) value;
            }
            if (value instanceof Double) {
                return ((Double) value).intValue();
            }
            return (Integer) DEFAULTS.getOrDefault(key, 0);
        }
        
        public double getDouble(String key) {
            Object value = values.get(key);
            if (value instanceof Double) {
                return (Double) value;
            }
            if (value instanceof Integer) {
                return ((Integer) value).doubleValue();
            }
            return (Double) DEFAULTS.getOrDefault(key, 0.0);
        }
        
        public boolean getBoolean(String key) {
            Object value = values.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            return (Boolean) DEFAULTS.getOrDefault(key, false);
        }
        
        public String getString(String key) {
            Object value = values.get(key);
            if (value != null) {
                return value.toString();
            }
            return (String) DEFAULTS.getOrDefault(key, "");
        }
        
        public <T> T get(String key, Class<T> type) {
            Object value = values.get(key);
            if (value != null && type.isInstance(value)) {
                return type.cast(value);
            }
            return type.cast(DEFAULTS.get(key));
        }
        
        public void set(String key, Object value) {
            values.put(key, value);
        }
        
        public Map<String, Object> getAll() {
            return new HashMap<>(values);
        }
    }
    
    /**
     * Configuration presets
     */
    public enum Preset {
        LOW_LATENCY("Low Latency - Optimized for responsive gameplay"),
        HIGH_THROUGHPUT("High Throughput - Maximum TPS at cost of latency"),
        BALANCED("Balanced - Good mix of latency and throughput"),
        CONSERVATIVE("Conservative - Minimal risk, gradual optimization");
        
        private final String description;
        
        Preset(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        public Config createConfig() {
            Map<String, Object> values = new HashMap<>(DEFAULTS);
            
            switch (this) {
                case LOW_LATENCY:
                    values.put("performance.workerCount", Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
                    values.put("performance.regionSize", 8);
                    values.put("performance.tickTimeout", 30);
                    values.put("performance.rebalanceInterval", 50);
                    break;
                    
                case HIGH_THROUGHPUT:
                    values.put("performance.workerCount", Runtime.getRuntime().availableProcessors());
                    values.put("performance.regionSize", 32);
                    values.put("performance.tickTimeout", 60);
                    values.put("performance.rebalanceInterval", 200);
                    break;
                    
                case BALANCED:
                    // Use defaults
                    break;
                    
                case CONSERVATIVE:
                    values.put("performance.workerCount", Math.max(1, Runtime.getRuntime().availableProcessors() / 4));
                    values.put("performance.regionSize", 16);
                    values.put("performance.tickTimeout", 40);
                    values.put("performance.rebalanceInterval", 100);
                    values.put("health.autoRollback", true);
                    values.put("experimental.parallelTick", false);
                    break;
            }
            
            return new Config(values);
        }
    }
}
