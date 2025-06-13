package com.example.aichatplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.logging.Level;

/**
 * AI聊天命令处理器
 * 
 * 职责：
 * 1. 处理玩家输入的AI聊天命令
 * 2. 转发消息到对话管理器
 * 3. 处理命令参数
 */
public class AIChatCommand implements CommandExecutor {
    private final AIChatPlugin plugin;
    private final ConversationManager conversationManager;
    private final ConfigLoader config;

    public AIChatCommand(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.conversationManager = plugin.getConversationManager();
        this.config = plugin.getConfigLoader();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getMessageFormat("error").replace("{error}", "该命令只能由玩家执行"));
            return true;
        }

        Player player = (Player) sender;
        
        if (args.length == 0) {
            player.sendMessage(config.getMessageFormat("help"));
            return true;
        }

        // 构建完整的消息
        StringBuilder message = new StringBuilder();
        for (String arg : args) {
            message.append(arg).append(" ");
        }
        String fullMessage = message.toString().trim();

        // 转发到对话管理器处理
        conversationManager.processMessage(player, fullMessage, "chat");
        return true;
    }
} 