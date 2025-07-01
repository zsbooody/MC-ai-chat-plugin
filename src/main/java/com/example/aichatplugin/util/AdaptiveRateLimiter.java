package com.example.aichatplugin.util;

import com.example.aichatplugin.AIChatPlugin;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自适应频率限制器
 * 
 * 根据服务器TPS动态调整消息处理频率，优化性能
 * 当服务器卡顿时降低频率，性能良好时提高频率
 */
public class AdaptiveRateLimiter {
    private final AIChatPlugin plugin;
    private final AtomicInteger currentLimit = new AtomicInteger(10);
    private final AtomicInteger baseLimit;
    private final AtomicLong lastAdjustTime = new AtomicLong(0);
    
    // 调整间隔和阈值
    private static final long ADJUST_INTERVAL = 5000; // 5秒调整一次
    private static final double HIGH_TPS_THRESHOLD = 18.0;
    private static final double LOW_TPS_THRESHOLD = 15.0;
    private static final double CRITICAL_TPS_THRESHOLD = 10.0;
    
    // 频率限制范围
    private static final int MIN_LIMIT = 3;
    private static final int MAX_LIMIT = 20;
    private static final int DEFAULT_LIMIT = 10;
    
    public AdaptiveRateLimiter(AIChatPlugin plugin, int baseLimit) {
        this.plugin = plugin;
        this.baseLimit = new AtomicInteger(baseLimit);
        this.currentLimit.set(baseLimit);
    }
    
    public AdaptiveRateLimiter(AIChatPlugin plugin) {
        this(plugin, DEFAULT_LIMIT);
    }
    
    /**
     * 获取当前频率限制
     */
    public int getCurrentLimit() {
        return currentLimit.get();
    }
    
    /**
     * 根据服务器TPS调整频率限制
     */
    public void adjustLimit(double serverTPS) {
        long now = System.currentTimeMillis();
        
        // 检查是否需要调整（避免频繁调整）
        if (now - lastAdjustTime.get() < ADJUST_INTERVAL) {
            return;
        }
        
        int newLimit;
        
        if (serverTPS >= HIGH_TPS_THRESHOLD) {
            // 高TPS：提高限制
            newLimit = Math.min(baseLimit.get() + 5, MAX_LIMIT);
        } else if (serverTPS >= LOW_TPS_THRESHOLD) {
            // 中等TPS：使用基础限制
            newLimit = baseLimit.get();
        } else if (serverTPS >= CRITICAL_TPS_THRESHOLD) {
            // 低TPS：降低限制
            newLimit = Math.max(baseLimit.get() - 3, MIN_LIMIT);
        } else {
            // 严重卡顿：大幅降低限制
            newLimit = MIN_LIMIT;
        }
        
        int oldLimit = currentLimit.getAndSet(newLimit);
        lastAdjustTime.set(now);
        
        // 记录调整日志
        if (oldLimit != newLimit) {
            plugin.debug(String.format("频率限制调整: %d -> %d (TPS: %.1f)", 
                oldLimit, newLimit, serverTPS));
        }
    }
    
    /**
     * 检查是否超出频率限制
     */
    public boolean isAllowed(CircularBuffer messageHistory) {
        long now = System.currentTimeMillis();
        int count = messageHistory.countInWindow(now, 60000); // 1分钟窗口
        return count < getCurrentLimit();
    }
    
    /**
     * 设置基础限制
     */
    public void setBaseLimit(int limit) {
        if (limit >= MIN_LIMIT && limit <= MAX_LIMIT) {
            baseLimit.set(limit);
            plugin.debug("基础频率限制已设置为: " + limit);
        }
    }
    
    /**
     * 获取基础限制
     */
    public int getBaseLimit() {
        return baseLimit.get();
    }
    
    /**
     * 重置为基础限制
     */
    public void reset() {
        currentLimit.set(baseLimit.get());
        lastAdjustTime.set(0);
        plugin.debug("频率限制已重置为基础值: " + baseLimit.get());
    }
    
    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("当前限制: %d, 基础限制: %d, 范围: %d-%d", 
            getCurrentLimit(), getBaseLimit(), MIN_LIMIT, MAX_LIMIT);
    }
    
    /**
     * 手动设置临时限制
     */
    public void setTemporaryLimit(int limit, long durationMs) {
        if (limit >= MIN_LIMIT && limit <= MAX_LIMIT) {
            currentLimit.set(limit);
            
            // 创建定时任务恢复基础限制
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                currentLimit.set(baseLimit.get());
                plugin.debug("临时频率限制已到期，恢复为基础值: " + baseLimit.get());
            }, durationMs / 50); // 转换为tick
            
            plugin.debug(String.format("设置临时频率限制: %d，持续 %.1f 秒", 
                limit, durationMs / 1000.0));
        }
    }
} 