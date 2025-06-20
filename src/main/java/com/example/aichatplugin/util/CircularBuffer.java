package com.example.aichatplugin.util;

import java.util.Arrays;

/**
 * 环形缓冲区
 * 
 * 用于精确控制消息频率，实现滑动窗口算法
 * 特点：
 * 1. 固定大小，自动覆盖最旧数据
 * 2. 线程安全设计
 * 3. 精确的时间窗口统计
 */
public class CircularBuffer {
    private final long[] timestamps;
    private int head;
    private final int size;
    
    /**
     * 创建指定大小的环形缓冲区
     * @param size 缓冲区大小
     */
    public CircularBuffer(int size) {
        this.size = size;
        this.timestamps = new long[size];
        Arrays.fill(timestamps, Long.MIN_VALUE);
        this.head = 0;
    }
    
    /**
     * 添加时间戳
     * @param time 当前时间戳
     */
    public synchronized void add(long time) {
        timestamps[head] = time;
        head = (head + 1) % size;
    }
    
    /**
     * 统计指定时间窗口内的记录数
     * @param now 当前时间
     * @param windowMs 时间窗口（毫秒）
     * @return 窗口内的记录数
     */
    public synchronized int countInWindow(long now, long windowMs) {
        return (int) Arrays.stream(timestamps)
            .filter(t -> now - t < windowMs)
            .count();
    }
    
    /**
     * 统计最近1秒内的记录数
     * @param now 当前时间
     * @return 1秒内的记录数
     */
    public synchronized int countInLastSecond(long now) {
        return countInWindow(now, 1000);
    }
    
    /**
     * 清理过期数据
     * @param now 当前时间
     * @param maxAge 最大年龄（毫秒）
     */
    public synchronized void cleanup(long now, long maxAge) {
        for (int i = 0; i < size; i++) {
            if (now - timestamps[i] > maxAge) {
                timestamps[i] = Long.MIN_VALUE;
            }
        }
    }
    
    /**
     * 获取缓冲区大小
     */
    public int size() {
        return size;
    }
    
    /**
     * 清空缓冲区
     */
    public synchronized void clear() {
        Arrays.fill(timestamps, Long.MIN_VALUE);
        head = 0;
    }
} 