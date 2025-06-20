# 配置文档

AI Chat Plugin v1.1.0618 完整配置指南。

## 📁 配置文件位置

```
plugins/AIChatPlugin/
├── config.yml          # 主配置文件
├── history/            # 历史记录目录
└── logs/              # 日志目录
```

## ⚙️ 完整配置模板

```yaml
# AI Chat Plugin v1.1.0618 配置文件
# 配置说明: https://github.com/zsbooody/ai-chat-plugin/docs/CONFIG.md

# ====================
# 核心设置
# ====================
settings:
  # DeepSeek API配置
  api-key: ""                           # 必填：您的DeepSeek API密钥
  api-url: "https://api.deepseek.com"   # API服务地址
  model: "deepseek-chat"                # AI模型名称
  max-tokens: 200                       # 最大回复长度（建议200-500）
  temperature: 0.7                      # 创造性水平（0.1-1.0）
  timeout: 30                           # 请求超时时间（秒）
  
  # 系统角色设定
  role-system: "你是Minecraft智能助手。环境信息是帮你理解上下文的背景，不是要你播报的内容。像真人朋友一样自然对话：玩家问什么答什么，需要建议时给建议，聊天时就聊天。保持有用、自然、简洁。"

# ====================
# 聊天功能
# ====================
chat:
  enabled: true                         # 启用聊天功能
  prefix: ""                            # 聊天前缀（空字符串=监听所有聊天）
  cooldown: 3                           # 冷却时间（秒）
  max-length: 500                       # 最大消息长度
  
  # 广播设置
  broadcast:
    enabled: false                      # 启用AI回复广播
    format: "&7[AI] &f%message%"        # 广播格式
  
  # 响应设置
  respond-to:
    chat: true                          # 响应聊天消息
    damage: true                        # 响应伤害事件
    join: false                         # 响应玩家加入
    leave: false                        # 响应玩家离开
    death: true                         # 响应玩家死亡

# ====================
# 环境感知
# ====================
environment:
  enabled: true                         # 启用环境收集
  cache-time: 600                       # 缓存时间（秒）
  scan-radius: 8                        # 扫描半径（格）
  max-entities: 5                       # 最大实体数量
  
  # 环境信息类型
  collect:
    location: true                      # 收集位置信息
    weather: true                       # 收集天气信息
    time: true                          # 收集时间信息
    entities: true                      # 收集实体信息
    blocks: false                       # 收集方块信息（影响性能）

# ====================
# 性能优化
# ====================
performance:
  # 自动优化
  auto-optimize: true                   # 启用自动性能优化
  check-interval: 10                    # 检查间隔（秒）
  
  # TPS阈值
  tps_thresholds:
    full: 18.0                          # 全功能模式阈值
    lite: 15.0                          # 精简模式阈值
    basic: 10.0                         # 基础模式阈值
    # < 10.0 自动进入应急模式
  
  # 硬件阈值
  hardware:
    cpu-warning: 80.0                   # CPU警告阈值（%）
    memory-warning: 85.0                # 内存警告阈值（%）
    disk-warning: 90.0                  # 磁盘警告阈值（%）
    min-free-memory: 2.0                # 最少空闲内存（GB）
    min-free-disk: 5.0                  # 最少空闲磁盘（GB）
    min-available-cores: 2              # 最少可用CPU核心
  
  # 手动模式
  manual:
    enabled: false                      # 启用手动模式
    mode: "FULL"                        # 手动指定模式（FULL/LITE/BASIC/EMERGENCY）

# ====================
# 历史记录
# ====================
history:
  enabled: true                         # 启用历史记录
  max-messages: 50                      # 每个玩家最大消息数
  save-interval: 600                    # 保存间隔（秒，600=10分钟）
  message-timeout: 7200000              # 消息超时时间（毫秒，2小时）
  compress: true                        # 启用压缩
  auto-cleanup: true                    # 自动清理过期记录

# ====================
# VIP用户
# ====================
vip:
  enabled: true                         # 启用VIP功能
  users:                                # VIP用户UUID列表
    - "00000000-0000-0000-0000-000000000000"
  
  # VIP特权
  privileges:
    cooldown-reduction: 0.5             # 冷却时间减少50%
    max-tokens-bonus: 100               # 额外Token奖励
    priority-response: true             # 优先响应
    bypass-limits: true                 # 绕过部分限制

# ====================
# 调试和日志
# ====================
debug:
  enabled: false                        # 启用调试模式
  log-level: "INFO"                     # 日志级别（DEBUG/INFO/WARN/ERROR）
  log-ai-requests: false                # 记录AI请求
  log-environment: false                # 记录环境信息
  performance-logging: true             # 性能日志

# ====================
# 命令和权限
# ====================
commands:
  aliases:
    ai: true                            # 启用/ai别名
    aichat: true                        # 启用/aichat别名
  
  # 命令冷却时间
  cooldowns:
    ai: 3                               # /ai命令冷却（秒）
    reload: 5                           # /aichat reload冷却（秒）
    clear: 1                            # /aichat clear冷却（秒）

# ====================
# 消息格式
# ====================
messages:
  # 系统消息
  system:
    reload: "&a配置已重新加载"
    no-permission: "&c您没有权限执行此命令"
    cooldown: "&c请等待 %time% 秒后再试"
    
  # AI响应格式
  ai:
    prefix: "&7[AI] &f"                 # AI回复前缀
    error: "&c抱歉，我暂时无法回应"        # 错误消息
    thinking: "&e正在思考中..."          # 思考消息
  
  # 帮助信息
  help:
    header: "&6=== AI Chat Plugin 帮助 ==="
    basic: "&e/ai <消息> &7- 与AI对话\n&e/aichat help &7- 显示帮助\n&e/aichat clear &7- 清空历史记录"
    admin: "&e/aichat reload &7- 重载配置\n&e/aichat status &7- 查看状态\n&e/aichat debug &7- 切换调试"
    footer: "&7输入 /aichat help 查看更多信息"

# ====================
# 高级设置
# ====================
advanced:
  # 多线程设置
  threading:
    core-pool-size: 2                   # 核心线程数
    max-pool-size: 10                   # 最大线程数
    keep-alive-time: 60                 # 线程存活时间（秒）
  
  # 网络设置
  network:
    connect-timeout: 10                 # 连接超时（秒）
    read-timeout: 30                    # 读取超时（秒）
    retry-attempts: 3                   # 重试次数
    retry-delay: 1000                   # 重试延迟（毫秒）
  
  # 缓存设置
  cache:
    environment-cache-size: 100         # 环境信息缓存大小
    response-cache-size: 50             # 响应缓存大小
    cache-ttl: 600                      # 缓存生存时间（秒）
```

