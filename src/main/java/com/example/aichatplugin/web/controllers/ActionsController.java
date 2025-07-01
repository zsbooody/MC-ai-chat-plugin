package com.example.aichatplugin.web.controllers;

import com.example.aichatplugin.AIChatPlugin;
import com.google.gson.Gson;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * 系统操作API控制器
 * 
 * 提供操作接口：
 * POST /api/actions/run-gc - 执行垃圾回收
 */
public class ActionsController extends HttpServlet {
    
    private final AIChatPlugin plugin;
    private final Gson gson = new Gson();
    
    public ActionsController(AIChatPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String subPath = (String) request.getAttribute("subPath");
        if (subPath == null) subPath = "";
        
        setJsonHeaders(response);
        
        try {
            if (subPath.equals("/run-gc") || subPath.equals("run-gc")) {
                handleRunGC(response);
            } else {
                sendErrorResponse(response, 404, "操作不存在: " + subPath);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "操作API处理异常", e);
            sendErrorResponse(response, 500, "服务器内部错误: " + e.getMessage());
        }
    }
    
    /**
     * 执行垃圾回收
     */
    private void handleRunGC(HttpServletResponse response) throws IOException {
        try {
            // 记录GC前内存
            Runtime runtime = Runtime.getRuntime();
            long beforeGC = runtime.totalMemory() - runtime.freeMemory();
            
            // 执行垃圾回收
            System.gc();
            Thread.sleep(1000); // 等待GC完成
            
            // 计算释放的内存
            long afterGC = runtime.totalMemory() - runtime.freeMemory();
            long freed = beforeGC - afterGC;
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "垃圾回收已执行");
            result.put("freedMemory", freed);
            result.put("freedMemoryMB", Math.max(0, freed / 1024 / 1024));
            
            plugin.getLogger().info("手动执行垃圾回收，释放内存: " + (freed / 1024 / 1024) + "MB");
            sendJsonResponse(response, result);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "执行垃圾回收失败", e);
            sendErrorResponse(response, 500, "执行垃圾回收失败: " + e.getMessage());
        }
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
        
        sendJsonResponse(response, error);
    }
} 