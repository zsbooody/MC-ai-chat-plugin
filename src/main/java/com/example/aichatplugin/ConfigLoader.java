package com.example.aichatplugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import org.bukkit.Material;

/**
 * 配置加载器
 * 
 * 职责：
 * 1. 集中管理所有插件配置
 * 2. 提供类型安全的配置访问
 * 3. 处理配置文件的加载和保存
 * 4. 维护配置的一致性
 * 
 * 配置分类：
 * 1. settings: API和模型相关配置
 * 2. chat: 聊天功能配置
 * 3. messages: 消息格式配置
 * 4. environment: 环境信息配置
 * 5. player-status: 玩家状态响应配置
 * 6. debug: 调试相关配置
 * 7. conversation: 对话管理配置
 * 8. player-profile: 玩家档案配置
 */
public class ConfigLoader {
    private final File configFile;
    private FileConfiguration config;
    private final AIChatPlugin plugin;
    private ScheduledExecutorService scheduler;
    private long lastModified;
    private final Object configLock = new Object();
    private final Set<ConfigChangeListener> listeners = new HashSet<>();
    private volatile boolean isShuttingDown = false;
    
    // 配置分类
    private static final String SETTINGS = "settings";
    private static final String CHAT = "chat";
    private static final String MESSAGES = "messages";
    private static final String ENVIRONMENT = "environment";
    private static final String PLAYER_STATUS = "player-status";
    private static final String DEBUG = "debug";
    private static final String CONVERSATION = "conversation";
    private static final String PLAYER_PROFILE = "player-profile";
    private static final String PERFORMANCE = "performance";
    private static final String TESTING = "testing";
    private static final String DEVELOPMENT = "development";
    
    // 默认值
    private static final String DEFAULT_API_URL = "https://api.deepseek.com/chat/completions/";
    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final int DEFAULT_TIMEOUT = 10;
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 150;
    private static final String DEFAULT_CHAT_PREFIX = "!";
    private static final int DEFAULT_DETECTION_RANGE = 10;
    private static final double DEFAULT_DAMAGE_THRESHOLD = 0.3;
    private static final long DEFAULT_DAMAGE_COOLDOWN = 1000;
    private static final int DEFAULT_MAX_HISTORY = 5;
    private static final int DEFAULT_MAX_CONTEXT = 1000;
    private static final int DEFAULT_SAVE_INTERVAL = 300;
    
    // 性能优化默认值
    private static final boolean DEFAULT_AUTO_OPTIMIZE = false;
    private static final int DEFAULT_CHECK_INTERVAL = 30;
    private static final double DEFAULT_CPU_THRESHOLD = 80.0;
    private static final double DEFAULT_MEMORY_THRESHOLD = 80.0;
    private static final double DEFAULT_TPS_THRESHOLD = 18.0;
    private static final int DEFAULT_ENTITY_THRESHOLD = 100;
    private static final int DEFAULT_CHUNK_THRESHOLD = 100;
    
    // 调试默认值
    private static final boolean DEFAULT_DEBUG_ENABLED = false;
    private static final boolean DEFAULT_VERBOSE_LOGGING = false;
    private static final boolean DEFAULT_SHOW_PERFORMANCE = true;
    private static final boolean DEFAULT_MOCK_API = false;
    private static final int DEFAULT_MOCK_DELAY = 200;
    private static final boolean DEFAULT_LOG_ENVIRONMENT = false;
    private static final boolean DEFAULT_LOG_PLAYER_STATUS = false;
    private static final boolean DEFAULT_ENABLE_PROFILING = false;
    private static final int DEFAULT_PROFILING_INTERVAL = 500;
    private static final boolean DEFAULT_LOG_MEMORY = false;
    private static final int DEFAULT_MEMORY_LOG_INTERVAL = 60;
    
    // 测试默认值
    private static final boolean DEFAULT_TESTING_ENABLED = false;
    private static final boolean DEFAULT_ALLOW_NO_API_KEY = false;
    private static final boolean DEFAULT_STRESS_TEST = false;
    private static final int DEFAULT_STRESS_TEST_CONCURRENCY = 10;
    private static final int DEFAULT_STRESS_TEST_DURATION = 300;
    private static final boolean DEFAULT_ERROR_INJECTION = false;
    private static final int DEFAULT_ERROR_INJECTION_RATE = 5;
    private static final boolean DEFAULT_NETWORK_LATENCY = false;
    private static final int DEFAULT_MIN_LATENCY = 50;
    private static final int DEFAULT_MAX_LATENCY = 200;
    private static final boolean DEFAULT_PERSISTENCE_TEST = false;
    private static final int DEFAULT_PERSISTENCE_TEST_INTERVAL = 5;
    
    // 开发默认值
    private static final boolean DEFAULT_DEV_ENABLED = false;
    private static final boolean DEFAULT_HOT_RELOAD = false;
    private static final int DEFAULT_HOT_RELOAD_INTERVAL = 5;
    private static final boolean DEFAULT_VALIDATE_CONFIG = true;
    private static final boolean DEFAULT_CACHE_API_RESPONSES = false;
    private static final int DEFAULT_API_CACHE_TTL = 300;
    private static final boolean DEFAULT_DETAILED_ERROR_STACK = true;
    private static final boolean DEFAULT_PERFORMANCE_MONITORING = true;
    private static final int DEFAULT_MONITORING_INTERVAL = 500;
    
