/**
 * DeepSeek AI服务实现
 * 
 * 职责：
 * 1. 实现与DeepSeek API的通信
 * 2. 管理API请求和响应
 * 3. 处理错误和重试
 * 4. 维护API连接状态
 * 
 * 主要功能：
 * 1. 发送聊天请求到DeepSeek API
 * 2. 处理API响应和错误
 * 3. 管理API密钥和URL
 * 4. 控制请求超时和重试
 * 
 * 配置项：
 * 1. API密钥和URL
 * 2. 模型参数（temperature, max_tokens等）
 * 3. 超时设置（连接、读取、写入）
 * 4. 系统提示词
 */

package com.example.aichatplugin;

import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import okhttp3.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.HashMap;

public class DeepSeekAIService {
    private final AIChatPlugin plugin;
    private final OkHttpClient client;
    private final Gson gson;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final String roleSystem;
    private final int maxTokens;
    private final double temperature;
    
    public DeepSeekAIService(AIChatPlugin plugin) {
        this.plugin = plugin;
        ConfigLoader config = plugin.getConfigLoader();
        
        this.apiKey = config.getApiKey();
        this.apiUrl = config.getApiUrl();
        this.model = config.getModel();
        this.roleSystem = config.getRoleSystem();
        this.maxTokens = config.getMaxTokens();
        this.temperature = config.getTemperature();
        
        this.client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
        
        plugin.debug("DeepSeekAIService初始化完成");
    }
    
    /**
     * 生成AI响应
     */
    public String generateResponse(String prompt, Player player) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("temperature", temperature);
            requestBody.addProperty("max_tokens", maxTokens);
            
            JsonArray messages = new JsonArray();
            
            // 添加系统角色消息
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", roleSystem);
            messages.add(systemMessage);
            
            // 添加历史消息
            List<ConversationManager.Message> history = plugin.getConversationManager().getConversationHistory(player.getUniqueId());
            for (ConversationManager.Message msg : history) {
                JsonObject historyMessage = new JsonObject();
                historyMessage.addProperty("role", msg.isAI ? "assistant" : "user");
                historyMessage.addProperty("content", msg.content);
                messages.add(historyMessage);
            }
            
            // 添加当前消息
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", prompt);
            messages.add(userMessage);
            
            requestBody.add("messages", messages);
            
            Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(
                    MediaType.parse("application/json"),
                    gson.toJson(requestBody)
                ))
                .build();
                
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "未知错误";
                    throw new RuntimeException("API请求失败: " + response.code() + " - " + errorBody);
                }
                
                return parseResponse(response.body().string());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "调用AI服务时发生错误", e);
            throw new RuntimeException("AI服务调用失败", e);
        }
    }
    
    /**
     * 解析API响应
     */
    private String parseResponse(String responseBody) {
        try {
            JsonObject response = gson.fromJson(responseBody, JsonObject.class);
            JsonArray choices = response.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                throw new RuntimeException("API响应格式错误: 没有choices字段");
            }
            
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");
            if (message == null) {
                throw new RuntimeException("API响应格式错误: 没有message字段");
            }
            
            String content = message.get("content").getAsString();
            if (content == null || content.trim().isEmpty()) {
                throw new RuntimeException("API响应格式错误: 内容为空");
            }
            
            return content.trim();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "解析API响应时发生错误", e);
            throw new RuntimeException("解析API响应失败", e);
        }
    }
    
    /**
     * 关闭服务
     */
    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        plugin.debug("DeepSeekAIService已关闭");
    }
}