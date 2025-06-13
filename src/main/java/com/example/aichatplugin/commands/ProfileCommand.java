package com.example.aichatplugin.commands;

import com.example.aichatplugin.AIChatPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 档案命令处理器
 * 
 * 命令：/profile
 * 功能：查看玩家档案信息
 */
public class ProfileCommand implements CommandExecutor {
    private final AIChatPlugin plugin;
    
    public ProfileCommand(AIChatPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此命令！");
            return true;
        }
        
        Player player = (Player) sender;
        String report = plugin.getProfileManager().getProfileReport(player);
        player.sendMessage(report);
        
        return true;
    }
} 