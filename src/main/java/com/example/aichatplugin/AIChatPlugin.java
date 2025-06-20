package com.example.aichatplugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import java.util.logging.Level;
import org.bukkit.event.Listener;
import com.example.aichatplugin.performance.PerformanceMonitor;
import com.example.aichatplugin.performance.HardwareMonitor;
import com.example.aichatplugin.commands.PerformanceCommand;
import com.example.aichatplugin.commands.ProfileCommand;
import com.example.aichatplugin.status.PluginStatusService;

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
 * 
 * 依赖关系：
 * - ConversationManager 依赖 DeepSeekAIService
 * - PlayerStatusListener 依赖 PlayerProfileManager
 * - PluginStatusService 依赖 PerformanceMonitor 和 HardwareMonitor
 */
public class AIChatPlugin extends JavaPlugin {
    private ConfigLoader configLoader;
    private ConversationManager conversationManager;
    private PlayerProfileManager playerProfileManager;
    private EnvironmentCollector environmentCollector;
    private DeepSeekAIService aiService;
    private PlayerStatusListener statusListener;
    private PlayerChatListener chatListener;
    private AIChatCommand chatCommand;
    private PerformanceCommand performanceCommand;
    private ProfileCommand profileCommand;
    private PerformanceMonitor performanceMonitor;
    private HardwareMonitor hardwareMonitor;
    private PluginStatusService statusService;
    
    @Override
    public void onEnable() {
        try {
            // 保存默认配置
            saveDefaultConfig();
            
            // 初始化配置加载器
            configLoader = new ConfigLoader(this);
            
            // 初始化服务
            initializeServices();
            
            // 注册命令
            registerCommand("ai", chatCommand);
            registerCommand("aichat", chatCommand);
            registerCommand("performance", performanceCommand);
            registerCommand("promote", chatCommand);
            registerCommand("profile", profileCommand);
            
            // 启动性能监控
            startPerformanceMonitoring();
            
            // 注册事件监听器
            registerListeners();
            
            getLogger().info("AIChatPlugin v" + getDescription().getVersion() + " 已启用");
        } catch (Exception e) {
            getLogger().severe("插件启动时发生错误");
            getLogger().log(Level.SEVERE, e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        try {
            // 按依赖顺序关闭组件
            if (performanceMonitor != null) {
                performanceMonitor.stop();
            }
            if (chatCommand != null) {
                chatCommand.shutdown();
            }
            if (performanceCommand != null) {
                performanceCommand.shutdown();
            }
            if (statusService != null) {
                statusService.shutdown();
            }
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
            getLogger().severe("插件关闭时发生错误");
            e.printStackTrace();
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
    
    public PlayerStatusListener getStatusListener() {
        return statusListener;
    }
    
    /**
     * 输出调试日志
     * @param message 调试信息
     */
    public void debug(String message) {
        if (getConfigLoader().isDebugEnabled()) {
            getLogger().info("[DEBUG] " + message);
        }
    }
    
    /**
     * 检查是否启用调试模式
     */
    public boolean isDebugEnabled() {
        return configLoader.getBoolean("debug", false);
    }
    
    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }
    
    public HardwareMonitor getHardwareMonitor() {
        return hardwareMonitor;
    }
    
    /**
     * 获取状态服务
     */
    public PluginStatusService getStatusService() {
        return statusService;
    }
    
    private void initializeServices() {
        // 按依赖顺序初始化组件
        this.playerProfileManager = new PlayerProfileManager(this);
        this.environmentCollector = new EnvironmentCollector(this);
        this.aiService = new DeepSeekAIService(this);
        this.conversationManager = new ConversationManager(this);
        
        // 初始化性能监控相关组件
        this.performanceMonitor = new PerformanceMonitor(this);
        this.hardwareMonitor = this.performanceMonitor.getHardwareMonitor();
        
        // 初始化状态服务
        this.statusService = new PluginStatusService(this, performanceMonitor, hardwareMonitor);
        
        // 初始化事件监听器
        this.statusListener = new PlayerStatusListener(this);
        this.chatListener = new PlayerChatListener(this);
        this.chatCommand = new AIChatCommand(this);
        this.performanceCommand = new PerformanceCommand(this);
        this.profileCommand = new ProfileCommand(this);
    }
    
    private void startPerformanceMonitoring() {
        // 启动性能监控
        performanceMonitor.start();
    }
    
    private void registerListeners() {
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(statusListener, this);
        getServer().getPluginManager().registerEvents(chatListener, this);
    }
    
    /**
     * 安全注册命令
     * @param commandName 命令名称
     * @param executor 命令执行器
     */
    private void registerCommand(String commandName, org.bukkit.command.CommandExecutor executor) {
        org.bukkit.command.PluginCommand command = getCommand(commandName);
        if (command != null) {
            command.setExecutor(executor);
            getLogger().info("已注册命令: /" + commandName);
        } else {
            getLogger().warning("无法注册命令: /" + commandName + " - 命令在plugin.yml中未定义");
        }
    }
} 


