/**
 * PlayerProfileManager - 玩家档案管理器
 * 
 * 这个类负责管理玩家的档案信息，包括：
 * 1. 玩家基本信息
 * 2. 游戏统计数据
 * 3. 成就记录
 * 4. 行为分析
 * 
 * 主要功能：
 * - 档案管理
 * - 数据分析
 * - 行为追踪
 */

package com.example.aichatplugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * 玩家档案管理器
 * 
 * 职责：
 * 1. 管理玩家档案
 * 2. 记录玩家统计数据
 * 3. 提供玩家信息
 * 4. 数据持久化
 */
public class PlayerProfileManager implements Listener {
    private final AIChatPlugin plugin;
    private final Map<UUID, PlayerProfile> profiles;
    private final ScheduledExecutorService scheduler;
    private final Map<UUID, Long> lastMoveTime;
    private final File dataFile;
    private static final long MOVE_UPDATE_INTERVAL = 1000; // 1秒更新一次移动数据
    private static final long SAVE_INTERVAL = 300; // 5分钟保存一次数据
    
    public PlayerProfileManager(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.profiles = new ConcurrentHashMap<>();
        this.lastMoveTime = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.dataFile = new File(plugin.getDataFolder(), "player_profiles.yml");
        
        // 加载数据
        loadProfiles();
        
        // 启动定期保存任务
        scheduler.scheduleAtFixedRate(this::saveProfiles, SAVE_INTERVAL, SAVE_INTERVAL, TimeUnit.SECONDS);
    }
    
    /**
     * 加载玩家档案
     */
    private void loadProfiles() {
        if (!plugin.getConfigLoader().isPlayerProfilePersistenceEnabled()) {
            return;
        }
        
        if (!dataFile.exists()) {
            plugin.getLogger().info("玩家档案文件不存在，将创建新文件");
            return;
        }
        
        plugin.getLogger().info("正在加载玩家档案...");
        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        int loadedCount = 0;
        
        for (String key : config.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                PlayerProfile profile = new PlayerProfile();
                
                String section = key + ".";
                profile.name = config.getString(section + "name", "");
                profile.playTime = config.getInt(section + "playTime", 0);
                profile.kills = config.getInt(section + "kills", 0);
                profile.deaths = config.getInt(section + "deaths", 0);
                profile.blocksMined = config.getInt(section + "blocksMined", 0);
                profile.blocksPlaced = config.getInt(section + "blocksPlaced", 0);
                profile.distanceWalked = config.getDouble(section + "distanceWalked", 0.0);
                
                profiles.put(playerId, profile);
                loadedCount++;
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("加载玩家档案时发生错误: " + key);
            }
        }
        
