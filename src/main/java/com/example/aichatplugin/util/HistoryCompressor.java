package com.example.aichatplugin.util;

import com.example.aichatplugin.Message;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 历史记录压缩工具
 * 提供消息历史的压缩、解压缩和差异更新功能
 */
public class HistoryCompressor {
    private static final Gson gson = new Gson();
    private static final int VERSION = 1;
    private static final int HEADER_SIZE = 16; // 版本(4) + CRC32(4) + 数据长度(8)
    
    /**
     * 压缩消息列表
     * @param messages 要压缩的消息列表
     * @return 压缩后的字节数组
     */
    public static byte[] compress(List<Message> messages) throws IOException {
        // 转换为JSON
        JsonArray jsonArray = new JsonArray();
        for (Message msg : messages) {
            JsonObject jsonMsg = new JsonObject();
            jsonMsg.addProperty("sender", msg.getSender());
            jsonMsg.addProperty("content", msg.getContent());
            jsonMsg.addProperty("isAI", msg.isAI());
            jsonMsg.addProperty("timestamp", msg.getTimestamp());
            jsonArray.add(jsonMsg);
        }
        String json = gson.toJson(jsonArray);
        
        // 计算CRC32
        CRC32 crc = new CRC32();
        crc.update(json.getBytes(StandardCharsets.UTF_8));
        
        // 压缩数据
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(json.getBytes(StandardCharsets.UTF_8));
        }
        byte[] compressed = baos.toByteArray();
        
        // 构建最终数据包
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + compressed.length);
        buffer.putInt(VERSION);
        buffer.putInt((int) crc.getValue());
        buffer.putLong(compressed.length);
        buffer.put(compressed);
        
        return buffer.array();
    }
    
    /**
     * 解压缩消息列表
     * @param compressed 压缩的数据
     * @return 解压后的消息列表
     */
    public static List<Message> decompress(byte[] compressed) throws IOException {
        if (compressed == null || compressed.length < HEADER_SIZE) {
            return new ArrayList<>();
        }
        
        // 读取头部信息
        ByteBuffer buffer = ByteBuffer.wrap(compressed);
        int version = buffer.getInt();
        int storedCrc = buffer.getInt();
        long dataLength = buffer.getLong();
        
        // 验证版本
        if (version != VERSION) {
            throw new IOException("不支持的压缩版本: " + version);
        }
        
        // 读取压缩数据
        byte[] data = new byte[(int) dataLength];
        buffer.get(data);
        
        // 解压数据
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(data))) {
            byte[] buffer2 = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer2)) > 0) {
                baos.write(buffer2, 0, len);
            }
        }
        
        // 验证CRC32
        String json = baos.toString(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(json.getBytes(StandardCharsets.UTF_8));
        if ((int) crc.getValue() != storedCrc) {
            throw new IOException("数据校验失败");
        }
        
        // 解析JSON
        JsonArray jsonArray = gson.fromJson(json, JsonArray.class);
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject jsonMsg = jsonArray.get(i).getAsJsonObject();
            messages.add(new Message(
                jsonMsg.get("sender").getAsString(),
                jsonMsg.get("content").getAsString(),
                jsonMsg.get("isAI").getAsBoolean()
            ));
        }
        
        return messages;
    }
    
    /**
     * 增量压缩
     * 只压缩新增的消息，减少CPU和IO开销
     * @param newMessages 新的消息列表
     * @param baseCompressed 基础压缩数据
     * @return 增量压缩后的数据
     */
    public static byte[] compressDelta(List<Message> newMessages, byte[] baseCompressed) throws IOException {
        if (baseCompressed == null || baseCompressed.length == 0) {
            return compress(newMessages);
        }
        
        // 如果新消息列表为空，返回空数组
        if (newMessages == null || newMessages.isEmpty()) {
            return new byte[0];
        }
        
        // 直接压缩新的完整消息列表
        // 注意：这里应该压缩完整的消息列表，而不是只压缩差异
        // 因为我们需要保存完整的历史记录
        return compress(newMessages);
    }
    
    /**
     * 解压增量数据
     * @param deltaCompressed 增量压缩数据
     * @param baseCompressed 基础压缩数据
     * @return 合并后的消息列表
     */
    public static List<Message> decompressDelta(byte[] deltaCompressed, byte[] baseCompressed) throws IOException {
        List<Message> baseMessages = decompress(baseCompressed);
        List<Message> deltaMessages = decompress(deltaCompressed);
        
        // 合并消息，去重
        Set<String> contents = new HashSet<>();
        List<Message> result = new ArrayList<>();
        
        for (Message msg : baseMessages) {
            if (contents.add(msg.getContent())) {
                result.add(msg);
            }
        }
        
        for (Message msg : deltaMessages) {
            if (contents.add(msg.getContent())) {
                result.add(msg);
            }
        }
        
        return result;
    }
} 