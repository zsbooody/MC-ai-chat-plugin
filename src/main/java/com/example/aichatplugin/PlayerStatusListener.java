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

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家状态监听器
 * 
 * 职责：
 * 1. 监听玩家状态变化事件
 * 2. 触发状态变化响应
 * 3. 管理状态响应冷却
 * 
 * 监听事件：
 * 1. 玩家加入/退出
 * 2. 玩家死亡
 * 3. 玩家升级
 * 4. 玩家完成进度
 * 5. 玩家受到伤害
 */
public class PlayerStatusListener implements Listener {
    private final AIChatPlugin plugin;
    private final ConversationManager conversationManager;
    private final PlayerProfileManager profileManager;
    private final ConfigLoader config;
    private final Map<UUID, Long> lastDamageTime = new ConcurrentHashMap<>();
    
    public PlayerStatusListener(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.conversationManager = plugin.getConversationManager();
        this.profileManager = plugin.getProfileManager();
        this.config = plugin.getConfigLoader();
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!config.isRespondToJoin()) return;
        
        Player player = event.getPlayer();
        profileManager.updateProfile(player);
        conversationManager.processMessage(player, "", "join");
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!config.isRespondToQuit()) return;
        conversationManager.processMessage(event.getPlayer(), "", "quit");
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!config.isRespondToDeath()) return;
        
        Player player = event.getEntity();
        profileManager.updateProfile(player);
        conversationManager.processMessage(player, event.getDeathMessage(), "death");
    }
    
    @EventHandler
    public void onPlayerLevelUp(PlayerLevelChangeEvent event) {
        if (!config.isRespondToLevelUp()) return;
        
        Player player = event.getPlayer();
        conversationManager.processMessage(player, 
            String.format("%d,%d", event.getOldLevel(), event.getNewLevel()), 
            "level_up");
    }
    
    @EventHandler
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        if (!config.isRespondToAdvancement()) return;
        
        Player player = event.getPlayer();
        conversationManager.processMessage(player, 
            event.getAdvancement().getKey().getKey(), 
            "advancement");
    }
    
    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        if (isOnDamageCooldown(playerId, currentTime) || 
            !isDamageSignificant(event, player)) {
            return;
        }
        
        lastDamageTime.put(playerId, currentTime);
        conversationManager.processMessage(player, buildDamageInfo(event, player), "damage");
    }
    
    private boolean isOnDamageCooldown(UUID playerId, long currentTime) {
        Long lastTime = lastDamageTime.get(playerId);
        return lastTime != null && 
               currentTime - lastTime < config.getDamageCooldown();
    }
    
    private boolean isDamageSignificant(EntityDamageEvent event, Player player) {
        return event.getFinalDamage() / player.getMaxHealth() >= config.getDamageThreshold();
    }
    
    private String buildDamageInfo(EntityDamageEvent event, Player player) {
        StringBuilder info = new StringBuilder(String.format("%.1f,%.1f,%.1f",
            event.getFinalDamage(),
            player.getHealth(),
            player.getMaxHealth()));
            
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent entityEvent = (EntityDamageByEntityEvent) event;
            String damageSource = entityEvent.getDamager() instanceof Player ?
                ((Player) entityEvent.getDamager()).getName() :
                entityEvent.getDamager().getName();
            info.append(",").append(damageSource);
        }
        
        return info.toString();
    }
}
