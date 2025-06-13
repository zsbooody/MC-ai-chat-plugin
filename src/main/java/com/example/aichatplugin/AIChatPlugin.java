package com.example.aichatplugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import java.util.logging.Level;
import org.bukkit.event.Listener;

/**
 * AI聊天插件主类
 * 
 * 职责：
 * 1. 插件生命周期管理
 * 2. 组件初始化和协调
 * 3. 事件监听器注册
 * 4. 资源管理
 * 
 * 主要组件：
 * 1. ConfigLoader: 配置管理
 * 2. ConversationManager: 对话管理
 * 3. PlayerProfileManager: 玩家数据管理
 * 4. EnvironmentCollector: 环境信息收集
 * 5. DeepSeekAIService: AI服务实现
 * 
 * 功能特性：
 * 1. 玩家聊天响应
 * 2. 环境感知对话
 * 3. 玩家状态响应
 * 4. 调试模式支持
 */
public class AIChatPlugin extends JavaPlugin {
    private ConfigLoader configLoader;
    private ConversationManager conversationManager;
    private PlayerProfileManager playerProfileManager;
    private DeepSeekAIService aiService;
    private EnvironmentCollector environmentCollector;
    private PlayerStatusListener statusListener;
    private PlayerChatListener chatListener;
    private AIChatCommand chatCommand;
    
    @Override
    public void onEnable() {
        try {
            // 保存默认配置
            saveDefaultConfig();
            
            // 按依赖顺序初始化组件
            this.configLoader = new ConfigLoader(this);
            this.environmentCollector = new EnvironmentCollector(this);
            this.aiService = new DeepSeekAIService(this);
            this.playerProfileManager = new PlayerProfileManager(this);
            this.conversationManager = new ConversationManager(this);
            
            // 注册事件监听器
            this.statusListener = new PlayerStatusListener(this);
            this.chatListener = new PlayerChatListener(this);
            this.chatCommand = new AIChatCommand(this);
            
            getServer().getPluginManager().registerEvents(statusListener, this);
            getServer().getPluginManager().registerEvents(chatListener, this);
            getCommand("ai").setExecutor(chatCommand);
            
            getLogger().info("AIChatPlugin已启用");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "插件启动时发生错误", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        try {
            // 按依赖顺序关闭组件
            if (conversationManager != null) {
                conversationManager.shutdown();
            }
            if (playerProfileManager != null) {
                playerProfileManager.shutdown();
            }
            if (aiService != null) {
                aiService.shutdown();
            }
            getLogger().info("AIChatPlugin已禁用");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "插件关闭时发生错误", e);
        }
    }
    
    public DeepSeekAIService getAIService() {
        return aiService;
    }
    
    public PlayerProfileManager getProfileManager() {
        return playerProfileManager;
    }
    
    public ConversationManager getConversationManager() {
        return conversationManager;
    }
    
    public EnvironmentCollector getEnvironmentCollector() {
        return environmentCollector;
    }
    
    public ConfigLoader getConfigLoader() {
        return configLoader;
    }
    
    public void debug(String message) {
        if (configLoader.isDebugEnabled()) {
            getLogger().info("[DEBUG] " + message);
        }
    }
} 


