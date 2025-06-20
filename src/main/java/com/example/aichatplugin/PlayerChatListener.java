/**
 * PlayerChatListener - 玩家聊天监听器
 * 
 * 负责处理玩家聊天事件，包括：
 * 1. 消息过滤
 * 2. 对话处理
 * 3. 环境信息收集
 * 4. 响应生成
 */

package com.example.aichatplugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import java.util.logging.Level;

/**
 * 玩家聊天监听器
 * 
 * 职责：
 * 1. 监听玩家聊天消息
 * 2. 过滤无效消息
 * 3. 触发对话处理
 */
public class PlayerChatListener implements Listener {
    private final AIChatPlugin plugin;
    private final ConversationManager conversationManager;
    private final MessageProcessor messageProcessor;
    
    public PlayerChatListener(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.conversationManager = plugin.getConversationManager();
        this.messageProcessor = new MessageProcessor(plugin);
    }
    
    /**
     * 处理玩家聊天事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        // 异步验证
        if (!messageProcessor.isValidMessage(message)) {
            return;
        }
        
        // 频率检查（线程安全）
        if (!messageProcessor.isRateLimitExempt(player) && 
            !messageProcessor.checkMessageRate(player.getUniqueId())) {
            scheduleMessage(player, "§c消息发送过快，请稍后再试");
            return;
        }
        
        // 检查消息前缀
        String prefix = plugin.getConfigLoader().getChatPrefix();
        if (prefix == null || prefix.isEmpty() || message.startsWith(prefix)) {
            // 如果前缀为空，直接处理消息；否则移除前缀
            String content = prefix == null || prefix.isEmpty() ? 
                message : message.substring(prefix.length()).trim();
            
            if (!content.isEmpty()) {
                // 提交到主线程处理
                scheduleProcessing(player, content);
            }
        }
    }
    
    /**
     * 调度消息发送
     */
    private void scheduleMessage(Player player, String msg) {
        plugin.getServer().getScheduler().runTask(plugin, 
            () -> player.sendMessage(msg));
    }
    
    /**
     * 调度消息处理
     */
    private void scheduleProcessing(Player player, String rawMessage) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                String content = messageProcessor.sanitizeMessage(rawMessage);
                if (messageProcessor.tryAcquire(player)) {
                    conversationManager.processMessage(player, content, "chat");
                } else {
                    player.sendMessage("§c请等待冷却时间结束");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "处理聊天消息时发生错误", e);
                player.sendMessage("§c处理消息时发生错误，请稍后重试");
            }
        });
    }
}
