/**
 * EnvironmentCollector - 环境信息收集器
 * 
 * 职责：
 * 1. 收集玩家周围的环境信息
 * 2. 分析环境状态
 * 3. 提供环境描述
 * 4. 监测周围实体和方块
 */

package com.example.aichatplugin;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.Collection;
import org.bukkit.configuration.file.FileConfiguration;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class EnvironmentCollector {
    private final AIChatPlugin plugin;
    private final ConfigLoader config;
    private final Map<PlayerLocationKey, CachedEnvironment> cache = new WeakHashMap<>();
    private final Map<EntityType, String> entityNameMap = initEntityNameMap();
    private final Map<Material, String> blockNameMap = initBlockNameMap();
    private static final int DIRECTION_SEGMENTS = 8; // 每45度一个区间
    private static final Pattern BIOME_PATTERN = Pattern.compile("DESERT|SNOW");
    private static final long MAX_CACHE_AGE = 600000; // 🔧 延长到10分钟缓存，减少重复收集
    private static final double MIN_SCAN_RANGE = 5.0; // 最小扫描范围
    private static final double MAX_SCAN_RANGE = 16.0; // 🔧 降低最大扫描范围，减少性能开销
    private double currentScanRange; // 动态扫描范围
    private final ExecutorService executor;
    private final Map<UUID, CompletableFuture<String>> pendingScans;
    private final int maxEntities;
    
    public EnvironmentCollector(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigLoader();
        this.currentScanRange = config.getEntityRange();
        this.executor = Executors.newFixedThreadPool(2);
        this.pendingScans = new ConcurrentHashMap<>();
        this.maxEntities = plugin.getConfig().getInt("environment.max_entities", 20);
        this.currentScanRange = plugin.getConfig().getDouble("environment.scan_range", 16.0);
        
        // 启动缓存清理任务
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            cache.entrySet().removeIf(entry -> 
                now - entry.getValue().timestamp > MAX_CACHE_AGE);
        }, 6000, 6000); // 每5分钟清理一次

        // 启动性能监控任务
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            updateScanRange();
        }, 200, 200); // 每10秒更新一次
    }
    
    /**
     * 玩家位置组合键
     */
    static class PlayerLocationKey {
        final UUID playerId;
        final Location location;
        final int directionSegment;
        
        PlayerLocationKey(UUID playerId, Location location) {
            this.playerId = playerId;
            this.location = location.clone();
            this.directionSegment = (int)(location.getYaw() / 45.0) % DIRECTION_SEGMENTS;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PlayerLocationKey)) return false;
            PlayerLocationKey that = (PlayerLocationKey) o;
            return playerId.equals(that.playerId) && 
                   location.getBlockX() == that.location.getBlockX() &&
                   location.getBlockY() == that.location.getBlockY() &&
                   location.getBlockZ() == that.location.getBlockZ() &&
                   directionSegment == that.directionSegment;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(playerId, 
                location.getBlockX(), 
                location.getBlockY(), 
                location.getBlockZ(),
                directionSegment);
        }
    }
    
    /**
     * 缓存的环境信息
     */
    private static class CachedEnvironment {
        final String data;
        final long timestamp;

        CachedEnvironment(String data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * 更新扫描范围
     * 根据服务器性能动态调整
     */
    private void updateScanRange() {
        // 根据服务器负载动态调整范围
        double newRange = config.getEntityRange();
        int onlinePlayers = plugin.getServer().getOnlinePlayers().size();
        
        // 根据在线玩家数调整范围
        if (onlinePlayers > 50) {
            newRange *= 0.8; // 高负载时减少扫描范围
        } else if (onlinePlayers < 10) {
            newRange *= 1.2; // 低负载时增加扫描范围
        }
        
        // 限制范围在合理区间
        newRange = Math.min(newRange, MAX_SCAN_RANGE);
        newRange = Math.max(newRange, MIN_SCAN_RANGE);
        
        if (newRange != currentScanRange) {
            currentScanRange = newRange;
            plugin.debug("环境扫描范围已更新: " + currentScanRange);
        }
    }
    
    /**
     * 获取实体分类标签
     */
    private List<String> getEntityTags(Entity e) {
        List<String> tags = new ArrayList<>();
        
        // 基础分类
        if (e instanceof Monster) {
            tags.add("敌对");
        } else if (e instanceof Animals) {
            tags.add("友好");
        } else if (e instanceof NPC) {
            tags.add("NPC");
        }
        
        // 特殊状态
        if (e instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) e;
            if (living.isInvisible()) tags.add("隐身");
            if (living.isGlowing()) tags.add("发光");
            if (living.isLeashed()) tags.add("拴绳");
        }
        
        return tags;
    }
    
    /**
     * 获取本地化实体名称（带分类标签）
     */
    private String getLocalizedEntityName(Entity e) {
        String baseName = entityNameMap.getOrDefault(e.getType(), e.getType().name());
        List<String> tags = getEntityTags(e);
        return tags.isEmpty() ? baseName : baseName + "(" + String.join(",", tags) + ")";
    }
    
    /**
     * 收集环境信息
     * 
     * @param player 目标玩家
     * @return 环境信息字符串的CompletableFuture
     */
    public CompletableFuture<String> collectEnvironmentInfo(Player player) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Location loc = player.getLocation();
        PlayerLocationKey key = new PlayerLocationKey(player.getUniqueId(), loc);

        // 使用缓存数据
        if (cache.containsKey(key)) {
            CachedEnvironment cached = cache.get(key);
            if (System.currentTimeMillis() - cached.timestamp < config.getCacheTTL()) {
                plugin.debug("使用缓存的环境信息: " + player.getName());
                future.complete(cached.data);
                return future;
            }
        }
        
        long startTime = System.currentTimeMillis();
        
        // 🔧 关键修复：完全异步化，避免主线程阻塞
        CompletableFuture<String> asyncTask = CompletableFuture.supplyAsync(() -> {
            try {
                return collectEnvironmentDataAsync(player, loc, startTime);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "收集环境信息时发生错误", e);
                return "环境信息收集失败";
            }
        }, executor);
        
        asyncTask.thenAccept(result -> {
            // 异步更新缓存
            cache.put(key, new CachedEnvironment(result));
            future.complete(result);
        });
        
        return future;
    }
    
    /**
     * 🔧 异步收集环境数据（不阻塞主线程）
     */
    private String collectEnvironmentDataAsync(Player player, Location loc, long startTime) {
        World world = player.getWorld();
        StringBuilder info = new StringBuilder();
        
        // 🔧 提供有意义的环境上下文，而不是机械状态
        String biome = world.getBiome(loc).name().toLowerCase();
        String heightDesc = getHeightDescription(loc.getY());
        info.append(String.format("你在%s的%s", 
            getBiomeDescription(biome), heightDesc));
        
        // 天气信息（作为背景上下文）
        if (config.isShowWeather()) {
            String weather = getWeatherInfo(world, loc);
            plugin.debug("天气信息: " + weather);
            if (!weather.equals("晴朗")) {
                info.append("，现在").append(weather);
            }
        }
        
        // 只有夜晚时才提及时间
        if (config.isShowTime()) {
            long ticks = world.getTime();
            boolean isNight = ticks >= 13000 && ticks <= 23000;
            if (isNight) {
                info.append("，夜晚");
            }
        }
        
        // 🔧 只提及重要的实体（敌对生物或有趣的生物）
        if (config.isShowEntities()) {
            List<String> entities = scanNearbyEntitiesOptimized(world, loc, player);
            List<String> importantEntities = entities.stream()
                .filter(this::isImportantEntity)
                .limit(3)
                .collect(Collectors.toList());
            
            if (!importantEntities.isEmpty()) {
                info.append("。附近有").append(String.join("、", importantEntities));
            }
        }
        
        // 🔧 优化：跳过方块扫描，这是最耗时的部分
        // 或者仅在必要时获取脚下方块
        if (config.isShowBlocks()) {
            Block footBlock = world.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
            if (footBlock.getType() != Material.AIR) {
                info.append("\n脚下: ").append(getLocalizedBlockName(footBlock));
            }
        }
        
        // 性能监控
        long duration = System.currentTimeMillis() - startTime;
        plugin.debug("环境收集完成: " + player.getName() + " | 耗时: " + duration + "ms");
        plugin.debug("环境信息: " + info.toString());
        
        return info.toString();
    }
    
    /**
     * 🔧 优化的实体扫描（减少主线程阻塞）
     */
    private List<String> scanNearbyEntitiesOptimized(World world, Location loc, Player player) {
        List<String> entityNames = new ArrayList<>();
        
        // 🔧 使用较小的扫描范围，避免大量实体遍历
        double limitedRange = Math.min(currentScanRange, 8.0); // 最大8格范围
        
        try {
            // 使用getNearbyEntities而不是getEntities()，更高效
            Collection<Entity> nearbyEntities = world.getNearbyEntities(loc, limitedRange, limitedRange, limitedRange);
            
            int count = 0;
            for (Entity e : nearbyEntities) {
                if (count >= 5) break; // 最多5个实体
                
                if (isValidEntityQuick(e, player)) {
                    entityNames.add(getLocalizedEntityName(e));
                    count++;
                }
            }
        } catch (Exception e) {
            plugin.debug("实体扫描出错，跳过: " + e.getMessage());
        }
        
        return entityNames;
    }
    
    /**
     * 🔧 快速实体验证（减少计算）
     */
    private boolean isValidEntityQuick(Entity e, Player player) {
        if (e == null || e.equals(player) || !e.isValid()) {
            return false;
        }
        
        // 快速类型检查
        return !(e instanceof ArmorStand || e instanceof Projectile || 
                e instanceof Item || e instanceof ExperienceOrb);
    }
    
    /**
     * 验证实体是否有效
     */
    private boolean isValidEntity(Entity e, Player player) {
        if (e == null || e.equals(player) || !e.isValid() || e.isDead()) {
            return false;
        }
        
        // 快速类型检查
        if (e instanceof ArmorStand || e instanceof Projectile || 
            e instanceof Item || e instanceof ExperienceOrb) {
            return false;
        }
        
        // 维度检查
        if (!e.getWorld().equals(player.getWorld())) {
            return false;
        }
        
        // 完整距离检查（包含Y轴）
        Location loc = player.getLocation();
        double dx = e.getLocation().getX() - loc.getX();
        double dy = e.getLocation().getY() - loc.getY();
        double dz = e.getLocation().getZ() - loc.getZ();
        double rangeSq = currentScanRange * currentScanRange;
        return dx*dx + dy*dy + dz*dz <= rangeSq;
    }
    
    /**
     * 扫描玩家周围的实体
     * 使用流式处理避免全量拷贝
     */
    private List<String> scanNearbyEntities(World world, Location loc, Player player) {
        List<String> entityNames = new ArrayList<>(config.getMaxEntities());
        Collection<Entity> rawEntities = world.getNearbyEntities(loc, currentScanRange, currentScanRange, currentScanRange);
        
        // 🔧 移除强制区块加载，避免I/O阻塞
        // 只在区块已加载时扫描，不强制加载
        
        for (Entity e : rawEntities) {
            if (entityNames.size() >= config.getMaxEntities()) break;
            if (isValidEntity(e, player)) {
                entityNames.add(getLocalizedEntityName(e));
            }
        }
        return entityNames;
    }
    
    /**
     * 扫描玩家周围的方块
     * 增加区块边界检查
     */
    private List<String> scanNearbyBlocks(World world, Location loc) {
        List<String> blocks = new ArrayList<>();
        int[][] offsets = {
            {0,-1,0}, {0,1,0}, {-1,0,0}, {1,0,0}, {0,0,-1}, {0,0,1}
        };
        
        for (int[] offset : offsets) {
            int chunkX = (loc.getBlockX() + offset[0]) >> 4;
            int chunkZ = (loc.getBlockZ() + offset[2]) >> 4;
            // 🔧 只检查已加载的区块，不强制加载
            if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
            
            Block block = world.getBlockAt(
                loc.getBlockX() + offset[0],
                loc.getBlockY() + offset[1],
                loc.getBlockZ() + offset[2]
            );
            if (block.getType() != Material.AIR) {
                blocks.add(getLocalizedBlockName(block));
            }
        }
        return blocks;
    }
    
    /**
     * 获取方向字符串
     */
    private String getDirectionString(float yaw) {
        yaw = (yaw + 360) % 360;
        if (yaw >= 315 || yaw < 45) return "南";
        if (yaw >= 45 && yaw < 135) return "西";
        if (yaw >= 135 && yaw < 225) return "北";
        return "东";
    }
    
    /**
     * 🔧 获取高度描述（模糊化）
     */
    private String getHeightDescription(double y) {
        if (y < 20) return "地下深处";
        if (y < 50) return "地下";
        if (y < 70) return "地面";
        if (y < 100) return "较高处";
        if (y < 150) return "高处";
        return "极高处";
    }
    
    /**
     * 🔧 获取生物群系描述（友好化）
     */
    private String getBiomeDescription(String biome) {
        if (biome.contains("forest")) return "森林";
        if (biome.contains("desert")) return "沙漠";
        if (biome.contains("snow") || biome.contains("ice")) return "雪地";
        if (biome.contains("mountain")) return "山地";
        if (biome.contains("ocean")) return "海洋";
        if (biome.contains("swamp")) return "沼泽";
        if (biome.contains("jungle")) return "丛林";
        if (biome.contains("plain")) return "平原";
        if (biome.contains("cave")) return "洞穴";
        return "普通";
    }
    
    /**
     * 🔧 判断实体是否重要（值得提及）
     */
    private boolean isImportantEntity(String entityName) {
        // 敌对生物 - 重要
        if (entityName.contains("僵尸") || entityName.contains("骷髅") || 
            entityName.contains("苦力怕") || entityName.contains("蜘蛛") ||
            entityName.contains("末影人") || entityName.contains("女巫")) {
            return true;
        }
        
        // 有用的生物 - 重要  
        if (entityName.contains("村民") || entityName.contains("铁傀儡")) {
            return true;
        }
        
        // 普通动物 - 不重要
        return false;
    }
    
    /**
     * 获取天气信息
     * 修复天气判断优先级和逻辑
     */
    private String getWeatherInfo(World world, Location loc) {
        String biomeName = world.getBiome(loc).name();
        Matcher matcher = BIOME_PATTERN.matcher(biomeName);
        boolean isDesert = matcher.find();
        
        // 🔧 修复：使用更准确的天气判断逻辑
        if (world.isThundering()) {
            return isDesert ? "沙暴雷电" : "雷暴雨";
        } else if (world.hasStorm()) {
            return isDesert ? "沙尘暴" : "下雨"; 
        } else {
            // 🔧 修复：不使用isClearWeather()，直接判断为晴朗
            return "晴朗";
        }
    }
    
    /**
     * 获取时间信息
     */
    private String getTimeInfo(World world) {
        long ticks = world.getTime();
        double totalMinutes = (ticks * 24 * 60.0) / 24000;
        return String.format("%02d:%02d", (int)totalMinutes / 60, (int)totalMinutes % 60);
    }
    
    /**
     * 获取简化方块名称
     */
    private String getLocalizedBlockName(Block block) {
        return blockNameMap.getOrDefault(block.getType(), block.getType().name());
    }
    
    /**
     * 初始化实体名称映射
     */
    private Map<EntityType, String> initEntityNameMap() {
        Map<EntityType, String> map = new EnumMap<>(EntityType.class);
        map.put(EntityType.ZOMBIE, "僵尸");
        map.put(EntityType.SKELETON, "骷髅");
        map.put(EntityType.CREEPER, "苦力怕");
        map.put(EntityType.SPIDER, "蜘蛛");
        map.put(EntityType.CHICKEN, "鸡");
        map.put(EntityType.COW, "牛");
        map.put(EntityType.PIG, "猪");
        map.put(EntityType.SHEEP, "羊");
        map.put(EntityType.WOLF, "狼");
        map.put(EntityType.VILLAGER, "村民");
        return map;
    }
    
    /**
     * 初始化方块名称映射
     */
    private Map<Material, String> initBlockNameMap() {
        Map<Material, String> map = new EnumMap<>(Material.class);
        map.put(Material.STONE, "石头");
        map.put(Material.GRASS_BLOCK, "草方块");
        map.put(Material.DIRT, "泥土");
        map.put(Material.OAK_LOG, "橡木");
        map.put(Material.STONE_BRICKS, "石砖");
        map.put(Material.GLASS, "玻璃");
        map.put(Material.WATER, "水");
        map.put(Material.LAVA, "岩浆");
        map.put(Material.SAND, "沙子");
        map.put(Material.GRAVEL, "沙砾");
        map.put(Material.COAL_ORE, "煤矿石");
        map.put(Material.IRON_ORE, "铁矿石");
        map.put(Material.GOLD_ORE, "金矿石");
        map.put(Material.DIAMOND_ORE, "钻石矿石");
        map.put(Material.EMERALD_ORE, "绿宝石矿石");
        map.put(Material.REDSTONE_ORE, "红石矿石");
        map.put(Material.LAPIS_ORE, "青金石矿石");
        return map;
    }
    
    /**
     * 应用新的配置
     * 
     * @param config 新的配置
     */
    public void applyConfig(FileConfiguration config) {
        plugin.debug("应用环境收集器新配置");
        
        // 更新环境收集设置
        boolean showDetailedLocation = config.getBoolean("environment.show-detailed-location", true);
        boolean showWeather = config.getBoolean("environment.show-weather", true);
        boolean showTime = config.getBoolean("environment.show-time", true);
        boolean showEntities = config.getBoolean("environment.show-entities", true);
        boolean showBlocks = config.getBoolean("environment.show-blocks", true);
        
        // 更新配置
        this.config.set("environment.show-detailed-location", showDetailedLocation);
        this.config.set("environment.show-weather", showWeather);
        this.config.set("environment.show-time", showTime);
        this.config.set("environment.show-entities", showEntities);
        this.config.set("environment.show-blocks", showBlocks);
        
        // 清除缓存
        cache.clear();
        
        plugin.debug("环境收集器配置已更新");
    }

    /**
     * 重新加载本地化配置
     */
    public void reloadLocalization(FileConfiguration langConfig) {
        entityNameMap.clear();
        blockNameMap.clear();
        
        // 加载实体名称
        if (langConfig.contains("entities")) {
            langConfig.getConfigurationSection("entities").getKeys(false)
                .forEach(key -> {
                    try {
                        EntityType type = EntityType.valueOf(key);
                        entityNameMap.put(type, langConfig.getString("entities." + key));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("无效的实体类型: " + key);
                    }
                });
        }
        
        // 加载方块名称
        if (langConfig.contains("blocks")) {
            langConfig.getConfigurationSection("blocks").getKeys(false)
                .forEach(key -> {
                    try {
                        Material material = Material.valueOf(key);
                        blockNameMap.put(material, langConfig.getString("blocks." + key));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("无效的方块类型: " + key);
                    }
                });
        }
    }

    /**
     * 记录环境快照
     */
    public void logEnvironmentSnapshot(Player player) {
        String snapshot = collectEnvironmentInfo(player).join();
        // 记录到玩家数据文件
        File playerFile = new File(plugin.getDataFolder(), "players/" + player.getUniqueId() + ".yml");
        if (!playerFile.exists()) {
            playerFile.getParentFile().mkdirs();
        }
        try {
            FileConfiguration data = YamlConfiguration.loadConfiguration(playerFile);
            data.set("environment." + System.currentTimeMillis(), snapshot);
            data.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存环境快照: " + e.getMessage());
        }
    }

    /**
     * 异步收集环境信息
     * @param player 目标玩家
     * @param callback 完成回调
     */
    public void collectEnvironmentAsync(Player player, Consumer<String> callback) {
        UUID playerId = player.getUniqueId();
        
        // 取消之前的扫描
        CompletableFuture<String> previousScan = pendingScans.remove(playerId);
        if (previousScan != null) {
            previousScan.cancel(true);
        }
        
        // 创建新的扫描任务
        CompletableFuture<String> scanFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return collectEnvironment(player);
            } catch (Exception e) {
                plugin.getLogger().warning("环境收集失败: " + e.getMessage());
                return "环境信息收集失败";
            }
        }, executor);
        
        // 存储并设置回调
        pendingScans.put(playerId, scanFuture);
        scanFuture.thenAccept(callback);
    }
    
    /**
     * 收集环境信息
     */
    private String collectEnvironment(Player player) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        
        // 异步加载区块
        CompletableFuture<Void> chunkLoadFuture = loadChunksAsync(world, loc);
        
        // 收集实体信息
        List<String> entityInfo = collectEntities(world, loc, player);
        
        // 等待区块加载完成
        try {
            chunkLoadFuture.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().warning("区块加载超时: " + e.getMessage());
        }
        
        // 组合环境信息
        return buildEnvironmentDescription(player, entityInfo);
    }
    
    /**
     * 异步加载区块
     */
    private CompletableFuture<Void> loadChunksAsync(World world, Location center) {
        int centerX = center.getBlockX() >> 4;
        int centerZ = center.getBlockZ() >> 4;
        
        List<CompletableFuture<Chunk>> chunkFutures = new ArrayList<>();
        
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                int chunkX = centerX + x;
                int chunkZ = centerZ + z;
                
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    // 🔧 跳过未加载的区块，避免同步I/O阻塞
                    plugin.debug("跳过未加载区块: " + chunkX + "," + chunkZ);
                    continue;
                }
            }
        }
        
        return CompletableFuture.allOf(
            chunkFutures.toArray(new CompletableFuture[0])
        );
    }
    
    /**
     * 收集实体信息
     */
    private List<String> collectEntities(World world, Location center, Player player) {
        return world.getEntities().stream()
            .filter(e -> isValidEntity(e, player))
            .filter(e -> isInRange(e.getLocation(), center))
            .limit(maxEntities)
            .map(this::getEntityDescription)
            .collect(Collectors.toList());
    }
    
    /**
     * 检查位置是否在范围内
     */
    private boolean isInRange(Location loc1, Location loc2) {
        return loc1.distanceSquared(loc2) <= currentScanRange * currentScanRange;
    }
    
    /**
     * 🔧 获取实体描述（移除具体坐标）
     */
    private String getEntityDescription(Entity entity) {
        String name = entity.getCustomName();
        if (name == null) {
            name = getLocalizedEntityName(entity);
        }
        
        // 🔧 只返回实体名称，不包含具体坐标位置
        return name;
    }
    
    /**
     * 🔧 构建环境描述（移除具体坐标）
     */
    private String buildEnvironmentDescription(Player player, List<String> entities) {
        StringBuilder desc = new StringBuilder();
        
        // 🔧 添加玩家信息（不包含具体坐标）
        Location loc = player.getLocation();
        String biome = player.getWorld().getBiome(loc).name().toLowerCase();
        desc.append("玩家").append(player.getName())
            .append("在").append(player.getWorld().getName())
            .append("世界的").append(getBiomeDescription(biome))
            .append("区域，").append(getHeightDescription(loc.getY()));
        
        // 添加实体信息（简化格式）
        if (!entities.isEmpty()) {
            desc.append("，周围有：");
            for (int i = 0; i < Math.min(entities.size(), 3); i++) {
                if (i > 0) desc.append("、");
                desc.append(entities.get(i));
            }
            if (entities.size() > 3) desc.append("等");
        }
        
        return desc.toString();
    }
    
    /**
     * 关闭收集器
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
} 