# AI Chat Plugin - Minecraft 智能对话插件

## 简介
AI Chat Plugin 是一个为 Minecraft 服务器添加智能对话功能的插件。它使用 DeepSeek AI 来为玩家提供自然、友好的对话体验，让游戏世界更加生动有趣。

## 主要功能
1. 智能对话
   - 使用 `!` 前缀触发 AI 对话
   - AI 会记住最近的对话历史
   - 支持中文对话
   - 对话内容会广播给所有玩家

2. 玩家状态响应
   - 玩家加入/离开游戏时的欢迎和告别
   - 玩家生命值低时的关心提醒
   - 玩家升级时的祝贺
   - 玩家完成成就时的赞赏

3. 环境感知
   - 了解玩家周围的环境
   - 根据环境提供相关建议
   - 保持对话的上下文连贯性

## 安装要求
1. 服务器要求
   - Minecraft 1.16.5 或更高版本
   - Java 17 或更高版本
   - 至少 2GB 内存
   - 稳定的网络连接

2. 网络要求
   - 稳定的互联网连接
   - 建议带宽：10Mbps 以上
   - 低延迟（建议 < 100ms）

## 安装步骤
1. 下载插件
   - 从发布页面下载最新版本的 `ai-chat-plugin-1.0.0.jar`
   - 将文件放入服务器的 `plugins` 文件夹

2. 配置 API 密钥
   - 在 `config.yml` 中设置你的 DeepSeek API 密钥
   - 路径：`settings.api-key`

3. 重启服务器
   - 输入 `/reload` 或重启服务器
   - 检查控制台是否有错误信息

## 配置指南
### 基础配置
```yaml
settings:
  api-key: "your-api-key-here"  # 必填：DeepSeek API 密钥
  model: "deepseek-chat"        # 可选：使用的 AI 模型
  temperature: 0.7              # 可选：AI 响应随机性（0-1）
  max-tokens: 150              # 可选：AI 响应最大长度
```

### 聊天设置
```yaml
chat:
  enabled: true                # 是否启用聊天功能
  prefix: "!"                  # 触发 AI 对话的前缀
  broadcast: true              # 是否广播对话内容
  broadcast-format: "&7[AI对话] &f{player}: {message}"  # 广播消息格式
```

### 玩家状态设置
```yaml
player-status:
  damage-threshold: 0.3        # 低血量提醒阈值（0-1）
  respond-to-join: true        # 是否响应玩家加入
  respond-to-quit: true        # 是否响应玩家离开
  respond-to-death: true       # 是否响应玩家死亡
  respond-to-level-up: true    # 是否响应玩家升级
  respond-to-advancement: true # 是否响应玩家成就
```

## 性能优化建议
### 低配置服务器（2GB 内存，5Mbps 带宽）
建议配置：
```yaml
settings:
  max-tokens: 100              # 减少响应长度
  temperature: 0.5             # 降低随机性

chat:
  broadcast: false             # 关闭广播功能

player-status:
  respond-to-join: true        # 保留欢迎消息
  respond-to-quit: true        # 保留告别消息
  respond-to-death: true       # 保留死亡消息
  respond-to-level-up: false   # 关闭升级响应
  respond-to-advancement: false # 关闭成就响应
```

### 中等配置服务器（4GB 内存，10Mbps 带宽）
建议配置：
```yaml
settings:
  max-tokens: 150              # 标准响应长度
  temperature: 0.7             # 标准随机性

chat:
  broadcast: true              # 启用广播功能

player-status:
  respond-to-join: true        # 保留欢迎消息
  respond-to-quit: true        # 保留告别消息
  respond-to-death: true       # 保留死亡消息
  respond-to-level-up: true    # 启用升级响应
  respond-to-advancement: true # 启用成就响应
```

### 高配置服务器（8GB 内存，20Mbps 带宽）
建议配置：
```yaml
settings:
  max-tokens: 200              # 增加响应长度
  temperature: 0.8             # 增加随机性

chat:
  broadcast: true              # 启用广播功能

player-status:
  respond-to-join: true        # 保留欢迎消息
  respond-to-quit: true        # 保留告别消息
  respond-to-death: true       # 保留死亡消息
  respond-to-level-up: true    # 启用升级响应
  respond-to-advancement: true # 启用成就响应
```

## 使用示例
1. 与 AI 对话
   ```
   !你好
   [AI对话] AI: 你好！很高兴见到你。需要我帮你什么吗？
   ```

2. 玩家加入游戏
   ```
   [系统] Zhao加入了游戏
   [AI对话] AI: 欢迎回来，Zhao！准备好开始今天的冒险了吗？
   ```

3. 玩家生命值低
   ```
   [AI对话] AI: 小心！你的生命值很低了，需要我帮你找些食物吗？
   ```

## 常见问题
1. 插件无法启动
   - 检查 API 密钥是否正确
   - 确认服务器版本兼容性
   - 查看服务器日志获取详细错误信息

2. AI 响应延迟
   - 检查网络连接
   - 考虑降低 `max-tokens` 值
   - 关闭不必要的功能

3. 服务器卡顿
   - 检查内存使用情况
   - 关闭广播功能
   - 减少状态响应功能

## 注意事项
1. API 使用
   - 注意 API 调用频率限制
   - 定期检查 API 密钥有效性
   - 监控 API 使用成本

2. 性能监控
   - 定期检查服务器性能
   - 根据玩家数量调整配置
   - 必要时降低功能复杂度

3. 数据安全
   - 定期备份配置文件
   - 不要分享 API 密钥
   - 注意玩家隐私保护

## 更新日志
### 1.0.0
- 初始版本发布
- 支持基础对话功能
- 支持玩家状态响应
- 支持环境感知

## 联系方式
如有问题或建议，请通过以下方式联系：
- 提交 Issue
- 发送邮件
- 访问项目主页

## 许可证
本项目采用 MIT 许可证 