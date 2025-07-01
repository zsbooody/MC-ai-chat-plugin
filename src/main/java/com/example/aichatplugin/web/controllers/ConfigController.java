package com.example.aichatplugin.web.controllers;

import com.example.aichatplugin.AIChatPlugin;
import com.example.aichatplugin.ConfigLoader;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * 配置管理API控制器
 * 
 * 提供RESTful API接口：
 * GET  /api/config - 获取当前配置
 * POST /api/config - 更新配置
 * POST /api/config/validate - 验证配置
 * POST /api/config/reset - 重置配置
 */
public class ConfigController extends HttpServlet {
    
    private final AIChatPlugin plugin;
    private final ConfigLoader configLoader;
    private final Gson gson = new Gson();
    private final Map<String, BiConsumer<JsonElement, ConfigLoader>> configUpdaters;

    @FunctionalInterface
    interface ConfigUpdater {
        void update(JsonElement value, ConfigLoader loader);
    }
    
    public ConfigController(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.configLoader = plugin.getConfigLoader();
        this.configUpdaters = new HashMap<>();
        initializeUpdaters();
    }

    private void initializeUpdaters() {
        // 基础设置
        configUpdaters.put("basic.debugEnabled", (v, c) -> c.setDebugEnabled(v.getAsBoolean()));
        configUpdaters.put("basic.chatEnabled", (v, c) -> c.setChatEnabled(v.getAsBoolean()));
        configUpdaters.put("basic.chatPrefix", (v, c) -> c.setChatPrefix(v.getAsString()));
        configUpdaters.put("basic.broadcastEnabled", (v, c) -> c.setBroadcastEnabled(v.getAsBoolean()));
        
        // AI配置 - 增强API密钥安全处理
        configUpdaters.put("ai.apiKey", (v, c) -> {
            try {
            String raw = v.getAsString();
                
                // 🔧 增强的引号和特殊字符清理
                String cleaned = sanitizeApiKey(raw);
                
                // 🔧 验证API密钥格式
                if (!validateApiKeyFormat(cleaned)) {
                    throw new IllegalArgumentException("API密钥格式无效");
                }
                
                // 🔧 只有在密钥有效且不是掩码时才保存
                if (!cleaned.isEmpty() && !cleaned.startsWith("****")) {
                    // 🔧 创建配置备份
                    backupConfigBeforeChange();
                    
                c.setApiKey(cleaned);
                    plugin.getLogger().info("API密钥已安全更新，长度: " + cleaned.length() + " 字符");
                } else if (cleaned.startsWith("****")) {
                    plugin.getLogger().info("跳过掩码API密钥的保存");
                }
            } catch (Exception e) {
                plugin.getLogger().severe("API密钥更新失败: " + e.getMessage());
                throw new RuntimeException("API密钥保存失败: " + e.getMessage());
            }
        });
        configUpdaters.put("ai.model", (v, c) -> c.set("settings.model", v.getAsString()));
        configUpdaters.put("ai.temperature", (v, c) -> c.set("ai.temperature", v.getAsDouble()));
        configUpdaters.put("ai.maxTokens", (v, c) -> c.set("ai.max-tokens", (int)Math.round(v.getAsDouble())));
        
        // 性能优化
        configUpdaters.put("performance.autoOptimizeEnabled", (v, c) -> c.setAutoOptimize(v.getAsBoolean()));
        configUpdaters.put("performance.tpsThresholdFull", (v, c) -> c.set("performance.tps-threshold-full", v.getAsDouble()));
        configUpdaters.put("performance.tpsThresholdLite", (v, c) -> c.set("performance.tps-threshold-lite", v.getAsDouble()));
        configUpdaters.put("performance.tpsThresholdBasic", (v, c) -> c.set("performance.tps-threshold-basic", v.getAsDouble()));
        
        // 环境检测
        configUpdaters.put("environment.entityRange", (v, c) -> c.set("environment.entity-range", (int)Math.round(v.getAsDouble())));
        configUpdaters.put("environment.blockScanRange", (v, c) -> c.set("environment.block-scan-range", (int)Math.round(v.getAsDouble())));
        configUpdaters.put("environment.showWeather", (v, c) -> c.set("environment.show-weather", v.getAsBoolean()));
        configUpdaters.put("environment.showTime", (v, c) -> c.set("environment.show-time", v.getAsBoolean()));
        
        // 聊天管理
        configUpdaters.put("chat.normalUserCooldown", (v, c) -> c.setNormalUserCooldown(Math.round(v.getAsDouble())));
        configUpdaters.put("chat.vipUserCooldown", (v, c) -> c.setVipUserCooldown(Math.round(v.getAsDouble())));
        configUpdaters.put("chat.maxMessagesPerMinute", (v, c) -> c.setMaxMessagesPerMinute((int)Math.round(v.getAsDouble())));
        configUpdaters.put("chat.contentFilterEnabled", (v, c) -> c.setFilterEnabled(v.getAsBoolean()));
        
        // 事件响应配置
        configUpdaters.put("events.joinEnabled", (v, c) -> c.set("events.join.enabled", v.getAsBoolean()));
        configUpdaters.put("events.joinCooldown", (v, c) -> c.set("events.join.cooldown", Math.round(v.getAsDouble())));
        configUpdaters.put("events.quitEnabled", (v, c) -> c.set("events.quit.enabled", v.getAsBoolean()));
        configUpdaters.put("events.quitCooldown", (v, c) -> c.set("events.quit.cooldown", Math.round(v.getAsDouble())));
        configUpdaters.put("events.damageEnabled", (v, c) -> c.set("events.damage.enabled", v.getAsBoolean()));
        configUpdaters.put("events.damageCooldown", (v, c) -> c.set("events.damage.cooldown", Math.round(v.getAsDouble())));
        configUpdaters.put("events.damageThreshold", (v, c) -> c.set("events.damage.threshold", v.getAsDouble()));
    }
    
