package com.example.aichatplugin;

import com.example.aichatplugin.commands.PerformanceCommand;
import com.example.aichatplugin.commands.ProfileCommand;
import com.example.aichatplugin.commands.BenchmarkCommand;
import com.example.aichatplugin.web.WebServer;
import com.example.aichatplugin.performance.HardwareMonitor;
import com.example.aichatplugin.performance.PerformanceMonitor;
import com.example.aichatplugin.status.PluginStatusService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.List;

/**
 * AI Chat Plugin 主类
 * 版本: v1.1.0618
 * 
 * 这是一个功能强大的Minecraft智能聊天插件，基于DeepSeek AI API
 * 提供自然语言对话、环境感知、性能优化等功能
 * 
 * 主要组件：
 * 1. ConversationManager - 对话管理器
 * 2. DeepSeekAIService - AI服务
 * 3. EnvironmentCollector - 环境收集器
 * 4. PerformanceMonitor - 性能监控器
 * 5. PlayerProfileManager - 玩家档案管理器
 * 6. ConfigLoader - 配置加载器
 * 7. DiagnosticManager - 诊断管理器
 */
public class AIChatPlugin extends JavaPlugin {
    
    // 核心组件
    private ConversationManager conversationManager;
    private PlayerProfileManager profileManager;
    private PerformanceMonitor performanceMonitor;
    private EnvironmentCollector environmentCollector;
    private DiagnosticManager diagnosticManager;
    
    // 服务组件
    private DeepSeekAIService aiService;
    private PluginStatusService statusService;
    private ConfigLoader configLoader;
    private HardwareMonitor hardwareMonitor;
    
    // 监听器
    private PlayerStatusListener statusListener;
    private PlayerChatListener chatListener;
    
    // 命令处理器
    private AIChatCommand chatCommand;
    private PerformanceCommand performanceCommand;
    private ProfileCommand profileCommand;
    private BenchmarkCommand benchmarkCommand;
    
    // Web服务器
    private WebServer webServer;
    
    // 插件状态管理
    private static AIChatPlugin instance;
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private boolean debugMode = false;
    
    // 初始化任务
    private BukkitTask initTask;
    
    @Override
    public void onEnable() {
        getLogger().info("正在启动 AI Chat Plugin v" + getDescription().getVersion() + "...");
        
        // 🔧 紧急修复：检查配置文件是否损坏
        checkAndRepairConfig();
        
        // 初始化插件（异步）
        Bukkit.getScheduler().runTaskAsynchronously(this, this::initializePlugin);
    }
    
    /**
     * 🔧 检查并修复损坏的配置文件
     */
    private void checkAndRepairConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        
        if (!configFile.exists()) {
            getLogger().info("配置文件不存在，将创建默认配置");
            saveDefaultConfig();
            return;
        }
        
