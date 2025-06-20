package com.example.aichatplugin.commands;

import com.example.aichatplugin.AIChatPlugin;
import com.example.aichatplugin.PlayerProfileManager.PlayerProfile;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * 玩家档案命令
 * 用于查看玩家游戏数据
 */
public class ProfileCommand implements CommandExecutor, TabCompleter {
    private final AIChatPlugin plugin;
    
    // 配置键
    private static final String CONFIG_FORMAT = "profile.format";
    private static final String CONFIG_THRESHOLDS = "profile.thresholds";
    private static final String CONFIG_MESSAGES = "profile.messages";
    
    // 默认值
    private static final String DEFAULT_FORMAT = """
        玩家档案报告
        ==========
        玩家名称: %s
        游戏时长: %d 分钟 (%d 秒)
        击杀数: %d
        死亡数: %d
        挖掘方块数: %d
        放置方块数: %d
        行走距离: %d 米
        最后更新: %s
        ==========
        """;
    
    private static final int DEFAULT_DEATH_THRESHOLD = 10;
    private static final int DEFAULT_KILL_THRESHOLD = 100;
    private static final int DEFAULT_PLAYTIME_THRESHOLD = 3600; // 60小时
    
    public ProfileCommand(AIChatPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        try {
            if (!(sender instanceof Player)) {
                sendConfigMessage(sender, "console-denied");
                return true;
            }
            
            Player player = (Player) sender;
            if (!player.hasPermission("aichat.profile")) {
                sendConfigMessage(sender, "no-permission");
                return true;
            }
            
            // 异步获取档案数据
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        PlayerProfile profile = plugin.getProfileManager().getProfile(player.getUniqueId());
                        if (profile == null) {
                            sendConfigMessage(player, "profile-not-found");
                            return;
                        }
                        
                        // 构建并发送报告
                        String report = buildReport(profile);
                        player.sendMessage(report);
                        
                        // 发送性能建议
                        sendPerformanceTips(player, profile);
                        
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "获取玩家档案失败", e);
                        sendConfigMessage(player, "error");
                    }
                }
            }.runTaskAsynchronously(plugin);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "档案命令执行错误", e);
            sendConfigMessage(sender, "error");
        }
        
        return true;
    }
    
    private String buildReport(PlayerProfile profile) {
        String format = plugin.getConfig().getString(CONFIG_FORMAT, DEFAULT_FORMAT);
        return String.format(format,
            profile.getLastName(),
            profile.getPlayTimeMinutes(),
            profile.getPlayTimeSeconds(),
            profile.getKills(),
            profile.getDeaths(),
            profile.getBlocksBroken(),
            profile.getBlocksPlaced(),
            profile.getDistanceTraveled(),
            new java.util.Date(profile.getLastUpdated())
        );
    }
    
    private void sendPerformanceTips(Player player, PlayerProfile profile) {
        // 检查死亡次数
        if (profile.getDeaths() > getDeathThreshold()) {
            sendConfigMessage(player, "death-warning", profile.getDeaths());
        }
        
        // 检查击杀数
        if (profile.getKills() > getKillThreshold()) {
            sendConfigMessage(player, "kill-warning", profile.getKills());
        }
        
        // 检查游戏时长
        if (profile.getPlayTimeMinutes() > getPlaytimeThreshold()) {
            sendConfigMessage(player, "playtime-warning", profile.getPlayTimeMinutes());
        }
    }
    
    private int getDeathThreshold() {
        return plugin.getConfig().getInt(CONFIG_THRESHOLDS + ".deaths", DEFAULT_DEATH_THRESHOLD);
    }
    
    private int getKillThreshold() {
        return plugin.getConfig().getInt(CONFIG_THRESHOLDS + ".kills", DEFAULT_KILL_THRESHOLD);
    }
    
    private int getPlaytimeThreshold() {
        return plugin.getConfig().getInt(CONFIG_THRESHOLDS + ".playtime", DEFAULT_PLAYTIME_THRESHOLD);
    }
    
    private void sendConfigMessage(CommandSender sender, String key) {
        String message = plugin.getConfig().getString(CONFIG_MESSAGES + "." + key);
        if (message != null) {
            sender.sendMessage(message);
        }
    }
    
    private void sendConfigMessage(CommandSender sender, String key, Object... args) {
        String message = plugin.getConfig().getString(CONFIG_MESSAGES + "." + key);
        if (message != null) {
            sender.sendMessage(String.format(message, args));
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        return new ArrayList<>();
    }
} 