package com.example.aichatplugin;

/**
 * 聊天消息类
 * 
 * 职责：
 * 1. 存储单条聊天消息
 * 2. 记录消息元数据
 * 3. 支持序列化
 */
public class Message {
    private final String sender;
    private final String content;
    private final boolean isAI;
    private final long timestamp;
    
    public Message(String sender, String content, boolean isAI) {
        this.sender = sender;
        this.content = content;
        this.isAI = isAI;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getSender() {
        return sender;
    }
    
    public String getContent() {
        return content;
    }
    
    public boolean isAI() {
        return isAI;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s: %s", 
            isAI ? "AI" : "玩家",
            sender,
            content
        );
    }
} 