# API 文档

AI Chat Plugin v1.1.0618 开发者API文档。

## 🏗️ 核心架构

### 主要组件

```java
AIChatPlugin              // 插件主类
├── ConversationManager   // 对话管理器
├── DeepSeekAIService    // AI服务
├── EnvironmentCollector // 环境收集器
├── PerformanceMonitor   // 性能监控器
└── PlayerProfileManager // 玩家档案管理器
```

## 🔌 核心API

### AIChatPlugin

```java
public class AIChatPlugin extends JavaPlugin {
    // 获取服务实例
    public ConversationManager getConversationManager();
    public DeepSeekAIService getAIService();
    public EnvironmentCollector getEnvironmentCollector();
    public PerformanceMonitor getPerformanceMonitor();
    public PlayerProfileManager getProfileManager();
    public ConfigLoader getConfigLoader();
    
    // 调试功能
    public void debug(String message);
    public boolean isDebugEnabled();
}
```

### ConversationManager

```java
public class ConversationManager {
    // 消息处理
    public void processMessage(Player player, String message, String type, String... args);
    
    // 历史记录管理
    public List<Message> getConversationHistory(UUID playerId);
    public void addMessage(UUID playerId, String sender, String content, boolean isAI);
    public void clearPlayerHistory(UUID playerId); // v1.1.0618新增
    
    // 状态管理
    public boolean isOnCooldown(UUID playerId);
    public String getLastResponse(UUID playerId);
    
    // 缓存管理
    public JsonArray getCachedMessages(UUID playerId);
    
    // 生命周期
    public void shutdown();
}
```

### EnvironmentCollector

```java
public class EnvironmentCollector {
    // 异步环境收集
    public CompletableFuture<String> collectEnvironmentInfo(Player player);
    
    // 同步环境收集
    public String collectDetailedInfo(Player player);
    
    // 配置应用
    public void applyConfig(FileConfiguration config);
    
    // 生命周期
    public void shutdown();
}
```

### PerformanceMonitor

```java
public class PerformanceMonitor {
    // 性能监控
    public void start();
    public void stop();
    public double getCurrentTPS();
    public OperationMode getCurrentMode();
    
    // 模式切换
    public void switchMode(OperationMode newMode);
    
    // 功能管理
    public FeatureManager getFeatureManager();
    public Map<String, Object> getFeatureStatusReport();
}
```

## 🎯 事件系统

### 聊天事件监听

```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onPlayerChat(AsyncPlayerChatEvent event) {
    Player player = event.getPlayer();
    String message = event.getMessage();
    
    // 检查前缀
    String prefix = plugin.getConfigLoader().getChatPrefix();
    if (prefix == null || prefix.isEmpty() || message.startsWith(prefix)) {
        // 处理消息
        conversationManager.processMessage(player, content, "chat");
    }
}
```

### 玩家状态监听

```java
@EventHandler(priority = EventPriority.MONITOR)
public void onPlayerDamage(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Player)) return;
    
    Player player = (Player) event.getEntity();
    // 智能伤害事件处理
    // 支持性能模式自动调整
}
```

## ⚙️ 配置API

### ConfigLoader

```java
public class ConfigLoader {
    // 基础配置
    public String getApiKey();
    public String getApiUrl();
    public String getModel();
    public String getRoleSystem();
    
    // 聊天配置
    public String getChatPrefix();
    public boolean isChatEnabled();
    public boolean isBroadcastEnabled();
    
    // 性能配置
    public boolean isAutoOptimizeEnabled();
    public double getTpsThresholdFull();
    public double getTpsThresholdLite();
    public double getTpsThresholdBasic();
    
    // 配置重载
    public void reloadConfig();
    public void saveConfig();
}
```

## 🚀 插件集成

### 依赖添加

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>ai-chat-plugin</artifactId>
    <version>1.1.0618</version>
    <scope>provided</scope>
</dependency>
```

### 插件检测

```java
public class YourPlugin extends JavaPlugin {
    private AIChatPlugin aiChatPlugin;
    
