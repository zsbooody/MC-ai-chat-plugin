package com.example.aichatplugin.web;

import com.example.aichatplugin.AIChatPlugin;
import com.example.aichatplugin.web.controllers.*;
import com.example.aichatplugin.web.websocket.ConfigWebSocketHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.server.Handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/**
 * AI Chat Plugin 嵌入式Web服务器
 * 
 * 功能：
 * 1. 提供配置管理界面
 * 2. RESTful API接口
 * 3. WebSocket实时通信
 * 4. 静态资源服务
 */
public class WebServer {
    
    private final AIChatPlugin plugin;
    private Server server;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    // 配置参数
    private final int DEFAULT_PORT = 28080;
    private final int MAX_PORT_ATTEMPTS = 10;
    private final String CONTEXT_PATH = "/";
    
    // Web控制器
    private ConfigController configController;
    private StatusController statusController;
    private BenchmarkController benchmarkController;
    
    private String accessToken;
    private boolean tokenRequired = true;
    
    public WebServer(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.configController = new ConfigController(plugin);
        this.statusController = new StatusController(plugin);
        this.benchmarkController = new BenchmarkController(plugin);
        generateAccessToken();
    }
    
    /**
     * 生成访问令牌
     */
    private void generateAccessToken() {
        this.accessToken = UUID.randomUUID().toString();
        plugin.getLogger().info("=====================================");
        plugin.getLogger().info("Web界面访问令牌 (Access Token):");
        plugin.getLogger().info(accessToken);
        plugin.getLogger().info("请在Web界面中输入此令牌以访问配置");
        plugin.getLogger().info("=====================================");
    }
    
    /**
     * 获取访问令牌
     */
    public String getAccessToken() {
        return accessToken;
    }
    
    /**
     * 验证请求令牌
     */
    public boolean validateToken(String token) {
        return accessToken != null && accessToken.equals(token);
    }
    
    /**
     * 是否需要令牌验证
     */
    public boolean isTokenRequired() {
        return tokenRequired;
    }
    
