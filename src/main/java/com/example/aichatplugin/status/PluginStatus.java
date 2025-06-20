package com.example.aichatplugin.status;

import java.util.Map;
import java.util.HashMap;

/**
 * 插件状态数据模型
 * 存储插件的当前状态信息，包括功能状态、配置参数和性能统计
 */
public class PluginStatus {
    private final long timestamp;
    private Map<String, Boolean> featureStatus;
    private Map<String, Object> configParams;
    private Map<String, Object> performanceStats;
    
    /**
     * 功能枚举
     */
    public enum Feature {
        JOIN("玩家加入事件响应"),
        QUIT("玩家退出事件响应"),
        RESPAWN("玩家重生事件响应"),
        LEVEL_UP("玩家升级事件响应"),
        DAMAGE("玩家受伤事件响应"),
        DEATH("玩家死亡事件响应"),
        ADVANCEMENT("成就事件响应"),
        POTION_EFFECT("药水效果变更响应");
        
        private final String description;
        
        Feature(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public PluginStatus() {
        this.timestamp = System.currentTimeMillis();
        this.featureStatus = new HashMap<>();
        this.configParams = new HashMap<>();
        this.performanceStats = new HashMap<>();
    }
    
    /**
     * 获取状态时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * 设置功能状态
     * @param feature 功能枚举
     * @param enabled 是否启用
     */
    public void setFeatureStatus(Feature feature, boolean enabled) {
        this.featureStatus.put(feature.name().toLowerCase(), enabled);
    }
    
    /**
     * 获取功能状态
     * @param feature 功能枚举
     * @return 是否启用
     */
    public boolean getFeatureStatus(Feature feature) {
        return this.featureStatus.getOrDefault(feature.name().toLowerCase(), false);
    }
    
    /**
     * 设置配置参数
     * @param params 配置参数映射
     */
    public void setConfigParams(Map<String, Object> params) {
        this.configParams = params;
    }
    
    /**
     * 获取配置参数
     * @return 配置参数映射
     */
    public Map<String, Object> getConfigParams() {
        return configParams;
    }
    
    /**
     * 获取指定配置参数
     * @param key 参数键
     * @return 参数值
     */
    public Object getConfigParam(String key) {
        return configParams.get(key);
    }
    
    /**
     * 设置性能统计
     * @param stats 性能统计映射
     */
    public void setPerformanceStats(Map<String, Object> stats) {
        this.performanceStats = stats;
    }
    
    /**
     * 获取性能统计
     * @return 性能统计映射
     */
    public Map<String, Object> getPerformanceStats() {
        return performanceStats;
    }
    
    /**
     * 获取可用内存 (GB)
     */
    public double getFreeMemory() {
        return (double) performanceStats.getOrDefault("free_memory", 0.0);
    }
    
    /**
     * 获取 TPS
     */
    public double getTps() {
        return (double) performanceStats.getOrDefault("tps", 20.0);
    }
} 