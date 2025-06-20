package com.example.aichatplugin;

/**
 * 环境信息缓存类
 * 
 * 职责：
 * 1. 存储环境信息数据
 * 2. 记录缓存时间戳
 * 3. 标记环境信息需求
 */
public class CachedEnvironment {
    public final String data;
    public final long timestamp;
    public final boolean required;
    
    public CachedEnvironment(String data, boolean required) {
        this.data = data;
        this.timestamp = System.currentTimeMillis();
        this.required = required;
    }
    
    /**
     * 检查缓存是否过期
     * @param ttl 过期时间（毫秒）
     * @return 是否过期
     */
    public boolean isExpired(long ttl) {
        return System.currentTimeMillis() - timestamp > ttl;
    }
} 