        plugin.getLogger().info("已加载 " + loadedCount + " 个玩家的档案");
    }
    
    /**
     * 保存玩家档案
     */
    private void saveProfiles() {
        if (!plugin.getConfigLoader().isPlayerProfilePersistenceEnabled()) {
            return;
        }
        
        plugin.getLogger().info("正在保存玩家档案...");
        FileConfiguration config = new YamlConfiguration();
        int savedCount = 0;
        
        try {
            // 确保目录存在
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }
            
            // 创建临时文件
            File tempFile = new File(dataFile.getParentFile(), "player_profiles_temp.yml");
            
            // 写入新数据
            for (Map.Entry<UUID, PlayerProfile> entry : profiles.entrySet()) {
                String key = entry.getKey().toString();
                PlayerProfile profile = entry.getValue();
                
                String section = key + ".";
                config.set(section + "name", profile.name);
                config.set(section + "playTime", profile.playTime);
                config.set(section + "kills", profile.kills);
                config.set(section + "deaths", profile.deaths);
                config.set(section + "blocksMined", profile.blocksMined);
                config.set(section + "blocksPlaced", profile.blocksPlaced);
                config.set(section + "distanceWalked", profile.distanceWalked);
                
                savedCount++;
                plugin.getLogger().info("保存玩家 " + profile.name + " 的档案，死亡次数: " + profile.deaths);
            }
            
            // 先保存到临时文件
            config.save(tempFile);
            
            // 如果原文件存在，先备份
            if (dataFile.exists()) {
                File backupFile = new File(dataFile.getParentFile(), "player_profiles_backup.yml");
                if (backupFile.exists()) {
                    backupFile.delete();
                }
                dataFile.renameTo(backupFile);
            }
            
            // 将临时文件重命名为正式文件
            tempFile.renameTo(dataFile);
            
            // 删除备份文件
            File backupFile = new File(dataFile.getParentFile(), "player_profiles_backup.yml");
            if (backupFile.exists()) {
                backupFile.delete();
            }
            
            plugin.getLogger().info("已保存 " + savedCount + " 个玩家的档案");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "保存玩家档案时发生错误", e);
        }
    }
    
    /**
     * 获取玩家档案
     */
    public PlayerProfile getProfile(UUID playerId) {
        return profiles.computeIfAbsent(playerId, k -> new PlayerProfile());
    }
    
    /**
     * 更新玩家档案
     */
    public void updateProfile(Player player) {
        getProfile(player.getUniqueId()).update(player);
    }

    /**
     * 获取玩家档案报告
     */
    public String getProfileReport(Player player) {
        PlayerProfile profile = getProfile(player.getUniqueId());
        return String.format("""
            玩家档案报告
            ==========
            玩家名称: %s
            游戏时长: %d 分钟
            击杀数: %d
            死亡数: %d
            挖掘方块数: %d
            放置方块数: %d
            行走距离: %.1f 米
            数据存储: %s
            ==========
            """,
            profile.getName(),
            profile.getPlayTime(),
            profile.getKills(),
            profile.getDeaths(),
            profile.getBlocksMined(),
            profile.getBlocksPlaced(),
            profile.getDistanceWalked(),
            dataFile.getAbsolutePath()
        );
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        getProfile(event.getEntity().getUniqueId()).incrementDeaths();
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        getProfile(event.getPlayer().getUniqueId()).incrementBlocksMined();
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        getProfile(event.getPlayer().getUniqueId()).incrementBlocksPlaced();
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        if (lastMoveTime.getOrDefault(playerId, 0L) + MOVE_UPDATE_INTERVAL <= currentTime) {
            double distance = event.getFrom().distance(event.getTo());
            getProfile(playerId).addDistanceWalked(distance);
            lastMoveTime.put(playerId, currentTime);
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = getProfile(player.getUniqueId());
        profile.update(player);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        saveProfiles(); // 玩家退出时保存数据
    }
    
    /**
     * 获取玩家物品栏信息
     */
    public String getInventoryString(Player player) {
        StringBuilder inventory = new StringBuilder();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) {
                inventory.append(item.getType().name())
                    .append(" x")
                    .append(item.getAmount())
                    .append(", ");
            }
        }
        return inventory.length() > 0 ? 
            inventory.substring(0, inventory.length() - 2) : "空";
    }
    
    /**
     * 玩家档案类
     */
    public static class PlayerProfile {
        private String name;
        private int playTime;
        private int kills;
        private int deaths;
        private int blocksMined;
        private int blocksPlaced;
        private double distanceWalked;
        
        public void update(Player player) {
            this.name = player.getName();
        }
        
        public String getName() { return name; }
        public int getPlayTime() { return playTime; }
        public int getKills() { return kills; }
        public int getDeaths() { return deaths; }
        public int getBlocksMined() { return blocksMined; }
        public int getBlocksPlaced() { return blocksPlaced; }
        public double getDistanceWalked() { return distanceWalked; }
        
        public void incrementKills() { this.kills++; }
        public void incrementDeaths() { this.deaths++; }
        public void incrementBlocksMined() { this.blocksMined++; }
        public void incrementBlocksPlaced() { this.blocksPlaced++; }
        public void addDistanceWalked(double distance) { this.distanceWalked += distance; }
    }
    
    /**
     * 关闭管理器
     */
    public void shutdown() {
        saveProfiles(); // 关闭前保存数据
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 更新玩家死亡记录
     */
    public void updatePlayerDeath(UUID playerId) {
        PlayerProfile profile = getProfile(playerId);
        if (profile != null) {
            profile.deaths++;
            // 立即保存更新
            saveProfiles();
            plugin.getLogger().info("已更新玩家 " + profile.name + " 的死亡记录，当前死亡次数: " + profile.deaths);
        }
    }
}
