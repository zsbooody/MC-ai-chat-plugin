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
 * 
 * 过滤规则：
 * 1. 空消息
 * 2. 命令消息
 * 3. 冷却状态
 */
public class PlayerChatListener implements Listener {
    private final AIChatPlugin plugin;
    private final ConversationManager conversationManager;
    
    public PlayerChatListener(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.conversationManager = plugin.getConversationManager();
    }
    
    /**
     * 处理玩家聊天事件
     * 
     * 流程：
     * 1. 获取玩家和消息
     * 2. 检查消息有效性
     * 3. 检查冷却状态
     * 4. 处理聊天消息
     * 5. 异常处理
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        if (isValidMessage(message) && !conversationManager.isOnCooldown(player.getUniqueId())) {
            try {
                // 检查消息前缀
                String prefix = plugin.getConfigLoader().getChatPrefix();
                if (message.startsWith(prefix)) {
                    // 移除前缀并处理消息
                    String content = message.substring(prefix.length()).trim();
                    if (!content.isEmpty()) {
                        conversationManager.processMessage(player, content, "chat");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "处理聊天消息时发生错误", e);
            }
        }
    }
    
    private boolean isValidMessage(String message) {
        return message != null && !message.trim().isEmpty() && !message.startsWith("/");
    }
}
