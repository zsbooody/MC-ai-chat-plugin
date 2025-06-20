package com.example.aichatplugin.status;

import com.example.aichatplugin.AIChatPlugin;
import com.example.aichatplugin.ConfigLoader;
import com.example.aichatplugin.PlayerStatusListener;
import com.example.aichatplugin.performance.PerformanceMonitor;
import com.example.aichatplugin.performance.HardwareMonitor;
import com.example.aichatplugin.performance.OperationMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.StringJoiner;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.Collections;
import java.util.concurrent.atomic.LongAdder;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
import java.util.Iterator;

/**
 * 插件状态服务
 * 负责收集和生成插件状态报告，整合所有性能统计信息
 */
public class PluginStatusService {
    private static final String CONFIG_WINDOW_SIZE = "status.window_size";
    private static final String CONFIG_RETENTION_HOURS = "status.retention_hours";
    private static final String CONFIG_SUGGESTION_RULES = "status.suggestion_rules";
    private static final String CONFIG_REPORT_TEMPLATE = "status.report_template";
    
    private final AIChatPlugin plugin;
    private FileConfiguration config;
    private final PerformanceMonitor performanceMonitor;
    private final HardwareMonitor hardwareMonitor;
    private final Map<String, LongAdder> eventCounters;
    private final Map<String, Long> lastAccess;
    private final Set<String> builtInEvents;
    private final List<SuggestionRule> suggestionRules;
    private final Map<String, Function<PluginStatus, String>> sectionGenerators;
    private final CircularBuffer tpsBuffer;
    private final CircularBuffer memoryBuffer;
    private final long startTime;
    private final ScheduledExecutorService cleanupScheduler;
    private final Map<Long, PluginStatus> historicalStatus = new ConcurrentSkipListMap<>();
    private static final int HEALTH_SCORE_MAX = 100;
    private static final double MEMORY_THRESHOLD = 2.0;
    private static final double TPS_THRESHOLD = 18.0;
    private double lastTps = 20;
    private double lastFreeMemory = 0;
    private static final double RESPONSE_TIME_SPIKE_THRESHOLD = 2.0;
    private static final double RESPONSE_TIME_ABSOLUTE_THRESHOLD = 1000.0;
    private static final double HEALTH_TREND_THRESHOLD = 0.2; // 20%变化阈值
    private static final long HEALTH_TREND_WINDOW = 3600000; // 1小时
    private final Map<String, EventPriority> eventPriorities = new ConcurrentHashMap<>();
    private static final Set<String> CORE_EVENTS = Set.of(
        "join", "quit", "respawn", "level_up", 
        "damage", "death", "advancement", "potion_effect"
    );
    private static final int MAX_HISTORY_SIZE = 1000;
    private volatile PerformanceMonitor.PerformanceSnapshot lastSnapshot;
    private volatile long lastSnapshotTime;
    private volatile double lastResponseTime = 0;
    private final Object snapshotLock = new Object();

    public enum EventPriority {
        CRITICAL(3),
        HIGH(2),
        NORMAL(1);

        private final int weight;

        EventPriority(int weight) {
            this.weight = weight;
        }

        public int getWeight() {
            return weight;
        }
    }

    public PluginStatusService(AIChatPlugin plugin, PerformanceMonitor performanceMonitor, HardwareMonitor hardwareMonitor) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.performanceMonitor = performanceMonitor;
        this.hardwareMonitor = hardwareMonitor;
        this.eventCounters = new ConcurrentHashMap<>();
        this.lastAccess = new ConcurrentHashMap<>();
        this.builtInEvents = new HashSet<>(Arrays.asList(
            "chat_processed", "command_executed", "ai_response_time"
        ));
        this.suggestionRules = new ArrayList<>();
        this.sectionGenerators = new HashMap<>();
        
        // 初始化时间窗口
        int windowSize = config.getInt(CONFIG_WINDOW_SIZE, 300); // 默认5分钟
        this.tpsBuffer = new CircularBuffer(windowSize, plugin);
        this.memoryBuffer = new CircularBuffer(windowSize, plugin);
        
