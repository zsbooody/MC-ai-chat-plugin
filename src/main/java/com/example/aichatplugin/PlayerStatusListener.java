/**
 * PlayerStatusListener - 玩家状态监听器
 * 
 * 这个类负责监听玩家的各种状态变化，包括：
 * 1. 玩家加入服务器
 * 2. 玩家离开服务器
 * 3. 玩家受到伤害
 * 4. 玩家状态变化
 * 
 * 主要功能：
 * - 收集玩家状态信息
 * - 触发AI响应
 * - 广播状态变化消息
 */

package com.example.aichatplugin;

import com.example.aichatplugin.status.PluginStatusService;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.block.Block;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.lang.reflect.Method;

/**
 * 玩家状态监听器
 * 
 * 职责：
 * 1. 监听玩家状态变化事件
 * 2. 处理玩家生命值变化
 * 3. 处理玩家等级变化
 * 4. 处理玩家成就解锁
 * 
 * 主要功能：
 * 1. 玩家加入/退出
 * 2. 玩家死亡/重生
 * 3. 玩家升级
 * 4. 玩家受伤
 * 5. 玩家成就
 */
public class PlayerStatusListener implements Listener {
    private final AIChatPlugin plugin;
    private final ConversationManager conversationManager;
    private final PlayerProfileManager profileManager;
    private final ConfigLoader config;
    private final Map<UUID, Long> lastDamageTime = new ConcurrentHashMap<>();
    private final Map<PotionEffectType, String> potionNameMap = initPotionNameMap();
    private final ThreadLocal<StringBuilder> damageInfoBuilder = ThreadLocal.withInitial(
        () -> new StringBuilder(128)
    );
    
    // 伤害状态跟踪
    private final Map<UUID, PlayerDamageState> playerDamageStates = new ConcurrentHashMap<>();
    
    // 性能统计相关
    private static final long STATS_INTERVAL = 60000; // 1分钟
    private static final int MIN_EVENTS_FOR_OUTPUT = 100;
    private final Map<String, Long> eventProcessingTimes = new ConcurrentHashMap<>();
    private final Map<String, Integer> eventCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastOutputTime = new ConcurrentHashMap<>();
    
    // 事件冷却相关
    private static final Map<Class<?>, Long> EVENT_SPECIFIC_COOLDOWN = Map.of(
        EntityDamageEvent.class, 50L,
        PlayerAdvancementDoneEvent.class, 500L,
        PlayerLevelChangeEvent.class, 200L,
        EntityPotionEffectEvent.class, 100L
    );
    private static final long DEFAULT_EVENT_COOLDOWN = 100L; // 默认冷却时间
    private final Map<UUID, Map<Class<?>, Long>> lastEventTime = new ConcurrentHashMap<>();
    
    // 字符串常量
    private static final String UNKNOWN_SOURCE = "未知来源";
    private static final String UNKNOWN_ENTITY = "未知实体";
    private static final String PROJECTILE = "投射物";
    private static final String BLOCK_DAMAGE = "方块伤害";
    private static final String ENVIRONMENT_DAMAGE = "环境伤害";
    
    // 缓存反射方法
    private static final Method DEATH_LOCATION_METHOD;
    private static final Method IS_CRITICAL_METHOD;
    private static final boolean HAS_CRITICAL_API;
    
    static {
        Method deathLoc = null;
        Method isCrit = null;
        try {
            deathLoc = PlayerDeathEvent.class.getMethod("getDeathLocation");
        } catch (NoSuchMethodException ignored) {}
        try {
            isCrit = EntityDamageByEntityEvent.class.getMethod("isCritical");
        } catch (NoSuchMethodException ignored) {}
        DEATH_LOCATION_METHOD = deathLoc;
        IS_CRITICAL_METHOD = isCrit;
        HAS_CRITICAL_API = (IS_CRITICAL_METHOD != null);
    }
    