    /**
     * 🔧 安全清理API密钥
     */
    private String sanitizeApiKey(String raw) {
        if (raw == null) return "";
        
        // 移除所有类型的引号和特殊字符
        String cleaned = raw
            .replaceAll("^\"|\"$", "")           // 移除首尾双引号
            .replaceAll("^'|'$", "")             // 移除首尾单引号
            .replaceAll("[\u201C\u201D]", "")    // 移除中文引号
            .replaceAll("[\u2018\u2019]", "")    // 移除中文单引号
            .replaceAll("[\\r\\n\\t]", "")       // 移除换行和制表符
            .trim();                             // 移除首尾空格
        
        // 移除中间的引号（但保留连字符等有效字符）
        cleaned = cleaned.replaceAll("[\"\']", "");
        
        return cleaned;
    }
    
    /**
     * 🔧 验证API密钥格式
     */
    private boolean validateApiKeyFormat(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return true; // 允许空密钥（用于清除）
        }
        
        // 检查长度 - 放宽限制
        if (apiKey.length() < 8 || apiKey.length() > 500) {
            plugin.getLogger().warning("API密钥长度无效: " + apiKey.length() + " 字符");
            return false;
        }
        
        // 只检查明显的危险字符，不要过度限制
        if (apiKey.contains("\r") || apiKey.contains("\n") || apiKey.contains("\t")) {
            plugin.getLogger().warning("API密钥包含危险字符");
            return false;
        }
        
        // 🔧 修复：使用更宽松的验证 - 只要不包含明显的控制字符就允许
        // DeepSeek API密钥通常格式为: sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
        // 允许字母、数字、连字符、下划线、点号等常见字符
        if (apiKey.matches(".*[\\x00-\\x1F\\x7F].*")) {
            plugin.getLogger().warning("API密钥包含控制字符");
            return false;
        }
        
