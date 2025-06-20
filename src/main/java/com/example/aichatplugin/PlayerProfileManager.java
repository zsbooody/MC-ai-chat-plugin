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
import org.bukkit.event.EventPriority;
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
import org.bukkit.Location;
import org.bukkit.Material;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.bukkit.scheduler.BukkitTask;

/**
 * 玩家档案管理器
 * 
 * 职责：
 * 1. 管理玩家游戏数据
 * 2. 处理数据持久化
 * 3. 提供数据访问接口
 * 
 * 主要功能：
 * 1. 玩家数据追踪
 * 2. 数据持久化
 * 3. 定时自动保存
 * 4. 玩家加入/退出处理
 */
public class PlayerProfileManager implements Listener {
    private final AIChatPlugin plugin;
    private final File dataFile;
    private final Map<UUID, PlayerProfile> profiles;
    private final ScheduledExecutorService scheduler;
    private final Map<UUID, Long> loginTimeMap;
    private final Map<UUID, Location> lastMoveLocation;
    private final Map<UUID, Long> lastMoveTimestamp;
    private final Set<UUID> dirtyProfiles;
    private final Map<UUID, AtomicLong> pendingDistance;
    private BukkitTask saveTask;
    
    private static final long SAVE_INTERVAL = 300000; // 5分钟
    private static final long MOVE_UPDATE_INTERVAL = 1000; // 1秒
    private static final int BATCH_SIZE = 50;
    private static final double MOVE_THRESHOLD = 0.01; // 移动距离阈值
    
    public PlayerProfileManager(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "player_profiles.yml");
        this.profiles = new ConcurrentHashMap<>();
        this.loginTimeMap = new ConcurrentHashMap<>();
        this.lastMoveLocation = new ConcurrentHashMap<>();
        this.lastMoveTimestamp = new ConcurrentHashMap<>();
        this.dirtyProfiles = Collections.synchronizedSet(new HashSet<>());
        this.pendingDistance = new ConcurrentHashMap<>();
        
        // 初始化调度器
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PlayerProfile-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // 启动定时保存任务
        startSaveTask();
        
