package com.example.aichatplugin.performance;

import com.example.aichatplugin.AIChatPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Bukkit;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import com.sun.management.UnixOperatingSystemMXBean;
import java.util.logging.Level;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 硬件监控器（简化版）
 * 
 * 职责：
 * 1. 监控服务器内存使用情况
 * 2. 检查内存是否满足运行要求
 * 3. 提供内存状态报告
 * 
 * 简化说明：
 * - 移除了CPU检测（容易在不同环境下出现问题）
 * - 移除了硬盘检测（检测不稳定且误报较多）
 * - 只保留内存检测（最稳定可靠的指标）
 */
public class HardwareMonitor implements AutoCloseable {
    private final AIChatPlugin plugin;
    private final FileConfiguration config;
    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    
    // 内存检测阈值
    private double minAvailableMemory;
    private double minSystemMemory;
    private int minCpuCores;
    private String hardwareWarningMessage;
    
    // 检测状态
    private boolean lastCheckResult = true;
    private long lastWarningTime = 0;
    private static final long WARNING_COOLDOWN = 30000; // 30秒警告冷却时间
    
    // 配置键名
    private static final String CONFIG_MIN_FREE_MEMORY = "hardware.min-free-memory";
    private static final String CONFIG_MIN_FREE_SYSTEM_MEMORY = "hardware.min-free-system-memory";
    private static final String CONFIG_MIN_AVAILABLE_CORES = "hardware.min-available-cores";
    
    // 默认阈值
    private static final double DEFAULT_MIN_FREE_MEMORY = 0.5; // GB
    private static final double DEFAULT_MIN_FREE_SYSTEM_MEMORY = 0.5; // GB
    private static final int DEFAULT_MIN_AVAILABLE_CORES = 2;
    
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    
    // 性能计数器
    private final Map<String, AtomicLong> performanceCounters = new ConcurrentHashMap<>();

    public HardwareMonitor(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        
        // 初始化性能计数器
        performanceCounters.put("memory_checks", new AtomicLong(0));
        performanceCounters.put("core_checks", new AtomicLong(0));
        
        int parallelThreads = config.getInt("hardware.parallel-threads", 2);
        scheduler = Executors.newScheduledThreadPool(parallelThreads, r -> {
            Thread t = new Thread(r, "HardwareMonitor-Worker");
            t.setDaemon(true);
            return t;
        });
        
        loadConfig();
        
        plugin.getLogger().info("硬件监控器已初始化 - 简化版（仅内存检测）");
    }

    /**
     * 重新加载配置
     */
    public void loadConfig() {
        minAvailableMemory = config.getDouble("hardware.min-free-memory", 0.5);
        minSystemMemory = config.getDouble("hardware.min-free-system-memory", 0.5);
        minCpuCores = config.getInt("hardware.min-available-cores", 2);
        hardwareWarningMessage = config.getString("hardware.hardware-warning", "&c服务器硬件资源不足（内存）");
        
        plugin.getLogger().info("硬件监控配置已加载 - 内存要求: " + minAvailableMemory + "GB, 系统内存: " + minSystemMemory + "GB, 核心: " + minCpuCores);
    }

