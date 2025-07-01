package com.example.aichatplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;
import org.bukkit.permissions.PermissionAttachmentInfo;
import java.util.logging.Level;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.ChatColor;
import java.io.File;

/**
 * AI聊天命令处理器
 * 
 * 职责：
 * 1. 处理玩家输入的AI聊天命令
 * 2. 转发消息到对话管理器
 * 3. 处理命令参数
 * 4. 实现权限检查、冷却时间和敏感词过滤
 */
public class AIChatCommand implements CommandExecutor {
    private final AIChatPlugin plugin;
    private final ConversationManager conversationManager;
    private final ConfigLoader config;
    
    // 冷却时间管理
    private final Map<UUID, Long> cooldownMap = new ConcurrentHashMap<>();
    private final Map<UUID, ConcurrentLinkedQueue<Long>> messageTimestamps = new ConcurrentHashMap<>();
    
    // 敏感词过滤 - 性能优化版
    private SensitiveFilter sensitiveFilter;
    private boolean filterEnabled;
    private int filterOptimizationLevel = 0; // 0=完整, 1=轻度, 2=中度, 3=禁用
    
    // 消息限制
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_MESSAGES_PER_MINUTE = 20;
    private static final int MAX_BUKKIT_MESSAGE_LENGTH = 256;
    
    // 全局重置任务
    private BukkitTask resetTask;
    private BukkitTask cleanupTask;
    
    // 消息预处理器
    private final List<MessagePreprocessor> preprocessors = new ArrayList<>();
    private static final int MAX_PREPROCESSOR_CHAIN_LENGTH = 10;
    
    // 帮助系统缓存（优化版）
    private final Map<String, CacheEntry> helpCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_ENTRIES = 50;
    
    // 统计计数器（高性能版）
    private final LongAdder successCount = new LongAdder();
    private final LongAdder filterCount = new LongAdder();
    private final LongAdder rateLimitCount = new LongAdder();
    
    // 熔断机制
    private final LongAdder errorCount = new LongAdder();
    private volatile long lastErrorReset = System.currentTimeMillis();
    private static final int ERROR_THRESHOLD = 10;
    private static final long ERROR_WINDOW = TimeUnit.MINUTES.toMillis(5);
    
    // 配置哈希缓存
    private volatile String cachedConfigHash;
    private volatile long configHashTimestamp = 0;
    
    // 缓存条目
    private static class CacheEntry {
        final List<String> content;
        final long timestamp;
        final String configHash;
        
        CacheEntry(List<String> content, String configHash) {
            this.content = new ArrayList<>(content);
            this.timestamp = System.currentTimeMillis();
            this.configHash = configHash;
        }
        
        boolean isExpired(long maxAge) {
            return System.currentTimeMillis() - timestamp > maxAge;
        }
        
        boolean isConfigChanged(String currentHash) {
            return !configHash.equals(currentHash);
        }
    }
    
    public AIChatCommand(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.conversationManager = plugin.getConversationManager();
        this.config = plugin.getConfigLoader();
        
        // 初始化敏感词过滤
        initializeFilter();
        
        // 启动资源清理任务
        startIncrementalCleanupTask();
    }
    
