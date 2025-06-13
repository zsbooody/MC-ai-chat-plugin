package com.example.aichatplugin;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;

/**
 * 对话管理器
 * 
 * 职责：
 * 1. 管理玩家对话历史
 * 2. 处理AI响应生成
 * 3. 控制消息广播
 * 4. 维护对话状态
 * 
 * 主要功能：
 * 1. 存储和更新对话历史
 * 2. 生成AI响应
 * 3. 格式化消息显示
 * 4. 管理对话超时
 * 
 * 配置项：
 * 1. 最大历史记录数
 * 2. 最大上下文长度
 * 3. 消息格式
 * 4. 超时设置
 */
public class ConversationManager {
    private final AIChatPlugin plugin;
    private final Map<UUID, List<Message>> conversations;
    private final int maxHistorySize;
    private final int maxContextLength;
    private final String replyFormat;
    private final DeepSeekAIService aiService;
    private final EnvironmentCollector environmentCollector;
    private final PlayerProfileManager profileManager;
    private final Executor executor;
    private final ScheduledExecutorService cleanupService;
    private final File historyFile;
    
    private static final long MESSAGE_TIMEOUT = 2000;
    private static final long RESPONSE_COOLDOWN = 3000; // 3秒冷却
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY = 1000; // 1秒重试延迟
    
    private final Map<UUID, Long> lastResponseTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastResponse = new ConcurrentHashMap<>();
    
    public ConversationManager(AIChatPlugin plugin) {
        this.plugin = plugin;
        ConfigLoader config = plugin.getConfigLoader();
        this.conversations = new ConcurrentHashMap<>();
        this.maxHistorySize = config.getConversationMaxHistory();
        this.maxContextLength = config.getConversationMaxContext();
        this.replyFormat = config.getMessageFormat("reply");
        this.aiService = plugin.getAIService();
        this.environmentCollector = plugin.getEnvironmentCollector();
        this.profileManager = plugin.getProfileManager();
        this.executor = Executors.newCachedThreadPool();
        this.cleanupService = Executors.newSingleThreadScheduledExecutor();
        this.historyFile = new File(plugin.getDataFolder(), "conversations.yml");
        
        // 确保插件目录存在
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        // 加载对话历史
        loadHistory();
        
        // 启动定期保存任务
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveHistory, 300L, 300L);
        
