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
import java.util.Random;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DeepSeekAIService {
    private final AIChatPlugin plugin;
    private final ConfigLoader configLoader;
    private final OkHttpClient client;
    private final Gson gson;
    private final Random random;
    private final Cache<String, String> responseCache;
    private final List<String> apiKeys;
    private final Map<UUID, String> playerKeyMap;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY = 1000; // 1 second

    /**
     * 🔧 修复：线程安全的LRU缓存实现
     */
    private static class Cache<K, V> {
        private final ConcurrentHashMap<K, CacheEntry<V>> cache;
        private final int maxSize;
        private final long ttl;
        private final Object cleanupLock = new Object();

        public Cache(int maxSize, long ttl) {
            this.cache = new ConcurrentHashMap<>();
            this.maxSize = maxSize;
            this.ttl = ttl;
        }

        public V get(K key) {
            CacheEntry<V> entry = cache.get(key);
            if (entry == null || entry.isExpired(ttl)) {
                if (entry != null) {
                    cache.remove(key);
                }
                return null;
            }
            return entry.value;
        }

        public void put(K key, V value) {
            cache.put(key, new CacheEntry<>(value));
            
            // 🔧 线程安全的大小控制
            if (cache.size() > maxSize) {
                synchronized (cleanupLock) {
                    if (cache.size() > maxSize) {
                        cleanupOldEntries();
                    }
                }
            }
        }
        
        // 🔧 新增：清理过期和多余的条目
        private void cleanupOldEntries() {
            long now = System.currentTimeMillis();
            // 首先移除过期的条目
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired(ttl));
            
            // 如果仍然超过大小限制，移除最老的条目
            if (cache.size() > maxSize) {
                cache.entrySet().stream()
                    .sorted((e1, e2) -> Long.compare(e1.getValue().timestamp, e2.getValue().timestamp))
                    .limit(cache.size() - maxSize)
                    .map(Map.Entry::getKey)
                    .forEach(cache::remove);
            }
        }

        private static class CacheEntry<V> {
            private final V value;
            private final long timestamp;

            public CacheEntry(V value) {
                this.value = value;
                this.timestamp = System.currentTimeMillis();
            }

            public boolean isExpired(long ttl) {
                return System.currentTimeMillis() - timestamp > ttl;
            }
        }
    }

    // 自定义异常类
    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }

    public static class ServerException extends RuntimeException {
        public ServerException(String message) {
            super(message);
        }
    }

    public static class ClientException extends RuntimeException {
        public ClientException(String message) {
            super(message);
        }
    }

    public DeepSeekAIService(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.configLoader = plugin.getConfigLoader();
        this.gson = new Gson();
        this.random = new Random();
        
        // 初始化API密钥轮换
        this.apiKeys = new ArrayList<>();
        this.apiKeys.add(configLoader.getApiKey());
        // 可以在这里添加更多API密钥
        
        // 初始化玩家-密钥映射
        this.playerKeyMap = new ConcurrentHashMap<>();
        
        // 配置OkHttpClient
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(50, 10, TimeUnit.MINUTES));
            
        if (configLoader.isNetworkLatencySimulationEnabled()) {
            builder.addInterceptor(chain -> {
                try {
                    int delay = random.nextInt(configLoader.getMaxLatency() - configLoader.getMinLatency() + 1) + configLoader.getMinLatency();
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return chain.proceed(chain.request());
            });
        }
        
        this.client = builder.build();
        
        // 初始化响应缓存
        if (configLoader.isApiResponseCachingEnabled()) {
            this.responseCache = new Cache<>(1000, 300000); // 1000条缓存，5分钟TTL
        } else {
            this.responseCache = null;
        }
        
        plugin.debug("DeepSeekAIService初始化完成");
    }
    
    /**
     * 获取玩家的API密钥
     */
    private String getPlayerApiKey(UUID playerId) {
        // 直接返回最新的API密钥，不使用缓存
        return configLoader.getApiKey();
    }

    /**
     * 生成安全的缓存键
     */
    private String generateCacheKey(UUID playerId, String prompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(prompt.getBytes(StandardCharsets.UTF_8));
            return playerId + "|" + Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // 降级到普通哈希
            return playerId + "|" + prompt.hashCode();
        }
    }

    /**
     * 生成AI响应
     */
    public String generateResponse(String prompt, Player player) {
        return internalGenerate(prompt, player, true);
    }

    /**
     * 内部生成方法，统一处理缓存和请求逻辑
     */
    private String internalGenerate(String prompt, Player player, boolean useCache) {
        // 错误注入测试
        if (configLoader.isErrorInjectionEnabled() && 
            random.nextInt(100) < configLoader.getErrorInjectionRate()) {
            throw new RuntimeException("模拟错误注入");
        }

        // 构建缓存键：玩家ID + 消息SHA-256哈希
        String cacheKey = generateCacheKey(player.getUniqueId(), prompt);
        
        // 检查缓存
        if (useCache && responseCache != null) {
            String cachedResponse = responseCache.get(cacheKey);
            if (cachedResponse != null) {
                return cachedResponse;
            }
        }

        // 指数退避重试
        int retry = 0;
        long delay = INITIAL_RETRY_DELAY;
        Exception lastException = null;

        while (retry < MAX_RETRIES) {
            try {
                String response = executeRequest(prompt, player);
                
                // 缓存响应
                if (useCache && responseCache != null) {
                    responseCache.put(cacheKey, response);
                }
                
                return response;
            } catch (RateLimitExceededException e) {
                lastException = e;
                try {
                    Thread.sleep(delay * (1 << retry)); // 指数退避
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试被中断", ie);
                }
                retry++;
            } catch (Exception e) {
                lastException = e;
                break;
            }
        }

        throw new RuntimeException("AI服务调用失败", lastException);
    }

    /**
     * 执行API请求
     */
    private String executeRequest(String prompt, Player player) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", configLoader.getModel());
        requestBody.addProperty("temperature", configLoader.getTemperature());
        requestBody.addProperty("max_tokens", configLoader.getMaxTokens());
        
        JsonArray messages = new JsonArray();
        
        // 添加系统角色消息
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", configLoader.getRoleSystem());
        messages.add(systemMessage);
        
        // 使用ConversationManager的缓存消息
        JsonArray historyMessages = plugin.getConversationManager().getCachedMessages(player.getUniqueId());
        if (historyMessages != null) {
            // 转换缓存的消息格式为API所需格式
            for (int i = 0; i < historyMessages.size(); i++) {
                JsonObject cachedMsg = historyMessages.get(i).getAsJsonObject();
                JsonObject apiMsg = new JsonObject();
                
                // 根据 isAI 字段确定角色
                boolean isAI = cachedMsg.get("isAI").getAsBoolean();
                apiMsg.addProperty("role", isAI ? "assistant" : "user");
                apiMsg.addProperty("content", cachedMsg.get("content").getAsString());
                
                messages.add(apiMsg);
            }
        } else {
            // 降级到实时构建
            List<com.example.aichatplugin.Message> history = plugin.getConversationManager().getConversationHistory(player.getUniqueId());
            for (com.example.aichatplugin.Message msg : history) {
                JsonObject historyMessage = new JsonObject();
                historyMessage.addProperty("role", msg.isAI() ? "assistant" : "user");
                historyMessage.addProperty("content", msg.getContent());
                messages.add(historyMessage);
            }
        }
        
        // 添加当前消息
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);
        
        requestBody.add("messages", messages);
        
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        Request request = new Request.Builder()
            .url(configLoader.getApiUrl())
            .addHeader("Authorization", "Bearer " + getPlayerApiKey(player.getUniqueId()))
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(gson.toJson(requestBody), JSON))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                int code = response.code();
                String errorBody = response.body() != null ? response.body().string() : "未知错误";
                
                if (code == 429) {
                    throw new RateLimitExceededException("API调用超限");
                } else if (code >= 500) {
                    throw new ServerException("API服务异常: " + errorBody);
                } else {
                    throw new ClientException("请求错误: " + code + " - " + errorBody);
                }
            }
            
            return parseResponse(response.body().string());
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
        try {
            // 关闭所有活动的连接
            client.dispatcher().cancelAll();
            
            // 关闭连接池
            client.connectionPool().evictAll();
            
            // 关闭线程池
            if (!client.dispatcher().executorService().isShutdown()) {
                client.dispatcher().executorService().shutdown();
                try {
                    if (!client.dispatcher().executorService().awaitTermination(5, TimeUnit.SECONDS)) {
                        client.dispatcher().executorService().shutdownNow();
                    }
                } catch (InterruptedException e) {
                    client.dispatcher().executorService().shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            
            // 关闭缓存
            if (client.cache() != null) {
                client.cache().close();
            }
            
            plugin.debug("DeepSeekAIService已关闭");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "关闭DeepSeekAIService时发生错误", e);
        }
    }

    public String getResponse(String prompt) throws IOException {
        // 检查是否启用了API模拟
        if (configLoader.isMockApiEnabled()) {
            return getMockResponse(prompt);
        }

        // 检查缓存
        if (responseCache != null) {
            String cachedResponse = responseCache.get(prompt);
            if (cachedResponse != null) {
                return cachedResponse;
            }
        }

        // 检查是否启用了错误注入
        if (configLoader.isErrorInjectionEnabled() && 
            random.nextInt(100) < configLoader.getErrorInjectionRate()) {
            throw new IOException("模拟的API错误");
        }

        // 构建请求
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", configLoader.getModel());
        
        JsonArray messages = new JsonArray();
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", configLoader.getRoleSystem());
        messages.add(systemMessage);
        
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);
        
        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", configLoader.getTemperature());
        requestBody.addProperty("max_tokens", configLoader.getMaxTokens());

        Request request = new Request.Builder()
            .url(configLoader.getApiUrl())
            .addHeader("Authorization", "Bearer " + configLoader.getApiKey())
            .post(RequestBody.create(
                MediaType.parse("application/json"),
                gson.toJson(requestBody)
            ))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API请求失败: " + response.code());
            }

            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            String content = jsonResponse.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();

            // 缓存响应
            if (responseCache != null) {
                responseCache.put(prompt, content);
            }

            return content;
        } catch (Exception e) {
            if (configLoader.isDetailedErrorStackEnabled()) {
                plugin.getLogger().log(Level.SEVERE, "API请求异常", e);
            } else {
                plugin.getLogger().severe("API请求异常: " + e.getMessage());
            }
            throw e;
        }
    }

    private String getMockResponse(String prompt) {
        try {
            Thread.sleep(configLoader.getMockDelay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "这是一个模拟的API响应，用于测试。\n原始提示: " + prompt;
    }
}