        // 尝试加载配置文件以检测YAML语法错误
        try {
            YamlConfiguration testConfig = YamlConfiguration.loadConfiguration(configFile);
            
            // 检查关键节点是否存在
            if (!testConfig.contains("settings")) {
                throw new Exception("缺少关键配置节点");
            }
            
            getLogger().info("配置文件检查通过");
            
        } catch (Exception e) {
            getLogger().severe("检测到配置文件损坏，正在进行紧急修复...");
            getLogger().severe("错误详情: " + e.getMessage());
            
            try {
                repairCorruptedConfig(configFile);
                getLogger().info("配置文件修复完成");
            } catch (Exception repairError) {
                getLogger().severe("配置文件修复失败: " + repairError.getMessage());
                getLogger().severe("将使用默认配置");
                
                // 最后的备用方案
                try {
                    configFile.delete();
                    saveDefaultConfig();
                } catch (Exception fallbackError) {
                    getLogger().severe("创建默认配置也失败了: " + fallbackError.getMessage());
                }
            }
        }
    }
    
    /**
     * 🔧 修复损坏的配置文件
     */
    private void repairCorruptedConfig(File configFile) throws Exception {
        getLogger().info("开始修复损坏的配置文件...");
        
        // 1. 备份损坏的文件
        File backupFile = new File(configFile.getParent(), 
            "config.yml.corrupted." + System.currentTimeMillis());
        java.nio.file.Files.copy(configFile.toPath(), backupFile.toPath());
        getLogger().info("已备份损坏的配置文件: " + backupFile.getName());
        
        // 2. 尝试提取API密钥
        String apiKey = extractApiKeyFromCorruptedFile(configFile);
        
        // 3. 删除损坏的文件并创建新的默认配置
        configFile.delete();
        saveDefaultConfig();
        getLogger().info("已重新创建默认配置文件");
        
        // 4. 如果提取到了API密钥，写入新配置
        if (apiKey != null && !apiKey.isEmpty() && !apiKey.equals("your-deepseek-api-key")) {
            try {
                YamlConfiguration newConfig = YamlConfiguration.loadConfiguration(configFile);
                newConfig.set("settings.api-key", apiKey);
                newConfig.save(configFile);
                
                // 修复API密钥格式
                fixApiKeyFormat(configFile, apiKey);
                
                getLogger().info("已恢复用户的API密钥: " + maskApiKey(apiKey));
            } catch (Exception e) {
                getLogger().warning("恢复API密钥时出错: " + e.getMessage());
            }
        }
        
        getLogger().info("配置文件修复完成");
    }
    
    /**
     * 🔧 从损坏的配置文件中提取API密钥
     */
    private String extractApiKeyFromCorruptedFile(File configFile) {
        try {
            List<String> lines = java.nio.file.Files.readAllLines(configFile.toPath(), 
                                                                  java.nio.charset.StandardCharsets.UTF_8);
            
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("api-key:")) {
                    String value = trimmed.substring(8).trim();
                    
                    // 移除引号
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    } else if (value.startsWith("'") && value.endsWith("'")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    
                    // 验证API密钥
                    if (value.length() > 10 && !value.equals("your-deepseek-api-key")) {
                        getLogger().info("从损坏配置中提取到API密钥");
                        return value;
                    }
                }
            }
            
            getLogger().info("未在损坏配置中找到有效的API密钥");
            return null;
            
        } catch (Exception e) {
            getLogger().warning("提取API密钥时出错: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 🔧 修复API密钥格式
     */
    private void fixApiKeyFormat(File configFile, String apiKey) {
        try {
            List<String> lines = java.nio.file.Files.readAllLines(configFile.toPath(), 
                                                                  java.nio.charset.StandardCharsets.UTF_8);
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.trim().startsWith("api-key:")) {
                    String indent = line.substring(0, line.indexOf("api-key:"));
                    String newLine = indent + "api-key: \"" + apiKey + "\"";
                    lines.set(i, newLine);
                    
                    java.nio.file.Files.write(configFile.toPath(), lines, 
                                             java.nio.charset.StandardCharsets.UTF_8);
                    getLogger().info("已修复API密钥的YAML格式");
                    break;
                }
            }
        } catch (Exception e) {
            getLogger().warning("修复API密钥格式时出错: " + e.getMessage());
        }
    }
    
    /**
     * 🔧 掩码API密钥用于日志显示
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
    
    /**
     * 初始化插件组件
     */
    private void initializePlugin() {
        try {
            getLogger().info("正在初始化插件组件...");
            
            // 设置实例
            instance = this;
            
            // 创建插件目录
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }
            
            // 初始化配置加载器
            configLoader = new ConfigLoader(this);
            debugMode = configLoader.isDebugEnabled();
            debug("调试模式已启用");
            
            // 2. 初始化硬件监控器
            hardwareMonitor = new HardwareMonitor(this);
            debug("硬件监控器已初始化");
            
            // 3. 初始化AI服务
            aiService = new DeepSeekAIService(this);
            debug("AI服务已初始化");
            
            // 4. 初始化环境收集器
            environmentCollector = new EnvironmentCollector(this);
            debug("环境收集器已初始化");
            
            // 5. 初始化玩家档案管理器
            profileManager = new PlayerProfileManager(this);
            debug("玩家档案管理器已初始化");
            
            // 6. 初始化对话管理器
            conversationManager = new ConversationManager(this);
            debug("对话管理器已初始化");
            
            // 7. 初始化性能监控器
            performanceMonitor = new PerformanceMonitor(this);
            performanceMonitor.setHardwareMonitor(hardwareMonitor);
            debug("性能监控器已初始化");
            
            // 8. 初始化状态服务（需要3个参数）
            statusService = new PluginStatusService(this, performanceMonitor, hardwareMonitor);
            debug("状态服务已初始化");
            
            // 9. 初始化诊断管理器
            diagnosticManager = new DiagnosticManager(this);
            debug("诊断管理器已初始化");
            
            // 在主线程中注册监听器和命令
            Bukkit.getScheduler().runTask(this, this::registerListenersAndCommands);
            
            getLogger().info("AI Chat Plugin 初始化完成!");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "插件初始化失败", e);
            Bukkit.getScheduler().runTask(this, () -> {
                Bukkit.getPluginManager().disablePlugin(this);
            });
        }
    }
    
    /**
     * 注册监听器和命令（必须在主线程中执行）
     */
    private void registerListenersAndCommands() {
        try {
            // 注册事件监听器
            statusListener = new PlayerStatusListener(this);
            chatListener = new PlayerChatListener(this);
            
            Bukkit.getPluginManager().registerEvents(statusListener, this);
            Bukkit.getPluginManager().registerEvents(chatListener, this);
            debug("事件监听器已注册");
            
            // 注册命令
            registerCommands();
            
            // 启动后台服务
            startServices();
            
            getLogger().info("AI Chat Plugin 启动完成! 所有功能已就绪。");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "注册监听器和命令失败", e);
        }
    }
    
    /**
     * 注册命令
     */
    private void registerCommands() {
        try {
            // 注册主命令
            chatCommand = new AIChatCommand(this);
            registerCommand("ai", chatCommand);
            registerCommand("aichat", chatCommand);
            
            // 注册性能命令
            performanceCommand = new PerformanceCommand(this);
            registerCommand("performance", performanceCommand);
            
            // 注册档案命令
            profileCommand = new ProfileCommand(this);
            registerCommand("profile", profileCommand);
            
            // 注册基准测试命令
            benchmarkCommand = new BenchmarkCommand(this);
            registerCommand("benchmark", benchmarkCommand);
            
            // 启动Web服务器
            if (configLoader.getBoolean("web.enabled", true)) {
                webServer = new WebServer(this);
                if (webServer.start()) {
                    debug("Web管理界面已启动: " + webServer.getUrl());
                } else {
                    getLogger().warning("Web管理界面启动失败");
                }
            }
            
            debug("所有命令已注册");
            
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "命令注册失败", e);
        }
    }
    
    /**
     * 安全注册命令的辅助方法
     */
    private void registerCommand(String commandName, Object executor) {
        try {
            PluginCommand command = getCommand(commandName);
            if (command != null) {
                if (executor instanceof org.bukkit.command.CommandExecutor) {
                    command.setExecutor((org.bukkit.command.CommandExecutor) executor);
                }
                if (executor instanceof org.bukkit.command.TabCompleter) {
                    command.setTabCompleter((org.bukkit.command.TabCompleter) executor);
                }
                debug("已注册命令: /" + commandName);
            } else {
                getLogger().warning("无法注册命令: /" + commandName + " (命令不存在于plugin.yml)");
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "注册命令 /" + commandName + " 时发生错误", e);
        }
    }
    
    /**
     * 启动后台服务
     */
    private void startServices() {
        try {
            // 启动性能监控
            if (configLoader.isAutoOptimizeEnabled()) {
                performanceMonitor.start();
                debug("性能监控已启动");
            }
            
            // 启动诊断监控
            if (debugMode) {
                diagnosticManager.startMonitoring();
                debug("诊断监控已启动");
            }
            
            // statusService 不需要显式启动
            debug("状态服务已就绪");
            
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "启动后台服务时发生错误", e);
        }
    }
    
    @Override
    public void onDisable() {
        isShuttingDown.set(true);
        getLogger().info("正在关闭 AI Chat Plugin...");
        
        try {
            // 取消初始化任务
            if (initTask != null && !initTask.isCancelled()) {
                initTask.cancel();
            }
            
            // 停止服务
            stopServices();
            
            // 保存数据
            saveData();
            
            // 关闭组件
            shutdownComponents();
            
            getLogger().info("AI Chat Plugin 已完全关闭");
            
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "插件关闭时发生错误", e);
        } finally {
            instance = null;
        }
    }
    
    /**
     * 停止后台服务
     */
    private void stopServices() {
        if (performanceMonitor != null) {
            performanceMonitor.stop();
            debug("性能监控已停止");
        }
        
        if (diagnosticManager != null) {
            diagnosticManager.stopMonitoring();
            debug("诊断监控已停止");
        }
        
        if (statusService != null) {
            statusService.shutdown();
            debug("状态服务已停止");
        }
    }
    
    /**
     * 保存数据
     */
    private void saveData() {
        CompletableFuture<Void> saveFuture = CompletableFuture.allOf(
            // 保存对话历史
            CompletableFuture.runAsync(() -> {
                if (conversationManager != null) {
                    conversationManager.forceSaveAll();
                    debug("对话历史已保存");
                }
            }),
            
            // 保存玩家档案
            CompletableFuture.runAsync(() -> {
                if (profileManager != null) {
                    profileManager.shutdown(); // 关闭时会自动保存
                    debug("玩家档案已保存");
                }
            })
        );
        
        try {
            // 等待最多5秒完成保存
            saveFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "保存数据时发生超时", e);
        }
    }
    
    /**
     * 关闭组件
     */
    private void shutdownComponents() {
        if (conversationManager != null) {
            conversationManager.shutdown();
        }
        
        if (aiService != null) {
            aiService.shutdown();
        }
        
        if (chatCommand != null) {
            chatCommand.shutdown();
        }
        
        if (statusListener != null) {
            statusListener.shutdown();
        }
        
        if (benchmarkCommand != null) {
            benchmarkCommand.shutdown();
        }
        
        if (webServer != null) {
            webServer.stop();
        }
        
        debug("所有组件已关闭");
    }
    
    /**
     * 重载配置
     */
    public void reloadPluginConfig() {
        try {
            reloadConfig();
            if (configLoader != null) {
                configLoader.reloadConfig();
            }
            
            debugMode = configLoader.isDebugEnabled();
            
            if (performanceMonitor != null) {
                performanceMonitor.reloadConfig();
            }
            
            getLogger().info("配置已重载");
            
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "重载配置时发生错误", e);
        }
    }
    
    // ==================== Getter 方法 ====================
    
    public static AIChatPlugin getInstance() {
        return instance;
    }
    
    public ConversationManager getConversationManager() {
        return conversationManager;
    }
    
    public PlayerProfileManager getProfileManager() {
        return profileManager;
    }
    
    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }
    
    public EnvironmentCollector getEnvironmentCollector() {
        return environmentCollector;
    }
    
    public DiagnosticManager getDiagnosticManager() {
        return diagnosticManager;
    }
    
    public DeepSeekAIService getAIService() {
        return aiService;
    }
    
    public PluginStatusService getStatusService() {
        return statusService;
    }
    
    public ConfigLoader getConfigLoader() {
        return configLoader;
    }
    
    public HardwareMonitor getHardwareMonitor() {
        return hardwareMonitor;
    }
    
    public PlayerStatusListener getStatusListener() {
        return statusListener;
    }
    
    public PlayerChatListener getChatListener() {
        return chatListener;
    }
    
    public WebServer getWebServer() {
        return webServer;
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 调试日志输出
     */
    public void debug(String message) {
        if (debugMode) {
            getLogger().info("[DEBUG] " + message);
        }
    }
    
    /**
     * 检查调试模式是否启用
     */
    public boolean isDebugEnabled() {
        return debugMode;
    }
    
    /**
     * 设置调试模式
     */
    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
        getLogger().info("调试模式已" + (debug ? "启用" : "禁用"));
    }
    
    /**
     * 检查插件是否正在关闭
     */
    public boolean isShuttingDown() {
        return isShuttingDown.get();
    }
    
    /**
     * 获取插件版本
     */
    public String getPluginVersion() {
        return getDescription().getVersion();
    }
    
    /**
     * 获取插件数据目录
     */
    public File getPluginDataFolder() {
        return getDataFolder();
    }
    
    /**
     * 检查插件是否完全初始化
     */
    public boolean isFullyInitialized() {
        return conversationManager != null && 
               aiService != null && 
               configLoader != null &&
               !isShuttingDown.get();
    }
    
    /**
     * 获取插件状态摘要
     */
    public String getStatusSummary() {
        if (!isFullyInitialized()) {
            return "插件未完全初始化";
        }
        
        StringBuilder status = new StringBuilder();
        status.append("AI Chat Plugin v").append(getPluginVersion()).append("\n");
        status.append("调试模式: ").append(debugMode ? "启用" : "禁用").append("\n");
        status.append("性能监控: ").append(performanceMonitor != null ? "运行中" : "未启动").append("\n");
        
        if (performanceMonitor != null) {
            status.append("当前模式: ").append(performanceMonitor.getCurrentMode()).append("\n");
            status.append("当前TPS: ").append(String.format("%.1f", performanceMonitor.getCurrentTPS()));
        }
        
        return status.toString();
    }
} 