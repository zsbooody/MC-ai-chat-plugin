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

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Collection;

public class EnvironmentCollector {
    private final AIChatPlugin plugin;
    private final ConfigLoader config;
    
    public EnvironmentCollector(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigLoader();
    }
    
    /**
     * 收集环境信息
     */
    public CompletableFuture<String> collectEnvironmentInfo(Player player) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Location loc = player.getLocation();
                    World world = player.getWorld();
                    
                    // 收集位置信息
                    StringBuilder info = new StringBuilder();
                    if (config.isShowDetailedLocation()) {
                        info.append(String.format("位置: %.1f, %.1f, %.1f, 朝向: %s",
                            loc.getX(), loc.getY(), loc.getZ(),
                            getDirectionString(loc.getYaw())));
                    }
                    
                    // 收集天气信息
                    if (config.isShowWeather()) {
                        info.append("\n天气: ").append(getWeatherInfo(world));
                    }
                    
                    // 收集时间信息
                    if (config.isShowTime()) {
                        info.append("\n时间: ").append(getTimeInfo(world));
                    }
                    
                    // 收集实体信息
                    Collection<Entity> nearbyEntities = world.getNearbyEntities(
                        loc, 
                        config.getEntityDetectionRange(),
                        config.getEntityDetectionRange(),
                        config.getEntityDetectionRange()
                    );
                    
                    if (!nearbyEntities.isEmpty()) {
                        info.append("\n附近实体: ");
                        info.append(nearbyEntities.stream()
                            .filter(e -> e != player)
                            .map(Entity::getName)
                            .collect(Collectors.joining(", ")));
                    }
                    
                    // 收集方块信息
                    List<String> blockInfo = new ArrayList<>();
                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 1; y++) {
                            for (int z = -1; z <= 1; z++) {
                                Block block = world.getBlockAt(
                                    loc.getBlockX() + x,
                                    loc.getBlockY() + y,
                                    loc.getBlockZ() + z
                                );
                                if (!block.getType().isAir()) {
                                    blockInfo.add(block.getType().name());
                                }
                            }
                        }
                    }
                    
                    if (!blockInfo.isEmpty()) {
                        info.append("\n周围方块: ");
                        info.append(String.join(", ", blockInfo));
                    }
                    
                    future.complete(info.toString());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        }.runTask(plugin);
        
        return future;
    }
    
    private String getDirectionString(float yaw) {
        yaw = (yaw + 360) % 360;
        if (yaw >= 315 || yaw < 45) return "南";
        if (yaw >= 45 && yaw < 135) return "西";
        if (yaw >= 135 && yaw < 225) return "北";
        return "东";
    }
    
    private String getWeatherInfo(World world) {
        if (world.isThundering()) return "雷暴";
        if (world.hasStorm()) return "下雨";
        return "晴朗";
    }
    
    private String getTimeInfo(World world) {
        long time = world.getTime();
        long hours = (time / 1000 + 6) % 24;
        return String.format("%02d:00", hours);
    }
} 