        plugin.getLogger().info("API密钥格式验证通过");
        return true;
    }
    
    /**
     * 🔧 配置备份机制
     */
    private void backupConfigBeforeChange() {
        try {
            java.io.File configFile = new java.io.File(plugin.getDataFolder(), "config.yml");
            if (configFile.exists()) {
                java.io.File backupFile = new java.io.File(plugin.getDataFolder(), 
                    "config.yml.backup." + System.currentTimeMillis());
                
                java.nio.file.Files.copy(configFile.toPath(), backupFile.toPath());
                plugin.getLogger().info("配置备份已创建: " + backupFile.getName());
                
                // 清理旧备份（保留最近5个）
                cleanupOldBackups();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("创建配置备份失败: " + e.getMessage());
        }
    }
    
    /**
     * 🔧 清理旧的配置备份
     */
    private void cleanupOldBackups() {
        try {
            java.io.File dataFolder = plugin.getDataFolder();
            java.io.File[] backupFiles = dataFolder.listFiles((dir, name) -> 
                name.startsWith("config.yml.backup."));
            
            if (backupFiles != null && backupFiles.length > 5) {
                // 按修改时间排序，删除最旧的
                java.util.Arrays.sort(backupFiles, 
                    java.util.Comparator.comparingLong(java.io.File::lastModified));
                
                for (int i = 0; i < backupFiles.length - 5; i++) {
                    if (backupFiles[i].delete()) {
                        plugin.getLogger().info("已清理旧备份: " + backupFiles[i].getName());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("清理备份文件失败: " + e.getMessage());
        }
    }
    
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        setJsonHeaders(response);
        
        // 从ApiServlet传递的子路径
        String pathInfo = (String) request.getAttribute("subPath");
        if (pathInfo == null) pathInfo = "";
        
        if (pathInfo.equals("") || pathInfo.equals("/") || pathInfo.equals("/all")) {
            // 获取完整配置
            handleGetFullConfig(response);
        } else if (pathInfo.equals("/categories")) {
            // 获取配置分类
            handleGetCategories(response);
        } else {
            // 获取特定分类的配置
            String category = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
            handleGetCategoryConfig(category, response);
        }
    }
    
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        setJsonHeaders(response);
        
        // 从ApiServlet传递的子路径
        String pathInfo = (String) request.getAttribute("subPath");
        if (pathInfo == null) pathInfo = "";
        
        try {
            if (pathInfo.equals("") || pathInfo.equals("/")) {
                // 更新配置
                handleUpdateConfig(request, response);
            } else if (pathInfo.equals("/validate")) {
                // 验证配置
                handleValidateConfig(request, response);
            } else if (pathInfo.equals("/reset")) {
                // 重置配置
                handleResetConfig(response);
            } else if (pathInfo.equals("/reload")) {
                // 重载配置
                handleReloadConfig(response);
            } else {
                sendErrorResponse(response, 404, "API端点不存在");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "配置API处理异常", e);
            sendErrorResponse(response, 500, "服务器内部错误: " + e.getMessage());
        }
    }
    
    /**
     * 获取完整配置
     */
    public void handleGetFullConfig(HttpServletResponse response) throws IOException {
        Map<String, Object> config = new HashMap<>();
        
        // 基础设置
        Map<String, Object> basic = new HashMap<>();
        basic.put("debugEnabled", configLoader.isDebugEnabled());
        basic.put("chatEnabled", configLoader.isChatEnabled());
        basic.put("chatPrefix", configLoader.getChatPrefix());
        basic.put("broadcastEnabled", configLoader.isBroadcastEnabled());
        config.put("basic", basic);
        
        // AI配置
        Map<String, Object> ai = new HashMap<>();
        ai.put("apiKey", maskApiKey(configLoader.getApiKey()));
        ai.put("apiUrl", configLoader.getApiUrl());
        ai.put("model", configLoader.getModel());
        ai.put("temperature", configLoader.getTemperature());
        ai.put("maxTokens", configLoader.getMaxTokens());
        ai.put("roleSystem", configLoader.getRoleSystem());
        config.put("ai", ai);
        
        // 性能配置
        Map<String, Object> performance = new HashMap<>();
        performance.put("autoOptimizeEnabled", configLoader.isAutoOptimizeEnabled());
        performance.put("tpsThresholdFull", configLoader.getTpsThresholdFull());
        performance.put("tpsThresholdLite", configLoader.getTpsThresholdLite());
        performance.put("tpsThresholdBasic", configLoader.getTpsThresholdBasic());
        performance.put("cpuThreshold", configLoader.getCpuThreshold());
        performance.put("memoryThreshold", configLoader.getMemoryThreshold());
        config.put("performance", performance);
        
        // 环境配置
        Map<String, Object> environment = new HashMap<>();
        environment.put("entityRange", configLoader.getEntityRange());
        environment.put("blockScanRange", configLoader.getBlockScanRange());
        environment.put("showDetailedLocation", configLoader.isShowDetailedLocation());
        environment.put("showWeather", configLoader.isShowWeather());
        environment.put("showTime", configLoader.isShowTime());
        environment.put("cacheTTL", configLoader.getCacheTTL());
        config.put("environment", environment);
        
        // 聊天配置
        Map<String, Object> chat = new HashMap<>();
        chat.put("normalUserCooldown", configLoader.getNormalUserCooldown());
        chat.put("vipUserCooldown", configLoader.getVipUserCooldown());
        chat.put("maxMessagesPerMinute", configLoader.getMaxMessagesPerMinute());
        chat.put("contentFilterEnabled", configLoader.isFilterEnabled());
        config.put("chat", chat);
        
        // 事件配置
        Map<String, Object> events = new HashMap<>();
        events.put("joinEnabled", configLoader.isJoinEnabled());
        events.put("joinCooldown", configLoader.getJoinCooldown());
        events.put("quitEnabled", configLoader.isQuitEnabled());
        events.put("quitCooldown", configLoader.getQuitCooldown());
        events.put("damageEnabled", configLoader.isDamageEnabled());
        events.put("damageCooldown", configLoader.getDamageCooldown());
        events.put("damageThreshold", configLoader.getDamageThreshold());
        config.put("events", events);
        
        sendJsonResponse(response, config);
    }
    
    /**
     * 获取配置分类列表
     */
    private void handleGetCategories(HttpServletResponse response) throws IOException {
        Map<String, Object> categories = new HashMap<>();
        
        categories.put("basic", Map.of(
            "name", "基础设置",
            "icon", "🔧",
            "description", "插件的基本配置选项"
        ));
        
        categories.put("ai", Map.of(
            "name", "AI配置",
            "icon", "🤖",
            "description", "AI模型和API相关设置"
        ));
        
        categories.put("performance", Map.of(
            "name", "性能优化",
            "icon", "⚡",
            "description", "性能监控和优化设置"
        ));
        
        categories.put("environment", Map.of(
            "name", "环境检测",
            "icon", "🌍",
            "description", "环境信息收集配置"
        ));
        
        categories.put("chat", Map.of(
            "name", "聊天管理",
            "icon", "💬",
            "description", "聊天功能和限制设置"
        ));
        
        categories.put("events", Map.of(
            "name", "事件响应",
            "icon", "⚡",
            "description", "玩家事件响应配置"
        ));
        
        sendJsonResponse(response, categories);
    }
    
    /**
     * 获取特定分类的配置
     */
    private void handleGetCategoryConfig(String category, HttpServletResponse response) throws IOException {
        Map<String, Object> config = new HashMap<>();
        
        switch (category) {
            case "basic":
                config.put("debugEnabled", configLoader.isDebugEnabled());
                config.put("chatEnabled", configLoader.isChatEnabled());
                config.put("chatPrefix", configLoader.getChatPrefix());
                config.put("broadcastEnabled", configLoader.isBroadcastEnabled());
                break;
                
            case "ai":
                config.put("apiKey", maskApiKey(configLoader.getApiKey()));
                config.put("apiUrl", configLoader.getApiUrl());
                config.put("model", configLoader.getModel());
                config.put("temperature", configLoader.getTemperature());
                config.put("maxTokens", configLoader.getMaxTokens());
                config.put("roleSystem", configLoader.getRoleSystem());
                break;
                
            case "performance":
                config.put("autoOptimizeEnabled", configLoader.isAutoOptimizeEnabled());
                config.put("tpsThresholdFull", configLoader.getTpsThresholdFull());
                config.put("tpsThresholdLite", configLoader.getTpsThresholdLite());
                config.put("tpsThresholdBasic", configLoader.getTpsThresholdBasic());
                config.put("cpuThreshold", configLoader.getCpuThreshold());
                config.put("memoryThreshold", configLoader.getMemoryThreshold());
                break;
                
            case "environment":
                config.put("entityRange", configLoader.getEntityRange());
                config.put("blockScanRange", configLoader.getBlockScanRange());
                config.put("showDetailedLocation", configLoader.isShowDetailedLocation());
                config.put("showWeather", configLoader.isShowWeather());
                config.put("showTime", configLoader.isShowTime());
                config.put("cacheTTL", configLoader.getCacheTTL());
                break;
                
            case "chat":
                config.put("normalUserCooldown", configLoader.getNormalUserCooldown());
                config.put("vipUserCooldown", configLoader.getVipUserCooldown());
                config.put("maxMessagesPerMinute", configLoader.getMaxMessagesPerMinute());
                config.put("contentFilterEnabled", configLoader.isFilterEnabled());
                break;
                
            case "events":
                config.put("joinEnabled", configLoader.isJoinEnabled());
                config.put("joinCooldown", configLoader.getJoinCooldown());
                config.put("quitEnabled", configLoader.isQuitEnabled());
                config.put("quitCooldown", configLoader.getQuitCooldown());
                config.put("damageEnabled", configLoader.isDamageEnabled());
                config.put("damageCooldown", configLoader.getDamageCooldown());
                config.put("damageThreshold", configLoader.getDamageThreshold());
                break;
                
            default:
                sendErrorResponse(response, 404, "配置分类不存在: " + category);
                return;
        }
        
        sendJsonResponse(response, config);
    }
    
    /**
     * 更新配置
     */
    private void handleUpdateConfig(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        // 读取请求体
        String body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
        plugin.debug("[WebConfig] 收到配置保存请求: " + body);
        
        try {
            JsonElement jsonElement = JsonParser.parseString(body);
            
            // 验证和应用配置
            Map<String, String> errors = new HashMap<>();
            int updatedCount = 0;
            int totalCount = 0;
            
            // 🔧 修复：支持两种格式 - JSON对象和JSON数组
            if (jsonElement.isJsonObject()) {
                // 原有的对象格式：{"key1": "value1", "key2": "value2"}
                JsonObject json = jsonElement.getAsJsonObject();
                totalCount = json.keySet().size();
            
            for (String key : json.keySet()) {
                JsonElement value = json.get(key);
                try {
                    if (updateConfigValue(key, value)) {
                        updatedCount++;
                        plugin.debug("[WebConfig] 配置项更新成功: " + key + " = " + value);
                        } else {
                            plugin.getLogger().warning("[WebConfig] 配置项未处理: " + key);
                    }
                } catch (Exception e) {
                    errors.put(key, e.getMessage());
                    plugin.getLogger().warning("[WebConfig] 配置项更新失败: " + key + ", 错误: " + e.getMessage());
                }
                }
            } else if (jsonElement.isJsonArray()) {
                // 新的数组格式：[{"key": "key1", "value": "value1"}, {"key": "key2", "value": "value2"}]
                JsonArray jsonArray = jsonElement.getAsJsonArray();
                totalCount = jsonArray.size();
                
                for (JsonElement element : jsonArray) {
                    if (element.isJsonObject()) {
                        JsonObject configItem = element.getAsJsonObject();
                        if (configItem.has("key") && configItem.has("value")) {
                            String key = configItem.get("key").getAsString();
                            JsonElement value = configItem.get("value");
                            
                            try {
                                if (updateConfigValue(key, value)) {
                                    updatedCount++;
                                    plugin.debug("[WebConfig] 配置项更新成功: " + key + " = " + value);
                                } else {
                                    plugin.getLogger().warning("[WebConfig] 配置项未处理: " + key);
                                }
                            } catch (Exception e) {
                                errors.put(key, e.getMessage());
                                plugin.getLogger().warning("[WebConfig] 配置项更新失败: " + key + ", 错误: " + e.getMessage());
                            }
                        } else {
                            errors.put("格式错误", "数组元素必须包含key和value字段");
                            plugin.getLogger().warning("[WebConfig] 数组元素格式错误: " + element);
                        }
                    } else {
                        errors.put("格式错误", "数组元素必须是JSON对象");
                        plugin.getLogger().warning("[WebConfig] 无效的数组元素: " + element);
                    }
                }
            } else {
                throw new IllegalArgumentException("请求体必须是JSON对象或JSON数组");
            }
            
            // 保存配置
            if (updatedCount > 0) {
                try {
                configLoader.saveConfig(); // 统一保存
                configLoader.reloadConfig(); // 重新加载以确保缓存和运行时一致
                plugin.getLogger().info("配置文件已保存并重新加载，更新了 " + updatedCount + " 项配置");
                } catch (Exception e) {
                    plugin.getLogger().severe("保存配置文件失败: " + e.getMessage());
                    sendErrorResponse(response, 500, "配置保存失败: " + e.getMessage());
                    return;
                }
            }
            
            // 🔧 修复：改进成功判断逻辑
            boolean isFullSuccess = errors.isEmpty();
            boolean hasPartialSuccess = updatedCount > 0;
            
            // 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("success", isFullSuccess || hasPartialSuccess); // 只要有成功的就算成功
            result.put("updatedCount", updatedCount);
            result.put("totalCount", totalCount);
            result.put("errors", errors);
            
            // 🔧 修复：改进消息内容
            String message;
            if (isFullSuccess) {
                message = "所有配置更新成功，共更新 " + updatedCount + " 项";
            } else if (hasPartialSuccess) {
                message = String.format("部分配置更新成功，成功 %d 项，失败 %d 项", 
                    updatedCount, errors.size());
            } else {
                message = "配置更新失败，没有任何配置项被更新";
            }
            result.put("message", message);
            
            // 🔧 修复：根据结果设置HTTP状态码
            if (!hasPartialSuccess) {
                response.setStatus(400); // 完全失败时返回400
            }
            
            sendJsonResponse(response, result);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[WebConfig] 更新配置时发生严重错误", e);
            sendErrorResponse(response, 400, "无效的JSON格式: " + e.getMessage());
        }
    }
    
    /**
     * 验证配置
     */
    private void handleValidateConfig(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = request.getReader().readLine()) != null) {
            body.append(line);
        }
        
        try {
            JsonObject json = JsonParser.parseString(body.toString()).getAsJsonObject();
            Map<String, Object> validationResult = validateConfiguration(json);
            sendJsonResponse(response, validationResult);
            
        } catch (Exception e) {
            sendErrorResponse(response, 400, "无效的JSON格式: " + e.getMessage());
        }
    }
    
    /**
     * 重置配置
     */
    private void handleResetConfig(HttpServletResponse response) throws IOException {
        try {
            // 创建默认配置的备份
            plugin.saveDefaultConfig();
            configLoader.reloadConfig();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "配置已重置为默认值");
            
            sendJsonResponse(response, result);
            
        } catch (Exception e) {
            sendErrorResponse(response, 500, "重置配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 重载配置
     */
    private void handleReloadConfig(HttpServletResponse response) throws IOException {
        try {
            configLoader.reloadConfig();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "配置已重新加载");
            
            sendJsonResponse(response, result);
            
        } catch (Exception e) {
            sendErrorResponse(response, 500, "重载配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新单个配置值
     */
    private boolean updateConfigValue(String key, JsonElement value) {
        BiConsumer<JsonElement, ConfigLoader> updater = configUpdaters.get(key);
        if (updater != null) {
            updater.accept(value, configLoader);
            return true;
        }

        // 如果未显式注册，则尝试直接写入配置路径，保持最大兼容性
        try {
            if (value.isJsonPrimitive()) {
                if (value.getAsJsonPrimitive().isBoolean()) {
                    configLoader.set(key, value.getAsBoolean());
                } else if (value.getAsJsonPrimitive().isNumber()) {
                    // 默认使用Double，然后ConfigLoader内部会正确序列化数字
                    configLoader.set(key, value.getAsDouble());
                } else if (value.getAsJsonPrimitive().isString()) {
                    configLoader.set(key, value.getAsString());
                }
            } else {
                // 复杂对象直接序列化为字符串
                configLoader.set(key, value.toString());
            }
            plugin.debug("通过通用路径保存配置: " + key + "=" + value);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("保存未注册配置项失败: " + key + ", 错误: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 验证配置值
     */
    private Map<String, Object> validateConfiguration(JsonObject config) {
        Map<String, Object> result = new HashMap<>();
        Map<String, String> errors = new HashMap<>();
        Map<String, String> warnings = new HashMap<>();
        
        // 验证API密钥
        if (config.has("ai.apiKey")) {
            String apiKey = config.get("ai.apiKey").getAsString();
            if (apiKey.length() < 10) {
                errors.put("ai.apiKey", "API密钥长度不能少于10个字符");
            }
        }
        
        // 验证温度参数
        if (config.has("ai.temperature")) {
            double temperature = config.get("ai.temperature").getAsDouble();
            if (temperature < 0 || temperature > 2) {
                errors.put("ai.temperature", "温度参数必须在0-2之间");
            } else if (temperature > 1.5) {
                warnings.put("ai.temperature", "温度参数过高可能导致回复不稳定");
            }
        }
        
        // 验证TPS阈值
        if (config.has("performance.tpsThresholdFull")) {
            double threshold = config.get("performance.tpsThresholdFull").getAsDouble();
            if (threshold < 10 || threshold > 20) {
                warnings.put("performance.tpsThresholdFull", "TPS阈值建议设置在10-20之间");
            }
        }
        
        result.put("valid", errors.isEmpty());
        result.put("errors", errors);
        result.put("warnings", warnings);
        
        return result;
    }
    
    /**
     * 掩码API密钥
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "未设置";
        }
        
        // 如果密钥长度小于8，显示长度信息而不是固定的****
        if (apiKey.length() < 8) {
            return "****(" + apiKey.length() + "位)";
        }
        
        // 正常情况：显示前4个*号和后4位
        return "****" + apiKey.substring(apiKey.length() - 4);
    }
    
    /**
     * 设置JSON响应头
     */
    private void setJsonHeaders(HttpServletResponse response) {
        response.setContentType("application/json; charset=utf-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
    
    /**
     * 发送JSON响应
     */
    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        PrintWriter writer = response.getWriter();
        writer.print(gson.toJson(data));
        writer.flush();
    }
    
    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        error.put("timestamp", System.currentTimeMillis());
        
        PrintWriter writer = response.getWriter();
        writer.print(gson.toJson(error));
        writer.flush();
    }
} 