    public void shutdown() {
        if (resetTask != null) {
            resetTask.cancel();
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        
        // 清理所有缓存和数据
        helpCache.clear();
        cooldownMap.clear();
        messageTimestamps.clear();
    }
    
    public void registerPreprocessor(MessagePreprocessor preprocessor) {
        if (preprocessors.size() >= MAX_PREPROCESSOR_CHAIN_LENGTH) {
            plugin.getLogger().warning("预处理器链长度超过限制，忽略新的预处理器");
            return;
        }
        preprocessors.add(preprocessor);
    }
    
    private void initializeFilter() {
        filterEnabled = config.getBoolean("filter.enabled", true);
        if (filterEnabled) {
            String wordsString = config.getString("filter.words", "");
            if (wordsString != null && !wordsString.trim().isEmpty()) {
                List<String> words = Arrays.asList(wordsString.split(","));
                sensitiveFilter = new SensitiveFilter(words);
            } else {
                filterEnabled = false;
            }
        }
    }
    
    /**
     * 应用性能优化配置
     * 根据性能监控器的功能管理器调整各功能的运行状态
     */
    public void applyPerformanceOptimization() {
        if (plugin.getPerformanceMonitor() != null) {
            var featureManager = plugin.getPerformanceMonitor().getFeatureManager();
            
            // 应用敏感词过滤优化
            if (!featureManager.isFeatureEnabled("sensitive_word_filter")) {
                filterEnabled = false;
                filterOptimizationLevel = 3;
                plugin.debug("性能优化：敏感词过滤已禁用");
            } else {
                filterEnabled = config.getBoolean("filter.enabled", true);
                filterOptimizationLevel = featureManager.getOptimizationLevel("sensitive_word_filter");
                plugin.debug("敏感词过滤优化级别: " + filterOptimizationLevel);
            }
            
            // 重新初始化过滤器（如果需要）
            if (filterEnabled && filterOptimizationLevel < 3) {
                initializeOptimizedFilter();
            }
        }
    }
    
    /**
     * 初始化优化版敏感词过滤器
     */
    private void initializeOptimizedFilter() {
        String wordsString = config.getString("filter.words", "");
        if (wordsString != null && !wordsString.trim().isEmpty()) {
            List<String> words = Arrays.asList(wordsString.split(","));
            
            // 根据优化级别调整敏感词列表
            switch (filterOptimizationLevel) {
                case 1:
                    // 轻度优化：只保留高优先级敏感词（前50%）
                    words = words.subList(0, Math.max(1, words.size() / 2));
                    break;
                case 2:
                    // 中度优化：只保留最高优先级敏感词（前25%）
                    words = words.subList(0, Math.max(1, words.size() / 4));
                    break;
            }
            
            sensitiveFilter = new SensitiveFilter(words, filterOptimizationLevel);
        }
    }
    
    private void startIncrementalCleanupTask() {
        // 每30秒执行增量清理
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            incrementalCleanup();
        }, 600, 600); // 30秒后开始，每30秒执行
    }
    
    private void incrementalCleanup() {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        long cacheMaxAge = TimeUnit.HOURS.toMillis(1);
        int processed = 0;
        int removed = 0;
        
        // 使用迭代器安全清理，避免数组复制
        Iterator<Map.Entry<UUID, Long>> cooldownIterator = cooldownMap.entrySet().iterator();
        while (cooldownIterator.hasNext() && processed < 100) {
            Map.Entry<UUID, Long> entry = cooldownIterator.next();
            processed++;
            
            if (entry.getValue() < cutoff) {
                cooldownIterator.remove();
                messageTimestamps.remove(entry.getKey());
                removed++;
            }
        }
        
        // 清理帮助缓存
        int cacheRemoved = 0;
        if (helpCache.size() > MAX_CACHE_ENTRIES) {
            // 清理过期缓存
            Iterator<Map.Entry<String, CacheEntry>> cacheIterator = helpCache.entrySet().iterator();
            while (cacheIterator.hasNext()) {
                Map.Entry<String, CacheEntry> entry = cacheIterator.next();
                if (entry.getValue().isExpired(cacheMaxAge)) {
                    cacheIterator.remove();
                    cacheRemoved++;
                }
            }
        }
        
        // 只在有清理时输出日志
        if (removed > 0 || cacheRemoved > 0) {
            plugin.getLogger().fine(String.format("增量清理: 用户数据 %d/%d, 缓存 %d", 
                removed, processed, cacheRemoved));
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 熔断检查
        if (isCircuitBreakerOpen()) {
            sender.sendMessage("§c服务暂时不可用，请稍后重试");
            return true;
        }
        
        // 1. 检查发送者类型
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getMessageFormat("error").replace("{error}", "该命令只能由玩家执行"));
            return true;
        }
        
        Player player = (Player) sender;
        
        // promote命令处理
        if (label.equalsIgnoreCase("promote")) {
            if (!player.hasPermission("aichat.promote")) {
                player.sendMessage("你没有权限执行此命令！");
                return true;
            }
            if (args.length != 1) {
                player.sendMessage("用法: /promote <玩家名>");
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[0]);
            if (target == null) {
                player.sendMessage("找不到玩家: " + args[0]);
                return true;
            }
            List<String> vipUsers = config.getConfig().getStringList("advanced.vip-users");
            if (!vipUsers.contains(target.getUniqueId().toString())) {
                vipUsers.add(target.getUniqueId().toString());
                config.set("advanced.vip-users", vipUsers);
                config.saveConfig();
                player.sendMessage("已将 " + target.getName() + " 提升为VIP用户！");
            } else {
                player.sendMessage(target.getName() + " 已经是VIP用户。");
            }
            return true;
        }
        
        // 2. 处理帮助命令（无参数）
        if (args.length == 0) {
            sendHelpMessage(player, 1);
            return true;
        }
        
        // 3. 处理管理员子命令
        String subCommand = args[0].toLowerCase();
        if ("reload".equals(subCommand)) {
            return checkPermission(player, "aichat.admin") && handleReload(player);
        }
        if ("stats".equals(subCommand)) {
            return checkPermission(player, "aichat.admin") && handleStats(player);
        }
        if ("status".equals(subCommand)) {
            return checkPermission(player, "aichat.admin") && handleStatus(player);
        }
        if ("debug".equals(subCommand)) {
            return checkPermission(player, "aichat.admin") && handleDebug(player);
        }
        if ("performance".equals(subCommand) || "perf".equals(subCommand)) {
            return checkPermission(player, "aichat.admin") && handlePerformance(player, Arrays.copyOfRange(args, 1, args.length));
        }
        if ("save".equals(subCommand)) {
            return checkPermission(player, "aichat.admin") && handleSave(player);
        }
        if ("clear".equals(subCommand) || "clearhistory".equals(subCommand)) {
            return handleClearHistory(player);
        }
        if ("test-config".equals(subCommand)) {
            testConfigSync(player);
            return true;
        }
        if ("diagnose".equals(subCommand)) {
            if (plugin.getDiagnosticManager() != null) {
                plugin.getDiagnosticManager().runConfigDiagnostics(player);
            } else {
                player.sendMessage("§c诊断管理器未初始化");
            }
            return true;
        }
        
        // 4. 处理帮助子命令
        if ("help".equalsIgnoreCase(args[0])) {
            int page = 1;
            if (args.length > 1) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "无效的页码。");
                    return true;
                }
            }
            sendHelpMessage(player, page);
            return true;
        }
        
        // 5. 检查基础权限
        if (!checkPermission(player, "aichat.use")) {
            return true;
        }
        
        // 6. 检查冷却时间
        if (!checkCooldown(player)) {
            return true;
        }
        
        // 7. 检查消息频率
        if (!checkMessageFrequency(player)) {
            rateLimitCount.increment();
            return true;
        }
        
        // 8. 处理聊天消息
        return processChatMessage(player, args);
    }
    
    private boolean isCircuitBreakerOpen() {
        long now = System.currentTimeMillis();
        if (now - lastErrorReset > ERROR_WINDOW) {
            errorCount.reset();
            lastErrorReset = now;
        }
        return errorCount.sum() >= ERROR_THRESHOLD;
    }
    
    private boolean checkPermission(Player player, String permission) {
        if (player.hasPermission(permission)) {
            return true;
        }
        player.sendMessage(config.getMessageFormat("no_permission"));
        return false;
    }
    
    private boolean handleReload(Player player) {
        try {
            config.reloadConfig();
            initializeFilter();
            helpCache.clear();
            cachedConfigHash = null; // 清除配置哈希缓存
            player.sendMessage("§a配置已重载");
        } catch (Exception e) {
            String errorMsg = config.getMessageFormat("error").replace("{error}", "配置重载失败");
            player.sendMessage(errorMsg);
            plugin.getLogger().log(Level.SEVERE, "配置重载失败", e);
            errorCount.increment();
        }
        return true;
    }
    
    private boolean handleStats(Player player) {
        player.sendMessage("§6=== AI聊天统计 ===");
        player.sendMessage(String.format("§f成功处理: §a%,d", successCount.sum()));
        player.sendMessage(String.format("§f敏感词拦截: §c%,d", filterCount.sum()));
        player.sendMessage(String.format("§f频率限制: §e%,d", rateLimitCount.sum()));
        player.sendMessage(String.format("§f活跃用户: §b%,d", cooldownMap.size()));
        player.sendMessage(String.format("§f缓存大小: §d%,d", helpCache.size()));
        player.sendMessage(String.format("§f错误计数: §c%,d", errorCount.sum()));
        
        // 性能优化信息
        if (plugin.getPerformanceMonitor() != null) {
            var performanceMonitor = plugin.getPerformanceMonitor();
            var currentMode = performanceMonitor.getCurrentMode();
            var featureReport = performanceMonitor.getFeatureStatusReport();
            
            player.sendMessage("§6=== 性能优化状态 ===");
            player.sendMessage(String.format("§f当前模式: §b%s", currentMode));
            player.sendMessage(String.format("§f优化级别: §e%s", featureReport.get("optimizationLevel")));
            player.sendMessage(String.format("§f敏感词过滤: §%c%s", 
                filterEnabled ? 'a' : 'c', 
                filterEnabled ? "启用(级别" + filterOptimizationLevel + ")" : "禁用"));
            
            @SuppressWarnings("unchecked")
            List<String> disabledFeatures = (List<String>) featureReport.get("disabledFeatures");
            if (!disabledFeatures.isEmpty()) {
                player.sendMessage("§f已禁用功能: §c" + String.join(", ", disabledFeatures));
            }
        }
        
        return true;
    }
    
    private boolean handleStatus(Player player) {
        try {
            player.sendMessage("§6=== AI聊天插件状态 ===");
            
            // 基本信息
            player.sendMessage(String.format("§f版本: §a%s", plugin.getDescription().getVersion()));
            player.sendMessage(String.format("§f状态: §%c%s", 
                plugin.isEnabled() ? 'a' : 'c',
                plugin.isEnabled() ? "运行中" : "已禁用"));
            
            // 缓存信息
            player.sendMessage(String.format("§f帮助缓存: §b%,d", helpCache.size()));
            player.sendMessage(String.format("§f错误计数: §c%,d", errorCount.sum()));
            
            // 历史记录状态
            if (plugin.getConversationManager() != null) {
                String dirtyStats = plugin.getConversationManager().getDirtyStatsMessage();
                player.sendMessage("§f历史记录: §e" + dirtyStats);
            }
            
            // 熔断器状态
            player.sendMessage(String.format("§f熔断器: §%c%s", 
                isCircuitBreakerOpen() ? 'c' : 'a',
                isCircuitBreakerOpen() ? "开启（保护中）" : "正常"));
            
            // 性能监控
            if (plugin.getPerformanceMonitor() != null) {
                var performanceMonitor = plugin.getPerformanceMonitor();
                var getCurrentTPSMethod = performanceMonitor.getClass().getMethod("getCurrentTPS");
                var currentTPS = (Double) getCurrentTPSMethod.invoke(performanceMonitor);
                
                player.sendMessage("§6=== 性能状态 ===");
                player.sendMessage(String.format("§f当前TPS: §%c%.1f", 
                    currentTPS >= 18.0 ? 'a' : (currentTPS >= 15.0 ? 'e' : 'c'), currentTPS));
                player.sendMessage(String.format("§f性能监控: §a正常"));
            } else {
                player.sendMessage("§f性能监控: §c未启用");
            }
            
            // 熔断器状态
            player.sendMessage("§6=== 保护机制 ===");
            player.sendMessage(String.format("§f熔断器: §%c%s", 
                isCircuitBreakerOpen() ? 'c' : 'a',
                isCircuitBreakerOpen() ? "开启（保护中）" : "正常"));
            player.sendMessage(String.format("§f错误计数: §%c%d", 
                errorCount.sum() > ERROR_THRESHOLD / 2 ? 'e' : 'a', errorCount.sum()));
            
        } catch (Exception e) {
            player.sendMessage("§c获取状态信息失败: " + e.getMessage());
            plugin.getLogger().log(Level.WARNING, "获取状态信息失败", e);
        }
        return true;
    }
    
    private boolean handleSave(Player player) {
        if (!player.hasPermission("aichat.admin")) {
            player.sendMessage("§c你没有权限执行此命令");
            return true;
        }
        
        try {
            player.sendMessage("§6正在强制保存历史记录...");
            
            if (plugin.getConversationManager() != null) {
                String beforeStats = plugin.getConversationManager().getDirtyStatsMessage();
                player.sendMessage("§e保存前: " + beforeStats);
                
                // 执行强制保存
                plugin.getConversationManager().forceSaveAll();
                
                String afterStats = plugin.getConversationManager().getDirtyStatsMessage();
                player.sendMessage("§a保存后: " + afterStats);
                player.sendMessage("§a历史记录保存完成！");
            } else {
                player.sendMessage("§c对话管理器未初始化");
            }
            
        } catch (Exception e) {
            player.sendMessage("§c保存历史记录失败: " + e.getMessage());
            plugin.getLogger().log(Level.WARNING, "强制保存历史记录失败", e);
        }
        
        return true;
    }
    
    private boolean handleClearHistory(Player player) {
        try {
            player.sendMessage("§6正在清空历史记录...");
            
            if (plugin.getConversationManager() != null) {
                // 获取清空前的统计信息
                String beforeStats = plugin.getConversationManager().getDirtyStatsMessage();
                player.sendMessage("§e清空前: " + beforeStats);
                
                // 清空玩家的历史记录
                UUID playerId = player.getUniqueId();
                plugin.getConversationManager().clearPlayerHistory(playerId);
                
                // 显示清空结果
                player.sendMessage("§a✅ 历史记录已清空！");
                player.sendMessage("§a现在AI将使用全新的对话风格，不再受旧记录影响。");
                player.sendMessage("§e建议: 说句\"你好\"测试新的对话风格");
            } else {
                player.sendMessage("§c对话管理器未初始化");
            }
            
        } catch (Exception e) {
            player.sendMessage("§c清空历史记录失败: " + e.getMessage());
            plugin.getLogger().log(Level.WARNING, "清空历史记录失败", e);
        }
        
        return true;
    }
    
    private boolean handleDebug(Player player) {
        try {
            // 切换调试模式
            boolean currentDebug = plugin.isDebugEnabled();
            config.setDebugEnabled(!currentDebug);
            config.reloadConfig(); // 重载配置使更改生效
            
            boolean newDebug = plugin.isDebugEnabled();
            player.sendMessage(String.format("§6调试模式已%s", 
                newDebug ? "§a开启" : "§c关闭"));
            
            if (newDebug) {
                player.sendMessage("§e现在将在控制台显示详细的调试信息");
                plugin.debug("调试模式已由 " + player.getName() + " 开启");
            } else {
                plugin.debug("调试模式已由 " + player.getName() + " 关闭");
                player.sendMessage("§e调试信息已停止输出");
            }
            
        } catch (Exception e) {
            player.sendMessage("§c切换调试模式失败: " + e.getMessage());
            plugin.getLogger().log(Level.WARNING, "切换调试模式失败", e);
        }
        return true;
    }
    
    /**
     * 处理性能管理命令
     * /ai performance [status|mode <mode>|feature <feature> <enable|disable>]
     */
    private boolean handlePerformance(Player player, String[] args) {
        if (plugin.getPerformanceMonitor() == null) {
            player.sendMessage("§c性能监控器未启用");
            return true;
        }
        
        var performanceMonitor = plugin.getPerformanceMonitor();
        var featureManager = performanceMonitor.getFeatureManager();
        
        if (args.length == 0) {
            // 显示性能状态
            return showPerformanceStatus(player, performanceMonitor);
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "status":
                return showPerformanceStatus(player, performanceMonitor);
                
            case "mode":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /ai performance mode <FULL|LITE|BASIC|EMERGENCY>");
                    return true;
                }
                return setPerformanceMode(player, args[1]);
                
            case "feature":
                if (args.length < 3) {
                    player.sendMessage("§c用法: /ai performance feature <功能名> <enable|disable>");
                    return true;
                }
                return toggleFeature(player, featureManager, args[1], args[2]);
                
            case "optimize":
                // 手动触发性能优化
                applyPerformanceOptimization();
                player.sendMessage("§a已手动应用性能优化");
                return true;
                
            default:
                player.sendMessage("§c用法: /ai performance <status|mode|feature|optimize>");
                return true;
        }
    }
    
    /**
     * 显示性能状态
     */
    private boolean showPerformanceStatus(Player player, Object performanceMonitor) {
        try {
            // 使用反射获取性能信息（避免编译时依赖）
            var getCurrentModeMethod = performanceMonitor.getClass().getMethod("getCurrentMode");
            var getFeatureStatusReportMethod = performanceMonitor.getClass().getMethod("getFeatureStatusReport");
            var getCurrentTPSMethod = performanceMonitor.getClass().getMethod("getCurrentTPS");
            
            var currentMode = getCurrentModeMethod.invoke(performanceMonitor);
            var featureReport = (Map<String, Object>) getFeatureStatusReportMethod.invoke(performanceMonitor);
            var currentTPS = (Double) getCurrentTPSMethod.invoke(performanceMonitor);
            
            player.sendMessage("§6=== 性能监控状态 ===");
            player.sendMessage(String.format("§f当前TPS: §%c%.1f", 
                currentTPS >= 18.0 ? 'a' : (currentTPS >= 15.0 ? 'e' : 'c'), currentTPS));
            player.sendMessage(String.format("§f运行模式: §b%s", currentMode));
            player.sendMessage(String.format("§f优化级别: §e%s", featureReport.get("optimizationLevel")));
            
            // 显示功能状态
            @SuppressWarnings("unchecked")
            Map<String, Boolean> featureStatus = (Map<String, Boolean>) featureReport.get("featureStatus");
            player.sendMessage("§6=== 功能状态 ===");
            
            // 按类别显示功能
            showFeaturesByCategory(player, featureStatus, "核心功能", 
                Arrays.asList("basic_chat", "command_processing", "error_handling"));
            showFeaturesByCategory(player, featureStatus, "重要功能", 
                Arrays.asList("permission_check", "cooldown_management", "rate_limiting", "message_validation"));
            showFeaturesByCategory(player, featureStatus, "增强功能", 
                Arrays.asList("sensitive_word_filter", "message_preprocessing", "help_system", "statistics_collection"));
            showFeaturesByCategory(player, featureStatus, "高级功能", 
                Arrays.asList("advanced_caching", "detailed_logging", "performance_monitoring", "circuit_breaker", "config_hot_reload"));
            
            @SuppressWarnings("unchecked")
            List<String> disabledFeatures = (List<String>) featureReport.get("disabledFeatures");
            if (!disabledFeatures.isEmpty()) {
                player.sendMessage("§c已禁用功能: " + String.join(", ", disabledFeatures));
            }
            
        } catch (Exception e) {
            player.sendMessage("§c获取性能状态失败: " + e.getMessage());
            plugin.getLogger().log(Level.WARNING, "获取性能状态失败", e);
        }
        
        return true;
    }
    
    /**
     * 按类别显示功能状态
     */
    private void showFeaturesByCategory(Player player, Map<String, Boolean> featureStatus, 
                                       String categoryName, List<String> features) {
        List<String> enabled = new ArrayList<>();
        List<String> disabled = new ArrayList<>();
        
        for (String feature : features) {
            if (featureStatus.containsKey(feature)) {
                if (featureStatus.get(feature)) {
                    enabled.add(feature);
                } else {
                    disabled.add(feature);
                }
            }
        }
        
        if (!enabled.isEmpty() || !disabled.isEmpty()) {
            player.sendMessage("§f" + categoryName + ":");
            if (!enabled.isEmpty()) {
                player.sendMessage("  §a启用: " + String.join(", ", enabled));
            }
            if (!disabled.isEmpty()) {
                player.sendMessage("  §c禁用: " + String.join(", ", disabled));
            }
        }
    }
    
    /**
     * 设置性能模式
     */
    private boolean setPerformanceMode(Player player, String modeStr) {
        // 这里需要通过插件主类来设置模式，因为直接设置可能不安全
        player.sendMessage("§e性能模式切换功能需要通过自动优化系统控制");
        player.sendMessage("§e请使用 §f/ai performance optimize §e手动触发优化");
        return true;
    }
    
    /**
     * 切换功能状态
     */
    private boolean toggleFeature(Player player, Object featureManager, String featureName, String action) {
        try {
            var setFeatureEnabledMethod = featureManager.getClass().getMethod("setFeatureEnabled", String.class, boolean.class);
            var isFeatureEnabledMethod = featureManager.getClass().getMethod("isFeatureEnabled", String.class);
            
            boolean enable = "enable".equalsIgnoreCase(action);
            boolean currentState = (Boolean) isFeatureEnabledMethod.invoke(featureManager, featureName);
            
            if (currentState == enable) {
                player.sendMessage(String.format("§e功能 %s 已经是 %s 状态", 
                    featureName, enable ? "启用" : "禁用"));
                return true;
            }
            
            setFeatureEnabledMethod.invoke(featureManager, featureName, enable);
            player.sendMessage(String.format("§a已%s功能: %s", 
                enable ? "启用" : "禁用", featureName));
            
            // 重新应用优化
            applyPerformanceOptimization();
            
        } catch (Exception e) {
            player.sendMessage("§c切换功能状态失败: " + e.getMessage());
            plugin.getLogger().log(Level.WARNING, "切换功能状态失败", e);
        }
        
        return true;
    }
    
    private boolean processChatMessage(Player player, String[] args) {
        try {
            // 构建并验证消息
            String fullMessage = String.join(" ", args);
            
            // 性能优化：根据功能管理器状态调整处理流程
            if (!validateMessageWithOptimization(player, fullMessage)) {
                return true;
            }
            
            // 预处理消息（性能优化版）
            fullMessage = preprocessMessageWithOptimization(player, fullMessage);
            
            // 预处理后重新验证
            if (!validateMessageWithOptimization(player, fullMessage)) {
                return true;
            }
            
            // 处理敏感词（性能优化版）
            if (shouldCheckSensitiveWords() && 
                sensitiveFilter != null && 
                sensitiveFilter.containsSensitiveWord(fullMessage)) {
                player.sendMessage(config.getMessageFormat("filtered"));
                filterCount.increment();
                return true;
            }
            
            // 转发到对话管理器
            conversationManager.processMessage(player, fullMessage, "chat");
            // 更新冷却时间和消息计数
            updateCooldown(player);
            updateMessageCount(player);
            successCount.increment();
            
        } catch (Exception e) {
            handleProcessError(player, e);
            errorCount.increment();
        }
        
        return true;
    }
    
    /**
     * 性能优化版消息验证
     */
    private boolean validateMessageWithOptimization(Player player, String message) {
        if (plugin.getPerformanceMonitor() != null) {
            var featureManager = plugin.getPerformanceMonitor().getFeatureManager();
            
            // 检查消息验证功能是否启用
            if (!featureManager.isFeatureEnabled("message_validation")) {
                return true; // 跳过验证以节省性能
            }
            
            // 根据优化级别调整验证严格程度
            int optimizationLevel = featureManager.getOptimizationLevel("message_validation");
            switch (optimizationLevel) {
                case 2:
                    // 中度优化：只检查基本长度
                    return message.length() <= MAX_MESSAGE_LENGTH;
                case 1:
                    // 轻度优化：跳过一些复杂检查
                    return validateMessageLite(player, message);
                default:
                    // 完整验证
                    return validateMessage(player, message);
            }
        }
        
        return validateMessage(player, message);
    }
    
    /**
     * 轻量级消息验证
     */
    private boolean validateMessageLite(Player player, String message) {
        // 基本长度检查
        if (message.length() > MAX_MESSAGE_LENGTH) {
            player.sendMessage("§c消息过长");
            return false;
        }
        
        // 跳过复杂的Unicode检查等
        return true;
    }
    
    /**
     * 性能优化版消息预处理
     */
    private String preprocessMessageWithOptimization(Player player, String message) {
        if (plugin.getPerformanceMonitor() != null) {
            var featureManager = plugin.getPerformanceMonitor().getFeatureManager();
            
            // 检查预处理功能是否启用
            if (!featureManager.isFeatureEnabled("message_preprocessing")) {
                return message; // 跳过预处理
            }
            
            // 根据优化级别调整预处理程度
            int optimizationLevel = featureManager.getOptimizationLevel("message_preprocessing");
            if (optimizationLevel >= 2) {
                return message; // 跳过预处理以节省性能
            }
        }
        
        return preprocessMessage(player, message);
    }
    
    /**
     * 检查是否应该进行敏感词检查
     */
    private boolean shouldCheckSensitiveWords() {
        if (!filterEnabled || filterOptimizationLevel >= 3) {
            return false; // 已禁用
        }
        
        if (plugin.getPerformanceMonitor() != null) {
            var featureManager = plugin.getPerformanceMonitor().getFeatureManager();
            return featureManager.isFeatureEnabled("sensitive_word_filter");
        }
        
        return filterEnabled;
    }
    
    private boolean checkCooldown(Player player) {
        long cooldown = getCooldownTime(player);
        long lastUsed = cooldownMap.getOrDefault(player.getUniqueId(), 0L);
        long timeLeft = cooldown - (System.currentTimeMillis() - lastUsed);
        
        if (timeLeft > 0) {
            String timeLeftStr = String.format("%.1f", timeLeft / 1000.0);
            player.sendMessage(config.getMessageFormat("cooldown").replace("{time}", timeLeftStr));
            return false;
        }
        return true;
    }
    
    private long getCooldownTime(Player player) {
        // 支持多级权限
        if (player.hasPermission("aichat.cooldown.none")) {
            return 0;
        }
        if (player.hasPermission("aichat.cooldown.vip") || player.hasPermission("aichat.vip")) {
            return config.getVipUserCooldown();
        }
        
        return config.getNormalUserCooldown();
    }
    
    private boolean checkMessageFrequency(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        
        ConcurrentLinkedQueue<Long> timestamps = messageTimestamps.computeIfAbsent(uuid, 
            k -> new ConcurrentLinkedQueue<>());
        
        // 清理过期记录并计数
        int validCount = 0;
        Long oldestValid = null;
        
        Iterator<Long> iterator = timestamps.iterator();
        while (iterator.hasNext()) {
            Long ts = iterator.next();
            if (now - ts > 60000) {
                iterator.remove();
            } else {
                validCount++;
                if (oldestValid == null || ts < oldestValid) {
                    oldestValid = ts;
                }
            }
        }
        
        if (validCount >= MAX_MESSAGES_PER_MINUTE) {
            long nextAvailable = oldestValid != null ? 
                (oldestValid + 60000) - now : 1000;
            String timeLeftStr = String.format("%.1f", Math.max(0.1, nextAvailable / 1000.0));
            player.sendMessage(config.getMessageFormat("rate-limit").replace("{time}", timeLeftStr));
            return false;
        }
        return true;
    }
    
    private void sendHelpMessage(Player player, int page) {
        // 从ConfigLoader获取帮助消息
        String pageHeader = plugin.getConfigLoader().getMessageFormat("help.page-header");
        String header = plugin.getConfigLoader().getMessageFormat("help.header");
        String basic = plugin.getConfigLoader().getMessageFormat("help.basic");
        String admin = plugin.getConfigLoader().getMessageFormat("help.admin");
        String footer = plugin.getConfigLoader().getMessageFormat("help.footer");
        
        // 添加基础帮助内容（如果配置中没有）
        addIfNotEmpty(player, pageHeader, "§6=== AI聊天助手帮助 ===");
        addIfNotEmpty(player, header, "§e基本命令:");
        addIfNotEmpty(player, basic, "§f/ai <消息> §7- 与AI聊天\n§f/aichat help §7- 显示帮助\n§f/aichat stats §7- 查看统计信息\n§f/aichat status §7- 查看插件状态");
        
        // 管理员命令（需要权限）
        if (player.hasPermission("aichat.admin")) {
            addIfNotEmpty(player, admin, "§c管理员命令:\n§f/aichat reload §7- 重新加载配置\n§f/aichat debug §7- 切换调试模式\n§f/aichat performance §7- 性能管理\n§f/aichat save §7- 强制保存历史记录");
        }
        
        addIfNotEmpty(player, footer, "§7使用 /ai <消息> 开始对话！");
    }
    
    private String getOrGenerateConfigHash() {
        long now = System.currentTimeMillis();
        // 缓存5分钟
        if (cachedConfigHash == null || now - configHashTimestamp > TimeUnit.MINUTES.toMillis(5)) {
            cachedConfigHash = generateConfigHash();
            configHashTimestamp = now;
        }
        return cachedConfigHash;
    }
    
    private String generateConfigHash() {
        // 使用排序确保一致性
        List<String> configItems = Arrays.asList(
            String.valueOf(config.getInt("help.page_size", 8)),
            config.getMessageFormat("help.header"),
            config.getMessageFormat("help.basic"),
            config.getMessageFormat("help.admin"),
            config.getMessageFormat("help.vip"),
            config.getMessageFormat("help.empty"),
            config.getMessageFormat("help.page-header"),
            config.getMessageFormat("help.page_footer")
        );
        
        // 过滤空值并排序
        List<String> filteredItems = configItems.stream()
            .filter(item -> item != null && !item.isEmpty())
            .sorted()
            .collect(Collectors.toList());
        
        StringBuilder summary = new StringBuilder();
        filteredItems.forEach(summary::append);
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(summary.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.substring(0, 16); // 取前16位
        } catch (NoSuchAlgorithmException e) {
            // 降级到改进的哈希
            return String.valueOf(summary.toString().hashCode() & 0x7FFFFFFF);
        }
    }
    
    // 辅助方法：只添加非空行，支持默认值
    private void addIfNotEmpty(Player player, String line, String defaultValue) {
        if (line != null && !line.trim().isEmpty() && !line.contains("未找到消息格式")) {
            player.sendMessage(line);
        } else if (defaultValue != null && !defaultValue.trim().isEmpty()) {
            player.sendMessage(defaultValue);
        }
    }
    
    private void addIfNotEmpty(Player player, String line) {
        addIfNotEmpty(player, line, null);
    }
    
    private String preprocessMessage(Player player, String message) {
        for (MessagePreprocessor preprocessor : preprocessors) {
            try {
                message = preprocessor.process(player, message);
                if (message == null) {
                    message = ""; // 防御性处理
                    break;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, String.format(
                    "预处理器 %s 处理消息失败: %s", 
                    preprocessor.getClass().getSimpleName(), e.getMessage()), e);
                errorCount.increment();
                // 继续处理下一个预处理器
            }
        }
        return message != null ? message : "";
    }
    
    private boolean validateMessage(Player player, String message) {
        if (message == null) {
            message = "";
        }
        
        // 使用更精确的字符计数
        int codePointCount = message.codePointCount(0, message.length());
        if (codePointCount > MAX_MESSAGE_LENGTH) {
            String errorMsg = config.getMessageFormat("error")
                .replace("{error}", "消息长度超过限制(" + MAX_MESSAGE_LENGTH + "字符)");
            player.sendMessage(errorMsg);
            return false;
        }
        
        if (message.trim().isEmpty()) {
            String errorMsg = config.getMessageFormat("error")
                .replace("{error}", "消息不能为空");
            player.sendMessage(errorMsg);
            return false;
        }
        
        // 检查Bukkit消息长度限制
        if (message.length() > MAX_BUKKIT_MESSAGE_LENGTH) {
            plugin.getLogger().warning(String.format("消息长度 %d 超过Bukkit限制 %d，可能被截断", 
                message.length(), MAX_BUKKIT_MESSAGE_LENGTH));
        }
        
        return true;
    }
    
    private void updateCooldown(Player player) {
        cooldownMap.put(player.getUniqueId(), System.currentTimeMillis());
    }
    
    private void updateMessageCount(Player player) {
        UUID uuid = player.getUniqueId();
        ConcurrentLinkedQueue<Long> timestamps = messageTimestamps.computeIfAbsent(uuid, 
            k -> new ConcurrentLinkedQueue<>());
        timestamps.offer(System.currentTimeMillis());
    }
    
    private void handleProcessError(Player player, Exception e) {
        plugin.getLogger().log(Level.SEVERE, "处理AI聊天消息时发生错误", e);
        
        // 根据异常类型提供不同的错误信息
        String errorMsg;
        if (e instanceof java.net.SocketTimeoutException) {
            errorMsg = "连接超时，请稍后重试";
        } else if (e instanceof java.net.ConnectException) {
            errorMsg = "无法连接到AI服务，请联系管理员";
        } else {
            errorMsg = "处理消息时发生错误，请稍后重试";
        }
        
        player.sendMessage(config.getMessageFormat("error").replace("{error}", errorMsg));
    }
    
    // 增强版敏感词过滤器 - 性能优化版
    private static class SensitiveFilter {
        private static class TrieNode {
            boolean isEnd = false;
            Map<Character, TrieNode> children = new HashMap<>();
        }

        private final TrieNode root = new TrieNode();
        private final int optimizationLevel;
        
        // 预编译正则表达式
        private static final Pattern PUNCT_REGEX = Pattern.compile("[\\p{Punct}\\s]");

        public SensitiveFilter(List<String> words) {
            this(words, 0);
        }
        
        public SensitiveFilter(List<String> words, int optimizationLevel) {
            this.optimizationLevel = optimizationLevel;
            for (String word : words) {
                if (word != null && !word.trim().isEmpty()) {
                    addWord(normalizeText(word.trim()));
                }
            }
        }

        private void addWord(String word) {
            if (word == null || word.isEmpty()) return;
            
            TrieNode node = root;
            for (char c : word.toCharArray()) {
                node = node.children.computeIfAbsent(c, k -> new TrieNode());
            }
            node.isEnd = true;
        }
        
        private String normalizeText(String text) {
            if (text == null) return "";
            return PUNCT_REGEX.matcher(
                Normalizer.normalize(text, Normalizer.Form.NFKC)
            ).replaceAll("").toLowerCase(Locale.ROOT);
        }

        public boolean containsSensitiveWord(String text) {
            if (text == null) return false;
            
            // 性能优化：根据优化级别调整检查策略
            switch (optimizationLevel) {
                case 1:
                    // 轻度优化：简化文本预处理
                    return containsSensitiveWordLite(text);
                case 2:
                    // 中度优化：只检查关键部分
                    return containsSensitiveWordBasic(text);
                default:
                    // 完整检查
                    return containsSensitiveWordFull(text);
            }
        }
        
        private boolean containsSensitiveWordFull(String text) {
            String normalized = normalizeText(text);
            if (normalized.isEmpty()) return false;
            
            for (int i = 0; i < normalized.length(); i++) {
                TrieNode node = root;
                for (int j = i; j < normalized.length(); j++) {
                    char c = normalized.charAt(j);
                    node = node.children.get(c);
                    if (node == null) break;
                    
                    // 短路返回：发现敏感词立即返回
                    if (node.isEnd) return true;
                }
            }
            return false;
        }
        
        private boolean containsSensitiveWordLite(String text) {
            // 轻度优化：跳过复杂的标准化，只做基本检查
            String simplified = text.toLowerCase().replaceAll("\\s+", "");
            if (simplified.isEmpty()) return false;
            
            for (int i = 0; i < simplified.length(); i++) {
                TrieNode node = root;
                for (int j = i; j < Math.min(i + 10, simplified.length()); j++) { // 限制检查长度
                    char c = simplified.charAt(j);
                    node = node.children.get(c);
                    if (node == null) break;
                    
                    if (node.isEnd) return true;
                }
            }
            return false;
        }
        
        private boolean containsSensitiveWordBasic(String text) {
            // 中度优化：只检查文本的前半部分，跳过详细处理
            String basic = text.toLowerCase();
            int checkLength = Math.min(basic.length(), 50); // 只检查前50个字符
            
            for (int i = 0; i < checkLength; i++) {
                TrieNode node = root;
                for (int j = i; j < Math.min(i + 5, checkLength); j++) { // 更短的检查窗口
                    char c = basic.charAt(j);
                    node = node.children.get(c);
                    if (node == null) break;
                    
                    if (node.isEnd) return true;
                }
            }
            return false;
        }
    }
    
    public interface MessagePreprocessor {
        String process(Player player, String message) throws Exception;
    }
    
    // 获取统计信息（用于监控）
    public Map<String, Long> getStatistics() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("success", successCount.sum());
        stats.put("filtered", filterCount.sum());
        stats.put("rateLimit", rateLimitCount.sum());
        stats.put("errors", errorCount.sum());
        stats.put("activeUsers", (long) cooldownMap.size());
        stats.put("cacheSize", (long) helpCache.size());
        return stats;
    }

    /**
     * 测试配置同步
     */
    private void testConfigSync(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== 配置同步测试 ===");
        
        // 读取当前配置值
        long normalCooldown = plugin.getConfigLoader().getNormalUserCooldown();
        long vipCooldown = plugin.getConfigLoader().getVipUserCooldown();
        int maxMessages = plugin.getConfigLoader().getMaxMessagesPerMinute();
        boolean filterEnabled = plugin.getConfigLoader().isFilterEnabled();
        
        player.sendMessage(ChatColor.YELLOW + "当前配置值:");
        player.sendMessage(ChatColor.GRAY + "  普通用户冷却: " + ChatColor.WHITE + normalCooldown + "ms");
        player.sendMessage(ChatColor.GRAY + "  VIP用户冷却: " + ChatColor.WHITE + vipCooldown + "ms");
        player.sendMessage(ChatColor.GRAY + "  每分钟最大消息: " + ChatColor.WHITE + maxMessages);
        player.sendMessage(ChatColor.GRAY + "  内容过滤: " + ChatColor.WHITE + (filterEnabled ? "启用" : "禁用"));
        
        // 直接从配置文件读取
        FileConfiguration fileConfig = plugin.getConfigLoader().getConfig();
        long fileCooldown1 = fileConfig.getLong("performance.rate-limit.normal-user", -1);
        long fileCooldown2 = fileConfig.getLong("performance.rate-limit.vip-user", -1);
        int fileMaxMsg = fileConfig.getInt("performance.rate-limit.max-messages-per-minute", -1);
        boolean fileFilter = fileConfig.getBoolean("advanced.filter-enabled", false);
        
        player.sendMessage(ChatColor.YELLOW + "配置文件值:");
        player.sendMessage(ChatColor.GRAY + "  performance.rate-limit.normal-user: " + ChatColor.WHITE + fileCooldown1);
        player.sendMessage(ChatColor.GRAY + "  performance.rate-limit.vip-user: " + ChatColor.WHITE + fileCooldown2);
        player.sendMessage(ChatColor.GRAY + "  performance.rate-limit.max-messages-per-minute: " + ChatColor.WHITE + fileMaxMsg);
        player.sendMessage(ChatColor.GRAY + "  advanced.filter-enabled: " + ChatColor.WHITE + fileFilter);
        
        // 测试setter方法
        player.sendMessage(ChatColor.YELLOW + "测试配置更新...");
        plugin.getConfigLoader().setNormalUserCooldown(5000);
        plugin.getConfigLoader().setVipUserCooldown(2000);
        
        // 重新读取
        long newNormal = plugin.getConfigLoader().getNormalUserCooldown();
        long newVip = plugin.getConfigLoader().getVipUserCooldown();
        
        player.sendMessage(ChatColor.GRAY + "  更新后普通冷却: " + ChatColor.WHITE + newNormal);
        player.sendMessage(ChatColor.GRAY + "  更新后VIP冷却: " + ChatColor.WHITE + newVip);
        
        // 恢复原值
        plugin.getConfigLoader().setNormalUserCooldown(normalCooldown);
        plugin.getConfigLoader().setVipUserCooldown(vipCooldown);
    }
} 