## 🔧 配置详解

### 🤖 AI设置

#### API密钥获取
1. 访问 [DeepSeek官网](https://www.deepseek.com)
2. 注册账号并获取API密钥
3. 将密钥填入 `settings.api-key`

#### 模型选择
- **deepseek-chat**: 通用对话模型（推荐）
- **deepseek-coder**: 代码专用模型
- **deepseek-v2**: 高级版本模型

#### 参数调优
```yaml
settings:
  max-tokens: 200      # 一般对话：150-300，详细回复：300-500
  temperature: 0.7     # 创造性：0.3-0.5，平衡：0.6-0.8，随机：0.8-1.0
```

### 💬 聊天配置

#### 前缀设置
```yaml
chat:
  prefix: ""           # 监听所有聊天
  prefix: "!"          # 只监听!开头的消息
  prefix: "@ai"        # 只监听@ai开头的消息
```

#### 冷却时间
```yaml
chat:
  cooldown: 3          # 标准用户3秒
  
vip:
  privileges:
    cooldown-reduction: 0.5  # VIP用户1.5秒
```

### 🌍 环境感知

#### 性能与功能平衡
```yaml
environment:
  scan-radius: 8       # 推荐值：6-12格
  max-entities: 5      # 推荐值：3-10个
  cache-time: 600      # 推荐值：300-1200秒
  
  collect:
    blocks: false      # 谨慎开启，会影响性能
```

### ⚡ 性能优化

#### TPS阈值配置
```yaml
performance:
  tps_thresholds:
    full: 18.0         # 流畅运行
    lite: 15.0         # 轻微卡顿
    basic: 10.0        # 中度卡顿
    # < 10.0 = 严重卡顿，应急模式
```

#### 硬件阈值
```yaml
performance:
  hardware:
    cpu-warning: 80.0     # CPU使用率警告线
    memory-warning: 85.0  # 内存使用率警告线
    min-free-memory: 2.0  # 最少2GB空闲内存
```

## 🎯 推荐配置

### 🏠 小型服务器（1-10人）
```yaml
settings:
  max-tokens: 200
  temperature: 0.7

chat:
  cooldown: 2

environment:
  scan-radius: 10
  max-entities: 8
  cache-time: 300

performance:
  auto-optimize: true
  tps_thresholds:
    full: 19.0
    lite: 17.0
    basic: 15.0
```

### 🏢 中型服务器（10-50人）
```yaml
settings:
  max-tokens: 150
  temperature: 0.6

chat:
  cooldown: 3

environment:
  scan-radius: 8
  max-entities: 5
  cache-time: 600

performance:
  auto-optimize: true
  tps_thresholds:
    full: 18.0
    lite: 15.0
    basic: 12.0
```

### 🏗️ 大型服务器（50+人）
```yaml
settings:
  max-tokens: 100
  temperature: 0.5

chat:
  cooldown: 5

environment:
  scan-radius: 6  
  max-entities: 3
  cache-time: 900

performance:
  auto-optimize: true
  tps_thresholds:
    full: 17.0
    lite: 14.0
    basic: 10.0
```

## 🔄 配置管理

### 热重载
```bash
# 在游戏中执行
/aichat reload
```

### 配置验证
```bash
# 检查配置状态
/aichat status

# 开启调试模式查看详细信息
/aichat debug
```

### 备份配置
```bash
# 建议定期备份配置文件
cp plugins/AIChatPlugin/config.yml plugins/AIChatPlugin/config.yml.backup
```

## 🚨 常见配置问题

### 1. API调用失败
```yaml
settings:
  api-key: ""          # 检查是否填写正确的API密钥
  timeout: 30          # 增加超时时间
```

### 2. 响应速度慢
```yaml
settings:
  max-tokens: 150      # 减少最大Token数
  
environment:
  enabled: false       # 临时关闭环境收集测试
```

### 3. 服务器卡顿
```yaml
performance:
  auto-optimize: true  # 确保开启自动优化
  
environment:
  cache-time: 1200     # 增加缓存时间
  scan-radius: 6       # 减少扫描范围
```

### 4. 历史记录过多
```yaml
history:
  max-messages: 30     # 减少最大消息数
  auto-cleanup: true   # 开启自动清理
  
# 或手动清空
# /aichat clear
```

## 📊 性能监控

### 状态检查命令
```bash
/aichat status       # 查看整体状态
/performance status  # 查看性能状态  
/profile status      # 查看玩家档案状态
```

### 日志监控
```yaml
debug:
  enabled: true
  performance-logging: true
```

监控日志文件：
- `plugins/AIChatPlugin/logs/performance.log`
- `plugins/AIChatPlugin/logs/debug.log`

---

**📅 最后更新**: 2025-06-20  
**🔖 配置版本**: v1.1.0618  
**📖 更多帮助**: [故障排除文档](TROUBLESHOOTING.md) 