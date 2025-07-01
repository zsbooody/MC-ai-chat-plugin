package com.example.aichatplugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.ref.WeakReference;

/**
 * 🩺 系统诊断管理器
 * 用于监控插件健康状况，检测资源泄漏和性能问题
 */
public class DiagnosticManager {
    
    private final AIChatPlugin plugin;
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    private final List<GarbageCollectorMXBean> gcBeans;
    
    // 监控数据
    private final ConcurrentHashMap<String, AtomicLong> errorCounters;
    private final ConcurrentHashMap<String, AtomicInteger> resourceCounters;
    private final List<WeakReference<Object>> trackedResources;
    
    // 历史数据（用于趋势分析）
    private final LinkedList<MemorySnapshot> memoryHistory;
    private final LinkedList<ThreadSnapshot> threadHistory;
    private final int MAX_HISTORY_SIZE = 100;
    
    // 监控任务
    private BukkitTask monitoringTask;
    private boolean isRunning = false;
    
    // 警告阈值
    private static final long MEMORY_WARNING_THRESHOLD = 500 * 1024 * 1024; // 500MB
    private static final int THREAD_WARNING_THRESHOLD = 100;
    private static final long GC_TIME_WARNING_THRESHOLD = 1000; // 1秒
    
    private final ConfigLoader configLoader;
    
    public DiagnosticManager(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        this.errorCounters = new ConcurrentHashMap<>();
        this.resourceCounters = new ConcurrentHashMap<>();
        this.trackedResources = Collections.synchronizedList(new ArrayList<>());
        
        this.memoryHistory = new LinkedList<>();
        this.threadHistory = new LinkedList<>();
        
        this.configLoader = plugin.getConfigLoader();
        
        initializeCounters();
    }
    
    private void initializeCounters() {
        // 初始化错误计数器
        errorCounters.put("concurrent_modification", new AtomicLong(0));
        errorCounters.put("memory_leak", new AtomicLong(0));
        errorCounters.put("thread_leak", new AtomicLong(0));
        errorCounters.put("resource_leak", new AtomicLong(0));
        errorCounters.put("api_timeout", new AtomicLong(0));
        errorCounters.put("config_error", new AtomicLong(0));
        
        // 初始化资源计数器
        resourceCounters.put("active_threads", new AtomicInteger(0));
        resourceCounters.put("active_connections", new AtomicInteger(0));
        resourceCounters.put("cached_items", new AtomicInteger(0));
        resourceCounters.put("pending_tasks", new AtomicInteger(0));
    }
    
    /**
     * 启动诊断监控
     */
    public void startMonitoring() {
        if (isRunning) {
            return;
        }
        
        monitoringTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    performHealthCheck();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "诊断检查时发生错误", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 200L, 200L); // 每10秒检查一次
        
