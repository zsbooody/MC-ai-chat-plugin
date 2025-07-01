package com.example.aichatplugin.performance;

import com.example.aichatplugin.AIChatPlugin;
import com.example.aichatplugin.ConfigLoader;
import com.example.aichatplugin.ConversationManager;
import com.example.aichatplugin.EnvironmentCollector;
import com.example.aichatplugin.util.AdaptiveRateLimiter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能基准测试系统
 * 
 * 功能：
 * 1. 运行多种性能测试场景
 * 2. 收集详细的性能指标
 * 3. 分析性能瓶颈
 * 4. 自动优化配置参数
 * 5. 生成性能报告
 */
public class PerformanceBenchmark {
    
    private final AIChatPlugin plugin;
    private final ConfigLoader configLoader;
    private final PerformanceMonitor performanceMonitor;
    private final ExecutorService testExecutor;
    
    // 测试结果存储
    private final List<BenchmarkResult> results = new ArrayList<>();
    private final Map<String, Double> currentMetrics = new ConcurrentHashMap<>();
    private final AtomicLong testStartTime = new AtomicLong(0);
    private final AtomicInteger completedTests = new AtomicInteger(0);
    
    // 测试配置
    private static final int DEFAULT_TEST_DURATION = 60; // 秒
    private static final int DEFAULT_CONCURRENT_USERS = 5;
    private static final int DEFAULT_MESSAGE_RATE = 10; // 每分钟
    
    public PerformanceBenchmark(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.configLoader = plugin.getConfigLoader();
        this.performanceMonitor = plugin.getPerformanceMonitor();
        this.testExecutor = Executors.newFixedThreadPool(10);
    }
    
    /**
     * 运行完整的基准测试套件
     */
    public CompletableFuture<BenchmarkReport> runBenchmark() {
        return runBenchmark(new BenchmarkConfig());
    }
    
    /**
     * 运行自定义配置的基准测试
     */
    public CompletableFuture<BenchmarkReport> runBenchmark(BenchmarkConfig config) {
        plugin.getLogger().info("开始性能基准测试...");
        testStartTime.set(System.currentTimeMillis());
        results.clear();
        currentMetrics.clear();
        completedTests.set(0);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                BenchmarkReport report = new BenchmarkReport();
                
                // 1. 系统信息收集
                report.systemInfo = collectSystemInfo();
                plugin.getLogger().info("系统信息收集完成");
                
                // 2. 基线性能测试
                report.baselineMetrics = runBaselineTest(config);
                plugin.getLogger().info("基线性能测试完成");
                
                // 3. 负载测试
                report.loadTestResults = runLoadTests(config);
                plugin.getLogger().info("负载测试完成");
                
                // 4. 分析结果并生成建议
                report.optimizationSuggestions = analyzeAndGenerateSuggestions(report);
                plugin.getLogger().info("性能分析完成");
                
                // 5. 保存报告
                saveReport(report);
                
                plugin.getLogger().info("性能基准测试完成！报告已保存。");
                return report;
                
            } catch (Exception e) {
                plugin.getLogger().severe("基准测试失败: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, testExecutor);
    }
    
    /**
     * 收集系统信息
     */
    private SystemInfo collectSystemInfo() {
        SystemInfo info = new SystemInfo();
        
        // 服务器信息
        info.serverVersion = Bukkit.getVersion();
        info.bukkitVersion = Bukkit.getBukkitVersion();
        info.onlinePlayerCount = Bukkit.getOnlinePlayers().size();
        info.worldCount = Bukkit.getWorlds().size();
        
        // JVM信息
        Runtime runtime = Runtime.getRuntime();
        info.javaVersion = System.getProperty("java.version");
        info.maxMemory = runtime.maxMemory();
        info.totalMemory = runtime.totalMemory();
        info.freeMemory = runtime.freeMemory();
        info.availableProcessors = runtime.availableProcessors();
        
        // 插件配置
        info.currentConfig = captureCurrentConfig();
        
        return info;
    }
    
    /**
     * 运行基线性能测试
     */
    private BaselineMetrics runBaselineTest(BenchmarkConfig config) {
        BaselineMetrics metrics = new BaselineMetrics();
        
        plugin.getLogger().info("运行基线性能测试...");
        
        // 测试配置加载性能
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            configLoader.isDebugEnabled();
            configLoader.getChatPrefix();
            configLoader.getApiKey();
        }
        metrics.configLoadTime = (System.nanoTime() - startTime) / 1_000_000.0; // 毫秒
        
        // 测试环境收集性能
        EnvironmentCollector collector = plugin.getEnvironmentCollector();
        if (collector != null && !Bukkit.getOnlinePlayers().isEmpty()) {
            Player testPlayer = Bukkit.getOnlinePlayers().iterator().next();
            startTime = System.nanoTime();
            for (int i = 0; i < 10; i++) {
                try {
                    collector.collectEnvironmentInfo(testPlayer).join();
                } catch (Exception e) {
                    // 忽略环境收集错误
                }
            }
            metrics.environmentCollectionTime = (System.nanoTime() - startTime) / 10_000_000.0; // 平均毫秒
        }
        
        // 测试性能监控开销
        startTime = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            performanceMonitor.getCurrentTPS();
            performanceMonitor.getCurrentMode();
        }
        metrics.monitoringOverhead = (System.nanoTime() - startTime) / 100_000.0; // 平均微秒
        