    // 缓存相关字段
    private volatile boolean cacheValid = false;
    private volatile long lastCacheUpdate = 0;
    private static final long CACHE_TTL = 5000; // 缓存有效期5秒
    
    // 高频访问配置缓存
    private volatile boolean debugEnabledCache;
    private volatile boolean chatEnabledCache;
    private volatile String chatPrefixCache;
    private volatile boolean broadcastEnabledCache;
    private volatile boolean filterEnabledCache;
    private volatile int maxMessagesPerMinuteCache;
    private volatile long normalUserCooldownCache;
    private volatile long vipUserCooldownCache;
    
    public ConfigLoader(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        loadConfig();
        if (isHotReloadEnabled()) {
            startHotReload();
        }
    }
    
    /**
     * 加载配置
     */
    private void loadConfig() {
        synchronized(configLock) {
            if (!configFile.exists()) {
                plugin.saveDefaultConfig();
            }
            config = YamlConfiguration.loadConfiguration(configFile);
            lastModified = configFile.lastModified();
            
            if (isConfigValidationEnabled()) {
                validateConfiguration();
            }
            
            invalidateCache(); // 配置重载时使缓存失效
            notifyListeners();
        }
    }
    
    /**
     * 保存配置
     */
    public void saveConfig() {
        synchronized(configLock) {
            try {
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "保存配置文件时发生错误", e);
            }
        }
    }
    
    /**
     * 设置配置值
     */
    public void set(String path, Object value) {
        synchronized(configLock) {
            config.set(path, value);
            saveConfig();
        }
    }
    
    /**
     * 获取配置值
     */
    public Object get(String path) {
        synchronized(configLock) {
            return config.get(path);
        }
    }
    
    /**
     * 获取配置值（带默认值）
     */
    public Object get(String path, Object def) {
        synchronized(configLock) {
            return config.get(path, def);
        }
    }
    
    /**
     * 获取布尔值
     */
    public boolean getBoolean(String path) {
        synchronized(configLock) {
            return config.getBoolean(path);
        }
    }
    
    /**
     * 获取布尔值（带默认值）
     */
    public boolean getBoolean(String path, boolean def) {
        synchronized(configLock) {
            return config.getBoolean(path, def);
        }
    }
    
    /**
     * 获取整数值
     */
    public int getInt(String path) {
        synchronized(configLock) {
            return config.getInt(path);
        }
    }
    
    /**
     * 获取整数值（带默认值）
     */
    public int getInt(String path, int def) {
        synchronized(configLock) {
            return config.getInt(path, def);
        }
    }
    
    /**
     * 获取字符串
     */
    public String getString(String path) {
        synchronized(configLock) {
            return config.getString(path);
        }
    }
    
    /**
     * 获取字符串（带默认值）
     */
    public String getString(String path, String def) {
        synchronized(configLock) {
            return config.getString(path, def);
        }
    }
    
    /**
     * 获取双精度浮点数
     */
    public double getDouble(String path) {
        synchronized(configLock) {
            return config.getDouble(path);
        }
    }
    
    /**
     * 获取双精度浮点数（带默认值）
     */
    public double getDouble(String path, double def) {
        synchronized(configLock) {
            return config.getDouble(path, def);
        }
    }
    
    /**
     * 获取长整数值
     */
    public long getLong(String path) {
        synchronized(configLock) {
            return config.getLong(path);
        }
    }
    
    /**
     * 获取长整数值（带默认值）
     */
    public long getLong(String path, long def) {
        synchronized(configLock) {
            return config.getLong(path, def);
        }
    }
    
    /**
     * 获取配置对象
     */
    public FileConfiguration getConfig() {
        synchronized(configLock) {
            return config;
        }
    }
    
    /**
     * 重新加载配置
     */
    public void reloadConfig() {
        synchronized(configLock) {
            loadConfig();
            plugin.getLogger().info("配置已重新加载");
        }
    }
    
    // API设置
    public String getApiKey() {
        synchronized(configLock) {
            return config.getString(SETTINGS + ".api-key", "");
        }
    }
    
    public String getApiUrl() {
        synchronized(configLock) {
            return config.getString(SETTINGS + ".api-url", DEFAULT_API_URL);
        }
    }
    
    public String getModel() {
        synchronized(configLock) {
            return config.getString(SETTINGS + ".model", DEFAULT_MODEL);
        }
    }
    
    public String getRoleSystem() {
        synchronized(configLock) {
            return config.getString(SETTINGS + ".role-system", "");
        }
    }
    
    public int getConnectTimeout() {
        synchronized(configLock) {
            return config.getInt(SETTINGS + ".connect-timeout", DEFAULT_TIMEOUT);
        }
    }
    
    public int getReadTimeout() {
        synchronized(configLock) {
            return config.getInt(SETTINGS + ".read-timeout", DEFAULT_TIMEOUT);
        }
    }
    
    public int getWriteTimeout() {
        synchronized(configLock) {
            return config.getInt(SETTINGS + ".write-timeout", DEFAULT_TIMEOUT);
        }
    }
    
    public double getTemperature() {
        synchronized(configLock) {
            return config.getDouble(SETTINGS + ".temperature", DEFAULT_TEMPERATURE);
        }
    }
    
    public int getMaxTokens() {
        synchronized(configLock) {
            return config.getInt(SETTINGS + ".max-tokens", DEFAULT_MAX_TOKENS);
        }
    }
    
    // 聊天设置
    public boolean isChatEnabled() {
        if (!isCacheValid()) {
            updateCache();
        }
        return chatEnabledCache;
    }
    
    public String getChatPrefix() {
        if (!isCacheValid()) {
            updateCache();
        }
        return chatPrefixCache;
    }
    
    public boolean isBroadcastEnabled() {
        if (!isCacheValid()) {
            updateCache();
        }
        return broadcastEnabledCache;
    }
    
    public String getBroadcastFormat() {
        synchronized(configLock) {
            return config.getString(CHAT + ".broadcast-format", 
                "&7[AI对话] &f{player}: {message}");
        }
    }
    
    // 消息格式
    public String getMessageFormat(String type) {
        synchronized(configLock) {
            // 首先尝试从messages节点获取
            String message = config.getString(MESSAGES + "." + type, null);
            if (message != null && !message.trim().isEmpty()) {
                return message;
            }
            
            // 如果messages节点没有，尝试从help节点获取
            message = config.getString("help." + type, null);
            if (message != null && !message.trim().isEmpty()) {
                return message;
            }
            
            // 返回默认的帮助消息
            return getDefaultHelpMessage(type);
        }
    }
    
    /**
     * 获取默认帮助消息
     */
    private String getDefaultHelpMessage(String type) {
        switch (type) {
            case "help.page_header":
            case "page_header":
                return "&6=== 帮助页面 {page}/{total} ===";
            case "help.header":
            case "header":
                return "&6=== AI聊天帮助 ===";
            case "help.basic":
            case "basic":
                return "&e/ai <消息> &7- 与AI对话\n&e/aichat help &7- 显示帮助";
            case "help.admin":
            case "admin":
                return "&c/aichat reload &7- 重载配置\n&c/aichat status &7- 查看状态\n&c/aichat debug &7- 切换调试";
            case "help.vip":
            case "vip":
                return "&b/aichat clear &7- 清除对话历史";
            case "help.empty":
            case "empty":
                return "&c暂无帮助信息可显示";
            case "help.page_footer":
            case "page_footer":
                return "&7输入 /aichat help 查看更多...";
            case "no_permission":
                return "&c你没有权限使用此功能";
            case "error":
                return "&c发生错误：{error}";
            case "cooldown":
                return "&e请等待 {time} 秒后再试";
            case "rate_limit":
                return "&c消息发送过于频繁，请等待 {time} 秒后再试";
            case "filtered":
                return "&e您的消息包含不当内容，请重新组织语言";
            default:
                plugin.getLogger().warning("未找到消息格式: " + type);
                return "&7[AI] 消息格式缺失: " + type;
        }
    }
    
    // 环境设置
    public int getEntityDetectionRange() {
        synchronized(configLock) {
            return config.getInt(ENVIRONMENT + ".entity-detection-range", DEFAULT_DETECTION_RANGE);
        }
    }
    
    public int getBlockDetectionRange() {
        synchronized(configLock) {
            return config.getInt(ENVIRONMENT + ".block-detection-range", DEFAULT_DETECTION_RANGE);
        }
    }
    
    public boolean isShowDetailedLocation() {
        synchronized(configLock) {
            return config.getBoolean(ENVIRONMENT + ".show-detailed-location", true);
        }
    }
    
    public boolean isShowWeather() {
        synchronized(configLock) {
            return config.getBoolean(ENVIRONMENT + ".show-weather", true);
        }
    }
    
    public boolean isShowTime() {
        synchronized(configLock) {
            return config.getBoolean(ENVIRONMENT + ".show-time", true);
        }
    }
    
    // 玩家状态设置
    public double getDamageThreshold() {
        synchronized(configLock) {
            return config.getDouble(PLAYER_STATUS + ".damage-threshold", DEFAULT_DAMAGE_THRESHOLD);
        }
    }
    
    public long getDamageCooldown() {
        synchronized(configLock) {
            return config.getLong(PLAYER_STATUS + ".damage-cooldown", DEFAULT_DAMAGE_COOLDOWN);
        }
    }
    
    // 调试设置
    public boolean isDebugEnabled() {
        if (!isCacheValid()) {
            updateCache();
        }
        return debugEnabledCache;
    }
    
    public boolean isVerboseLoggingEnabled() {
        synchronized(configLock) {
            return config.getBoolean(DEBUG + ".verbose-logging", DEFAULT_VERBOSE_LOGGING);
        }
    }
    
    public boolean isShowPerformanceEnabled() {
        synchronized(configLock) {
            return config.getBoolean(DEBUG + ".show-performance", DEFAULT_SHOW_PERFORMANCE);
        }
    }
    
    public boolean isMockApiEnabled() {
        synchronized(configLock) {
            return config.getBoolean(DEBUG + ".mock-api", DEFAULT_MOCK_API);
        }
    }
    
    public int getMockDelay() {
        synchronized(configLock) {
            return config.getInt(DEBUG + ".mock-delay", DEFAULT_MOCK_DELAY);
        }
    }
    
    public boolean isEnvironmentDataLoggingEnabled() {
        synchronized(configLock) {
            return config.getBoolean(DEBUG + ".log-environment-data", DEFAULT_LOG_ENVIRONMENT);
        }
    }
    
    public boolean isPlayerStatusLoggingEnabled() {
        synchronized(configLock) {
            return config.getBoolean(DEBUG + ".log-player-status", DEFAULT_LOG_PLAYER_STATUS);
        }
    }
    
    public boolean isProfilingEnabled() {
        synchronized(configLock) {
            return config.getBoolean(DEBUG + ".enable-profiling", DEFAULT_ENABLE_PROFILING);
        }
    }
    
    public int getProfilingInterval() {
        synchronized(configLock) {
            return config.getInt(DEBUG + ".profiling-interval", DEFAULT_PROFILING_INTERVAL);
        }
    }
    
    public boolean isMemoryUsageLoggingEnabled() {
        synchronized(configLock) {
            return config.getBoolean(DEBUG + ".log-memory-usage", DEFAULT_LOG_MEMORY);
        }
    }
    
    public int getMemoryLogInterval() {
        synchronized(configLock) {
            return config.getInt(DEBUG + ".memory-log-interval", DEFAULT_MEMORY_LOG_INTERVAL);
        }
    }
    
    // 对话设置
    public int getConversationMaxHistory() {
        synchronized(configLock) {
            return config.getInt(CONVERSATION + ".max-history", DEFAULT_MAX_HISTORY);
        }
    }
    
    public int getConversationMaxContext() {
        synchronized(configLock) {
            return config.getInt(CONVERSATION + ".max-context-length", DEFAULT_MAX_CONTEXT);
        }
    }
    
    public boolean isConversationPersistenceEnabled() {
        synchronized(configLock) {
            return config.getBoolean(CONVERSATION + ".enable-persistence", false);
        }
    }
    
    public int getConversationSaveInterval() {
        synchronized(configLock) {
            return config.getInt(CONVERSATION + ".save-interval", DEFAULT_SAVE_INTERVAL);
        }
    }
    
    public boolean isConversationSaveOnShutdown() {
        synchronized(configLock) {
            return config.getBoolean(CONVERSATION + ".save-on-shutdown", false);
        }
    }
    
    // 玩家档案设置
    public boolean isPlayerProfilePersistenceEnabled() {
        synchronized(configLock) {
            return config.getBoolean(PLAYER_PROFILE + ".enable-persistence", false);
        }
    }
    
    public int getPlayerProfileSaveInterval() {
        synchronized(configLock) {
            return config.getInt(PLAYER_PROFILE + ".save-interval", DEFAULT_SAVE_INTERVAL);
        }
    }
    
    public boolean isPlayerProfileSaveOnShutdown() {
        synchronized(configLock) {
            return config.getBoolean(PLAYER_PROFILE + ".save-on-shutdown", false);
        }
    }
    
    // 配置修改
    public void setApiKey(String apiKey) {
        synchronized(configLock) {
            config.set(SETTINGS + ".api-key", apiKey);
            saveConfig();
        }
    }
    
    public void setDebug(boolean debug) {
        synchronized(configLock) {
            config.set(DEBUG + ".enabled", debug);
            saveConfig();
            invalidateCache();
        }
    }
    
    // 环境收集器配置
    public boolean isShowEntities() {
        synchronized(configLock) {
            return config.getBoolean(ENVIRONMENT + ".show-entities", true);
        }
    }
    
    public double getEntityRange() {
        synchronized(configLock) {
            return config.getDouble(ENVIRONMENT + ".entity-range", 10.0);
        }
    }
    
    public int getMaxEntities() {
        synchronized(configLock) {
            return config.getInt(ENVIRONMENT + ".max-entities", 5);
        }
    }
    
    public boolean isShowBlocks() {
        synchronized(configLock) {
            return config.getBoolean(ENVIRONMENT + ".show-blocks", true);
        }
    }
    
    public int getBlockScanRange() {
        synchronized(configLock) {
            return config.getInt(ENVIRONMENT + ".block-scan-range", 1);
        }
    }
    
    public Set<Material> getExcludedBlocks() {
        synchronized(configLock) {
            List<String> excluded = config.getStringList(ENVIRONMENT + ".excluded-blocks");
            Set<Material> materials = new HashSet<>();
            for (String name : excluded) {
                try {
                    materials.add(Material.valueOf(name));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("无效的方块类型: " + name);
                }
            }
            return materials;
        }
    }
    
    public long getCacheTTL() {
        synchronized(configLock) {
            return config.getLong(ENVIRONMENT + ".cache-ttl", 1500);
        }
    }
    
    // 性能优化配置（统一版本）
    public boolean isAutoOptimizeEnabled() {
        synchronized(configLock) {
            return config.getBoolean(PERFORMANCE + ".auto-optimize", DEFAULT_AUTO_OPTIMIZE);
        }
    }
    
    public int getPerformanceCheckInterval() {
        synchronized(configLock) {
            return config.getInt(PERFORMANCE + ".check-interval", DEFAULT_CHECK_INTERVAL);
        }
    }
    
    // 新版TPS阈值（性能驱动优化）
    public double getTpsThresholdFull() {
        synchronized(configLock) {
            return config.getDouble(PERFORMANCE + ".tps_thresholds.full", 18.0);
        }
    }
    
    public double getTpsThresholdLite() {
        synchronized(configLock) {
            return config.getDouble(PERFORMANCE + ".tps_thresholds.lite", 15.0);
        }
    }
    
    public double getTpsThresholdBasic() {
        synchronized(configLock) {
            return config.getDouble(PERFORMANCE + ".tps_thresholds.basic", 10.0);
        }
    }
    
    // 硬件监控阈值
    public int getMinCpuCores() {
        synchronized(configLock) {
            return config.getInt(PERFORMANCE + ".hardware.min-cpu-cores", 2);
        }
    }
    
    public double getMinFreeMemory() {
        synchronized(configLock) {
            return config.getDouble(PERFORMANCE + ".hardware.min-free-memory", 2.0);
        }
    }
    
    public double getMinFreeDisk() {
        synchronized(configLock) {
            return config.getDouble(PERFORMANCE + ".hardware.min-free-disk", 5.0);
        }
    }
    
    public boolean isDynamicThresholdEnabled() {
        synchronized(configLock) {
            return config.getBoolean(PERFORMANCE + ".hardware.dynamic-threshold", true);
        }
    }
    
    // 传统阈值（向后兼容）
    public double getCpuThreshold() {
        synchronized(configLock) {
            return config.getDouble(PERFORMANCE + ".thresholds.cpu-usage", DEFAULT_CPU_THRESHOLD);
        }
    }
    
    public double getMemoryThreshold() {
        synchronized(configLock) {
            return config.getDouble(PERFORMANCE + ".thresholds.memory-usage", DEFAULT_MEMORY_THRESHOLD);
        }
    }
    
    public double getTpsThreshold() {
        synchronized(configLock) {
            return config.getDouble(PERFORMANCE + ".thresholds.tps", DEFAULT_TPS_THRESHOLD);
        }
    }
    
    public int getEntityCountThreshold() {
        synchronized(configLock) {
            return config.getInt(PERFORMANCE + ".thresholds.entity-count", DEFAULT_ENTITY_THRESHOLD);
        }
    }
    
    public int getChunkCountThreshold() {
        synchronized(configLock) {
            return config.getInt(PERFORMANCE + ".thresholds.chunk-count", DEFAULT_CHUNK_THRESHOLD);
        }
    }
    
    public boolean isAdjustDetectionRangeEnabled() {
        synchronized(configLock) {
            return config.getBoolean(PERFORMANCE + ".strategies.adjust-detection-range", true);
        }
    }
    
    public boolean isAdjustCacheTTLEnabled() {
        synchronized(configLock) {
            return config.getBoolean(PERFORMANCE + ".strategies.adjust-cache-ttl", true);
        }
    }
    
    public boolean isAdjustQueueSizeEnabled() {
        synchronized(configLock) {
            return config.getBoolean(PERFORMANCE + ".strategies.adjust-queue-size", true);
        }
    }
    
    public boolean isAdjustAsyncTasksEnabled() {
        synchronized(configLock) {
            return config.getBoolean(PERFORMANCE + ".strategies.adjust-async-tasks", true);
        }
    }
    
    public int getMinDetectionRange() {
        synchronized(configLock) {
            return config.getInt(PERFORMANCE + ".limits.min-detection-range", 5);
        }
    }
    
    public int getMaxDetectionRange() {
        synchronized(configLock) {
            return config.getInt(PERFORMANCE + ".limits.max-detection-range", 20);
        }
    }
    
    public long getMinCacheTTL() {
        synchronized(configLock) {
            return config.getLong(PERFORMANCE + ".limits.min-cache-ttl", 500);
        }
    }
    
    public long getMaxCacheTTL() {
        synchronized(configLock) {
            return config.getLong(PERFORMANCE + ".limits.max-cache-ttl", 5000);
        }
    }
    
    public int getMinQueueSize() {
        synchronized(configLock) {
            return config.getInt(PERFORMANCE + ".limits.min-queue-size", 10);
        }
    }
    
    public int getMaxQueueSize() {
        synchronized(configLock) {
            return config.getInt(PERFORMANCE + ".limits.max-queue-size", 100);
        }
    }
    
    public int getMinAsyncTasks() {
        synchronized(configLock) {
            return config.getInt(PERFORMANCE + ".limits.min-async-tasks", 2);
        }
    }
    
    public int getMaxAsyncTasks() {
        synchronized(configLock) {
            return config.getInt(PERFORMANCE + ".limits.max-async-tasks", 8);
        }
    }
    
    // 性能优化配置修改方法
    public void setAutoOptimize(boolean enabled) {
        synchronized(configLock) {
            config.set(PERFORMANCE + ".auto-optimize", enabled);
            saveConfig();
        }
    }
    
    public void setPerformanceCheckInterval(int minutes) {
        synchronized(configLock) {
            config.set(PERFORMANCE + ".check-interval", minutes);
            saveConfig();
        }
    }
    
    public void setThresholds(double cpuUsage, double memoryUsage, double tps, 
                            int entityCount, int chunkCount) {
        synchronized(configLock) {
            String thresholds = PERFORMANCE + ".thresholds";
            config.set(thresholds + ".cpu-usage", cpuUsage);
            config.set(thresholds + ".memory-usage", memoryUsage);
            config.set(thresholds + ".tps", tps);
            config.set(thresholds + ".entity-count", entityCount);
            config.set(thresholds + ".chunk-count", chunkCount);
            saveConfig();
        }
    }
    
    public void setOptimizationStrategies(boolean adjustDetectionRange, boolean adjustCacheTTL,
                                        boolean adjustQueueSize, boolean adjustAsyncTasks) {
        synchronized(configLock) {
            String strategies = PERFORMANCE + ".strategies";
            config.set(strategies + ".adjust-detection-range", adjustDetectionRange);
            config.set(strategies + ".adjust-cache-ttl", adjustCacheTTL);
            config.set(strategies + ".adjust-queue-size", adjustQueueSize);
            config.set(strategies + ".adjust-async-tasks", adjustAsyncTasks);
            saveConfig();
        }
    }
    
    public void setOptimizationLimits(int minDetectionRange, int maxDetectionRange,
                                    long minCacheTTL, long maxCacheTTL,
                                    int minQueueSize, int maxQueueSize,
                                    int minAsyncTasks, int maxAsyncTasks) {
        synchronized(configLock) {
            String limits = PERFORMANCE + ".limits";
            config.set(limits + ".min-detection-range", minDetectionRange);
            config.set(limits + ".max-detection-range", maxDetectionRange);
            config.set(limits + ".min-cache-ttl", minCacheTTL);
            config.set(limits + ".max-cache-ttl", maxCacheTTL);
            config.set(limits + ".min-queue-size", minQueueSize);
            config.set(limits + ".max-queue-size", maxQueueSize);
            config.set(limits + ".min-async-tasks", minAsyncTasks);
            config.set(limits + ".max-async-tasks", maxAsyncTasks);
            saveConfig();
        }
    }
    
    /**
     * 热重载支持
     */
    private void startHotReload() {
        if (scheduler != null) {
            stopHotReload();
        }
        
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ConfigHotReload");
            thread.setDaemon(true);
            return thread;
        });
        
        long checkInterval = isDevelopmentEnabled() ? 5 : 30; // 开发环境5秒，生产环境30秒
        
        scheduler.scheduleAtFixedRate(() -> {
            if (isShuttingDown) {
                return;
            }
            
            try {
                if (configFile.lastModified() > lastModified) {
                    loadConfig();
                    lastModified = configFile.lastModified();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "热重载配置时发生错误", e);
            }
        }, 0, checkInterval, TimeUnit.SECONDS);
    }
    
    /**
     * 停止热重载
     */
    public void stopHotReload() {
        isShuttingDown = true;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
    }
    
    /**
     * 添加配置变更监听器
     */
    public void addConfigChangeListener(ConfigChangeListener listener) {
        synchronized(listeners) {
            listeners.add(listener);
        }
    }
    
    /**
     * 移除配置变更监听器
     */
    public void removeConfigChangeListener(ConfigChangeListener listener) {
        synchronized(listeners) {
            listeners.remove(listener);
        }
    }
    
    /**
     * 通知所有监听器配置已重载
     */
    private void notifyListeners() {
        synchronized(listeners) {
            for (ConfigChangeListener listener : listeners) {
                try {
                    listener.onConfigReload();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "通知配置变更监听器时发生错误", e);
                }
            }
        }
    }
    
    // 配置验证
    private void validateConfiguration() {
        // 验证API密钥
        if (getApiKey().isEmpty() && !isAllowNoApiKey()) {
            plugin.getLogger().warning("API密钥未设置！");
        }
        
        // 验证超时设置
        if (getConnectTimeout() < 1 || getConnectTimeout() > 30) {
            plugin.getLogger().warning("连接超时时间超出建议范围(1-30秒)");
        }
        
        // 验证检测范围
        if (getEntityDetectionRange() < 5 || getEntityDetectionRange() > 30) {
            plugin.getLogger().warning("实体检测范围超出建议范围(5-30格)");
        }
        if (getBlockDetectionRange() < 5 || getBlockDetectionRange() > 30) {
            plugin.getLogger().warning("方块检测范围超出建议范围(5-30格)");
        }
        
        // 验证性能阈值
        if (getCpuThreshold() < 50 || getCpuThreshold() > 95) {
            plugin.getLogger().warning("CPU使用率阈值超出建议范围(50-95%)");
        }
        
        // 验证测试设置
        if (isStressTestEnabled() && getStressTestConcurrency() > 100) {
            plugin.getLogger().warning("压力测试并发数超出建议范围(1-100)");
        }
    }
    
    // 测试配置访问方法
    public boolean isTestingEnabled() {
        synchronized(configLock) {
            return config.getBoolean(TESTING + ".enabled", DEFAULT_TESTING_ENABLED);
        }
    }
    
    public boolean isAllowNoApiKey() {
        synchronized(configLock) {
            return config.getBoolean(TESTING + ".allow-no-api-key", DEFAULT_ALLOW_NO_API_KEY);
        }
    }
    
    public boolean isStressTestEnabled() {
        synchronized(configLock) {
            return config.getBoolean(TESTING + ".stress-test", DEFAULT_STRESS_TEST);
        }
    }
    
    public int getStressTestConcurrency() {
        synchronized(configLock) {
            return config.getInt(TESTING + ".stress-test-concurrency", DEFAULT_STRESS_TEST_CONCURRENCY);
        }
    }
    
    public int getStressTestDuration() {
        synchronized(configLock) {
            return config.getInt(TESTING + ".stress-test-duration", DEFAULT_STRESS_TEST_DURATION);
        }
    }
    
    public boolean isErrorInjectionEnabled() {
        synchronized(configLock) {
            return config.getBoolean(TESTING + ".error-injection", DEFAULT_ERROR_INJECTION);
        }
    }
    
    public int getErrorInjectionRate() {
        synchronized(configLock) {
            return config.getInt(TESTING + ".error-injection-rate", DEFAULT_ERROR_INJECTION_RATE);
        }
    }
    
    public boolean isNetworkLatencySimulationEnabled() {
        synchronized(configLock) {
            return config.getBoolean(TESTING + ".network-latency-simulation", DEFAULT_NETWORK_LATENCY);
        }
    }
    
    public int getMinLatency() {
        synchronized(configLock) {
            return config.getInt(TESTING + ".min-latency", DEFAULT_MIN_LATENCY);
        }
    }
    
    public int getMaxLatency() {
        synchronized(configLock) {
            return config.getInt(TESTING + ".max-latency", DEFAULT_MAX_LATENCY);
        }
    }
    
    public boolean isPersistenceTestEnabled() {
        synchronized(configLock) {
            return config.getBoolean(TESTING + ".persistence-test", DEFAULT_PERSISTENCE_TEST);
        }
    }
    
    public int getPersistenceTestInterval() {
        synchronized(configLock) {
            return config.getInt(TESTING + ".persistence-test-interval", DEFAULT_PERSISTENCE_TEST_INTERVAL);
        }
    }
    
    // 开发配置访问方法
    public boolean isDevelopmentEnabled() {
        synchronized(configLock) {
            return config.getBoolean(DEVELOPMENT + ".enabled", DEFAULT_DEV_ENABLED);
        }
    }
    
    public boolean isHotReloadEnabled() {
        synchronized(configLock) {
            return config.getBoolean(DEVELOPMENT + ".hot-reload", DEFAULT_HOT_RELOAD);
        }
    }
    
    public int getHotReloadInterval() {
        synchronized(configLock) {
            return config.getInt(DEVELOPMENT + ".hot-reload-interval", DEFAULT_HOT_RELOAD_INTERVAL);
        }
    }
    
    public boolean isConfigValidationEnabled() {
        synchronized(configLock) {
            return config.getBoolean(DEVELOPMENT + ".validate-config", DEFAULT_VALIDATE_CONFIG);
        }
    }
    
    public boolean isApiResponseCachingEnabled() {
        synchronized(configLock) {
            return config.getBoolean(DEVELOPMENT + ".cache-api-responses", DEFAULT_CACHE_API_RESPONSES);
        }
    }
    
    public int getApiCacheTtl() {
        synchronized(configLock) {
            return config.getInt(DEVELOPMENT + ".api-cache-ttl", DEFAULT_API_CACHE_TTL);
        }
    }
    
    public boolean isDetailedErrorStackEnabled() {
        synchronized(configLock) {
            return config.getBoolean(DEVELOPMENT + ".detailed-error-stack", DEFAULT_DETAILED_ERROR_STACK);
        }
    }
    
    public boolean isPerformanceMonitoringEnabled() {
        synchronized(configLock) {
            return config.getBoolean(DEVELOPMENT + ".performance-monitoring", DEFAULT_PERFORMANCE_MONITORING);
        }
    }
    
    public int getMonitoringInterval() {
        synchronized(configLock) {
            return config.getInt(DEVELOPMENT + ".monitoring-interval", DEFAULT_MONITORING_INTERVAL);
        }
    }
    
    /**
     * 检查玩家加入事件响应是否启用
     */
    public boolean isJoinEnabled() {
        synchronized(configLock) {
            return config.getBoolean("events.join.enabled", true);
        }
    }
    
    /**
     * 检查玩家退出事件响应是否启用
     */
    public boolean isQuitEnabled() {
        synchronized(configLock) {
            return config.getBoolean("events.quit.enabled", true);
        }
    }
    
    /**
     * 检查玩家重生事件响应是否启用
     */
    public boolean isRespawnEnabled() {
        synchronized(configLock) {
            return config.getBoolean("events.respawn.enabled", true);
        }
    }
    
    /**
     * 检查玩家升级事件响应是否启用
     */
    public boolean isLevelUpEnabled() {
        synchronized(configLock) {
            return config.getBoolean("events.level-up.enabled", true);
        }
    }
    
    /**
     * 检查玩家受伤事件响应是否启用
     */
    public boolean isDamageEnabled() {
        synchronized(configLock) {
            return config.getBoolean("events.damage.enabled", true);
        }
    }
    
    /**
     * 检查玩家死亡事件响应是否启用
     */
    public boolean isDeathEnabled() {
        synchronized(configLock) {
            return config.getBoolean("events.death.enabled", true);
        }
    }
    
    /**
     * 检查成就事件响应是否启用
     */
    public boolean isAdvancementEnabled() {
        synchronized(configLock) {
            return config.getBoolean("events.advancement.enabled", true);
        }
    }
    
    /**
     * 检查药水效果变更响应是否启用
     */
    public boolean isPotionEffectEnabled() {
        synchronized(configLock) {
            return config.getBoolean("events.potion-effect.enabled", true);
        }
    }

    /**
     * 获取全局事件冷却时间
     */
    public long getGlobalCooldown() {
        synchronized(configLock) {
            return config.getLong("events.global-cooldown", 1000L);
        }
    }

    /**
     * 获取药水效果事件冷却时间
     * @return 冷却时间(毫秒)
     */
    public long getPotionCooldown() {
        synchronized(configLock) {
            return config.getLong("events.potion-cooldown", 200L);
        }
    }
    
    // 命令配置访问方法
    /**
     * 获取普通用户命令冷却时间
     * @return 冷却时间(毫秒)
     */
    public long getNormalUserCooldown() {
        if (!isCacheValid()) {
            updateCache();
        }
        return normalUserCooldownCache;
    }
    
    /**
     * 获取VIP用户命令冷却时间
     * @return 冷却时间(毫秒)
     */
    public long getVipUserCooldown() {
        if (!isCacheValid()) {
            updateCache();
        }
        return vipUserCooldownCache;
    }
    
    /**
     * 获取每分钟最大消息数限制
     * @return 消息数量
     */
    public int getMaxMessagesPerMinute() {
        if (!isCacheValid()) {
            updateCache();
        }
        return maxMessagesPerMinuteCache;
    }
    
    // 敏感词过滤配置
    /**
     * 检查敏感词过滤是否启用
     * @return 是否启用
     */
    public boolean isFilterEnabled() {
        if (!isCacheValid()) {
            updateCache();
        }
        return filterEnabledCache;
    }
    
    /**
     * 获取敏感词列表字符串
     * @return 敏感词列表（逗号分隔）
     */
    public String getFilterWords() {
        synchronized(configLock) {
            return config.getString("filter.words", "");
        }
    }
    
    // 帮助系统配置
    /**
     * 获取帮助页面大小
     * @return 每页显示行数
     */
    public int getHelpPageSize() {
        synchronized(configLock) {
            return config.getInt("help.page-size", 8);
        }
    }
    
    /**
     * 获取帮助消息格式
     * @param type 消息类型
     * @return 格式化字符串
     */
    public String getHelpMessageFormat(String type) {
        synchronized(configLock) {
            return config.getString("help." + type, "");
        }
    }
    
    /**
     * 检查缓存是否有效
     */
    private boolean isCacheValid() {
        return cacheValid && (System.currentTimeMillis() - lastCacheUpdate) < CACHE_TTL;
    }
    
    /**
     * 更新缓存
     */
    private void updateCache() {
        synchronized(configLock) {
            debugEnabledCache = config.getBoolean(DEBUG + ".enabled", DEFAULT_DEBUG_ENABLED);
            chatEnabledCache = config.getBoolean(CHAT + ".enabled", true);
            chatPrefixCache = config.getString(CHAT + ".prefix", DEFAULT_CHAT_PREFIX);
            broadcastEnabledCache = config.getBoolean(CHAT + ".broadcast", true);
            filterEnabledCache = config.getBoolean("filter.enabled", true);
            maxMessagesPerMinuteCache = config.getInt("command.rate-limit.max-messages-per-minute", 20);
            normalUserCooldownCache = config.getLong("command.cooldown.normal", 3000L);
            vipUserCooldownCache = config.getLong("command.cooldown.vip", 1000L);
            
            lastCacheUpdate = System.currentTimeMillis();
            cacheValid = true;
        }
    }
    
    /**
     * 使缓存失效
     */
    private void invalidateCache() {
        cacheValid = false;
    }
    
    // 配置修改方法需要使缓存失效
    public void setChatEnabled(boolean enabled) {
        synchronized(configLock) {
            config.set(CHAT + ".enabled", enabled);
            saveConfig();
            invalidateCache();
        }
    }
    
    public void setChatPrefix(String prefix) {
        synchronized(configLock) {
            config.set(CHAT + ".prefix", prefix);
            saveConfig();
            invalidateCache();
        }
    }
    
    public void setBroadcastEnabled(boolean enabled) {
        synchronized(configLock) {
            config.set(CHAT + ".broadcast", enabled);
            saveConfig();
            invalidateCache();
        }
    }
    
    public void setFilterEnabled(boolean enabled) {
        synchronized(configLock) {
            config.set("filter.enabled", enabled);
            saveConfig();
            invalidateCache();
        }
    }
    
    public void setMaxMessagesPerMinute(int max) {
        synchronized(configLock) {
            config.set("command.rate-limit.max-messages-per-minute", max);
            saveConfig();
            invalidateCache();
        }
    }
    
    public void setNormalUserCooldown(long cooldown) {
        synchronized(configLock) {
            config.set("command.cooldown.normal", cooldown);
            saveConfig();
            invalidateCache();
        }
    }
    
    public void setVipUserCooldown(long cooldown) {
        synchronized(configLock) {
            config.set("command.cooldown.vip", cooldown);
            saveConfig();
            invalidateCache();
        }
    }

    /**
     * 设置调试模式
     */
    public void setDebugEnabled(boolean enabled) {
        synchronized(configLock) {
            config.set(DEBUG + ".enabled", enabled);
            saveConfig();
            invalidateCache();
        }
    }
    
    /**
     * 检查是否只处理重要伤害事件
     * @return 是否只处理重要伤害
     */
    public boolean isDamageOnlyImportant() {
        synchronized(configLock) {
            return config.getBoolean("events.damage.only-important", false);
        }
    }
    
    /**
     * 获取伤害事件最大频率（每秒）
     * @return 每秒最大事件数
     */
    public int getDamageMaxEventsPerSecond() {
        synchronized(configLock) {
            return config.getInt("events.damage.max-events-per-second", 10);
        }
    }
    
    /**
     * 检查是否启用伤害事件性能优化
     * @return 是否启用性能优化
     */
    public boolean isDamagePerformanceOptimizationEnabled() {
        synchronized(configLock) {
            return config.getBoolean("events.damage.performance-optimization", true);
        }
    }
}

/**
 * 配置变更监听器接口
 */
interface ConfigChangeListener {
    /**
     * 当配置重载时调用
     */
    void onConfigReload();
}