        // 加载现有数据
        loadProfiles();
    }
    
    /**
     * 启动定期保存任务
     */
    private void startSaveTask() {
        saveTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                saveDirtyProfiles();
            } catch (Exception e) {
                plugin.getLogger().warning("保存玩家档案失败: " + e.getMessage());
            }
        }, SAVE_INTERVAL / 50, SAVE_INTERVAL / 50);
    }
    
    /**
     * 玩家档案数据类
     */
    public static class PlayerProfile {
        private String lastName;
        private long lastUpdated;
        private int dataVersion;
        
        private final AtomicInteger kills;
        private final AtomicInteger deaths;
        private final AtomicInteger blocksBroken;
        private final AtomicInteger blocksPlaced;
        private final AtomicLong playTimeSeconds; // 改为秒级精度
        private final AtomicLong distanceTraveled;
        
        private final Map<Material, AtomicInteger> blocksBrokenByType;
        private final Map<Material, AtomicInteger> blocksPlacedByType;
        
        public PlayerProfile() {
            this.lastName = "";
            this.lastUpdated = System.currentTimeMillis();
            this.dataVersion = 1;
            
            this.kills = new AtomicInteger(0);
            this.deaths = new AtomicInteger(0);
            this.blocksBroken = new AtomicInteger(0);
            this.blocksPlaced = new AtomicInteger(0);
            this.playTimeSeconds = new AtomicLong(0);
            this.distanceTraveled = new AtomicLong(0);
            
            this.blocksBrokenByType = new ConcurrentHashMap<>();
            this.blocksPlacedByType = new ConcurrentHashMap<>();
        }
        
        public void update(Player player) {
            if (player != null) {
                this.lastName = player.getName();
            }
            this.lastUpdated = System.currentTimeMillis();
        }
        
        public void setLastName(String name) {
            this.lastName = name;
            this.lastUpdated = System.currentTimeMillis();
        }
        
        // Getters
        public String getLastName() { return lastName; }
        public long getLastUpdated() { return lastUpdated; }
        public int getDataVersion() { return dataVersion; }
        public int getKills() { return kills.get(); }
        public int getDeaths() { return deaths.get(); }
        public int getBlocksBroken() { return blocksBroken.get(); }
        public int getBlocksPlaced() { return blocksPlaced.get(); }
        public long getPlayTimeSeconds() { return playTimeSeconds.get(); }
        public long getPlayTimeMinutes() { return playTimeSeconds.get() / 60; }
        public long getDistanceTraveled() { return distanceTraveled.get(); }
        
        // Increment methods
        public void incrementKills() { kills.incrementAndGet(); }
        public void incrementDeaths() { deaths.incrementAndGet(); }
        public void incrementBlocksBroken() { blocksBroken.incrementAndGet(); }
        public void incrementBlocksPlaced() { blocksPlaced.incrementAndGet(); }
        public void addPlayTimeSeconds(long seconds) { playTimeSeconds.addAndGet(seconds); }
        public void addDistanceTraveled(long blocks) { distanceTraveled.addAndGet(blocks); }
        
        // Block type tracking
        public void incrementBlocksBrokenByType(Material type) {
            blocksBrokenByType.computeIfAbsent(type, k -> new AtomicInteger(0))
                .incrementAndGet();
        }
        
        public void incrementBlocksPlacedByType(Material type) {
            blocksPlacedByType.computeIfAbsent(type, k -> new AtomicInteger(0))
                .incrementAndGet();
        }
        
        public Map<Material, Integer> getBlocksBrokenByType() {
            return blocksBrokenByType.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().get()
                ));
        }
        
        public Map<Material, Integer> getBlocksPlacedByType() {
            return blocksPlacedByType.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().get()
                ));
        }
        
        /**
         * 创建数据快照
         */
        public PlayerProfileSnapshot createSnapshot() {
            return new PlayerProfileSnapshot(
                lastName,
                lastUpdated,
                kills.get(),
                deaths.get(),
                blocksBroken.get(),
                blocksPlaced.get(),
                playTimeSeconds.get(),
                distanceTraveled.get(),
                getBlocksBrokenByType().entrySet().stream()
                    .collect(Collectors.toMap(
                        e -> e.getKey().name(),
                        Map.Entry::getValue
                    )),
                getBlocksPlacedByType().entrySet().stream()
                    .collect(Collectors.toMap(
                        e -> e.getKey().name(),
                        Map.Entry::getValue
                    ))
            );
        }
    }
    
    /**
     * 玩家档案数据快照
     */
    public static class PlayerProfileSnapshot {
        private final String lastName;
        private final long lastUpdated;
        private final int kills;
        private final int deaths;
        private final int blocksBroken;
        private final int blocksPlaced;
        private final long playTimeSeconds;
        private final long distanceTraveled;
        private final Map<String, Integer> blocksBrokenByType;
        private final Map<String, Integer> blocksPlacedByType;
        
        public PlayerProfileSnapshot(
            String lastName,
            long lastUpdated,
            int kills,
            int deaths,
            int blocksBroken,
            int blocksPlaced,
            long playTimeSeconds,
            long distanceTraveled,
            Map<String, Integer> blocksBrokenByType,
            Map<String, Integer> blocksPlacedByType
        ) {
            this.lastName = lastName;
            this.lastUpdated = lastUpdated;
            this.kills = kills;
            this.deaths = deaths;
            this.blocksBroken = blocksBroken;
            this.blocksPlaced = blocksPlaced;
            this.playTimeSeconds = playTimeSeconds;
            this.distanceTraveled = distanceTraveled;
            this.blocksBrokenByType = Collections.unmodifiableMap(blocksBrokenByType);
            this.blocksPlacedByType = Collections.unmodifiableMap(blocksPlacedByType);
        }
        
        // Getters
        public String getLastName() { return lastName; }
        public long getLastUpdated() { return lastUpdated; }
        public int getKills() { return kills; }
        public int getDeaths() { return deaths; }
        public int getBlocksBroken() { return blocksBroken; }
        public int getBlocksPlaced() { return blocksPlaced; }
        public long getPlayTimeSeconds() { return playTimeSeconds; }
        public long getPlayTimeMinutes() { return playTimeSeconds / 60; }
        public long getDistanceTraveled() { return distanceTraveled; }
        public Map<String, Integer> getBlocksBrokenByType() { return blocksBrokenByType; }
        public Map<String, Integer> getBlocksPlacedByType() { return blocksPlacedByType; }
        
        @Override
        public String toString() {
            return String.format("""
                玩家档案快照
                ==========
                玩家名称: %s
                最后更新: %s
                游戏时长: %d 分钟 (%d 秒)
                击杀数: %d
                死亡数: %d
                挖掘方块数: %d
                放置方块数: %d
                行走距离: %d 米
                ==========
                """,
                lastName,
                new java.util.Date(lastUpdated),
                getPlayTimeMinutes(),
                playTimeSeconds,
                kills,
                deaths,
                blocksBroken,
                blocksPlaced,
                distanceTraveled
            );
        }
    }
    
    /**
     * 获取玩家档案
     */
    public PlayerProfile getProfile(UUID playerId) {
        return profiles.computeIfAbsent(playerId, this::loadProfile);
    }
    
    /**
     * 加载玩家档案
     */
    private PlayerProfile loadProfile(UUID playerId) {
        // 从文件加载或创建新档案
        PlayerProfile profile = new PlayerProfile();
        // TODO: 实现从文件加载逻辑
        return profile;
    }
    
    /**
     * 更新玩家名称
     */
    public void updateName(UUID playerId, String newName) {
        PlayerProfile profile = getProfile(playerId);
        if (!newName.equals(profile.getLastName())) {
            profile.setLastName(newName);
            markDirty(playerId);
        }
    }
    
    /**
     * 标记档案为脏数据
     */
    private void markDirty(UUID playerId) {
        dirtyProfiles.add(playerId);
    }
    
    /**
     * 加载所有玩家档案
     */
    private void loadProfiles() {
        if (!dataFile.exists()) return;
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : config.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                PlayerProfile profile = new PlayerProfile();
                
                // 加载基本数据
                profile.lastName = config.getString(key + ".name", "");
                profile.lastUpdated = config.getLong(key + ".lastUpdated", System.currentTimeMillis());
                int oldVersion = config.getInt(key + ".dataVersion", 1);
                profile.dataVersion = 2; // 当前版本
                
                // 迁移旧版本数据
                if (oldVersion < profile.dataVersion) {
                    migrateProfile(profile, oldVersion, config, key);
                } else {
                    // 加载当前版本数据
                    loadCurrentVersionData(profile, config, key);
                }
                
                profiles.put(playerId, profile);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("无效的玩家UUID: " + key);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "加载玩家档案时发生错误: " + key, e);
                // 创建备份
                createBackup(dataFile);
            }
        }
    }
    
    /**
     * 迁移旧版本数据
     */
    private void migrateProfile(PlayerProfile profile, int oldVersion, FileConfiguration config, String key) {
        if (oldVersion < 2) {
            // 从 v1 迁移到 v2
            profile.kills.set(config.getInt(key + ".kills", 0));
            profile.deaths.set(config.getInt(key + ".deaths", 0));
            profile.blocksBroken.set(config.getInt(key + ".blocksMined", 0));
            profile.blocksPlaced.set(config.getInt(key + ".blocksPlaced", 0));
            profile.playTimeSeconds.set(config.getLong(key + ".playTime", 0));
            profile.distanceTraveled.set((long) config.getDouble(key + ".distanceWalked", 0.0));
        }
    }
    
    /**
     * 加载当前版本数据
     */
    private void loadCurrentVersionData(PlayerProfile profile, FileConfiguration config, String key) {
        profile.kills.set(config.getInt(key + ".kills", 0));
        profile.deaths.set(config.getInt(key + ".deaths", 0));
        profile.blocksBroken.set(config.getInt(key + ".blocksBroken", 0));
        profile.blocksPlaced.set(config.getInt(key + ".blocksPlaced", 0));
        profile.playTimeSeconds.set(config.getLong(key + ".playTime", 0));
        profile.distanceTraveled.set(config.getLong(key + ".distanceTraveled", 0));
        
        // 加载方块统计
        loadBlockStats(config, key + ".blocksBrokenByType", profile.blocksBrokenByType);
        loadBlockStats(config, key + ".blocksPlacedByType", profile.blocksPlacedByType);
    }
    
    /**
     * 创建数据文件备份
     */
    private void createBackup(File file) {
        try {
            File backupFile = new File(file.getParentFile(), 
                file.getName() + ".backup." + System.currentTimeMillis());
            java.nio.file.Files.copy(file.toPath(), backupFile.toPath());
            plugin.getLogger().info("已创建数据文件备份: " + backupFile.getName());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "创建备份文件失败", e);
        }
    }
    
    /**
     * 加载方块统计数据
     */
    private void loadBlockStats(FileConfiguration config, String path, Map<Material, AtomicInteger> stats) {
        if (!config.contains(path)) return;
        
        for (String materialName : config.getConfigurationSection(path).getKeys(false)) {
            try {
                Material material = Material.valueOf(materialName);
                int count = config.getInt(path + "." + materialName, 0);
                stats.put(material, new AtomicInteger(count));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("无效的方块类型: " + materialName);
            }
        }
    }
    
    /**
     * 保存脏数据
     */
    private void saveDirtyProfiles() {
        if (dirtyProfiles.isEmpty()) {
            return;
        }
        
        // 批量处理
        List<UUID> batch = new ArrayList<>(BATCH_SIZE);
        for (UUID playerId : dirtyProfiles) {
            batch.add(playerId);
            
            if (batch.size() >= BATCH_SIZE) {
                saveBatch(batch);
                batch.clear();
            }
        }
        
        // 处理剩余的
        if (!batch.isEmpty()) {
            saveBatch(batch);
        }
        
        dirtyProfiles.clear();
    }
    
    /**
     * 保存一批档案
     */
    private void saveBatch(List<UUID> playerIds) {
        File tempFile = new File(dataFile.getParentFile(), "player_profiles_temp.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        
        try {
            // 保存每个玩家的数据
            for (UUID playerId : playerIds) {
                PlayerProfile profile = profiles.get(playerId);
                if (profile == null) continue;
                
                String path = playerId.toString();
                config.set(path + ".name", profile.getLastName());
                config.set(path + ".lastUpdated", profile.getLastUpdated());
                config.set(path + ".dataVersion", profile.getDataVersion());
                
                // 保存统计数据
                config.set(path + ".kills", profile.getKills());
                config.set(path + ".deaths", profile.getDeaths());
                config.set(path + ".blocksBroken", profile.getBlocksBroken());
                config.set(path + ".blocksPlaced", profile.getBlocksPlaced());
                config.set(path + ".playTimeSeconds", profile.getPlayTimeSeconds());
                config.set(path + ".distanceTraveled", profile.getDistanceTraveled());
                
                // 保存方块统计
                saveBlockStats(config, path + ".blocksBrokenByType", profile.getBlocksBrokenByType());
                saveBlockStats(config, path + ".blocksPlacedByType", profile.getBlocksPlacedByType());
            }
            
            // 保存到临时文件
            config.save(tempFile);
            
            // 替换原文件
            if (dataFile.exists() && !dataFile.delete()) {
                throw new IOException("无法删除原文件");
            }
            if (!tempFile.renameTo(dataFile)) {
                throw new IOException("无法重命名临时文件");
            }
            
        } catch (Exception e) {
            // 恢复脏标记防止数据丢失
            dirtyProfiles.addAll(playerIds);
            plugin.getLogger().log(Level.SEVERE, "批量保存失败", e);
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    
    /**
     * 保存方块统计数据
     */
    private void saveBlockStats(FileConfiguration config, String path, Map<Material, Integer> stats) {
        for (Map.Entry<Material, Integer> entry : stats.entrySet()) {
            config.set(path + "." + entry.getKey().name(), entry.getValue());
        }
    }
    
    /**
     * 关闭管理器
     */
    public void shutdown() {
        // 取消保存任务
        if (saveTask != null) {
            saveTask.cancel();
        }
        
        // 关闭调度器
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 保存所有脏数据
        try {
            saveDirtyProfiles();
        } catch (Exception e) {
            plugin.getLogger().warning("关闭时保存玩家档案失败: " + e.getMessage());
        }
    }
    
    // 事件处理器
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // 记录登录时间
        loginTimeMap.put(playerId, System.currentTimeMillis());
        
        // 更新玩家档案
        PlayerProfile profile = getProfile(playerId);
        profile.update(player);
        markDirty(playerId);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // 更新游戏时间（秒级精度）
        Long loginTime = loginTimeMap.remove(playerId);
        if (loginTime != null) {
            long seconds = (System.currentTimeMillis() - loginTime) / 1000;
            getProfile(playerId).addPlayTimeSeconds(seconds);
        }
        
        // 清理所有缓存
        lastMoveLocation.remove(playerId);
        lastMoveTimestamp.remove(playerId);
        
        // 保存档案
        markDirty(playerId);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();
        
        // 更新击杀和死亡统计
        if (killer != null) {
            getProfile(killer.getUniqueId()).incrementKills();
            markDirty(killer.getUniqueId());
        }
        
        getProfile(player.getUniqueId()).incrementDeaths();
        markDirty(player.getUniqueId());
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material type = event.getBlock().getType();
        
        PlayerProfile profile = getProfile(player.getUniqueId());
        profile.incrementBlocksBroken();
        profile.incrementBlocksBrokenByType(type);
        markDirty(player.getUniqueId());
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material type = event.getBlock().getType();
        
        PlayerProfile profile = getProfile(player.getUniqueId());
        profile.incrementBlocksPlaced();
        profile.incrementBlocksPlacedByType(type);
        markDirty(player.getUniqueId());
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 检查移动距离是否超过阈值
        if (event.getFrom().distanceSquared(event.getTo()) < MOVE_THRESHOLD) {
            return;
        }
        
        UUID playerId = event.getPlayer().getUniqueId();
        double distance = event.getFrom().distance(event.getTo());
        
        // 累加移动距离
        pendingDistance.computeIfAbsent(playerId, k -> new AtomicLong(0))
            .addAndGet((long)(distance * 100)); // 转换为厘米
    }
    
    /**
     * 更新移动距离
     */
    private void updateDistances() {
        pendingDistance.forEach((playerId, distance) -> {
            if (distance.get() > 0) {
                PlayerProfile profile = getProfile(playerId);
                profile.addDistanceTraveled(distance.get());
                markDirty(playerId);
                distance.set(0);
            }
        });
    }
    
    /**
     * 创建玩家档案快照
     */
    public PlayerProfileSnapshot createSnapshot(UUID playerId) {
        return getProfile(playerId).createSnapshot();
    }
}