        return metrics;
    }
    
    /**
     * 运行负载测试
     */
    private List<LoadTestResult> runLoadTests(BenchmarkConfig config) {
        List<LoadTestResult> results = new ArrayList<>();
        
        // 不同消息频率的负载测试
        int[] messageRates = {5, 10, 20, 30, 50}; // 每分钟消息数
        
        for (int rate : messageRates) {
            LoadTestResult result = runSingleLoadTest(rate, config.testDuration);
            results.add(result);
            
            // 测试间隔，让系统恢复
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return results;
    }
    
    /**
     * 运行单个负载测试
     */
    private LoadTestResult runSingleLoadTest(int messagesPerMinute, int durationSeconds) {
        plugin.getLogger().info(String.format("负载测试: %d消息/分钟, 持续%d秒", messagesPerMinute, durationSeconds));
        
        LoadTestResult result = new LoadTestResult();
        result.messageRate = messagesPerMinute;
        result.duration = durationSeconds;
        result.startTime = System.currentTimeMillis();
        
        AtomicInteger messagesSent = new AtomicInteger(0);
        AtomicInteger messagesProcessed = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        List<Double> tpsReadings = new ArrayList<>();
        List<Double> memoryReadings = new ArrayList<>();
        
        // 计算消息间隔
        long intervalMs = 60000 / messagesPerMinute;
        
        // 启动消息发送任务
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        
        // TPS和内存监控任务
        ScheduledFuture<?> monitorTask = scheduler.scheduleAtFixedRate(() -> {
            tpsReadings.add(performanceMonitor.getCurrentTPS());
            Runtime runtime = Runtime.getRuntime();
            double memoryUsage = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0);
            memoryReadings.add(memoryUsage);
        }, 0, 1, TimeUnit.SECONDS);
        
        // 消息发送任务（模拟）
        ScheduledFuture<?> messageTask = scheduler.scheduleAtFixedRate(() -> {
            long msgStartTime = System.currentTimeMillis();
            messagesSent.incrementAndGet();
            
            // 模拟消息处理（这里只做性能测试，不实际发送AI请求）
            CompletableFuture.runAsync(() -> {
                try {
                    // 模拟处理时间
                    Thread.sleep(50 + (int)(Math.random() * 100));
                    messagesProcessed.incrementAndGet();
                    totalResponseTime.addAndGet(System.currentTimeMillis() - msgStartTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
        
        // 等待测试完成
        try {
            Thread.sleep(durationSeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 停止任务
        messageTask.cancel(false);
        monitorTask.cancel(false);
        scheduler.shutdown();
        
        // 等待剩余任务完成
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 计算结果
        result.messagesSent = messagesSent.get();
        result.messagesProcessed = messagesProcessed.get();
        result.averageResponseTime = messagesProcessed.get() > 0 ? 
            (double)totalResponseTime.get() / messagesProcessed.get() : 0;
        result.successRate = messagesSent.get() > 0 ? 
            (double)messagesProcessed.get() / messagesSent.get() : 0;
        result.averageTPS = tpsReadings.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        result.minTPS = tpsReadings.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        result.averageMemoryUsage = memoryReadings.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        
        plugin.getLogger().info(String.format("负载测试结果: 发送%d, 处理%d, 成功率%.2f%%, 平均TPS%.1f", 
            result.messagesSent, result.messagesProcessed, result.successRate * 100, result.averageTPS));
        
        return result;
    }
    
    /**
     * 分析结果并生成优化建议
     */
    private List<OptimizationSuggestion> analyzeAndGenerateSuggestions(BenchmarkReport report) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();
        
        // 分析TPS性能
        analyzeTpsPerformance(report, suggestions);
        
        // 分析内存使用
        analyzeMemoryUsage(report, suggestions);
        
        // 分析响应时间
        analyzeResponseTime(report, suggestions);
        
        // 分析并发性能
        analyzeConcurrencyPerformance(report, suggestions);
        
        // 分析环境收集性能
        analyzeEnvironmentPerformance(report, suggestions);
        
        return suggestions;
    }
    
    /**
     * 分析TPS性能
     */
    private void analyzeTpsPerformance(BenchmarkReport report, List<OptimizationSuggestion> suggestions) {
        // 找到最低TPS
        double minTps = report.loadTestResults.stream()
            .mapToDouble(r -> r.minTPS)
            .min().orElse(20.0);
        
        if (minTps < 15.0) {
            suggestions.add(new OptimizationSuggestion(
                "TPS性能警告",
                "在负载测试中TPS降至" + String.format("%.1f", minTps) + "，建议优化性能设置",
                "performance.tps-threshold-basic",
                String.valueOf(Math.max(minTps - 2, 8.0)),
                OptimizationSuggestion.Priority.HIGH
            ));
        }
        
        if (minTps < 18.0) {
            suggestions.add(new OptimizationSuggestion(
                "降低检测范围",
                "TPS较低，建议减少环境检测范围以提高性能",
                "environment.entity-range",
                "8",
                OptimizationSuggestion.Priority.MEDIUM
            ));
        }
    }
    
    /**
     * 分析内存使用
     */
    private void analyzeMemoryUsage(BenchmarkReport report, List<OptimizationSuggestion> suggestions) {
        double maxMemory = report.systemInfo.maxMemory / (1024.0 * 1024.0); // MB
        
        double avgMemoryUsage = report.loadTestResults.stream()
            .mapToDouble(r -> r.averageMemoryUsage)
            .average().orElse(0);
        
        double memoryUsagePercent = avgMemoryUsage / maxMemory * 100;
        
        if (memoryUsagePercent > 80) {
            suggestions.add(new OptimizationSuggestion(
                "内存使用过高",
                String.format("内存使用率达到%.1f%%，建议减少缓存时间", memoryUsagePercent),
                "environment.cache-ttl",
                "30000",
                OptimizationSuggestion.Priority.HIGH
            ));
        }
        
        if (memoryUsagePercent > 60) {
            suggestions.add(new OptimizationSuggestion(
                "优化历史记录",
                "建议减少对话历史记录数量以节省内存",
                "history.max-history",
                "3",
                OptimizationSuggestion.Priority.MEDIUM
            ));
        }
    }
    
    /**
     * 分析响应时间
     */
    private void analyzeResponseTime(BenchmarkReport report, List<OptimizationSuggestion> suggestions) {
        double avgResponseTime = report.loadTestResults.stream()
            .mapToDouble(r -> r.averageResponseTime)
            .average().orElse(0);
        
        if (avgResponseTime > 1000) {
            suggestions.add(new OptimizationSuggestion(
                "响应时间过长",
                String.format("平均响应时间%.0fms，建议启用性能优化", avgResponseTime),
                "performance.auto-optimize-enabled",
                "true",
                OptimizationSuggestion.Priority.HIGH
            ));
        }
        
        if (avgResponseTime > 500) {
            suggestions.add(new OptimizationSuggestion(
                "增加冷却时间",
                "建议增加用户冷却时间以减轻系统负载",
                "performance.rate-limit.normal-user",
                "5000",
                OptimizationSuggestion.Priority.MEDIUM
            ));
        }
    }
    
    /**
     * 分析并发性能
     */
    private void analyzeConcurrencyPerformance(BenchmarkReport report, List<OptimizationSuggestion> suggestions) {
        if (report.loadTestResults.isEmpty()) return;
        
        // 找到最佳消息频率
        LoadTestResult bestResult = report.loadTestResults.stream()
            .max(Comparator.comparingDouble(r -> r.averageTPS))
            .orElse(null);
        
        if (bestResult != null && bestResult.messageRate <= 10) {
            suggestions.add(new OptimizationSuggestion(
                "限制消息频率",
                String.format("最佳性能在%d消息/分钟时达到，建议设置速率限制", bestResult.messageRate),
                "performance.rate-limit.max-messages-per-minute",
                String.valueOf(Math.max(bestResult.messageRate * 2, 5)),
                OptimizationSuggestion.Priority.MEDIUM
            ));
        }
    }
    
    /**
     * 分析环境收集性能
     */
    private void analyzeEnvironmentPerformance(BenchmarkReport report, List<OptimizationSuggestion> suggestions) {
        if (report.loadTestResults.isEmpty()) return;
        
        if (report.loadTestResults.stream()
            .anyMatch(r -> r.averageResponseTime > 1000)) {
            suggestions.add(new OptimizationSuggestion(
                "环境收集优化",
                "环境收集响应时间较长，建议优化收集策略",
                "environment.smart-collection-interval",
                "10000",
                OptimizationSuggestion.Priority.MEDIUM
            ));
        }
    }
    
    /**
     * 应用优化建议
     */
    public void applyOptimizations(List<OptimizationSuggestion> suggestions) {
        plugin.getLogger().info("开始应用性能优化建议...");
        
        int applied = 0;
        for (OptimizationSuggestion suggestion : suggestions) {
            try {
                // 获取当前值
                String currentValue = configLoader.getString(suggestion.configPath, "");
                
                // 应用新值
                configLoader.set(suggestion.configPath, parseConfigValue(suggestion.recommendedValue));
                
                plugin.getLogger().info(String.format("已应用优化: %s = %s (原值: %s)", 
                    suggestion.configPath, suggestion.recommendedValue, currentValue));
                applied++;
                
            } catch (Exception e) {
                plugin.getLogger().warning("应用优化失败 " + suggestion.configPath + ": " + e.getMessage());
            }
        }
        
        if (applied > 0) {
            // 保存配置
            configLoader.saveConfig();
            plugin.getLogger().info(String.format("成功应用%d项优化建议，配置已保存", applied));
        } else {
            plugin.getLogger().info("没有应用任何优化建议");
        }
    }
    
    /**
     * 解析配置值
     */
    private Object parseConfigValue(String value) {
        // 尝试解析为数字
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            // 尝试解析为布尔值
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                return Boolean.parseBoolean(value);
            }
            // 返回字符串
            return value;
        }
    }
    
    /**
     * 保存性能报告
     */
    private void saveReport(BenchmarkReport report) {
        try {
            File reportsDir = new File(plugin.getDataFolder(), "performance-reports");
            if (!reportsDir.exists()) {
                reportsDir.mkdirs();
            }
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            File reportFile = new File(reportsDir, "benchmark_report_" + timestamp + ".txt");
            
            try (FileWriter writer = new FileWriter(reportFile)) {
                writer.write(generateReportText(report));
            }
            
            plugin.getLogger().info("性能报告已保存至: " + reportFile.getAbsolutePath());
            
        } catch (IOException e) {
            plugin.getLogger().severe("保存性能报告失败: " + e.getMessage());
        }
    }
    
    /**
     * 生成报告文本
     */
    private String generateReportText(BenchmarkReport report) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== AI Chat Plugin 性能基准测试报告 ===\n");
        sb.append("生成时间: ").append(LocalDateTime.now()).append("\n\n");
        
        // 系统信息
        sb.append("=== 系统信息 ===\n");
        sb.append("服务器版本: ").append(report.systemInfo.serverVersion).append("\n");
        sb.append("Java版本: ").append(report.systemInfo.javaVersion).append("\n");
        sb.append("最大内存: ").append(report.systemInfo.maxMemory / (1024*1024)).append(" MB\n");
        sb.append("在线玩家: ").append(report.systemInfo.onlinePlayerCount).append("\n");
        sb.append("世界数量: ").append(report.systemInfo.worldCount).append("\n\n");
        
        // 基线性能
        sb.append("=== 基线性能 ===\n");
        sb.append("配置加载时间: ").append(String.format("%.2f", report.baselineMetrics.configLoadTime)).append(" ms\n");
        sb.append("环境收集时间: ").append(String.format("%.2f", report.baselineMetrics.environmentCollectionTime)).append(" ms\n");
        sb.append("监控开销: ").append(String.format("%.2f", report.baselineMetrics.monitoringOverhead)).append(" μs\n\n");
        
        // 负载测试结果
        sb.append("=== 负载测试结果 ===\n");
        for (LoadTestResult result : report.loadTestResults) {
            sb.append(String.format("消息频率: %d/分钟 | 成功率: %.1f%% | 平均TPS: %.1f | 响应时间: %.0fms\n",
                result.messageRate, result.successRate * 100, result.averageTPS, result.averageResponseTime));
        }
        sb.append("\n");
        
        // 优化建议
        sb.append("=== 优化建议 ===\n");
        if (report.optimizationSuggestions.isEmpty()) {
            sb.append("当前配置已经很好，无需优化。\n");
        } else {
            for (OptimizationSuggestion suggestion : report.optimizationSuggestions) {
                sb.append(String.format("[%s] %s\n", suggestion.priority, suggestion.title));
                sb.append("说明: ").append(suggestion.description).append("\n");
                sb.append("建议: ").append(suggestion.configPath).append(" = ").append(suggestion.recommendedValue).append("\n\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 捕获当前配置
     */
    private Map<String, Object> captureCurrentConfig() {
        Map<String, Object> config = new HashMap<>();
        
        // 主要配置项
        config.put("debug.enabled", configLoader.isDebugEnabled());
        config.put("chat.enabled", configLoader.isChatEnabled());
        config.put("performance.auto-optimize-enabled", configLoader.isAutoOptimizeEnabled());
        config.put("environment.entity-range", configLoader.getEntityDetectionRange());
        config.put("history.max-history", configLoader.getConversationMaxHistory());
        config.put("performance.rate-limit.normal-user", configLoader.getNormalUserCooldown());
        
        return config;
    }
    
    /**
     * 关闭基准测试系统
     */
    public void shutdown() {
        testExecutor.shutdown();
        try {
            if (!testExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                testExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            testExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // 内部类定义
    public static class BenchmarkConfig {
        public int testDuration = DEFAULT_TEST_DURATION;
        public int concurrentUsers = DEFAULT_CONCURRENT_USERS;
        public int messageRate = DEFAULT_MESSAGE_RATE;
        public boolean skipAITests = true; // 默认跳过真实AI测试
    }
    
    public static class BenchmarkReport {
        public SystemInfo systemInfo;
        public BaselineMetrics baselineMetrics;
        public List<LoadTestResult> loadTestResults = new ArrayList<>();
        public List<OptimizationSuggestion> optimizationSuggestions = new ArrayList<>();
    }
    
    public static class SystemInfo {
        public String serverVersion;
        public String bukkitVersion;
        public String javaVersion;
        public long maxMemory;
        public long totalMemory;
        public long freeMemory;
        public int availableProcessors;
        public int onlinePlayerCount;
        public int worldCount;
        public Map<String, Object> currentConfig;
    }
    
    public static class BaselineMetrics {
        public double configLoadTime; // ms
        public double environmentCollectionTime; // ms
        public double monitoringOverhead; // μs
    }
    
    public static class LoadTestResult {
        public int messageRate;
        public int duration;
        public long startTime;
        public int messagesSent;
        public int messagesProcessed;
        public double averageResponseTime;
        public double successRate;
        public double averageTPS;
        public double minTPS;
        public double averageMemoryUsage;
    }
    
    public static class OptimizationSuggestion {
        public enum Priority { HIGH, MEDIUM, LOW }
        
        public String title;
        public String description;
        public String configPath;
        public String recommendedValue;
        public Priority priority;
        
        public OptimizationSuggestion(String title, String description, String configPath, 
                                    String recommendedValue, Priority priority) {
            this.title = title;
            this.description = description;
            this.configPath = configPath;
            this.recommendedValue = recommendedValue;
            this.priority = priority;
        }
    }
    
    /**
     * 基准测试结果
     */
    public static class BenchmarkResult {
        public String testName;
        public long duration;
        public boolean success;
        public String details;
        public Map<String, Double> metrics = new HashMap<>();
    }
} 