    /**
     * 启动Web服务器
     */
    public boolean start() {
        if (isRunning.get()) {
            plugin.getLogger().warning("Web服务器已在运行中");
            return true;
        }
        
        int port = findAvailablePort();
        if (port == -1) {
            plugin.getLogger().severe("无法找到可用端口启动Web服务器");
            return false;
        }
        
        try {
            // 创建Jetty服务器
            server = new Server(port);
            
            // 创建上下文处理器
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath(CONTEXT_PATH);
            
            // 配置静态文件服务 (使用DefaultServlet)
            ClassLoader classLoader = WebServer.class.getClassLoader();
            URL resourceUrl = classLoader.getResource("web/");
            if (resourceUrl == null) {
                throw new RuntimeException("无法找到web资源目录！");
            }
            ServletHolder staticHolder = new ServletHolder("static", DefaultServlet.class);
            staticHolder.setInitParameter("resourceBase", Resource.newResource(resourceUrl.toExternalForm()).toString());
            staticHolder.setInitParameter("dirAllowed", "false");
            staticHolder.setInitParameter("welcomeServlets", "true");
            staticHolder.setInitParameter("welcomeFiles", "index.html");
            context.addServlet(staticHolder, "/");
            
            // 配置WebSocket
            configureWebSocket(context);
            
            // 添加认证Servlet
            context.addServlet(new ServletHolder(new AuthServlet()), "/api/auth/*");
            
            // 注册API Servlet（现在需要认证）
            context.addServlet(new ServletHolder(new ApiServlet(plugin, configController, statusController, benchmarkController)), "/api/*");
            
            // 将处理器添加到列表中
            HandlerList handlers = new HandlerList();
            // 统一使用同一个context，HandlerList 仅包含一个元素
            handlers.setHandlers(new Handler[] { context });
            
            server.setHandler(handlers);
            
            // 启动服务器
            server.start();
            
            isRunning.set(true);
            plugin.getLogger().info("Web管理界面已启动: http://localhost:" + port);
            plugin.getLogger().info("请在浏览器中访问上述地址进行配置管理");
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "启动Web服务器失败", e);
            return false;
        }
    }
    
    /**
     * 停止Web服务器
     */
    public void stop() {
        if (!isRunning.get() || server == null) {
            return;
        }
        
        try {
            plugin.getLogger().info("正在关闭Web服务器...");
            server.stop();
            server.destroy();
            isRunning.set(false);
            plugin.getLogger().info("Web服务器已关闭");
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "关闭Web服务器时发生错误", e);
        }
    }
    
    /**
     * 查找可用端口
     */
    private int findAvailablePort() {
        int configPort = plugin.getConfigLoader().getInt("web.port", DEFAULT_PORT);
        
        // 尝试配置的端口
        if (isPortAvailable(configPort)) {
            return configPort;
        }
        
        // 尝试默认端口范围
        for (int i = 0; i < MAX_PORT_ATTEMPTS; i++) {
            int port = DEFAULT_PORT + i;
            if (isPortAvailable(port)) {
                plugin.getLogger().info("端口 " + configPort + " 不可用，使用端口 " + port);
                return port;
            }
        }
        
        return -1;
    }
    
    /**
     * 检查端口是否可用
     */
    private boolean isPortAvailable(int port) {
        try {
            Server testServer = new Server(port);
            testServer.start();
            testServer.stop();
            return true;
        } catch (BindException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 配置WebSocket
     */
    private void configureWebSocket(ServletContextHandler context) {
        // 配置WebSocket容器
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
            wsContainer.setMaxTextMessageSize(65536);
            wsContainer.setMaxBinaryMessageSize(65536);
            wsContainer.setIdleTimeout(java.time.Duration.ofMinutes(10));
            
            // 添加WebSocket端点
            wsContainer.addMapping("/ws/config", ConfigWebSocketHandler.class);
        });
        
        plugin.debug("WebSocket已配置");
    }
    
    /**
     * 认证Servlet - 处理登录和令牌验证
     */
    private class AuthServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) 
                throws ServletException, IOException {
            
            String pathInfo = request.getPathInfo();
            response.setContentType("application/json; charset=utf-8");
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
            
            if ("/login".equals(pathInfo)) {
                // 处理登录请求
                String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (body.contains(accessToken)) {
                    response.getWriter().write("{\"success\": true, \"token\": \"" + accessToken + "\"}");
                } else {
                    response.setStatus(401);
                    response.getWriter().write("{\"success\": false, \"message\": \"无效的访问令牌\"}");
                }
            } else if ("/verify".equals(pathInfo)) {
                // 验证令牌
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ") && 
                    validateToken(authHeader.substring(7))) {
                    response.getWriter().write("{\"valid\": true}");
                } else {
                    response.setStatus(401);
                    response.getWriter().write("{\"valid\": false}");
                }
            }
        }
        
        @Override
        protected void doOptions(HttpServletRequest request, HttpServletResponse response) 
                throws ServletException, IOException {
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.setStatus(200);
        }
    }
    
    /**
     * 静态资源处理器
     */
    private static class StaticResourceServlet extends HttpServlet {
        
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) 
                throws ServletException, IOException {
            
            String path = request.getPathInfo();
            if (path == null || path.equals("/")) {
                path = "/index.html";
            }
            
            // 防止路径遍历攻击
            if (path.contains("..") || path.contains("//")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            
            // 从类路径加载静态资源
            String resourcePath = "/web" + path;
            InputStream inputStream = getClass().getResourceAsStream(resourcePath);
            
            if (inputStream == null) {
                // 如果找不到具体文件，尝试返回index.html（支持SPA路由）
                if (!path.equals("/index.html")) {
                    inputStream = getClass().getResourceAsStream("/web/index.html");
                }
                
                if (inputStream == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            }
            
            // 设置Content-Type
            String contentType = getContentType(path);
            if (contentType != null) {
                response.setContentType(contentType);
            }
            
            // 设置缓存头
            if (path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".png") || 
                path.endsWith(".jpg") || path.endsWith(".gif") || path.endsWith(".ico")) {
                response.setHeader("Cache-Control", "public, max-age=3600");
            } else {
                response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            }
            
            // 输出文件内容
            try {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    response.getOutputStream().write(buffer, 0, bytesRead);
                }
            } finally {
                inputStream.close();
            }
        }
        
        /**
         * 根据文件扩展名获取Content-Type
         */
        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html; charset=utf-8";
            if (path.endsWith(".css")) return "text/css; charset=utf-8";
            if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (path.endsWith(".json")) return "application/json; charset=utf-8";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
            if (path.endsWith(".gif")) return "image/gif";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".ico")) return "image/x-icon";
            return "text/plain; charset=utf-8";
        }
    }
    
    // Getter方法
    public boolean isRunning() {
        return isRunning.get();
    }
    
    public int getPort() {
        if (server != null && server.getConnectors().length > 0) {
            return ((ServerConnector) server.getConnectors()[0]).getLocalPort();
        }
        return -1;
    }
    
    public String getUrl() {
        if (isRunning()) {
            return "http://localhost:" + getPort();
        }
        return null;
    }
} 