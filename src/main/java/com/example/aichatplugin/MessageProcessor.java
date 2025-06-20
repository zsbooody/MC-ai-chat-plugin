package com.example.aichatplugin;

import org.bukkit.entity.Player;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.Arrays;
import com.example.aichatplugin.util.CircularBuffer;

/**
 * 消息处理器
 * 
 * 统一处理消息相关的通用功能：
 * 1. 消息验证
 * 2. 频率限制
 * 3. 冷却管理
 * 4. 敏感词过滤
 */
public class MessageProcessor {
    private final AIChatPlugin plugin;
    private final ConfigLoader config;
    
    // 常量定义
    public static final int MAX_MESSAGE_LENGTH = 256;
    public static final int MAX_MESSAGES_PER_SECOND = 5;
    public static final int MAX_BUKKIT_MESSAGE_LENGTH = 256;
    
    // 频率限制器 - 使用环形缓冲区
    private final Map<UUID, CircularBuffer> rateWindows = new ConcurrentHashMap<>();
    
    // 冷却时间管理
    private final Map<UUID, Long> cooldownMap = new ConcurrentHashMap<>();
    
    public MessageProcessor(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigLoader();
        
        // 启动清理任务
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            cleanup();
        }, 600L, 600L); // 每30秒清理一次
    }
    
    /**
     * 消息消毒
     */
    public String sanitizeMessage(String message) {
        if (message == null) return "";
        return message
            .replace("\u202E", "")  // 清除RLO字符
            .replaceAll("\\p{C}", "") // 移除控制字符
            .trim();
    }
    
    /**
     * 验证消息是否有效
     */
    public boolean isValidMessage(String message) {
        if (message == null || message.trim().isEmpty() || message.startsWith("/")) {
            return false;
        }
        
        String sanitized = sanitizeMessage(message);
        if (sanitized.isEmpty()) {
            return false;
        }
        
        // 检查消息长度
        if (sanitized.length() > MAX_MESSAGE_LENGTH) {
            return false;
        }
        
        // 检查UTF-8编码
        if (!Charset.forName("UTF-8").newEncoder().canEncode(sanitized)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查消息发送频率
     */
    public boolean checkMessageRate(UUID playerId) {
        long now = System.currentTimeMillis();
        CircularBuffer buffer = rateWindows.computeIfAbsent(playerId, 
            k -> new CircularBuffer(MAX_MESSAGES_PER_SECOND));
        
        buffer.add(now);
        return buffer.countInLastSecond(now) <= MAX_MESSAGES_PER_SECOND;
    }
    
    /**
     * 检查玩家是否免除频率限制
     */
    public boolean isRateLimitExempt(Player player) {
        return player.hasPermission("aichat.ratelimit.bypass");
    }
    
    /**
     * 尝试获取冷却许可
     */
    public boolean tryAcquire(Player player) {
        if (!checkCooldown(player)) {
            return false;
        }
        updateCooldown(player);
        return true;
    }
    
    /**
     * 检查冷却时间
     */
    public boolean checkCooldown(Player player) {
        long cooldown = getCooldownTime(player);
        if (cooldown <= 0) {
            return true;
        }
        
        long lastUsed = cooldownMap.getOrDefault(player.getUniqueId(), 0L);
        long timeLeft = cooldown - (System.currentTimeMillis() - lastUsed);
        
        if (timeLeft > 0) {
            String timeLeftStr = String.format("%.1f", timeLeft / 1000.0);
            player.sendMessage(config.getMessageFormat("cooldown").replace("{time}", timeLeftStr));
            return false;
        }
        return true;
    }
    
    /**
     * 获取冷却时间
     */
    private long getCooldownTime(Player player) {
        // 优先级顺序：无冷却 > VIP > 默认
        if (player.hasPermission("aichat.cooldown.none")) {
            return 0;
        }
        
        for (String perm : Arrays.asList("aichat.vip", "aichat.premium")) {
            if (player.hasPermission(perm)) {
                return config.getLong("cooldown.vip", 1000);
            }
        }
        
        return config.getLong("cooldown.default", 3000);
    }
    
    /**
     * 更新冷却时间
     */
    public void updateCooldown(Player player) {
        cooldownMap.put(player.getUniqueId(), System.currentTimeMillis());
    }
    
    /**
     * 清理过期数据
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        long cutoff = now - 3600000; // 1小时前的数据
        
        // 清理冷却时间数据
        cooldownMap.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        
        // 清理频率限制数据
        rateWindows.forEach((uuid, buffer) -> {
            buffer.cleanup(now, 3600000);
            if (buffer.countInLastSecond(now) == 0) {
                rateWindows.remove(uuid);
            }
        });
    }
} 