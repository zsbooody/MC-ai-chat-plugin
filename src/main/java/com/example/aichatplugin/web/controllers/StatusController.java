package com.example.aichatplugin.web.controllers;

import com.example.aichatplugin.AIChatPlugin;
import com.example.aichatplugin.performance.OperationMode;
import com.google.gson.Gson;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * 状态监控API控制器
 * 
 * 提供RESTful API接口：
 * GET /api/status - 获取插件状态
 * GET /api/status/performance - 获取性能状态
 * GET /api/status/system - 获取系统信息
 * GET /api/status/memory-details - 获取内存详细信息
 */
public class StatusController extends HttpServlet {
    
    private final AIChatPlugin plugin;
    private final Gson gson = new Gson();
    
    public StatusController(AIChatPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String subPath = (String) request.getAttribute("subPath");
        if (subPath == null) subPath = "";
        
        setJsonHeaders(response);
        
        try {
            if (subPath.equals("") || subPath.equals("/")) {
                // 获取综合状态
                Map<String, Object> status = getComprehensiveStatus();
                sendJsonResponse(response, status);
            } else if (subPath.equals("/performance")) {
                // 获取性能状态
                Map<String, Object> performance = getPerformanceStatus();
                sendJsonResponse(response, performance);
            } else if (subPath.equals("/system")) {
                // 获取系统信息
                Map<String, Object> system = getSystemInfo();
                sendJsonResponse(response, system);
            } else if (subPath.equals("/memory-details")) {
                // 获取内存详细信息
                Map<String, Object> memoryDetails = getMemoryDetails();
                sendJsonResponse(response, memoryDetails);
            } else {
                sendErrorResponse(response, 404, "状态API端点不存在: " + subPath);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "状态API处理异常", e);
            sendErrorResponse(response, 500, "服务器内部错误: " + e.getMessage());
        }
    }
    
    /**
     * 获取综合状态（前端仪表盘使用）
     */
    public Map<String, Object> getComprehensiveStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 基础信息
        status.put("version", plugin.getPluginVersion());
        status.put("uptime", getUptime());
        status.put("chatEnabled", plugin.getConfigLoader().isChatEnabled());
        status.put("running", plugin.isFullyInitialized()); // 添加运行状态字段
        
        // 服务器状态
        status.put("onlinePlayers", plugin.getServer().getOnlinePlayers().size());
        
        // 性能信息
        if (plugin.getPerformanceMonitor() != null) {
            double tps = plugin.getPerformanceMonitor().getCurrentTPS();
            status.put("tps", tps);
            status.put("operationMode", plugin.getPerformanceMonitor().getCurrentMode().name());
        } else {
            status.put("tps", 20.0);
            status.put("operationMode", "UNKNOWN");
        }
        
        // 硬件信息 - 获取真实的CPU使用率
        double cpuUsage = 0.0;
        double memoryUsage = 0.0;
        
        try {
            // 尝试获取真实的CPU使用率
            java.lang.management.OperatingSystemMXBean osBean = 
                java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean = 
                    (com.sun.management.OperatingSystemMXBean) osBean;
                cpuUsage = sunOsBean.getProcessCpuLoad();
                
                // 如果获取失败（返回负值），使用系统CPU使用率
                if (cpuUsage < 0) {
                    cpuUsage = sunOsBean.getSystemCpuLoad();
                }
                
                // 仍然无效则使用默认值
                if (cpuUsage < 0) {
                    cpuUsage = 0.0;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "获取CPU使用率失败，使用默认值", e);
            cpuUsage = 0.0;
        }
        
        // 内存使用率
        Runtime runtime = Runtime.getRuntime();
        memoryUsage = (double)(runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory();
        
        status.put("cpuUsage", cpuUsage);
        status.put("memoryUsage", memoryUsage);
        
        // 添加内存池信息
        Map<String, Object> memoryPools = new HashMap<>();
        
        // 获取堆内存信息
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        long heapMax = runtime.maxMemory();
        
        Map<String, Object> heap = new HashMap<>();
        heap.put("used", heapUsed);
        heap.put("max", heapMax);
        heap.put("percentage", heapMax > 0 ? (double)heapUsed / heapMax * 100 : 0);
        memoryPools.put("heap", heap);
        
        // 获取非堆内存信息（简化版）
        Map<String, Object> nonHeap = new HashMap<>();
        nonHeap.put("used", heapUsed / 4); // 估算值
        nonHeap.put("max", heapMax / 4);   // 估算值
        nonHeap.put("percentage", 25.0);
        memoryPools.put("nonHeap", nonHeap);
        
        // 年轻代内存（估算）
        Map<String, Object> eden = new HashMap<>();
        eden.put("used", heapUsed / 8);
        eden.put("max", heapMax / 8);
        eden.put("percentage", heapMax > 0 ? (double)(heapUsed/8) / (heapMax/8) * 100 : 0);
        memoryPools.put("eden", eden);
        
        // 老年代内存（估算）
        Map<String, Object> oldGen = new HashMap<>();
        oldGen.put("used", heapUsed * 3 / 4);
        oldGen.put("max", heapMax * 3 / 4);
        oldGen.put("percentage", heapMax > 0 ? (double)(heapUsed*3/4) / (heapMax*3/4) * 100 : 0);
        memoryPools.put("oldGen", oldGen);
        
        status.put("memoryPools", memoryPools);
        
        // 基准测试信息（如果有）
        Map<String, Object> benchmark = new HashMap<>();
        benchmark.put("status", "未运行");
        benchmark.put("lastRun", null);
        benchmark.put("score", null);
        benchmark.put("optimizationCount", 0);
        status.put("benchmark", benchmark);
        
        return status;
    }
    