        // 初始化建议规则
        initializeSuggestionRules();
        
        // 初始化报告模板
        initializeReportTemplate();
        
        this.startTime = System.currentTimeMillis();
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        startCleanupTask();
        
        initializeEventPriorities();
    }
    
    /**
     * 时间窗口统计缓冲区
     */
    private static class CircularBuffer {
        private long[] buffer;
        private int head;
        private int tail;
        private int count;
        private long sum;
        private final Object lock = new Object();
        private final AIChatPlugin plugin;

        public CircularBuffer(int capacity, AIChatPlugin plugin) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Buffer capacity must be positive");
            }
            this.buffer = new long[capacity];
            this.head = 0;
            this.tail = 0;
            this.count = 0;
            this.sum = 0;
            this.plugin = plugin;
        }

        public void add(long value) {
            synchronized (lock) {
                if (count == buffer.length) {
                    sum -= buffer[head];
                    head = (head + 1) % buffer.length;
                } else {
                    count++;
                }
                buffer[tail] = value;
                tail = (tail + 1) % buffer.length;
                sum += value;
            }
        }

        public double getAverage() {
            synchronized (lock) {
                return count > 0 ? (double) sum / count : 0;
            }
        }

        public void reset() {
            synchronized (lock) {
                Arrays.fill(buffer, 0);
                head = 0;
                tail = 0;
                count = 0;
                sum = 0;
            }
        }

        public void resize(int newCapacity) {
            synchronized (lock) {
                if (newCapacity <= 0) {
                    throw new IllegalArgumentException("Buffer capacity must be positive");
                }
                
                try {
                    long[] newBuffer = new long[newCapacity];
                    int elementsToCopy = Math.min(count, newCapacity);
                    
                    for (int i = 0; i < elementsToCopy; i++) {
                        newBuffer[i] = buffer[(head + i) % buffer.length];
                    }
                    
                    buffer = newBuffer;
                    head = 0;
                    tail = elementsToCopy;
                    count = elementsToCopy;
                    sum = Arrays.stream(buffer).sum();
                } catch (Exception e) {
                    plugin.getLogger().severe("环形缓冲区调整失败: " + e.getMessage());
                }
            }
        }

        public int capacity() {
            return buffer.length;
        }
    }
    
    /**
     * 初始化事件计数器
     */
    private void initializeEventCounters() {
        // 内置事件计数器
        eventCounters.put("join", new LongAdder());
        eventCounters.put("quit", new LongAdder());
        eventCounters.put("respawn", new LongAdder());
        eventCounters.put("level_up", new LongAdder());
        eventCounters.put("damage", new LongAdder());
        eventCounters.put("death", new LongAdder());
        eventCounters.put("advancement", new LongAdder());
        eventCounters.put("potion_effect", new LongAdder());
        
        // AI响应相关计数器
        eventCounters.put("ai_response_count", new LongAdder());
        eventCounters.put("ai_response_time", new LongAdder());
        
        // 性能相关计数器（简化版 - 移除CPU相关）
        eventCounters.put("tps_drops", new LongAdder());
        eventCounters.put("memory_warnings", new LongAdder());
    }
    
    /**
     * 记录事件处理（带时间窗口统计）
     */
    public void recordEventProcessed(String eventType) {
        EventPriority priority = eventPriorities.getOrDefault(eventType, EventPriority.NORMAL);
        tpsBuffer.add(priority.getWeight());
        
        // 确保计数器存在
        eventCounters.computeIfAbsent(eventType, k -> new LongAdder()).increment();
        lastAccess.put(eventType, System.currentTimeMillis());
    }
    
    /**
     * 获取事件在时间窗口内的平均值
     */
    public double getEventAveragePerMinute(String eventType) {
        CircularBuffer buffer = tpsBuffer;
        return buffer != null ? buffer.getAverage() : 0.0;
    }
    
    /**
     * 清理旧统计数据
     */
    private void cleanupOldStats() {
        long retentionMillis = TimeUnit.HOURS.toMillis(
            config.getLong(CONFIG_RETENTION_HOURS, 48)
        );
        
        // 清理过期数据
        long now = System.currentTimeMillis();
        eventCounters.keySet().removeIf(key -> 
            !builtInEvents.contains(key) && 
            now - lastAccess.getOrDefault(key, 0L) > retentionMillis
        );
        
        // 重置统计
        if (now - startTime > retentionMillis) {
            resetStats();
        }
    }
    
    /**
     * 关闭服务，释放资源
     */
    public void shutdown() {
        if (cleanupScheduler != null) {
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
        
        // 清理历史数据
        historicalStatus.clear();
        
        // 重置所有缓冲区
        tpsBuffer.reset();
        memoryBuffer.reset();
        
        // 清理计数器
        eventCounters.clear();
        lastAccess.clear();
    }
    
    /**
     * 记录响应生成
     * @param responseTime 响应时间(毫秒)
     */
    public void recordResponseGenerated(long responseTime) {
        LongAdder responseCount = eventCounters.computeIfAbsent(
            "ai_response_count", k -> new LongAdder());
        LongAdder responseTimeTotal = eventCounters.computeIfAbsent(
            "ai_response_time", k -> new LongAdder());
        
        responseCount.increment();
        responseTimeTotal.add(responseTime);
    }
    
    /**
     * 记录自定义统计项（直接设置值）
     * @param key 统计项名称
     * @param value 统计值
     */
    public void recordCustomStat(String key, long value) {
        eventCounters.compute(key, (k, v) -> {
            LongAdder newCounter = new LongAdder();
            newCounter.add(value);
            return newCounter;
        });
        lastAccess.put(key, System.currentTimeMillis());
    }

    /**
     * 增加自定义统计项（累加）
     * @param key 统计项名称
     * @param delta 增加值
     */
    public void incrementCustomStat(String key, long delta) {
        eventCounters.computeIfAbsent(key, k -> new LongAdder()).add(delta);
        lastAccess.put(key, System.currentTimeMillis());
    }

    /**
     * 获取某个统计项的当前值
     * @param key 统计项名称
     * @return 当前值
     */
    public long getCustomStat(String key) {
        LongAdder counter = eventCounters.get(key);
        return counter != null ? counter.sum() : 0L;
    }

    /**
     * 获取所有自定义统计项快照（只读）
     */
    public Map<String, Long> getAllCustomStats() {
        Map<String, Long> snapshot = new HashMap<>();
        for (Map.Entry<String, LongAdder> entry : eventCounters.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().sum());
        }
        return snapshot;
    }
    
    /**
     * 获取当前状态
     */
    public PluginStatus getCurrentStatus() {
        PluginStatus status = new PluginStatus();
        
        // 设置功能状态
        for (PluginStatus.Feature feature : PluginStatus.Feature.values()) {
            status.setFeatureStatus(feature, true);
        }
        
        // 收集配置参数
        collectConfigParams(status);
        
        // 收集性能数据
        collectPerformanceData(status);
        
        return status;
    }
    
    /**
     * 生成状态报告
     */
    public String generateStatusReport() {
        PluginStatus status = getCurrentStatus();
        return generateReport(status);
    }
    
    /**
     * 收集配置参数
     */
    private void collectConfigParams(PluginStatus status) {
        Map<String, Object> params = new HashMap<>();
        
        // 事件冷却时间
        params.put("global_cooldown", config.getLong("global_cooldown"));
        params.put("damage_cooldown", config.getLong("damage_cooldown"));
        params.put("potion_cooldown", config.getLong("potion_cooldown"));
        
        // 伤害阈值
        params.put("damage_threshold", config.getLong("damage_threshold"));
        
        status.setConfigParams(params);
    }
    
    /**
     * 收集性能数据
     */
    private void collectPerformanceData(PluginStatus status) {
        status.setPerformanceStats(collectStats());
    }
    
    /**
     * 重置性能统计
     */
    public void resetStats() {
        eventCounters.clear();
        lastAccess.clear();
        tpsBuffer.reset();
        memoryBuffer.reset();
    }
    
    private void initializeSuggestionRules() {
        List<String> enabledRules = config.getStringList(CONFIG_SUGGESTION_RULES);
        // 移除CPU建议规则
        if (enabledRules.contains("memory")) {
            suggestionRules.add(new LowMemoryRule());
        }
        if (enabledRules.contains("mode")) {
            suggestionRules.add(new OperationModeRule());
        }
    }
    
    private void initializeReportTemplate() {
        // 注册内置报告生成器
        sectionGenerators.put("header", status -> "📊 插件状态报告");
        sectionGenerators.put("features", this::generateFeatureSection);
        sectionGenerators.put("performance", this::generatePerformanceSection);
        sectionGenerators.put("suggestions", this::generateSuggestionsSection);
    }
    
    public String generateReport(PluginStatus status) {
        StringJoiner report = new StringJoiner("\n");
        
        // 按配置顺序生成报告
        List<String> sections = config.getStringList(CONFIG_REPORT_TEMPLATE);
        for (String section : sections) {
            Function<PluginStatus, String> generator = sectionGenerators.get(section);
            if (generator != null) {
                report.add(generator.apply(status));
            }
        }
        
        return report.toString();
    }
    
    private String generateFeatureSection(PluginStatus status) {
        StringJoiner section = new StringJoiner("\n");
        section.add("🔧 功能状态");
        for (PluginStatus.Feature feature : PluginStatus.Feature.values()) {
            section.add(String.format("- %s: %s", 
                feature.getDescription(), 
                status.getFeatureStatus(feature) ? "✅" : "❌"));
        }
        return section.toString();
    }
    
    private String generatePerformanceSection(PluginStatus status) {
        StringJoiner section = new StringJoiner("\n");
        section.add("📈 性能指标");
        status.getPerformanceStats().forEach((key, value) -> 
            section.add(String.format("- %s: %s", key, value)));
        return section.toString();
    }
    
    private String generateSuggestionsSection(PluginStatus status) {
        StringJoiner section = new StringJoiner("\n");
        section.add("💡 优化建议");
        suggestionRules.stream()
            .filter(rule -> rule.applies(status))
            .map(SuggestionRule::getSuggestion)
            .forEach(section::add);
        return section.toString();
    }
    
    private void startCleanupTask() {
        long retentionHours = config.getLong(CONFIG_RETENTION_HOURS, 48);
        cleanupScheduler.scheduleAtFixedRate(
            this::cleanupOldStats,
            1, 1, TimeUnit.HOURS
        );
    }
    
    private interface SuggestionRule {
        boolean applies(PluginStatus status);
        String getSuggestion();
    }
    
    private static class LowMemoryRule implements SuggestionRule {
        @Override
        public boolean applies(PluginStatus status) {
            return (double) status.getPerformanceStats().get("free_memory") < 2.0;
        }
        
        @Override
        public String getSuggestion() {
            return "⚠️ 可用内存不足，建议增加服务器内存";
        }
    }
    
    private static class OperationModeRule implements SuggestionRule {
        @Override
        public boolean applies(PluginStatus status) {
            return status.getFeatureStatus(PluginStatus.Feature.LEVEL_UP);
        }
        
        @Override
        public String getSuggestion() {
            return "ℹ️ 当前处于性能模式，部分功能可能受限";
        }
    }

    public double getHealthScore(PluginStatus status) {
        // 从配置获取阈值和权重（简化版 - 移除CPU相关）
        double memoryThreshold = getAnomalyThreshold("memory", MEMORY_THRESHOLD);
        double tpsThreshold = getAnomalyThreshold("tps", TPS_THRESHOLD);
        double responseThreshold = getAnomalyThreshold("response", RESPONSE_TIME_ABSOLUTE_THRESHOLD);
        
        // 从配置获取权重
        double memoryWeight = getHealthWeight("memory", 1.0);
        double tpsWeight = getHealthWeight("tps", 1.0);
        double responseWeight = getHealthWeight("response", 1.0);
        
        double score = HEALTH_SCORE_MAX;
        
        // 内存评分 - 线性扣分
        if (status.getFreeMemory() < memoryThreshold) {
            double memoryPenalty = (memoryThreshold - status.getFreeMemory()) / memoryThreshold * 30;
            score -= memoryPenalty * memoryWeight;
        }
        
        // TPS评分 - 指数型惩罚
        if (status.getTps() < tpsThreshold) {
            double tpsDeficit = tpsThreshold - status.getTps();
            double tpsPenalty = Math.pow(tpsDeficit, 1.5) * 2;
            score -= tpsPenalty * tpsWeight;
        }
        
        // 响应时间评分
        if (lastResponseTime > responseThreshold) {
            double responsePenalty = (lastResponseTime - responseThreshold) / responseThreshold * 20;
            score -= responsePenalty * responseWeight;
        }
        
        return Math.max(0, Math.min(HEALTH_SCORE_MAX, score));
    }

    public void saveHistoricalStatus() {
        PluginStatus currentStatus = getCurrentStatus();
        long timestamp = currentStatus.getTimestamp();
        historicalStatus.put(timestamp, currentStatus);
        
        // 自动化清理
        long retentionMillis = TimeUnit.HOURS.toMillis(
            config.getLong(CONFIG_RETENTION_HOURS, 48)
        );
        long cutoff = System.currentTimeMillis() - retentionMillis;
        historicalStatus.keySet().removeIf(t -> t < cutoff);
    }

    public List<PluginStatus> getHistoricalStatus(long startTime, long endTime) {
        return historicalStatus.entrySet().stream()
            .filter(entry -> entry.getKey() >= startTime && entry.getKey() <= endTime)
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    public List<String> detectAnomalies(PluginStatus status) {
        List<String> anomalies = new ArrayList<>();
        
        // 内存异常检测
        double memoryThreshold = config.getDouble("anomaly.memory", MEMORY_THRESHOLD);
        if (status.getFreeMemory() < memoryThreshold) {
            anomalies.add("⚠️ 可用内存过低: " + String.format("%.1f", status.getFreeMemory()) + "GB");
        }
        
        // TPS异常检测
        double tpsThreshold = config.getDouble("anomaly.tps", TPS_THRESHOLD);
        if (status.getTps() < tpsThreshold) {
            anomalies.add("⚠️ TPS过低: " + String.format("%.1f", status.getTps()));
        }
        
        // 响应时间异常检测
        double responseSpikeThreshold = config.getDouble("anomaly.response_spike", RESPONSE_TIME_SPIKE_THRESHOLD);
        double responseAbsoluteThreshold = config.getDouble("anomaly.response_absolute", RESPONSE_TIME_ABSOLUTE_THRESHOLD);
        
        if (lastResponseTime > responseAbsoluteThreshold || 
            (lastResponseTime > 0 && lastResponseTime / Math.max(1, lastResponseTime) > responseSpikeThreshold)) {
            anomalies.add("⚠️ AI响应时间异常: " + lastResponseTime + "ms");
        }
        
        // 事件频率异常检测
        double eventFreqThreshold = config.getDouble("anomaly.event_frequency", 50.0);
        long totalEvents = eventCounters.values().stream().mapToLong(LongAdder::sum).sum();
        if (totalEvents > eventFreqThreshold) {
            anomalies.add("⚠️ 事件频率过高: " + totalEvents + "/分钟");
        }
        
        return anomalies;
    }

    public String getHealthStatusIcon(double score) {
        if (score >= 80) return "🟢";
        if (score >= 60) return "🟡";
        if (score >= 40) return "🟠";
        return "🔴";
    }

    private String generateHealthSection(PluginStatus status) {
        double score = getHealthScore(status);
        return String.format("📊 健康度: %s %.1f/100", 
            getHealthStatusIcon(score), score);
    }

    public Map<String, Double> getEventRates() {
        Map<String, Double> rates = new HashMap<>();
        long totalEvents = eventCounters.values().stream()
            .mapToLong(LongAdder::sum)
            .sum();
            
        if (totalEvents > 0) {
            eventCounters.forEach((event, counter) -> {
                double rate = (double) counter.sum() / totalEvents * 100;
                rates.put(event, rate);
            });
        }
        
        return rates;
    }

    public void reloadConfiguration() {
        int newWindowSize = config.getInt(CONFIG_WINDOW_SIZE, 300);
        
        // 调整缓冲区大小
        tpsBuffer.resize(newWindowSize);
        memoryBuffer.resize(newWindowSize);
        
        // 重新加载建议规则
        suggestionRules.clear();
        initializeSuggestionRules();
        
        // 重新加载报告模板
        sectionGenerators.clear();
        initializeReportTemplate();
        
        plugin.getLogger().info("状态服务配置已重新加载");
    }

    private void cleanupOldData() {
        long retentionMillis = TimeUnit.HOURS.toMillis(
            config.getLong(CONFIG_RETENTION_HOURS, 48)
        );
        long cutoff = System.currentTimeMillis() - retentionMillis;
        
        Iterator<Long> it = historicalStatus.keySet().iterator();
        while (it.hasNext()) {
            if (it.next() < cutoff) {
                it.remove();
            }
        }
    }

    private double getAnomalyThreshold(String key, double defaultValue) {
        return config.getDouble("anomaly." + key, defaultValue);
    }

    private double getHealthWeight(String factor, double defaultValue) {
        return config.getDouble("health.weights." + factor, defaultValue);
    }

    private Map<String, Object> collectStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 从监控器获取实时数据（简化版 - 移除CPU相关）
        stats.put("free_memory", hardwareMonitor.getFreeMemory());
        stats.put("system_memory", hardwareMonitor.getSystemFreeMemory());
        stats.put("cpu_cores", hardwareMonitor.getAvailableCores());
        
        // 从性能监控器获取数据
        stats.put("current_tps", performanceMonitor.getCurrentTPS());
        stats.put("current_mode", performanceMonitor.getCurrentMode().name());
        stats.put("entity_count", performanceMonitor.getEntityCount());
        stats.put("chunk_count", performanceMonitor.getChunkCount());
        
        // 添加历史快照数据
        PerformanceMonitor.PerformanceSnapshot snap = getLatestSnapshot();
        if (snap != null) {
            stats.put("snapshot_tps", snap.getTps());
            stats.put("snapshot_mode", snap.getCurrentMode().name());
            stats.put("target_mode", snap.getTargetMode().name());
        }
        
        // 添加AI相关统计
        long responseCount = getCustomStat("ai_response_count");
        long responseTimeTotal = getCustomStat("ai_response_time");
        if (responseCount > 0) {
            stats.put("avg_response_time", responseTimeTotal / responseCount);
            stats.put("response_count", responseCount);
        }
        
        // 添加事件统计
        for (String event : CORE_EVENTS) {
            stats.put("event_" + event, getCustomStat(event));
        }
        
        return stats;
    }

    private PerformanceMonitor.PerformanceSnapshot getLatestSnapshot() {
        long now = System.currentTimeMillis();
        if (lastSnapshot != null && now - lastSnapshotTime <= 1000) {
            return lastSnapshot;
        }

        synchronized (snapshotLock) {
            // 双重检查锁定模式
            if (lastSnapshot != null && now - lastSnapshotTime <= 1000) {
                return lastSnapshot;
            }

            // 使用ConcurrentSkipListMap的lastEntry()直接获取最新快照
            ConcurrentSkipListMap<Long, PerformanceMonitor.PerformanceSnapshot> history = 
                (ConcurrentSkipListMap<Long, PerformanceMonitor.PerformanceSnapshot>) 
                performanceMonitor.getPerformanceHistory();
            
            Map.Entry<Long, PerformanceMonitor.PerformanceSnapshot> lastEntry = history.lastEntry();
            
            if (lastEntry != null) {
                lastSnapshot = lastEntry.getValue();
                lastSnapshotTime = now;
                
                // 检查并清理历史数据
                if (history.size() > MAX_HISTORY_SIZE) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            // 异步清理旧数据
                            long cutoff = System.currentTimeMillis() - (MAX_HISTORY_SIZE * 1000L);
                            history.headMap(cutoff).clear();
                        } catch (Exception e) {
                            plugin.getLogger().warning("清理历史数据时发生错误: " + e.getMessage());
                        }
                    });
                }
            }
            
            return lastSnapshot;
        }
    }

    private void appendPerformanceStats(StringBuilder sb, PluginStatus status) {
        sb.append("📈 性能指标:\n");
        sb.append("- TPS: ").append(String.format("%.1f", status.getTps())).append("\n");
        sb.append("- 内存: ").append(String.format("%.1f", status.getFreeMemory())).append("GB\n");
        
        // 从性能统计中获取其他信息
        Map<String, Object> stats = status.getPerformanceStats();
        sb.append("- 系统内存: ").append(String.format("%.1f", (Double) stats.getOrDefault("system_memory", 0.0))).append("GB\n");
        sb.append("- CPU核心: ").append(stats.getOrDefault("cpu_cores", 0)).append("\n");
        sb.append("- 运行模式: ").append(stats.getOrDefault("current_mode", "UNKNOWN")).append("\n");
        sb.append("- 健康度: ").append(String.format("%.1f", getHealthScore(status))).append("/100\n");
    }

    private void initializeEventPriorities() {
        ConfigurationSection prioritySection = config.getConfigurationSection("event_priority");
        if (prioritySection != null) {
            // 先加载配置
            loadPriorityEvents(prioritySection, "critical", EventPriority.CRITICAL);
            loadPriorityEvents(prioritySection, "high", EventPriority.HIGH);
            loadPriorityEvents(prioritySection, "normal", EventPriority.NORMAL);
        }
        
        // 仅当配置不存在时设置默认值
        if (eventPriorities.isEmpty()) {
            eventPriorities.put("death", EventPriority.CRITICAL);
            eventPriorities.put("damage", EventPriority.CRITICAL);
            eventPriorities.put("join", EventPriority.HIGH);
            eventPriorities.put("quit", EventPriority.HIGH);
            eventPriorities.put("level_up", EventPriority.NORMAL);
            eventPriorities.put("advancement", EventPriority.NORMAL);
        }
    }

    private void loadPriorityEvents(ConfigurationSection section, String key, EventPriority priority) {
        List<String> events = section.getStringList(key);
        events.forEach(event -> eventPriorities.put(event, priority));
    }

    public String getHealthTrend() {
        List<PluginStatus> lastHour = getHistoricalStatus(
            System.currentTimeMillis() - HEALTH_TREND_WINDOW,
            System.currentTimeMillis()
        );
        
        if (lastHour.isEmpty()) {
            return "→ 暂无趋势数据";
        }
        
        double current = getHealthScore(getCurrentStatus());
        double average = lastHour.stream()
            .mapToDouble(this::getHealthScore)
            .average()
            .orElseGet(() -> 
                historicalStatus.values().stream()
                    .mapToDouble(this::getHealthScore)
                    .average()
                    .orElse(current)
            );
        
        if (current > average * (1 + HEALTH_TREND_THRESHOLD)) {
            return "↑↑ 健康度显著提升";
        } else if (current < average * (1 - HEALTH_TREND_THRESHOLD)) {
            return "↓↓ 健康度明显下降";
        } else {
            return "→ 健康度稳定";
        }
    }
} 