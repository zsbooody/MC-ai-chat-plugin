# AI Chat Plugin Web API 文档

版本：v1.1.0618-enhanced  
最后更新：2025-06-20

## 📋 **概述**

AI Chat Plugin Web管理界面提供了RESTful API和WebSocket实时通信，让用户能够通过浏览器界面管理插件配置、监控系统状态、运行性能测试等。

## 🌐 **访问地址**

- **默认地址**: `http://localhost:28080`
- **配置地址**: 可在`config.yml`中修改`web.port`参数
- **CORS支持**: 已启用，支持跨域访问

## 🔐 **身份验证**

当前版本支持三种认证方式：
- `minecraft`: Minecraft玩家验证（推荐）
- `token`: 固定令牌验证
- `none`: 无验证（仅限测试环境）

在`config.yml`中配置：
```yaml
web:
  auth:
    enabled: true
    method: "minecraft"
```

## 📊 **API端点列表**

### 1. 配置管理 API

#### GET `/api/config`
获取完整的插件配置信息

**响应示例**:
```json
{
  "basic": {
    "debugEnabled": false,
    "chatEnabled": true,
    "chatPrefix": "",
    "broadcastEnabled": true
  },
  "ai": {
    "apiKey": "****ek23",
    "apiUrl": "https://api.deepseek.com/chat/completions",
    "model": "deepseek-chat",
    "temperature": 0.7,
    "maxTokens": 200
  },
  "performance": {
    "autoOptimizeEnabled": true
  }
}
```

#### GET `/api/config/categories`
获取配置分类信息

**响应示例**:
```json
{
  "basic": {
    "name": "基础设置",
    "icon": "🔧",
    "description": "插件的基本配置选项"
  },
  "ai": {
    "name": "AI配置",
    "icon": "🤖",
    "description": "AI模型和API相关设置"
  },
  "performance": {
    "name": "性能优化",
    "icon": "⚡",
    "description": "性能监控和优化设置"
  }
}
```

#### POST `/api/config/reload`
重载插件配置

**响应示例**:
```json
{
  "success": true,
  "message": "配置重载成功"
}
```

### 2. 状态监控 API

#### GET `/api/status`
获取插件总体状态

**响应示例**:
```json
{
  "version": "1.1.0618",
  "initialized": true,
  "debugMode": false,
  "uptime": 1671542400000,
  "currentTPS": 19.8,
  "operationMode": "FULL"
}
```

#### GET `/api/status/performance`
获取性能状态信息

**响应示例**:
```json
{
  "currentTPS": 19.8,
  "mode": "FULL",
  "autoOptimizeEnabled": true,
  "freeMemory": 2.5,
  "systemFreeMemory": 4.2,
  "availableCores": 8
}
```

#### GET `/api/status/system`
获取系统信息

**响应示例**:
```json
{
  "javaVersion": "17.0.2",
  "javaVendor": "Eclipse Adoptium",
  "osName": "Windows 10",
  "osVersion": "10.0",
  "availableProcessors": 8,
  "maxMemory": 8589934592,
  "totalMemory": 4294967296,
  "freeMemory": 2147483648,
  "serverVersion": "git-Spigot-4ac545c-e02cbb1 (MC: 1.20.1)",
  "bukkitVersion": "1.20.1-R0.1-SNAPSHOT",
  "onlinePlayers": 3,
  "maxPlayers": 20
}
```

### 3. 基准测试 API

#### GET `/api/benchmark/status`
获取基准测试状态

**响应示例**:
```json
{
  "isRunning": false,
  "lastResult": null
}
```

#### POST `/api/benchmark/run`
启动基准测试（开发中）

**响应示例**:
```json
{
  "success": false,
  "message": "基准测试功能开发中"
}
```

## 🔄 **错误响应格式**

所有API在出错时会返回统一的错误格式：

```json
{
  "success": false,
  "error": "错误描述信息",
  "timestamp": 1671542400000
}
```

### 常见状态码

- `200`: 请求成功
- `400`: 请求参数错误
- `404`: API端点不存在
- `500`: 服务器内部错误

## 📡 **WebSocket 实时通信**

### 连接地址
```
ws://localhost:28080/ws/config
```

### 消息格式
```json
{
  "type": "configUpdate",
  "key": "配置键名",
  "value": "配置值",
  "timestamp": 1671542400000
}
```

### 支持的消息类型
- `configUpdate`: 配置更新通知
- `statusUpdate`: 状态更新通知
- `performanceUpdate`: 性能数据更新

## 🛡️ **安全特性**

### CORS配置
```yaml
web:
  security:
    cors-enabled: true
    allowed-origins: ["*"]
```

### 速率限制
```yaml
web:
  security:
    rate-limit: 100  # 每分钟最大请求数
```

### 访问日志
```yaml
web:
  security:
    access-log: true
```

## 🔧 **配置示例**

### 完整的Web配置
```yaml
web:
  enabled: true
  port: 28080
  host: "0.0.0.0"
  
  auth:
    enabled: true
    method: "minecraft"
    session-timeout: 3600
    admin-permission: "aichat.web.admin"
  
  security:
    cors-enabled: true
    allowed-origins: ["*"]
    rate-limit: 100
    access-log: true
  
  features:
    config-management: true
    performance-monitoring: true
    benchmark-testing: true
    real-time-updates: true
    system-info: true
  
  ui:
    theme: "auto"
    language: "zh-CN"
    refresh-interval: 30
    show-advanced: false
```

## 🚀 **使用示例**

### JavaScript 示例

```javascript
// 获取配置信息
async function getConfig() {
    const response = await fetch('/api/config');
    const config = await response.json();
    console.log('当前配置:', config);
}

// 重载配置
async function reloadConfig() {
    const response = await fetch('/api/config/reload', {
        method: 'POST'
    });
    const result = await response.json();
    if (result.success) {
        console.log('配置重载成功');
    }
}

// WebSocket连接
const ws = new WebSocket('ws://localhost:28080/ws/config');
ws.onmessage = function(event) {
    const data = JSON.parse(event.data);
    console.log('收到实时更新:', data);
};
```

### cURL 示例

```bash
# 获取配置
curl http://localhost:28080/api/config

# 获取状态
curl http://localhost:28080/api/status

# 重载配置
curl -X POST http://localhost:28080/api/config/reload
```

## 🔮 **计划中的功能**

### v1.2.0 预计功能
- [ ] 配置修改API（POST /api/config）
- [ ] 配置验证API（POST /api/config/validate）
- [ ] 完整的基准测试功能
- [ ] 用户管理界面
- [ ] 插件管理功能

### v1.3.0 预计功能
- [ ] 图表数据API
- [ ] 历史数据查询
- [ ] 导出/导入配置
- [ ] 多语言支持

## 📞 **技术支持**

如有问题或建议，请提交Issue或联系开发团队：

- GitHub: [项目地址]
- 文档: [在线文档]
- 支持: [技术支持]

---

**注意**: 该API文档会随着版本更新而持续改进，请关注版本变更日志。 