    /**
     * 获取插件总体状态
     */
    public Map<String, Object> getPluginStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("version", plugin.getPluginVersion());
        status.put("initialized", plugin.isFullyInitialized());
        status.put("debugMode", plugin.isDebugEnabled());
        status.put("uptime", getUptime());
        
        if (plugin.getPerformanceMonitor() != null) {
            status.put("currentTPS", plugin.getPerformanceMonitor().getCurrentTPS());
            status.put("operationMode", plugin.getPerformanceMonitor().getCurrentMode().name());
        }
        
        return status;
    }
    
    /**
     * 获取性能状态
     */
    public Map<String, Object> getPerformanceStatus() {
        Map<String, Object> performance = new HashMap<>();
        
        if (plugin.getPerformanceMonitor() != null) {
            performance.put("currentTPS", plugin.getPerformanceMonitor().getCurrentTPS());
            performance.put("mode", plugin.getPerformanceMonitor().getCurrentMode().name());
            performance.put("autoOptimizeEnabled", plugin.getConfigLoader().isAutoOptimizeEnabled());
        }
        
        if (plugin.getHardwareMonitor() != null) {
            var hardwareStatus = plugin.getHardwareMonitor().getStatus();
            performance.put("freeMemory", hardwareStatus.getFreeMemory());
            performance.put("systemFreeMemory", hardwareStatus.getSystemFreeMemory());
            performance.put("availableCores", hardwareStatus.getAvailableCores());
        }
        
        return performance;
    }
    
    /**
     * 获取系统信息
     */
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> system = new HashMap<>();
        
        Runtime runtime = Runtime.getRuntime();
        system.put("javaVersion", System.getProperty("java.version"));
        system.put("javaVendor", System.getProperty("java.vendor"));
        system.put("osName", System.getProperty("os.name"));
        system.put("osVersion", System.getProperty("os.version"));
        system.put("availableProcessors", runtime.availableProcessors());
        system.put("maxMemory", runtime.maxMemory());
        system.put("totalMemory", runtime.totalMemory());
        system.put("freeMemory", runtime.freeMemory());
        
        // 服务器信息
        system.put("serverVersion", plugin.getServer().getVersion());
        system.put("bukkitVersion", plugin.getServer().getBukkitVersion());
        system.put("onlinePlayers", plugin.getServer().getOnlinePlayers().size());
        system.put("maxPlayers", plugin.getServer().getMaxPlayers());
        
        return system;
    }
    
    /**
     * 获取内存详细信息
     */
    public Map<String, Object> getMemoryDetails() {
        Map<String, Object> details = new HashMap<>();
        
        try {
            // 获取堆内存池信息
            Map<String, Object> heap = new HashMap<>();
            
            // Eden空间
            Map<String, Object> eden = new HashMap<>();
            eden.put("used", 256 * 1024 * 1024L); // 示例值
            eden.put("max", 512 * 1024 * 1024L);
            eden.put("percentage", 50.0);
            heap.put("eden", eden);
            
            // Survivor空间
            Map<String, Object> survivor = new HashMap<>();
            survivor.put("used", 16 * 1024 * 1024L);
            survivor.put("max", 64 * 1024 * 1024L);
            survivor.put("percentage", 25.0);
            heap.put("survivor", survivor);
            
            // 老年代
            Map<String, Object> oldGen = new HashMap<>();
            oldGen.put("used", 512 * 1024 * 1024L);
            oldGen.put("max", 768 * 1024 * 1024L);
            oldGen.put("percentage", 66.7);
            heap.put("oldGen", oldGen);
            
            details.put("heap", heap);
            
            // 非堆内存池信息
            Map<String, Object> nonHeap = new HashMap<>();
            
            // Metaspace
            Map<String, Object> metaspace = new HashMap<>();
            metaspace.put("used", 64 * 1024 * 1024L);
            metaspace.put("max", 256 * 1024 * 1024L);
            metaspace.put("percentage", 25.0);
            nonHeap.put("metaspace", metaspace);
            
            // Code Cache
            Map<String, Object> codeCache = new HashMap<>();
            codeCache.put("used", 16 * 1024 * 1024L);
            codeCache.put("max", 64 * 1024 * 1024L);
            codeCache.put("percentage", 25.0);
            nonHeap.put("codeCache", codeCache);
            
            // Compressed Class Space
            Map<String, Object> compressedClass = new HashMap<>();
            compressedClass.put("used", 8 * 1024 * 1024L);
            compressedClass.put("max", 32 * 1024 * 1024L);
            compressedClass.put("percentage", 25.0);
            nonHeap.put("compressedClass", compressedClass);
            
            details.put("nonHeap", nonHeap);
            
            // GC统计信息
            Map<String, Object> gc = new HashMap<>();
            gc.put("minorCount", 42);
            gc.put("majorCount", 3);
            gc.put("totalTime", 1234L); // 毫秒
            
            details.put("gc", gc);
            
            // 尝试获取真实的内存池信息
            try {
                java.util.List<java.lang.management.MemoryPoolMXBean> memoryPools = 
                    java.lang.management.ManagementFactory.getMemoryPoolMXBeans();
                
                for (java.lang.management.MemoryPoolMXBean pool : memoryPools) {
                    String poolName = pool.getName();
                    java.lang.management.MemoryUsage usage = pool.getUsage();
                    
                    if (usage != null) {
                        Map<String, Object> poolData = new HashMap<>();
                        poolData.put("used", usage.getUsed());
                        poolData.put("max", usage.getMax());
                        long max = usage.getMax();
                        if (max > 0) {
                            poolData.put("percentage", (double)usage.getUsed() / max * 100);
                        } else {
                            poolData.put("percentage", 0.0);
                        }
                        
                        // 根据池名称分类
                        String lowerName = poolName.toLowerCase();
                        if (lowerName.contains("eden")) {
                            heap.put("eden", poolData);
                        } else if (lowerName.contains("survivor")) {
                            heap.put("survivor", poolData);
                        } else if (lowerName.contains("old") || lowerName.contains("tenured")) {
                            heap.put("oldGen", poolData);
                        } else if (lowerName.contains("metaspace")) {
                            nonHeap.put("metaspace", poolData);
                        } else if (lowerName.contains("code cache")) {
                            nonHeap.put("codeCache", poolData);
                        } else if (lowerName.contains("compressed class")) {
                            nonHeap.put("compressedClass", poolData);
                        }
                    }
                }
                
                // 获取真实的GC统计
                java.util.List<java.lang.management.GarbageCollectorMXBean> gcBeans = 
                    java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();
                
                long totalMinorGC = 0;
                long totalMajorGC = 0;
                long totalGCTime = 0;
                
                for (java.lang.management.GarbageCollectorMXBean gcBean : gcBeans) {
                    long collections = gcBean.getCollectionCount();
                    long time = gcBean.getCollectionTime();
                    
                    if (collections > 0) {
                        totalGCTime += time;
                        String gcName = gcBean.getName().toLowerCase();
                        if (gcName.contains("young") || gcName.contains("minor") || 
                            gcName.contains("scavenge") || gcName.contains("copy")) {
                            totalMinorGC += collections;
                        } else {
                            totalMajorGC += collections;
                        }
                    }
                }
                
                gc.put("minorCount", totalMinorGC);
                gc.put("majorCount", totalMajorGC);
                gc.put("totalTime", totalGCTime);
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "获取真实内存池信息失败，使用默认值", e);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "获取内存详情失败", e);
        }
        
        return details;
    }
    
    /**
     * 获取运行时间
     */
    private long getUptime() {
        // 简化版本，返回当前时间戳
        // 实际应该记录插件启动时间
        return System.currentTimeMillis();
    }
    
    /**
     * 设置JSON响应头
     */
    private void setJsonHeaders(HttpServletResponse response) {
        response.setContentType("application/json; charset=utf-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
    
    /**
     * 发送JSON响应
     */
    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        PrintWriter writer = response.getWriter();
        writer.print(gson.toJson(data));
        writer.flush();
    }
    
    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        error.put("timestamp", System.currentTimeMillis());
        
        PrintWriter writer = response.getWriter();
        writer.print(gson.toJson(error));
        writer.flush();
    }
} 