    @Override
    public void onEnable() {
        // 检测AI Chat Plugin
        Plugin plugin = getServer().getPluginManager().getPlugin("AIChatPlugin");
        if (plugin instanceof AIChatPlugin) {
            aiChatPlugin = (AIChatPlugin) plugin;
            getLogger().info("AI Chat Plugin 集成成功");
        }
    }
}
```

### API调用示例

```java
// 发送AI消息
Player player = // 获取玩家
String message = "你好，AI助手";
aiChatPlugin.getConversationManager().processMessage(player, message, "custom");

// 获取环境信息
aiChatPlugin.getEnvironmentCollector()
    .collectEnvironmentInfo(player)
    .thenAccept(env -> {
        getLogger().info("环境信息: " + env);
    });

// 检查性能状态
OperationMode mode = aiChatPlugin.getPerformanceMonitor().getCurrentMode();
getLogger().info("当前性能模式: " + mode);
```

## 🎛️ 自定义扩展

### 自定义消息处理器

```java
public class CustomMessageProcessor {
    public void processCustomMessage(Player player, String message) {
        // 自定义处理逻辑
        aiChatPlugin.getConversationManager()
            .processMessage(player, message, "custom_type");
    }
}
```

### 自定义环境信息

```java
public class CustomEnvironmentProvider {
    public String getCustomEnvironmentInfo(Player player) {
        // 收集自定义环境信息
        return "自定义环境信息";
    }
}
```

## 📊 性能模式API

### OperationMode 枚举

```java
public enum OperationMode {
    FULL,      // 全功能模式 (TPS ≥ 18.0)
    LITE,      // 精简模式 (TPS ≥ 15.0)
    BASIC,     // 基础模式 (TPS ≥ 10.0)
    EMERGENCY  // 应急模式 (TPS < 10.0)
}
```

### 功能管理器

```java
public class FeatureManager {
    // 功能状态查询
    public boolean isFeatureEnabled(String feature);
    public Map<String, Boolean> getFeatureStatus();
    
    // 功能控制
    public void setFeatureEnabled(String feature, boolean enabled);
    public void applyModeOptimization(OperationMode mode);
    
    // 伤害事件配置
    public DamageEventConfig getCurrentDamageEventConfig();
    public boolean shouldProcessDamageEvent(double currentHealth, double finalDamage);
}
```

## 🔧 实用工具

### 消息类

```java
public class Message {
    private String sender;
    private String content;
    private boolean isAI;
    private long timestamp;
    
    // 构造器和getter方法
}
```

### 历史压缩工具

```java
public class HistoryCompressor {
    public static byte[] compress(List<Message> messages);
    public static List<Message> decompress(byte[] compressed);
    public static byte[] compressDelta(List<Message> messages, byte[] existingData);
}
```

## 🚨 异常处理

### 常见异常

```java
// API调用异常
try {
    String response = aiService.generateResponse(prompt, player);
} catch (IOException e) {
    // 网络或API异常
    getLogger().warning("AI服务调用失败: " + e.getMessage());
} catch (Exception e) {
    // 其他异常
    getLogger().log(Level.SEVERE, "未知错误", e);
}

// 配置异常
try {
    configLoader.reloadConfig();
} catch (Exception e) {
    getLogger().warning("配置重载失败: " + e.getMessage());
}
```

## 📝 最佳实践

### 1. 异步处理
```java
// 推荐：异步处理
CompletableFuture.runAsync(() -> {
    // 耗时操作
}).thenRun(() -> {
    // 回调处理
});

// 避免：主线程阻塞
// 直接调用耗时API
```

### 2. 资源管理
```java
// 确保正确关闭资源
@Override
public void onDisable() {
    if (aiChatPlugin != null) {
        // 清理资源
    }
}
```

### 3. 性能考虑
```java
// 检查性能模式
OperationMode mode = performanceMonitor.getCurrentMode();
if (mode == OperationMode.EMERGENCY) {
    // 跳过非必要操作
    return;
}
```

---

**📅 最后更新**: 2025-06-20  
**🔖 API版本**: v1.1.0618  
**📖 更多信息**: [项目主页](../README.md) 