    /**
     * 检查硬件是否满足最低要求（简化版）
     */
    public boolean meetsRequirements() {
        try {
            // 简化检测：只检查内存和CPU核心数
            boolean memoryOk = checkMemory();
            boolean systemMemoryOk = checkSystemMemory();
            boolean coresOk = checkCores();
            
            // 详细记录检测结果
            HardwareStatus status = getStatus();
            plugin.debug(String.format("硬件检测详情 - 内存: %.1fGB (%s), 系统内存: %.1fGB (%s), 核心: %d (%s)",
                status.getFreeMemory(), memoryOk ? "通过" : "失败",
                status.getSystemFreeMemory(), systemMemoryOk ? "通过" : "失败",
                status.getAvailableCores(), coresOk ? "通过" : "失败"));
            
            // 所有检测项都必须通过
            boolean currentResult = memoryOk && systemMemoryOk && coresOk;
            
            // 处理硬件不足警告
            if (!currentResult && lastCheckResult) {
                sendHardwareWarning();
                // 通知性能监控器
                notifyPerformanceMonitor();
            }
            
            lastCheckResult = currentResult;
            return currentResult;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "硬件检查时发生错误", e);
            // 发生异常时返回true，避免因检测错误导致误切换
            return true;
        }
    }

    /**
     * 通知性能监控器硬件状态变化
     */
    private void notifyPerformanceMonitor() {
        HardwareStatus status = getStatus();
        
        // 检查可用内存
        if (status.getFreeMemory() < minAvailableMemory) {
            plugin.getPerformanceMonitor().handleHardwareWarning("可用内存不足: " + 
                String.format("%.1f", status.getFreeMemory()) + "GB");
        }
        
        // 检查系统内存
        if (status.getSystemFreeMemory() < minSystemMemory) {
            plugin.getPerformanceMonitor().handleHardwareWarning("系统可用内存不足: " + 
                String.format("%.1f", status.getSystemFreeMemory()) + "GB");
        }
        
        // 检查CPU核心数
        if (status.getAvailableCores() < minCpuCores) {
            plugin.getPerformanceMonitor().handleHardwareWarning("CPU核心数不足: " + 
                status.getAvailableCores() + "核心");
        }
    }

    /**
     * 获取当前硬件状态报告
     */
    public HardwareStatus getStatus() {
        return new HardwareStatus(
            getFreeMemory(),
            getSystemFreeMemory(),
            getAvailableCores()
        );
    }

    private boolean checkMemory() {
        double freeMemory = getFreeMemory();
        performanceCounters.get("memory_checks").incrementAndGet();
        
        if (freeMemory < minAvailableMemory) {
            plugin.debug("可用内存不足: " + freeMemory + "GB，要求: " + minAvailableMemory + "GB");
            return false;
        }
        return true;
    }

    private boolean checkSystemMemory() {
        double freeSystemMem = getSystemFreeMemory();
        if (freeSystemMem < minSystemMemory) {
            plugin.debug("系统可用内存不足: " + freeSystemMem + "GB，要求: " + minSystemMemory + "GB");
            return false;
        }
        return true;
    }

    private boolean checkCores() {
        int cores = getAvailableCores();
        performanceCounters.get("core_checks").incrementAndGet();
        
        if (cores < minCpuCores) {
            plugin.debug("可用CPU核心数不足: " + cores + "，要求: " + minCpuCores);
            return false;
        }
        return true;
    }

    public double getFreeMemory() {
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        return (maxMemory - usedMemory) / (1024.0 * 1024.0 * 1024.0); // 转换为GB
    }

    public double getSystemFreeMemory() {
        try {
            if (osBean instanceof UnixOperatingSystemMXBean) {
                return ((UnixOperatingSystemMXBean) osBean).getFreePhysicalMemorySize() / (1024.0 * 1024 * 1024);
            } else {
                return Runtime.getRuntime().freeMemory() / (1024.0 * 1024 * 1024);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "获取系统空闲内存失败", e);
            return 0.0;
        }
    }

    public int getAvailableCores() {
        return Runtime.getRuntime().availableProcessors();
    }

    private void sendHardwareWarning() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastWarningTime < WARNING_COOLDOWN) {
            return;
        }

        String warning = hardwareWarningMessage;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage(warning);
        });

        lastWarningTime = currentTime;
    }

    @Override
    public void close() {
        isShuttingDown.set(true);
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    /**
     * 硬件状态数据类（简化版）
     */
    public static class HardwareStatus {
        private final double freeMemory;
        private final double systemFreeMemory;
        private final int availableCores;

        public HardwareStatus(double freeMemory, double systemFreeMemory, int availableCores) {
            this.freeMemory = freeMemory;
            this.systemFreeMemory = systemFreeMemory;
            this.availableCores = availableCores;
        }

        public double getFreeMemory() { return freeMemory; }
        public double getSystemFreeMemory() { return systemFreeMemory; }
        public int getAvailableCores() { return availableCores; }

        @Override
        public String toString() {
            return String.format(
                "内存: %.1fGB | 系统内存: %.1fGB | 核心: %d",
                freeMemory, systemFreeMemory, availableCores
            );
        }
    }
} 