        // 启动定期清理任务
        cleanupService.scheduleAtFixedRate(this::cleanup, 1, 1, TimeUnit.MINUTES);
    }
    
    /**
     * 加载对话历史
     */
    private void loadHistory() {
        if (!plugin.getConfigLoader().isConversationPersistenceEnabled()) {
            return;
        }
        
        if (!historyFile.exists()) {
            return;
        }
        
        plugin.getLogger().info("正在加载对话历史...");
        FileConfiguration config = YamlConfiguration.loadConfiguration(historyFile);
        int loadedCount = 0;
        
        for (String key : config.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                String section = key + ".";
                List<Map<?, ?>> messages = config.getMapList(section + "messages");
                
                if (!messages.isEmpty()) {
                    List<Message> history = new ArrayList<>();
                    for (Map<?, ?> msg : messages) {
                        history.add(new Message(
                            (String) msg.get("sender"),
                            (String) msg.get("content"),
                            (Boolean) msg.get("isAI"),
                            (Long) msg.get("timestamp")
                        ));
                    }
                    
                    conversations.put(playerId, history);
                    loadedCount++;
                    plugin.getLogger().info("加载玩家 " + key + " 的对话历史，消息数: " + history.size());
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("无效的玩家UUID: " + key);
            }
        }
        
        plugin.getLogger().info("已加载 " + loadedCount + " 个玩家的对话历史");
    }
    
    /**
     * 清理过期数据
     */
    private void cleanup() {
        long currentTime = System.currentTimeMillis();
        boolean hasChanges = false;
        
        for (Map.Entry<UUID, List<Message>> entry : conversations.entrySet()) {
            List<Message> history = entry.getValue();
            synchronized (history) {
                int originalSize = history.size();
                history.removeIf(msg -> currentTime - msg.timestamp > MESSAGE_TIMEOUT);
                if (history.size() != originalSize) {
                    hasChanges = true;
                }
            }
        }
        
        lastResponseTime.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > MESSAGE_TIMEOUT
        );
        
        if (hasChanges) {
            saveHistory();
        }
    }
    
    /**
     * 保存对话历史
     */
    private void saveHistory() {
        if (!plugin.getConfigLoader().isConversationPersistenceEnabled()) {
            return;
        }
        
        plugin.getLogger().info("正在保存对话历史...");
        FileConfiguration config = new YamlConfiguration();
        int savedCount = 0;
        
        try {
            // 确保目录存在
            if (!historyFile.getParentFile().exists()) {
                historyFile.getParentFile().mkdirs();
            }
            
            // 创建临时文件
            File tempFile = new File(historyFile.getParentFile(), "conversations_temp.yml");
            
            // 写入新数据
            for (Map.Entry<UUID, List<Message>> entry : conversations.entrySet()) {
                String key = entry.getKey().toString();
                List<Message> history = entry.getValue();
                
                if (!history.isEmpty()) {
                    String section = key + ".";
                    List<Map<String, Object>> messages = new ArrayList<>();
                    for (Message msg : history) {
                        Map<String, Object> messageMap = new HashMap<>();
                        messageMap.put("sender", msg.sender);
                        messageMap.put("content", msg.content);
                        messageMap.put("isAI", msg.isAI);
                        messageMap.put("timestamp", msg.timestamp);
                        messages.add(messageMap);
                    }
                    
                    config.set(section + "messages", messages);
                    savedCount++;
                    plugin.getLogger().info("保存玩家 " + key + " 的对话历史，消息数: " + messages.size());
                }
            }
            
            // 先保存到临时文件
            config.save(tempFile);
            
            // 如果原文件存在，先备份
            if (historyFile.exists()) {
                File backupFile = new File(historyFile.getParentFile(), "conversations_backup.yml");
                if (backupFile.exists()) {
                    backupFile.delete();
                }
                historyFile.renameTo(backupFile);
            }
            
            // 将临时文件重命名为正式文件
            tempFile.renameTo(historyFile);
            
            // 删除备份文件
            File backupFile = new File(historyFile.getParentFile(), "conversations_backup.yml");
            if (backupFile.exists()) {
                backupFile.delete();
            }
            
            plugin.getLogger().info("已保存 " + savedCount + " 个玩家的对话历史");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "保存对话历史时发生错误", e);
        }
    }

    /**
     * 处理消息
     */
    public void processMessage(Player player, String message, String type, String... args) {
        if (isOnCooldown(player.getUniqueId())) {
            return;
        }
        
        executor.execute(() -> {
            try {
                PlayerProfileManager.PlayerProfile profile = profileManager.getProfile(player.getUniqueId());
                environmentCollector.collectEnvironmentInfo(player)
                    .thenAccept(envInfo -> {
                        try {
                            String prompt = buildPrompt(player, message, type, profile, envInfo, args);
                            String response = generateResponseWithRetry(prompt, player);
                            if (response != null && !response.isEmpty()) {
                                // 先添加消息到历史记录
                                addMessage(player.getUniqueId(), player.getName(), message, false);
                                addMessage(player.getUniqueId(), "AI", response, true);
                                
                                // 然后广播响应
                                String formattedMessage = replyFormat.replace("{text}", response);
                                plugin.getServer().broadcastMessage(ChatColor.translateAlternateColorCodes('&', formattedMessage));
                                plugin.debug("已广播AI响应: " + response);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "处理消息时发生错误", e);
                        }
                    })
                    .exceptionally(e -> {
                        plugin.getLogger().log(Level.WARNING, "收集环境信息时发生错误", e);
                        return null;
                    });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "处理消息时发生错误", e);
            }
        });
    }
    
    /**
     * 构建提示词
     */
    private String buildPrompt(Player player, String message, String type,
            PlayerProfileManager.PlayerProfile profile,
            String envInfo,
            String... args) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("玩家 ").append(player.getName()).append(" ");
        
        switch (type) {
            case "chat":
                prompt.append("说：").append(message).append("\n");
                prompt.append("玩家数据：\n");
                prompt.append("- 游戏时长：").append(profile.getPlayTime()).append("分钟\n");
                prompt.append("- 击杀数：").append(profile.getKills()).append("\n");
                prompt.append("- 死亡数：").append(profile.getDeaths()).append("\n");
                prompt.append("- 挖掘方块：").append(profile.getBlocksMined()).append("\n");
                prompt.append("- 放置方块：").append(profile.getBlocksPlaced()).append("\n");
                prompt.append("- 行走距离：").append(profile.getDistanceWalked()).append("米\n");
                prompt.append("物品栏：").append(profileManager.getInventoryString(player)).append("\n");
                prompt.append("环境：").append(envInfo).append("\n");
                prompt.append("请根据玩家的游戏数据给出一个合适的回应。");
                break;
                
            case "join":
                prompt.append("加入了游戏\n");
                prompt.append("玩家数据：\n");
                prompt.append("- 游戏时长：").append(profile.getPlayTime()).append("分钟\n");
                prompt.append("- 击杀数：").append(profile.getKills()).append("\n");
                prompt.append("- 死亡数：").append(profile.getDeaths()).append("\n");
                prompt.append("请给出一个欢迎消息。");
                break;
                
            case "quit":
                prompt.append("离开了游戏\n");
                prompt.append("玩家数据：\n");
                prompt.append("- 游戏时长：").append(profile.getPlayTime()).append("分钟\n");
                prompt.append("- 击杀数：").append(profile.getKills()).append("\n");
                prompt.append("- 死亡数：").append(profile.getDeaths()).append("\n");
                prompt.append("请给出一个告别消息。");
                break;
                
            case "death":
                if (args == null || args.length == 0) {
                    prompt.append("死亡了\n");
                } else {
                    prompt.append("死亡了\n");
                    prompt.append("死亡原因：").append(args[0]).append("\n");
                }
                prompt.append("死亡位置：").append(envInfo).append("\n");
                prompt.append("玩家数据：\n");
                prompt.append("- 总死亡次数：").append(profile.getDeaths()).append("\n");
                prompt.append("- 击杀数：").append(profile.getKills()).append("\n");
                prompt.append("请给出一个安慰或鼓励的消息。");
                break;
                
            case "level_up":
                if (args == null || args.length == 0) {
                    prompt.append("升级了\n");
                } else {
                    String[] levels = args[0].split(",");
                    if (levels.length >= 2) {
                        prompt.append("升级了\n");
                        prompt.append("从 ").append(levels[0]).append(" 级升到 ").append(levels[1]).append(" 级\n");
                    } else {
                        prompt.append("升级了\n");
                    }
                }
                prompt.append("玩家数据：\n");
                prompt.append("- 游戏时长：").append(profile.getPlayTime()).append("分钟\n");
                prompt.append("- 击杀数：").append(profile.getKills()).append("\n");
                prompt.append("- 死亡数：").append(profile.getDeaths()).append("\n");
                prompt.append("请给出一个祝贺的消息。");
                break;
                
            case "advancement":
                if (args == null || args.length == 0) {
                    prompt.append("完成了进度\n");
                } else {
                    prompt.append("完成了进度：").append(args[0]).append("\n");
                }
                prompt.append("玩家数据：\n");
                prompt.append("- 游戏时长：").append(profile.getPlayTime()).append("分钟\n");
                prompt.append("- 击杀数：").append(profile.getKills()).append("\n");
                prompt.append("- 死亡数：").append(profile.getDeaths()).append("\n");
                prompt.append("请给出一个祝贺的消息。");
                break;
                
            case "damage":
                if (args == null || args.length == 0) {
                    prompt.append("受到伤害\n");
                } else {
                    String[] damageInfo = args[0].split(",");
                    prompt.append("受到伤害\n");
                    if (damageInfo.length >= 3) {
                        prompt.append("损失了 ").append(damageInfo[0]).append(" 点生命值\n");
                        prompt.append("剩余生命值：").append(damageInfo[1]).append("/").append(damageInfo[2]).append("\n");
                        if (damageInfo.length > 3) {
                            prompt.append("伤害来源：").append(damageInfo[3]).append("\n");
                        }
                    }
                }
                prompt.append("玩家数据：\n");
                prompt.append("- 总死亡次数：").append(profile.getDeaths()).append("\n");
                prompt.append("- 击杀数：").append(profile.getKills()).append("\n");
                prompt.append("请给出一个关心或提醒的消息。");
                break;
                
            default:
                prompt.append("发送了消息：").append(message).append("\n");
                prompt.append("请给出一个合适的回应。");
        }
        
        return prompt.toString();
    }
    
    /**
     * 带重试的响应生成
     */
    private String generateResponseWithRetry(String prompt, Player player) {
        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                return aiService.generateResponse(prompt, player);
            } catch (Exception e) {
                retries++;
                if (retries < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * 添加消息到历史记录
     */
    private void addMessage(UUID playerId, String sender, String content, boolean isAI) {
        conversations.computeIfAbsent(playerId, k -> new ArrayList<>());
        List<Message> history = conversations.get(playerId);
        
        synchronized (history) {
            history.add(new Message(sender, content, isAI));
            while (history.size() > maxHistorySize) {
                history.remove(0);
            }
        }
        
        if (isAI) {
            lastResponseTime.put(playerId, System.currentTimeMillis());
            lastResponse.put(playerId, content);
        }
        
        // 如果启用了持久化，则保存历史
        if (plugin.getConfigLoader().isConversationPersistenceEnabled()) {
            saveHistory();
        }
    }
    
    /**
     * 关闭管理器
     */
    public void shutdown() {
        saveHistory();
        cleanupService.shutdown();
        try {
            if (!cleanupService.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupService.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 检查是否在冷却中
     */
    public boolean isOnCooldown(UUID playerId) {
        Long lastTime = lastResponseTime.get(playerId);
        if (lastTime == null) return false;
        return System.currentTimeMillis() - lastTime < RESPONSE_COOLDOWN;
    }
    
    /**
     * 获取最后响应
     */
    public String getLastResponse(UUID playerId) {
        return lastResponse.get(playerId);
    }
    
    /**
     * 获取玩家的对话历史
     */
    public List<Message> getConversationHistory(UUID playerId) {
        return conversations.getOrDefault(playerId, new ArrayList<>());
    }
    
    /**
     * 获取玩家的最后一条消息
     */
    public String getLastMessage(UUID playerId) {
        List<Message> history = getConversationHistory(playerId);
        if (history.isEmpty()) {
            return null;
        }
        
        // 从后往前找玩家的最后一条消息
        for (int i = history.size() - 1; i >= 0; i--) {
            Message msg = history.get(i);
            if (!msg.isAI) {
                return msg.content;
            }
        }
        return null;
    }
    
    /**
     * 消息类
     */
    public static class Message {
        public final String sender;
        public final String content;
        public final boolean isAI;
        public long timestamp;
        
        Message(String sender, String content, boolean isAI) {
            this.sender = sender;
            this.content = content;
            this.isAI = isAI;
            this.timestamp = System.currentTimeMillis();
        }
        
        Message(String sender, String content, boolean isAI, long timestamp) {
            this.sender = sender;
            this.content = content;
            this.isAI = isAI;
            this.timestamp = timestamp;
        }
    }
}