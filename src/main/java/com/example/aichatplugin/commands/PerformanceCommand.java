package com.example.aichatplugin.commands;

import com.example.aichatplugin.AIChatPlugin;
import com.example.aichatplugin.performance.PerformanceMonitor;
import com.example.aichatplugin.performance.PerformanceMonitor.PerformanceSnapshot;
import com.example.aichatplugin.performance.HardwareMonitor;
import com.example.aichatplugin.performance.OperationMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginDisableEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import java.util.logging.Level;

/**
 * 性能报告命令
 * 用于查看服务器性能状态
 */
public class PerformanceCommand implements CommandExecutor, TabCompleter, Listener {
    private final AIChatPlugin plugin;
    private final Map<String, CachedData> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> cleanupTask;
    
    // 配置键
    private static final String CONFIG_CACHE_DURATION = "performance.cache-duration";
    private static final String CONFIG_DISPLAY_MINUTES = "performance.display-minutes";
    private static final String CONFIG_MAX_ENTRIES = "performance.max-entries";
    private static final String CONFIG_CLEANUP_INTERVAL = "performance.cleanup-interval";
    private static final String CONFIG_THRESHOLDS = "performance.thresholds";
    private static final String CONFIG_WARNINGS = "performance.warnings";
    
    // 默认值
    private static final int DEFAULT_CACHE_DURATION = 30;
    private static final int DEFAULT_DISPLAY_MINUTES = 5;
    private static final int DEFAULT_MAX_ENTRIES = 50;
    private static final int DEFAULT_CLEANUP_INTERVAL = 5;
    private static final double DEFAULT_TPS_THRESHOLD = 18.0;
    private static final double DEFAULT_CPU_THRESHOLD = 80.0;
    private static final double DEFAULT_MEMORY_THRESHOLD = 80.0;
    
    // 默认警告消息
    private static final String DEFAULT_TPS_WARNING = "§c⚠️ TPS过低，建议优化实体数量";
    private static final String DEFAULT_CPU_WARNING = "§c⚠️ CPU使用率较高: {value}%，建议检查服务器负载";
    private static final String DEFAULT_MEMORY_WARNING = "§c⚠️ 内存使用率较高: {value}%，建议增加内存或优化内存使用";
    
    private static class CachedData {
        final Map<Long, PerformanceSnapshot> history;
        final String hardwareStatus;
        final long timestamp;
        final int cacheDuration;
        
        CachedData(Map<Long, PerformanceSnapshot> history, String hardwareStatus, int cacheDuration) {
            this.history = history;
            this.hardwareStatus = hardwareStatus;
            this.timestamp = System.currentTimeMillis();
            this.cacheDuration = cacheDuration;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TimeUnit.SECONDS.toMillis(cacheDuration);
        }
    }
    
    public PerformanceCommand(AIChatPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startCleanupTask();
    }
    
