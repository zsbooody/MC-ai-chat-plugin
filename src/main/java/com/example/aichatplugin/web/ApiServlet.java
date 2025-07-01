package com.example.aichatplugin.web;

import com.example.aichatplugin.AIChatPlugin;
import com.example.aichatplugin.web.controllers.*;
import com.google.gson.Gson;
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
import java.util.stream.Collectors;

/**
 * 统一API路由处理器
 * 
 * 路由分发：
 * /api/config/* -> ConfigController
 * /api/status/* -> StatusController  
 * /api/benchmark/* -> BenchmarkController
 * /api/actions/* -> ActionsController (新增)
 */
public class ApiServlet extends HttpServlet {
    
    private final AIChatPlugin plugin;
    private final ConfigController configController;
    private final StatusController statusController;
    private final BenchmarkController benchmarkController;
    private final ActionsController actionsController;
    private final Gson gson = new Gson();
    
    public ApiServlet(AIChatPlugin plugin, ConfigController configController, 
                     StatusController statusController, BenchmarkController benchmarkController) {
        this.plugin = plugin;
        this.configController = configController;
        this.statusController = statusController;
        this.benchmarkController = benchmarkController;
        this.actionsController = new ActionsController(plugin);
    }
    
    /**
     * 验证请求的访问令牌
     */
    private boolean validateRequest(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        // 获取请求路径
        String pathInfo = request.getPathInfo();
        
        // 登录和状态查询端点是公开的
        if (pathInfo != null && (pathInfo.startsWith("/status") || pathInfo.startsWith("/auth/"))) {
            return true;
        }
        
        // 检查是否启用了令牌验证
        if (plugin.getWebServer() != null && plugin.getWebServer().isTokenRequired()) {
            String authHeader = request.getHeader("Authorization");
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendUnauthorizedResponse(response, "缺少认证令牌");
                return false;
            }
            
            String token = authHeader.substring(7);
            if (!plugin.getWebServer().validateToken(token)) {
                sendUnauthorizedResponse(response, "无效的认证令牌");
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // 验证访问令牌
        if (!validateRequest(request, response)) {
            return;
        }
        
        String pathInfo = request.getPathInfo();
        if (pathInfo == null) {
            sendErrorResponse(response, 404, "API路径不能为空");
            return;
        }
        
        try {
            if (pathInfo.startsWith("/config")) {
                String subPath = pathInfo.substring(7); // 移除 "/config"
                request.setAttribute("subPath", subPath);
                configController.doGet(request, response);
                
            } else if (pathInfo.startsWith("/status")) {
                String subPath = pathInfo.substring(7); // 移除 "/status"
                request.setAttribute("subPath", subPath);
                statusController.doGet(request, response);
                
            } else if (pathInfo.startsWith("/benchmark")) {
                String subPath = pathInfo.substring(10); // 移除 "/benchmark"
                request.setAttribute("subPath", subPath);
                benchmarkController.doGet(request, response);
                
            } else if (pathInfo.startsWith("/auth/")) {
                // 专门处理认证请求，不需要额外处理
                sendErrorResponse(response, 404, "未知的认证API路径: " + pathInfo);
            } else {
                sendErrorResponse(response, 404, "未知的API路径: " + pathInfo);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "API GET请求处理异常: " + pathInfo, e);
            sendErrorResponse(response, 500, "服务器内部错误");
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // 验证访问令牌
        if (!validateRequest(request, response)) {
            return;
        }
        
        String pathInfo = request.getPathInfo();
        if (pathInfo == null) {
            sendErrorResponse(response, 404, "API路径不能为空");
            return;
        }
        
        try {
            if (pathInfo.startsWith("/config")) {
                String subPath = pathInfo.substring(7);
                request.setAttribute("subPath", subPath);
                configController.doPost(request, response);
                
            } else if (pathInfo.startsWith("/benchmark")) {
                String subPath = pathInfo.substring(10);
                request.setAttribute("subPath", subPath);
                benchmarkController.doPost(request, response);
                
            } else if (pathInfo.startsWith("/actions")) {
                String subPath = pathInfo.substring(8); // 移除 "/actions"
                request.setAttribute("subPath", subPath);
                actionsController.doPost(request, response);
            
            } else if (pathInfo.startsWith("/auth/")) {
                // AuthController应该在这里处理
                // 由于没有AuthController，我们暂时返回一个错误
                sendErrorResponse(response, 404, "认证API不存在");

            } else {
                sendErrorResponse(response, 404, "未知的API路径: " + pathInfo);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "API POST请求处理异常: " + pathInfo, e);
            sendErrorResponse(response, 500, "服务器内部错误");
        }
    }
    
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        // 处理CORS预检请求
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(200);
    }
    
    /**
     * 发送JSON响应
     */
    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        response.setContentType("application/json; charset=utf-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        
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
    
    /**
     * 发送未授权响应
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401);
        response.setHeader("WWW-Authenticate", "Bearer realm=\"AI Chat Plugin\"");
        
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        error.put("code", "UNAUTHORIZED");
        
        sendJsonResponse(response, error);
    }
} 