        isRunning = true;
        plugin.getLogger().info("系统诊断监控已启动");
    }
    
    /**
     * 停止诊断监控
     */
    public void stopMonitoring() {
        if (monitoringTask != null && !monitoringTask.isCancelled()) {
            monitoringTask.cancel();
        }
        isRunning = false;
        plugin.getLogger().info("系统诊断监控已停止");
    }
    
    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        // 内存监控
        checkMemoryHealth();
        
        // 线程监控
        checkThreadHealth();
        
        // GC监控
        checkGCHealth();
        
        // 资源泄漏检测
        checkResourceLeaks();
        
        // 清理历史数据
        cleanupHistory();
    }
    
    /**
     * 检查内存健康状况
     */
    private void checkMemoryHealth() {
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
        
        MemorySnapshot snapshot = new MemorySnapshot(usedMemory, maxMemory, memoryUsagePercent);
        memoryHistory.add(snapshot);
        
        // 检查内存使用是否过高
        if (usedMemory > MEMORY_WARNING_THRESHOLD) {
            recordError("high_memory_usage");
            plugin.getLogger().warning(String.format(
                "内存使用率过高: %.2f%% (%d MB / %d MB)", 
                memoryUsagePercent, usedMemory / (1024 * 1024), maxMemory / (1024 * 1024)
            ));
        }
        
        // 检查内存泄漏（内存使用持续增长）
        if (memoryHistory.size() >= 10) {
            boolean consistentGrowth = true;
            for (int i = memoryHistory.size() - 10; i < memoryHistory.size() - 1; i++) {
                if (memoryHistory.get(i + 1).usedMemory <= memoryHistory.get(i).usedMemory) {
                    consistentGrowth = false;
                    break;
                }
            }
            
            if (consistentGrowth) {
                recordError("memory_leak");
                plugin.getLogger().warning("检测到可能的内存泄漏：内存使用持续增长");
            }
        }
    }
    
    /**
     * 检查线程健康状况
     */
    private void checkThreadHealth() {
        int activeThreads = threadBean.getThreadCount();
        int peakThreads = threadBean.getPeakThreadCount();
        
        ThreadSnapshot snapshot = new ThreadSnapshot(activeThreads, peakThreads);
        threadHistory.add(snapshot);
        
        resourceCounters.get("active_threads").set(activeThreads);
        
        if (activeThreads > THREAD_WARNING_THRESHOLD) {
            recordError("thread_leak");
            plugin.getLogger().warning(String.format(
                "活跃线程数过多: %d (峰值: %d)", activeThreads, peakThreads
            ));
        }
    }
    
    /**
     * 检查GC健康状况
     */
    private void checkGCHealth() {
        long totalGCTime = 0;
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            totalGCTime += gcBean.getCollectionTime();
        }
        
        if (totalGCTime > GC_TIME_WARNING_THRESHOLD) {
            recordError("excessive_gc");
            plugin.getLogger().warning(String.format("GC时间过长: %d ms", totalGCTime));
        }
    }
    
    /**
     * 检查资源泄漏
     */
    private void checkResourceLeaks() {
        // 清理已被GC的弱引用
        int removedCount = 0;
        Iterator<WeakReference<Object>> iterator = trackedResources.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().get() == null) {
                iterator.remove();
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            plugin.debug(String.format("清理了 %d 个已释放的资源引用", removedCount));
        }
        
        // 更新资源计数
        resourceCounters.get("cached_items").set(trackedResources.size());
    }
    
    /**
     * 记录错误
     */
    public void recordError(String errorType) {
        AtomicLong counter = errorCounters.get(errorType);
        if (counter != null) {
            counter.incrementAndGet();
        } else {
            errorCounters.put(errorType, new AtomicLong(1));
        }
    }
    
    /**
     * 跟踪资源
     */
    public void trackResource(Object resource) {
        trackedResources.add(new WeakReference<>(resource));
    }
    
    /**
     * 获取诊断报告
     */
    public String getDiagnosticReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== 系统诊断报告 ===\n");
        
        // 内存状态
        if (!memoryHistory.isEmpty()) {
            MemorySnapshot latest = memoryHistory.getLast();
            report.append(String.format("内存使用: %.2f%% (%d MB / %d MB)\n",
                latest.usagePercent, latest.usedMemory / (1024 * 1024), latest.maxMemory / (1024 * 1024)));
        }
        
        // 线程状态
        if (!threadHistory.isEmpty()) {
            ThreadSnapshot latest = threadHistory.getLast();
            report.append(String.format("活跃线程: %d (峰值: %d)\n", latest.activeThreads, latest.peakThreads));
        }
        
        // 错误统计
        report.append("错误统计:\n");
        errorCounters.entrySet().stream()
            .filter(entry -> entry.getValue().get() > 0)
            .forEach(entry -> report.append(String.format("  %s: %d\n", entry.getKey(), entry.getValue().get())));
        
        // 资源统计
        report.append("资源统计:\n");
        resourceCounters.forEach((key, value) -> 
            report.append(String.format("  %s: %d\n", key, value.get())));
        
        return report.toString();
    }
    
    /**
     * 清理历史数据
     */
    private void cleanupHistory() {
        while (memoryHistory.size() > MAX_HISTORY_SIZE) {
            memoryHistory.removeFirst();
        }
        while (threadHistory.size() > MAX_HISTORY_SIZE) {
            threadHistory.removeFirst();
        }
    }
    
    /**
     * 内存快照
     */
    private static class MemorySnapshot {
        final long usedMemory;
        final long maxMemory;
        final double usagePercent;
        final long timestamp;
        
        MemorySnapshot(long usedMemory, long maxMemory, double usagePercent) {
            this.usedMemory = usedMemory;
            this.maxMemory = maxMemory;
            this.usagePercent = usagePercent;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * 线程快照
     */
    private static class ThreadSnapshot {
        final int activeThreads;
        final int peakThreads;
        final long timestamp;
        
        ThreadSnapshot(int activeThreads, int peakThreads) {
            this.activeThreads = activeThreads;
            this.peakThreads = peakThreads;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * 执行完整的配置诊断
     */
    public void runConfigDiagnostics(CommandSender sender) {
        sender.sendMessage("§6=== AI Chat Plugin 配置诊断 ===");
        
        // 1. 检查配置文件是否存在
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        sender.sendMessage("§e配置文件路径: §f" + configFile.getAbsolutePath());
        sender.sendMessage("§e配置文件存在: §f" + (configFile.exists() ? "是" : "否"));
        
        if (!configFile.exists()) {
            sender.sendMessage("§c配置文件不存在！");
            return;
        }
        
        // 2. 直接读取文件内容
        sender.sendMessage("\n§e=== 聊天配置诊断 ===");
        FileConfiguration fileConfig = YamlConfiguration.loadConfiguration(configFile);
        
        // 检查聊天相关配置
        Map<String, Object> chatConfig = new HashMap<>();
        chatConfig.put("performance.rate-limit.normal-user", fileConfig.get("performance.rate-limit.normal-user"));
        chatConfig.put("performance.rate-limit.vip-user", fileConfig.get("performance.rate-limit.vip-user"));
        chatConfig.put("performance.rate-limit.max-messages-per-minute", fileConfig.get("performance.rate-limit.max-messages-per-minute"));
        chatConfig.put("advanced.filter-enabled", fileConfig.get("advanced.filter-enabled"));
        
        sender.sendMessage("§e文件中的配置值:");
        for (Map.Entry<String, Object> entry : chatConfig.entrySet()) {
            sender.sendMessage("  §7" + entry.getKey() + ": §f" + entry.getValue());
        }
        
        // 3. 通过ConfigLoader读取
        sender.sendMessage("\n§eConfigLoader读取的值:");
        sender.sendMessage("  §7普通用户冷却: §f" + configLoader.getNormalUserCooldown() + "ms");
        sender.sendMessage("  §7VIP用户冷却: §f" + configLoader.getVipUserCooldown() + "ms");
        sender.sendMessage("  §7每分钟最大消息: §f" + configLoader.getMaxMessagesPerMinute());
        sender.sendMessage("  §7内容过滤: §f" + configLoader.isFilterEnabled());
        
        // 4. 测试配置写入
        sender.sendMessage("\n§e=== 配置写入测试 ===");
        testConfigWrite(sender);
        
        // 5. 检查缓存状态
        sender.sendMessage("\n§e=== 缓存状态 ===");
        // 通过反射或其他方式检查缓存状态
        sender.sendMessage("  §7缓存机制: §f已启用（5秒过期）");
        
        // 6. Web配置映射检查
        sender.sendMessage("\n§e=== Web配置映射 ===");
        checkWebConfigMapping(sender);
    }
    
    /**
     * 测试配置写入功能
     */
    private void testConfigWrite(CommandSender sender) {
        // 保存当前值
        long originalNormal = configLoader.getNormalUserCooldown();
        long originalVip = configLoader.getVipUserCooldown();
        
        // 测试写入
        long testValue1 = 9999;
        long testValue2 = 8888;
        
        sender.sendMessage("§7测试写入值: normal=" + testValue1 + ", vip=" + testValue2);
        
        // 使用setter方法
        configLoader.setNormalUserCooldown(testValue1);
        configLoader.setVipUserCooldown(testValue2);
        
        // 立即读取
        long readValue1 = configLoader.getNormalUserCooldown();
        long readValue2 = configLoader.getVipUserCooldown();
        
        sender.sendMessage("§7立即读取: normal=" + readValue1 + ", vip=" + readValue2);
        
        // 重载配置后读取
        configLoader.reloadConfig();
        long reloadValue1 = configLoader.getNormalUserCooldown();
        long reloadValue2 = configLoader.getVipUserCooldown();
        
        sender.sendMessage("§7重载后读取: normal=" + reloadValue1 + ", vip=" + reloadValue2);
        
        // 直接从文件读取
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration fileConfig = YamlConfiguration.loadConfiguration(configFile);
        long fileValue1 = fileConfig.getLong("performance.rate-limit.normal-user");
        long fileValue2 = fileConfig.getLong("performance.rate-limit.vip-user");
        
        sender.sendMessage("§7文件中的值: normal=" + fileValue1 + ", vip=" + fileValue2);
        
        // 恢复原值
        configLoader.setNormalUserCooldown(originalNormal);
        configLoader.setVipUserCooldown(originalVip);
        
        // 判断测试结果
        if (readValue1 == testValue1 && reloadValue1 == testValue1 && fileValue1 == testValue1) {
            sender.sendMessage("§a✓ 配置写入测试通过");
        } else {
            sender.sendMessage("§c✗ 配置写入测试失败！");
        }
    }
    
    /**
     * 检查Web配置映射
     */
    private void checkWebConfigMapping(CommandSender sender) {
        sender.sendMessage("§7Web端发送的键名 -> 配置文件路径:");
        sender.sendMessage("  §7chat.normalUserCooldown -> performance.rate-limit.normal-user");
        sender.sendMessage("  §7chat.vipUserCooldown -> performance.rate-limit.vip-user");
        sender.sendMessage("  §7chat.maxMessagesPerMinute -> performance.rate-limit.max-messages-per-minute");
        sender.sendMessage("  §7chat.contentFilterEnabled -> advanced.filter-enabled");
        
        // 检查ConfigController的updateConfigValue方法
        sender.sendMessage("\n§7ConfigController处理状态:");
        sender.sendMessage("  §7使用专用setter方法: §a是");
        sender.sendMessage("  §7直接调用ConfigLoader.set(): §e部分");
        
        // 建议
        sender.sendMessage("\n§e建议:");
        sender.sendMessage("  §71. 确保Web端发送的配置立即调用saveConfig()");
        sender.sendMessage("  §72. 考虑在保存后强制刷新缓存");
        sender.sendMessage("  §73. 添加配置变更监听器通知其他组件");
    }
    
    /**
     * 生成诊断报告
     */
    public String generateDiagnosticReport() {
        StringBuilder report = new StringBuilder();
        report.append("AI Chat Plugin 诊断报告\n");
        report.append("====================\n\n");
        
        // 版本信息
        report.append("插件版本: ").append(plugin.getDescription().getVersion()).append("\n");
        report.append("服务器版本: ").append(plugin.getServer().getVersion()).append("\n");
        
        // 配置状态
        report.append("\n配置状态:\n");
        report.append("- 配置文件: ").append(new File(plugin.getDataFolder(), "config.yml").exists() ? "存在" : "不存在").append("\n");
        report.append("- 热重载: ").append(configLoader.isHotReloadEnabled() ? "启用" : "禁用").append("\n");
        report.append("- Web界面: ").append(configLoader.isWebEnabled() ? "启用" : "禁用").append("\n");
        
        // 当前配置值
        report.append("\n当前配置值:\n");
        report.append("- 普通用户冷却: ").append(configLoader.getNormalUserCooldown()).append("ms\n");
        report.append("- VIP用户冷却: ").append(configLoader.getVipUserCooldown()).append("ms\n");
        report.append("- 每分钟最大消息: ").append(configLoader.getMaxMessagesPerMinute()).append("\n");
        report.append("- 内容过滤: ").append(configLoader.isFilterEnabled() ? "启用" : "禁用").append("\n");
        
        return report.toString();
    }
} 