    private void startCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
        }
        
        int interval = getCleanupInterval();
        cleanupTask = cleanupScheduler.scheduleAtFixedRate(
            this::cleanupCache,
            interval,
            interval,
            TimeUnit.MINUTES
        );
    }
    
    private void cleanupCache() {
        try {
            synchronized (cache) {
                cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("清理性能数据缓存时发生错误: " + e.getMessage());
        }
    }
    
    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().equals(plugin)) {
            startCleanupTask();
        }
    }
    
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin)) {
            if (cleanupTask != null) {
                cleanupTask.cancel(false);
            }
            cleanupScheduler.shutdown();
        }
    }
    
    @EventHandler
    public void onConfigReload(org.bukkit.event.server.ServerLoadEvent event) {
        startCleanupTask();
    }
    
    private int getCacheDuration() {
        return plugin.getConfig().getInt(CONFIG_CACHE_DURATION, DEFAULT_CACHE_DURATION);
    }
    
    private int getDisplayMinutes() {
        return plugin.getConfig().getInt(CONFIG_DISPLAY_MINUTES, DEFAULT_DISPLAY_MINUTES);
    }
    
    private int getMaxEntries() {
        return plugin.getConfig().getInt(CONFIG_MAX_ENTRIES, DEFAULT_MAX_ENTRIES);
    }
    
    private int getCleanupInterval() {
        return plugin.getConfig().getInt(CONFIG_CLEANUP_INTERVAL, DEFAULT_CLEANUP_INTERVAL);
    }
    
    private double getTpsThreshold() {
        return plugin.getConfig().getDouble(CONFIG_THRESHOLDS + ".tps", DEFAULT_TPS_THRESHOLD);
    }
    
    private double getCpuThreshold() {
        return plugin.getConfig().getDouble(CONFIG_THRESHOLDS + ".cpu", DEFAULT_CPU_THRESHOLD);
    }
    
    private double getMemoryThreshold() {
        return plugin.getConfig().getDouble(CONFIG_THRESHOLDS + ".memory", DEFAULT_MEMORY_THRESHOLD);
    }
    
    private String getTpsWarning() {
        return plugin.getConfig().getString(CONFIG_WARNINGS + ".tps", DEFAULT_TPS_WARNING);
    }
    
    private String getCpuWarning(double value) {
        String warning = plugin.getConfig().getString(CONFIG_WARNINGS + ".cpu", DEFAULT_CPU_WARNING);
        return warning.replace("{value}", String.format("%.1f", value));
    }
    
    private String getMemoryWarning(double value) {
        String warning = plugin.getConfig().getString(CONFIG_WARNINGS + ".memory", DEFAULT_MEMORY_WARNING);
        return warning.replace("{value}", String.format("%.1f", value));
    }
    
    /**
     * 处理性能命令
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 0) {
                // 显示主帮助信息
                showMainHelp(sender);
                return true;
            }

            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "status":
                    return handleStatusCommand(sender, args);
                case "stats":
                    return handleStatsCommand(sender, args);
                case "mode":
                    return handleModeCommand(sender, args);
                case "manual":
                    return handleManualCommand(sender, args);
                case "auto":
                    return handleAutoCommand(sender, args);
                case "history":
                    return handleHistoryCommand(sender, args);
                case "reload":
                    return handleReloadCommand(sender, args);
                case "help":
                default:
                    showMainHelp(sender);
                    return true;
            }
        } catch (Exception e) {
            sender.sendMessage("&c命令执行时发生错误: " + e.getMessage());
            plugin.getLogger().log(Level.WARNING, "Performance command error", e);
            return false;
        }
    }

    /**
     * 处理状态命令 - /performance status
     */
    private boolean handleStatusCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aichat.performance")) {
            sender.sendMessage("&c你没有权限使用此命令");
            return false;
        }

        try {
            PerformanceMonitor monitor = plugin.getPerformanceMonitor();
            if (monitor == null) {
                sender.sendMessage("&c性能监控器未初始化");
                return true;
            }

            sender.sendMessage("&6=== 性能状态报告 ===");
            sender.sendMessage("&6当前模式: &e" + monitor.getCurrentMode().name());
            sender.sendMessage("&6控制方式: " + (monitor.isManualModeEnabled() ? "&c手动" : "&a自动"));
            sender.sendMessage("&6当前TPS: &e" + String.format("%.1f", monitor.getCurrentTPS()));
            
            // 硬件状态
            HardwareMonitor hardwareMonitor = plugin.getHardwareMonitor();
            if (hardwareMonitor != null) {
                sender.sendMessage("&6=== 硬件状态 ===");
                sender.sendMessage("&7" + hardwareMonitor.getStatus().toString());
            }
            
            return true;
        } catch (Exception e) {
            sender.sendMessage("&c获取状态时发生错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 处理统计命令 - /performance stats
     */
    private boolean handleStatsCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aichat.performance")) {
            sender.sendMessage("&c你没有权限使用此命令");
            return false;
        }

        try {
            PerformanceMonitor monitor = plugin.getPerformanceMonitor();
            if (monitor == null) {
                sender.sendMessage("&c性能监控器未初始化");
                return true;
            }

            sender.sendMessage("&6=== 详细性能统计 ===");
            
            // 基本信息
            sender.sendMessage("&6当前模式: &e" + monitor.getCurrentMode().name());
            sender.sendMessage("&6TPS: &e" + String.format("%.2f", monitor.getCurrentTPS()));
            sender.sendMessage("&6实体数量: &e" + monitor.getEntityCount());
            sender.sendMessage("&6区块数量: &e" + monitor.getChunkCount());
            
            // 功能状态报告
            Map<String, Object> featureReport = monitor.getFeatureStatusReport();
            sender.sendMessage("&6功能状态: &e" + featureReport.get("optimizationLevel"));
            
            @SuppressWarnings("unchecked")
            List<String> disabledFeatures = (List<String>) featureReport.get("disabledFeatures");
            if (!disabledFeatures.isEmpty()) {
                sender.sendMessage("&6禁用功能: &c" + String.join(", ", disabledFeatures));
            }
            
            return true;
        } catch (Exception e) {
            sender.sendMessage("&c获取统计时发生错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 处理历史命令 - /performance history
     */
    private boolean handleHistoryCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aichat.admin")) {
            sender.sendMessage("&c你没有权限使用此命令");
            return false;
        }

        try {
            PerformanceMonitor monitor = plugin.getPerformanceMonitor();
            Map<Long, PerformanceMonitor.PerformanceSnapshot> history = monitor.getPerformanceHistory();
            
            if (history.isEmpty()) {
                sender.sendMessage("&c暂无性能历史数据");
                return true;
            }

            sender.sendMessage("&6=== 性能历史记录 ===");
            
            // 显示最近10条记录
            history.entrySet().stream()
                .sorted(Map.Entry.<Long, PerformanceMonitor.PerformanceSnapshot>comparingByKey().reversed())
                .limit(10)
                .forEach(entry -> {
                    long timestamp = entry.getKey();
                    PerformanceMonitor.PerformanceSnapshot snapshot = entry.getValue();
                    sender.sendMessage(String.format("&7[%s] &f%s", 
                        formatTime(timestamp), snapshot.toString()));
                });
                
            return true;
        } catch (Exception e) {
            sender.sendMessage("&c获取历史时发生错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 处理重载命令 - /performance reload
     */
    private boolean handleReloadCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aichat.admin")) {
            sender.sendMessage("&c你没有权限使用此命令");
            return false;
        }

        try {
            plugin.getPerformanceMonitor().reloadConfig();
            sender.sendMessage("&a性能监控配置已重新加载");
            return true;
        } catch (Exception e) {
            sender.sendMessage("&c重载配置时发生错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 处理手动模式命令 - /performance manual [enable|disable|set] [mode]
     */
    private boolean handleManualCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aichat.admin")) {
            sender.sendMessage("&c你没有权限使用此命令");
            return false;
        }

        if (args.length < 2) {
            // 显示当前手动模式状态
            PerformanceMonitor monitor = plugin.getPerformanceMonitor();
            if (monitor.isManualModeEnabled()) {
                sender.sendMessage("&6手动模式状态: &a启用");
                sender.sendMessage("&6当前手动模式: &e" + monitor.getManualMode().name());
                sender.sendMessage("&6设置时间: &7" + formatTime(monitor.getManualModeSetTime()));
            } else {
                sender.sendMessage("&6手动模式状态: &c禁用");
                sender.sendMessage("&7当前使用自动性能检测");
            }
            sender.sendMessage("&7用法: /performance manual <enable|disable|set> [mode]");
            return true;
        }

        String action = args[1].toLowerCase();
        PerformanceMonitor monitor = plugin.getPerformanceMonitor();

        switch (action) {
            case "enable":
                if (args.length < 3) {
                    sender.sendMessage("&c请指定模式: FULL, LITE, BASIC, EMERGENCY");
                    return false;
                }
                return enableManualMode(sender, args[2]);

            case "disable":
                monitor.disableManualMode();
                sender.sendMessage("&a手动模式已禁用，恢复自动性能检测");
                return true;

            case "set":
                if (!monitor.isManualModeEnabled()) {
                    sender.sendMessage("&c手动模式未启用，请先使用 /performance manual enable <mode>");
                    return false;
                }
                if (args.length < 3) {
                    sender.sendMessage("&c请指定模式: FULL, LITE, BASIC, EMERGENCY");
                    return false;
                }
                return setManualMode(sender, args[2]);

            default:
                sender.sendMessage("&c无效的操作: " + action);
                sender.sendMessage("&7可用操作: enable, disable, set");
                return false;
        }
    }

    /**
     * 启用手动模式
     */
    private boolean enableManualMode(CommandSender sender, String modeStr) {
        try {
            OperationMode mode = OperationMode.valueOf(modeStr.toUpperCase());
            PerformanceMonitor monitor = plugin.getPerformanceMonitor();
            
            monitor.enableManualMode(mode);
            
            sender.sendMessage("&a手动模式已启用");
            sender.sendMessage("&6设置模式: &e" + mode.name());
            sender.sendMessage("&7自动性能检测已禁用");
            
            return true;
        } catch (IllegalArgumentException e) {
            sender.sendMessage("&c无效的模式: " + modeStr);
            sender.sendMessage("&7可用模式: FULL, LITE, BASIC, EMERGENCY");
            return false;
        }
    }

    /**
     * 设置手动模式
     */
    private boolean setManualMode(CommandSender sender, String modeStr) {
        try {
            OperationMode mode = OperationMode.valueOf(modeStr.toUpperCase());
            PerformanceMonitor monitor = plugin.getPerformanceMonitor();
            
            OperationMode oldMode = monitor.getManualMode();
            monitor.enableManualMode(mode); // 重新设置会更新模式
            
            sender.sendMessage("&a手动模式已更新");
            sender.sendMessage("&6从 &e" + oldMode.name() + " &6切换到 &e" + mode.name());
            
            return true;
        } catch (IllegalArgumentException e) {
            sender.sendMessage("&c无效的模式: " + modeStr);
            sender.sendMessage("&7可用模式: FULL, LITE, BASIC, EMERGENCY");
            return false;
        }
    }

    /**
     * 处理自动模式命令 - /performance auto
     */
    private boolean handleAutoCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aichat.admin")) {
            sender.sendMessage("&c你没有权限使用此命令");
            return false;
        }

        PerformanceMonitor monitor = plugin.getPerformanceMonitor();
        
        if (monitor.isManualModeEnabled()) {
            monitor.disableManualMode();
            sender.sendMessage("&a已切换到自动模式");
            sender.sendMessage("&7系统将根据性能自动调整运行模式");
        } else {
            sender.sendMessage("&e已经处于自动模式");
        }
        
        return true;
    }

    /**
     * 处理模式命令 - /performance mode [mode]
     */
    private boolean handleModeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aichat.admin")) {
            sender.sendMessage("&c你没有权限使用此命令");
            return false;
        }

        PerformanceMonitor monitor = plugin.getPerformanceMonitor();
        
        if (args.length < 2) {
            // 显示当前模式信息
            sender.sendMessage("&6=== 性能模式状态 ===");
            sender.sendMessage("&6当前模式: &e" + monitor.getCurrentMode().name());
            sender.sendMessage("&6控制方式: " + (monitor.isManualModeEnabled() ? "&c手动" : "&a自动"));
            
            if (monitor.isManualModeEnabled()) {
                sender.sendMessage("&6手动设置: &e" + monitor.getManualMode().name());
                sender.sendMessage("&6设置时间: &7" + formatTime(monitor.getManualModeSetTime()));
            }
            
            sender.sendMessage("&7");
            sender.sendMessage("&7模式说明:");
            sender.sendMessage("&e  FULL &7- 全功能模式 (性能充足)");
            sender.sendMessage("&e  LITE &7- 精简模式 (轻度性能不足)");
            sender.sendMessage("&e  BASIC &7- 基础模式 (中度性能不足)");
            sender.sendMessage("&e  EMERGENCY &7- 紧急模式 (严重性能不足)");
            sender.sendMessage("&7");
            sender.sendMessage("&7用法: /performance mode <FULL|LITE|BASIC|EMERGENCY>");
            return true;
        }

        // 直接设置模式（启用手动模式）
        return enableManualMode(sender, args[1]);
    }

    /**
     * 显示主帮助信息
     */
    private void showMainHelp(CommandSender sender) {
        sender.sendMessage("&6=== 性能管理命令帮助 ===");
        sender.sendMessage("&e/performance status &7- 查看性能状态");
        sender.sendMessage("&e/performance stats &7- 查看详细统计");
        sender.sendMessage("&e/performance mode [模式] &7- 查看/设置运行模式");
        
        if (sender.hasPermission("aichat.admin")) {
            sender.sendMessage("&c=== 管理员命令 ===");
            sender.sendMessage("&c/performance manual enable <模式> &7- 启用手动模式");
            sender.sendMessage("&c/performance manual disable &7- 禁用手动模式");
            sender.sendMessage("&c/performance manual set <模式> &7- 设置手动模式");
            sender.sendMessage("&c/performance auto &7- 切换到自动模式");
            sender.sendMessage("&c/performance history &7- 查看性能历史");
            sender.sendMessage("&c/performance reload &7- 重载配置");
        }
        
        sender.sendMessage("&7");
        sender.sendMessage("&7可用模式: &eFULL, LITE, BASIC, EMERGENCY");
    }

    /**
     * 格式化时间显示
     */
    private String formatTime(long timestamp) {
        if (timestamp == 0) {
            return "未设置";
        }
        
        long diff = System.currentTimeMillis() - timestamp;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + "天前";
        } else if (hours > 0) {
            return hours + "小时前";
        } else if (minutes > 0) {
            return minutes + "分钟前";
        } else {
            return seconds + "秒前";
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        return new ArrayList<>();
    }
    
    /**
     * 关闭命令处理器，释放资源
     */
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
        }
        
        if (cleanupScheduler != null && !cleanupScheduler.isShutdown()) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
} 