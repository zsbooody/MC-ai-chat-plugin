package com.example.aichatplugin.web.websocket;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket处理器
 * 用于配置实时同步通信
 */
public class ConfigWebSocketHandler {
    
    private final Gson gson = new Gson();
    
    public ConfigWebSocketHandler() {
        // 简化版本的WebSocket处理器
    }
    
    /**
     * 发送配置更新消息
     */
    public void sendConfigUpdate(String configKey, Object value) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "configUpdate");
        message.put("key", configKey);
        message.put("value", value);
        message.put("timestamp", System.currentTimeMillis());
        
        // 这里应该发送到所有连接的客户端
        // 简化版本先只是创建消息结构
    }
} 