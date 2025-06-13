package com.example.aichatplugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

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
    
    // 配置分类
    private static final String SETTINGS = "settings";
    private static final String CHAT = "chat";
    private static final String MESSAGES = "messages";
    private static final String ENVIRONMENT = "environment";
    private static final String PLAYER_STATUS = "player-status";
    private static final String DEBUG = "debug";
    private static final String CONVERSATION = "conversation";
    private static final String PLAYER_PROFILE = "player-profile";
    
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
    
    public ConfigLoader(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        loadConfig();
    }
    
    /**
     * 加载配置
     */
    private void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }
    
    /**
     * 保存配置
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "保存配置文件时发生错误", e);
        }
    }
    
    // API设置
    public String getApiKey() {
        return config.getString(SETTINGS + ".api-key", "");
    }
    
    public String getApiUrl() {
        return config.getString(SETTINGS + ".api-url", DEFAULT_API_URL);
    }
    
    public String getModel() {
        return config.getString(SETTINGS + ".model", DEFAULT_MODEL);
    }
    
    public String getRoleSystem() {
        return config.getString(SETTINGS + ".role-system", "");
    }
    
    public int getConnectTimeout() {
        return config.getInt(SETTINGS + ".connect-timeout", DEFAULT_TIMEOUT);
    }
    
    public int getReadTimeout() {
        return config.getInt(SETTINGS + ".read-timeout", DEFAULT_TIMEOUT);
    }
    
    public int getWriteTimeout() {
        return config.getInt(SETTINGS + ".write-timeout", DEFAULT_TIMEOUT);
    }
    
    public double getTemperature() {
        return config.getDouble(SETTINGS + ".temperature", DEFAULT_TEMPERATURE);
    }
    
    public int getMaxTokens() {
        return config.getInt(SETTINGS + ".max-tokens", DEFAULT_MAX_TOKENS);
    }
    
    // 聊天设置
    public boolean isChatEnabled() {
        return config.getBoolean(CHAT + ".enabled", true);
    }
    
    public String getChatPrefix() {
        return config.getString(CHAT + ".prefix", DEFAULT_CHAT_PREFIX);
    }
    
    public boolean isBroadcastEnabled() {
        return config.getBoolean(CHAT + ".broadcast", true);
    }
    
    public String getBroadcastFormat() {
        return config.getString(CHAT + ".broadcast-format", 
            "&7[AI对话] &f{player}: {message}");
    }
    
    // 消息格式
    public String getMessageFormat(String type) {
        return config.getString(MESSAGES + "." + type, "");
    }
    
    // 环境设置
    public int getEntityDetectionRange() {
        return config.getInt(ENVIRONMENT + ".entity-detection-range", DEFAULT_DETECTION_RANGE);
    }
    
    public int getBlockDetectionRange() {
        return config.getInt(ENVIRONMENT + ".block-detection-range", DEFAULT_DETECTION_RANGE);
    }
    
    public boolean isShowDetailedLocation() {
        return config.getBoolean(ENVIRONMENT + ".show-detailed-location", true);
    }
    
    public boolean isShowWeather() {
        return config.getBoolean(ENVIRONMENT + ".show-weather", true);
    }
    
    public boolean isShowTime() {
        return config.getBoolean(ENVIRONMENT + ".show-time", true);
    }
    
    // 玩家状态设置
    public double getDamageThreshold() {
        return config.getDouble(PLAYER_STATUS + ".damage-threshold", DEFAULT_DAMAGE_THRESHOLD);
    }
    
    public long getDamageCooldown() {
        return config.getLong(PLAYER_STATUS + ".damage-cooldown", DEFAULT_DAMAGE_COOLDOWN);
    }
    
    public boolean isRespondToJoin() {
        return config.getBoolean(PLAYER_STATUS + ".respond-to-join", true);
    }
    
    public boolean isRespondToQuit() {
        return config.getBoolean(PLAYER_STATUS + ".respond-to-quit", true);
    }
    
    public boolean isRespondToDeath() {
        return config.getBoolean(PLAYER_STATUS + ".respond-to-death", true);
    }
    
    public boolean isRespondToLevelUp() {
        return config.getBoolean(PLAYER_STATUS + ".respond-to-level-up", true);
    }
    
    public boolean isRespondToAdvancement() {
        return config.getBoolean(PLAYER_STATUS + ".respond-to-advancement", true);
    }
    
    // 调试设置
    public boolean isDebugEnabled() {
        return config.getBoolean(DEBUG + ".enabled", true);
    }
    
    public boolean isVerboseLogging() {
        return config.getBoolean(DEBUG + ".verbose-logging", false);
    }
    
    public boolean isShowPerformance() {
        return config.getBoolean(DEBUG + ".show-performance", true);
    }
    
    // 对话设置
    public int getConversationMaxHistory() {
        return config.getInt(CONVERSATION + ".max-history", DEFAULT_MAX_HISTORY);
    }
    
    public int getConversationMaxContext() {
        return config.getInt(CONVERSATION + ".max-context-length", DEFAULT_MAX_CONTEXT);
    }
    
    public boolean isConversationPersistenceEnabled() {
        return config.getBoolean(CONVERSATION + ".enable-persistence", false);
    }
    
    public int getConversationSaveInterval() {
        return config.getInt(CONVERSATION + ".save-interval", DEFAULT_SAVE_INTERVAL);
    }
    
    public boolean isConversationSaveOnShutdown() {
        return config.getBoolean(CONVERSATION + ".save-on-shutdown", false);
    }
    
    // 玩家档案设置
    public boolean isPlayerProfilePersistenceEnabled() {
        return config.getBoolean(PLAYER_PROFILE + ".enable-persistence", false);
    }
    
    public int getPlayerProfileSaveInterval() {
        return config.getInt(PLAYER_PROFILE + ".save-interval", DEFAULT_SAVE_INTERVAL);
    }
    
    public boolean isPlayerProfileSaveOnShutdown() {
        return config.getBoolean(PLAYER_PROFILE + ".save-on-shutdown", false);
    }
    
    // 配置修改
    public void setApiKey(String apiKey) {
        config.set(SETTINGS + ".api-key", apiKey);
        saveConfig();
    }
    
    public void setDebug(boolean debug) {
        config.set(DEBUG + ".enabled", debug);
        saveConfig();
    }
}