    private final ExecutorService eventExecutor;
    private static final Set<String> IMPORTANT_ADVANCEMENTS = Set.of(
        "minecraft:story/root", "minecraft:story/mine_stone", "minecraft:story/upgrade_tools",
        "minecraft:story/smelt_iron", "minecraft:story/obtain_armor", "minecraft:story/lava_bucket",
        "minecraft:story/iron_tools", "minecraft:story/deflect_arrow", "minecraft:story/form_obsidian",
        "minecraft:story/mine_diamond", "minecraft:story/enter_the_nether", "minecraft:story/enchant_item",
        "minecraft:story/cure_zombie_villager", "minecraft:story/follow_ender_eye",
        "minecraft:story/enter_the_end", "minecraft:story/elytra", "minecraft:story/shulker_box",
        "minecraft:nether", "minecraft:end", "minecraft:adventure", "minecraft:story"
    );
    
    private final ConfigLoader configLoader;
    private final PluginStatusService statusService;

    public PlayerStatusListener(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.conversationManager = plugin.getConversationManager();
        this.profileManager = plugin.getProfileManager();
        this.config = plugin.getConfigLoader();
        this.configLoader = plugin.getConfigLoader();
        this.statusService = plugin.getStatusService();
        this.eventExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "PlayerStatus-Event");
                t.setDaemon(true);
                return t;
            }
        );
    }
    
    public void shutdown() {
        plugin.debug("开始关闭PlayerStatusListener...");
        
        // 停止接收新任务
        eventExecutor.shutdown();
        
        try {
            // 等待现有任务完成
            if (!eventExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("事件处理器未能在5秒内关闭，强制关闭");
                eventExecutor.shutdownNow();
                
                // 再等待2秒
                if (!eventExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("事件处理器强制关闭失败");
                }
            }
        } catch (InterruptedException e) {
            plugin.getLogger().warning("等待事件处理器关闭时被中断");
            eventExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 清理玩家状态数据
        try {
            playerDamageStates.clear();
            lastEventTime.clear();
            eventProcessingTimes.clear();
            eventCounts.clear();
            lastOutputTime.clear();
            plugin.debug("玩家状态数据清理完成");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "清理玩家状态数据时发生错误", e);
        }
        
        plugin.debug("PlayerStatusListener关闭完成");
    }
    
    private boolean shouldProcessEvent(Player player, Class<?> eventType) {
        if (player == null) return false;
        
        long now = System.currentTimeMillis();
        Map<Class<?>, Long> playerEvents = lastEventTime.computeIfAbsent(
            player.getUniqueId(), 
            k -> new ConcurrentHashMap<>()
        );
        
        Long lastTime = playerEvents.get(eventType);
        
        // 🔧 修改：根据事件类型从配置获取冷却时间
        long cooldown = getEventCooldown(eventType);
        
        if (lastTime != null && now - lastTime < cooldown) {
            return false;
        }
        
        playerEvents.put(eventType, now);
        return true;
    }
    
    /**
     * 根据事件类型获取配置的冷却时间
     */
    private long getEventCooldown(Class<?> eventType) {
        if (eventType == PlayerJoinEvent.class) {
            return configLoader.getJoinCooldown();
        } else if (eventType == PlayerQuitEvent.class) {
            return configLoader.getQuitCooldown();
        } else if (eventType == EntityDamageEvent.class) {
            return configLoader.getDamageCooldown();
        } else {
            // 对于其他事件类型，使用原有的硬编码映射作为后备
            return EVENT_SPECIFIC_COOLDOWN.getOrDefault(eventType, DEFAULT_EVENT_COOLDOWN);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!configLoader.isJoinEnabled()) {
            return;
        }
        
        Player player = event.getPlayer();
        if (!shouldProcessEvent(player, PlayerJoinEvent.class)) {
            return;
        }
        
        statusService.recordEventProcessed("join");
        conversationManager.processMessage(player, "", "join");
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!configLoader.isQuitEnabled()) {
            return;
        }
        
        Player player = event.getPlayer();
        if (!shouldProcessEvent(player, PlayerQuitEvent.class)) {
            return;
        }
        
        statusService.recordEventProcessed("quit");
        conversationManager.processMessage(player, "", "quit");
        
        // 清理玩家伤害状态
        playerDamageStates.remove(player.getUniqueId());
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!configLoader.isRespawnEnabled()) {
            return;
        }
        
        Player player = event.getPlayer();
        if (!shouldProcessEvent(player, PlayerRespawnEvent.class)) {
            return;
        }
        
        statusService.recordEventProcessed("respawn");
        conversationManager.processMessage(player, 
            event.isAnchorSpawn() ? "1" : "0", 
            "respawn");
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!configLoader.isDeathEnabled()) {
            return;
        }
        
        Player player = event.getEntity();
        if (!shouldProcessEvent(player, PlayerDeathEvent.class)) {
            return;
        }
        
        statusService.recordEventProcessed("death");
        
        Location deathLoc = getDeathLocation(event);
        String locationInfo = String.format("(%.1f,%.1f,%.1f)", 
            deathLoc.getX(), deathLoc.getY(), deathLoc.getZ());
        
        conversationManager.processMessage(player, 
            event.getDeathMessage() + locationInfo, 
            "death");
    }
    
    private Location getDeathLocation(PlayerDeathEvent event) {
        if (DEATH_LOCATION_METHOD != null) {
            try {
                return (Location) DEATH_LOCATION_METHOD.invoke(event);
            } catch (Exception ignored) {}
        }
        return event.getEntity().getLocation();
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        if (!configLoader.isLevelUpEnabled()) {
            return;
        }
        
        Player player = event.getPlayer();
        if (!shouldProcessEvent(player, PlayerLevelChangeEvent.class)) {
            return;
        }
        
        statusService.recordEventProcessed("level_up");
        if (event.getNewLevel() <= event.getOldLevel()) return;
        
        AttributeInstance healthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        AttributeInstance attackAttr = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        
        double health = healthAttr != null ? healthAttr.getValue() : 20.0;
        double attack = attackAttr != null ? attackAttr.getValue() : 1.0;
        
        StringBuilder builder = damageInfoBuilder.get();
        builder.setLength(0);
        builder.append(event.getOldLevel())
            .append(',')
            .append(event.getNewLevel())
            .append(',')
            .append(health)
            .append(',')
            .append(attack);
        
        conversationManager.processMessage(player, builder.toString(), "level_up");
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        if (!configLoader.isAdvancementEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (!shouldProcessEvent(player, PlayerAdvancementDoneEvent.class)) {
            return;
        }
        
        statusService.recordEventProcessed("advancement");
        if (!isImportantAdvancement(event.getAdvancement())) return;
        
        Advancement advancement = event.getAdvancement();
        String message = advancement.getKey().getKey();
        
        try {
            AdvancementDisplay display = advancement.getDisplay();
            if (display != null) {
                String title = display.getTitle();
                if (title != null) {
                    message = title;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "获取成就名称失败", e);
        }
        
        conversationManager.processMessage(player, message, "advancement");
    }
    
    private boolean isImportantAdvancement(Advancement advancement) {
        String key = advancement.getKey().getKey();
        String namespace = advancement.getKey().getNamespace();
        
        return IMPORTANT_ADVANCEMENTS.contains(namespace) &&
               !key.startsWith("recipe/") && 
               !key.equals("root");
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        if (!configLoader.isDamageEnabled()) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        if (!shouldProcessEvent(player, EntityDamageEvent.class)) {
            return;
        }
        
        statusService.recordEventProcessed("damage");
        
        // 性能监控检查：根据当前运行模式决定是否处理
        var performanceMonitor = plugin.getPerformanceMonitor();
        if (performanceMonitor != null) {
            var featureManager = performanceMonitor.getFeatureManager();
            
            // 检查伤害事件响应是否启用
            if (!featureManager.shouldProcessDamageEvent(player.getHealth(), event.getFinalDamage())) {
                plugin.debug("性能优化：跳过伤害事件处理 - 当前模式禁用或不满足阈值");
                return;
            }
            
            // 获取当前配置
            var damageConfig = featureManager.getCurrentDamageEventConfig();
            
            // BASIC模式下只处理重要伤害
            if (damageConfig.isOnlyImportantDamage()) {
                if (!featureManager.isImportantDamageEvent(
                    player.getHealth(), player.getMaxHealth(), event.getFinalDamage())) {
                    plugin.debug("性能优化：跳过非重要伤害事件");
                    return;
                }
            }
        }
        
        // 直接在主线程中处理，避免频繁的线程切换
        UUID playerId = player.getUniqueId();
        double finalDamage = event.getFinalDamage();
        
        if (Double.isNaN(finalDamage)) {
            return;
        }
        
        // 获取或创建玩家伤害状态
        PlayerDamageState state = playerDamageStates.computeIfAbsent(
            playerId,
            k -> new PlayerDamageState(event.getCause())
        );
        
        // 检查是否需要处理（应用动态冷却机制）
        EntityDamageEvent.DamageCause cause = event.getCause();
        String damageSource = resolveDamageSource(event);
        
        // 获取动态冷却时间
        long dynamicCooldown = getDynamicCooldown();
        
        if (!state.shouldProcessDamage(cause, damageSource, dynamicCooldown)) {
            return; // 还在冷却中，跳过处理
        }
        
        // 构建伤害信息
        StringBuilder builder = damageInfoBuilder.get();
        builder.setLength(0);
        builder.append(finalDamage)
            .append(',')
            .append(player.getHealth())
            .append(',')
            .append(player.getMaxHealth())
            .append(',')
            .append(cause.name().toLowerCase().replace("_", " "))
            .append(',')
            .append(damageSource);
        
        if (isCriticalHit(event)) {
            builder.append(",1");
        }
        
        String damageInfo = builder.toString();
        
        // 直接调用processMessage，让ConversationManager内部处理异步逻辑
        conversationManager.processMessage(player, damageInfo, "damage");
        
        plugin.debug("处理伤害事件 - 模式: " + 
            (performanceMonitor != null ? performanceMonitor.getCurrentMode() : "未知") +
            ", 冷却: " + dynamicCooldown + "ms");
    }
    
    /**
     * 获取动态冷却时间（根据性能模式调整）
     */
    private long getDynamicCooldown() {
        var performanceMonitor = plugin.getPerformanceMonitor();
        if (performanceMonitor != null) {
            var featureManager = performanceMonitor.getFeatureManager();
            var config = featureManager.getCurrentDamageEventConfig();
            return config.getCooldownMs();
        }
        
        // 默认冷却时间
        return 3000L;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        if (!configLoader.isPotionEffectEnabled()) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        if (!shouldProcessEvent(player, EntityPotionEffectEvent.class)) {
            return;
        }
        
        // 忽略效果更新事件
        if (event.getAction() == EntityPotionEffectEvent.Action.CHANGED) {
            return;
        }
        
        PotionEffect newEffect = event.getNewEffect();
        PotionEffect oldEffect = event.getOldEffect();
        
        if (newEffect != null) {
            if (newEffect.getType() == null) {
                plugin.getLogger().warning("无效药水效果类型");
                return;
            }
            String effectName = getPotionEffectName(newEffect.getType());
            conversationManager.processMessage(player, 
                effectName + "," + newEffect.getAmplifier(), 
                "potion_add");
        } else if (oldEffect != null) {
            if (oldEffect.getType() == null) {
                plugin.getLogger().warning("无效药水效果类型");
                return;
            }
            String effectName = getPotionEffectName(oldEffect.getType());
            conversationManager.processMessage(player, 
                effectName, 
                "potion_remove");
        }
    }
    
    private String resolveDamageSource(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent entityEvent = (EntityDamageByEntityEvent) event;
            Entity damager = entityEvent.getDamager();
            
            if (damager instanceof Projectile) {
                Projectile projectile = (Projectile) damager;
                ProjectileSource shooter = projectile.getShooter();
                
                if (shooter == null) {
                    return UNKNOWN_SOURCE;
                } else if (shooter instanceof Entity) {
                    Entity source = (Entity) shooter;
                    return source.getName() != null ? source.getName() : UNKNOWN_ENTITY;
                } else if (shooter instanceof BlockProjectileSource) {
                    Block sourceBlock = ((BlockProjectileSource) shooter).getBlock();
                    return sourceBlock.getType().name();
                } else {
                    return PROJECTILE;
                }
            }
            
            return switch (damager.getType()) {
                case ENDER_DRAGON -> "末影龙";
                case WITHER -> "凋灵";
                case ELDER_GUARDIAN -> "远古守卫者";
                case WITHER_SKELETON -> "凋灵骷髅";
                case CREEPER -> ((Creeper) damager).isPowered() ? "高压苦力怕" : "苦力怕";
                case SKELETON -> damager instanceof Stray ? "流浪者" : "骷髅";
                case ZOMBIE -> {
                    if (damager instanceof ZombieVillager) yield "僵尸村民";
                    if (damager instanceof Husk) yield "尸壳";
                    if (damager instanceof Drowned) yield "溺尸";
                    yield "僵尸";
                }
                default -> damager.getName() != null ? damager.getName() : UNKNOWN_ENTITY;
            };
        } else if (event instanceof EntityDamageByBlockEvent) {
            Block damager = ((EntityDamageByBlockEvent) event).getDamager();
            return damager != null ? damager.getType().name() : BLOCK_DAMAGE;
        }
        
        return ENVIRONMENT_DAMAGE;
    }
    
    private boolean isCriticalHit(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent)) return false;
        
        if (HAS_CRITICAL_API) {
            try {
                return (boolean) IS_CRITICAL_METHOD.invoke(event);
            } catch (Exception ignored) {
                return ((EntityDamageByEntityEvent) event).getCause() == 
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK;
            }
        }
        return ((EntityDamageByEntityEvent) event).getCause() == 
            EntityDamageEvent.DamageCause.ENTITY_ATTACK;
    }
    
    private void recordEventProcessing(String eventType, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        eventProcessingTimes.merge(eventType, processingTime, Long::sum);
        eventCounts.merge(eventType, 1, Integer::sum);
        
        long now = System.currentTimeMillis();
        Long lastTime = lastOutputTime.get(eventType);
        int count = eventCounts.getOrDefault(eventType, 0);
        
        if (lastTime == null || 
            (now - lastTime > STATS_INTERVAL && count >= MIN_EVENTS_FOR_OUTPUT)) {
            outputStats(eventType);
            lastOutputTime.put(eventType, now);
        }
    }
    
    private void outputStats(String eventType) {
        int count = eventCounts.getOrDefault(eventType, 0);
        if (count == 0) return;
        
        long totalTime = eventProcessingTimes.getOrDefault(eventType, 0L);
        double avgTime = (double) totalTime / count;
        
        plugin.getLogger().info(String.format(
            "Event stats for %s: Count=%d, Avg Time=%.2fms, Total Time=%.2fs",
            eventType, count, avgTime, totalTime / 1000.0
        ));
        
        eventProcessingTimes.put(eventType, 0L);
        eventCounts.put(eventType, 0);
    }
    
    private String getPotionEffectName(PotionEffectType type) {
        String name = potionNameMap.get(type);
        if (name != null) return name;
        
        String key = type.getKey().getKey();
        return capitalizeWords(key.replace("_", " "));
    }
    
    private String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) return text;
        
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : text.toCharArray()) {
            if (Character.isSpaceChar(c)) {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        
        return result.toString();
    }
    
    private Map<PotionEffectType, String> initPotionNameMap() {
        Map<PotionEffectType, String> map = new HashMap<>();
        map.put(PotionEffectType.SPEED, "速度");
        map.put(PotionEffectType.SLOW, "缓慢");
        map.put(PotionEffectType.FAST_DIGGING, "急迫");
        map.put(PotionEffectType.SLOW_DIGGING, "挖掘疲劳");
        map.put(PotionEffectType.INCREASE_DAMAGE, "力量");
        map.put(PotionEffectType.HEAL, "瞬间治疗");
        map.put(PotionEffectType.HARM, "瞬间伤害");
        map.put(PotionEffectType.JUMP, "跳跃提升");
        map.put(PotionEffectType.CONFUSION, "反胃");
        map.put(PotionEffectType.REGENERATION, "生命恢复");
        map.put(PotionEffectType.DAMAGE_RESISTANCE, "抗性提升");
        map.put(PotionEffectType.FIRE_RESISTANCE, "防火");
        map.put(PotionEffectType.WATER_BREATHING, "水下呼吸");
        map.put(PotionEffectType.INVISIBILITY, "隐身");
        map.put(PotionEffectType.BLINDNESS, "失明");
        map.put(PotionEffectType.NIGHT_VISION, "夜视");
        map.put(PotionEffectType.HUNGER, "饥饿");
        map.put(PotionEffectType.WEAKNESS, "虚弱");
        map.put(PotionEffectType.POISON, "中毒");
        map.put(PotionEffectType.WITHER, "凋零");
        map.put(PotionEffectType.HEALTH_BOOST, "生命提升");
        map.put(PotionEffectType.ABSORPTION, "伤害吸收");
        map.put(PotionEffectType.SATURATION, "饱和");
        map.put(PotionEffectType.GLOWING, "发光");
        map.put(PotionEffectType.LEVITATION, "漂浮");
        map.put(PotionEffectType.LUCK, "幸运");
        map.put(PotionEffectType.UNLUCK, "霉运");
        map.put(PotionEffectType.SLOW_FALLING, "缓降");
        map.put(PotionEffectType.CONDUIT_POWER, "潮涌能量");
        map.put(PotionEffectType.DOLPHINS_GRACE, "海豚的恩惠");
        map.put(PotionEffectType.BAD_OMEN, "不祥之兆");
        map.put(PotionEffectType.HERO_OF_THE_VILLAGE, "村庄英雄");
        return map;
    }
    
    // 玩家伤害状态跟踪类
    private static class PlayerDamageState {
        private EntityDamageEvent.DamageCause lastDamageType;
        private String lastDamageSource;
        private long lastDamageTime;
        
        public PlayerDamageState(EntityDamageEvent.DamageCause initialCause) {
            this.lastDamageType = initialCause;
            this.lastDamageSource = "";
            this.lastDamageTime = 0;
        }
        
        public boolean shouldProcessDamage(EntityDamageEvent.DamageCause currentCause, String currentSource, long dynamicCooldown) {
            long currentTime = System.currentTimeMillis();
            
            // 计算基于模式的冷却时间
            long sameSourceCooldown = dynamicCooldown;
            long sameTypeCooldown = Math.max(1000L, dynamicCooldown / 2); // 同类型冷却时间为一半，最少1秒
            
            // 如果是不同类型的伤害，检查类型冷却
            if (currentCause != lastDamageType) {
                if (currentTime - lastDamageTime < sameTypeCooldown) {
                    return false; // 还在冷却中
                }
                // 更新状态
                lastDamageType = currentCause;
                lastDamageSource = currentSource;
                lastDamageTime = currentTime;
                return true;
            }
            
            // 如果是相同类型但不同来源的伤害
            if (!currentSource.equals(lastDamageSource)) {
                if (currentTime - lastDamageTime < sameTypeCooldown) {
                    return false; // 还在冷却中
                }
                // 更新状态
                lastDamageSource = currentSource;
                lastDamageTime = currentTime;
                return true;
            }
            
            // 如果是相同类型相同来源的伤害，检查来源冷却
            if (currentTime - lastDamageTime < sameSourceCooldown) {
                return false; // 还在冷却中
            }
            
            // 更新时间
            lastDamageTime = currentTime;
            return true;
        }
        
        public void reset(EntityDamageEvent.DamageCause newCause) {
            this.lastDamageType = newCause;
            this.lastDamageSource = "";
            this.lastDamageTime = System.currentTimeMillis();
        }
        
        public EntityDamageEvent.DamageCause getLastDamageType() {
            return lastDamageType;
        }
    }
}
