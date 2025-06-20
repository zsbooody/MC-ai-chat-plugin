# 故障排除指南

AI Chat Plugin v1.1.0618 常见问题解决方案。

## 🚨 快速诊断

### 一键诊断
```bash
# 在游戏中执行以下命令进行快速诊断
/aichat status      # 查看整体状态
/aichat debug       # 开启调试模式
/performance status # 查看性能状态
```

### 检查清单
- ✅ 插件版本是否为 v1.1.0618
- ✅ 服务器版本是否为 Minecraft 1.20.x+
- ✅ Java版本是否为 17+
- ✅ API密钥是否正确填写
- ✅ 网络连接是否正常

## 🔧 常见问题

### 1. AI无法响应

#### 🚫 症状
- 玩家发送消息后AI没有任何回复
- 控制台没有相关日志

#### 🔍 可能原因
1. **API密钥未配置或无效**
2. **聊天前缀设置错误**
3. **插件未正确加载**
4. **网络连接问题**

#### 💡 解决方案

**方案1：检查API配置**
```yaml
# config.yml
settings:
  api-key: "your-deepseek-api-key"  # 确保填写正确
  api-url: "https://api.deepseek.com"
```

**方案2：检查聊天前缀**
```yaml
# config.yml
chat:
  prefix: ""  # 空字符串=监听所有聊天
  enabled: true
```

**方案3：检查插件状态**
```bash
/plugins            # 查看插件是否绿色
/aichat status      # 查看详细状态
```

**方案4：网络测试**
```bash
# 在服务器系统中测试网络
ping api.deepseek.com
curl -I https://api.deepseek.com
```

### 2. 服务器卡顿/TPS下降

#### 🚫 症状
- 服务器TPS显著下降
- 玩家体验卡顿
- 大量TPS警告日志

#### 🔍 可能原因
1. **环境收集消耗过多资源**
2. **性能优化未启用**
3. **历史记录过度积累**
4. **伤害事件频繁触发**

#### 💡 解决方案

**方案1：启用性能优化**
```yaml
# config.yml
performance:
  auto-optimize: true
  tps_thresholds:
    full: 18.0
    lite: 15.0
    basic: 10.0
```

**方案2：优化环境收集**
```yaml
# config.yml
environment:
  scan-radius: 6      # 减少扫描范围
  max-entities: 3     # 限制实体数量
  cache-time: 1200    # 增加缓存时间
  collect:
    blocks: false     # 关闭方块收集
```

**方案3：清理历史记录**
```bash
/aichat clear       # 清空所有历史记录
```

**方案4：临时禁用环境收集**
```yaml
# config.yml  
environment:
  enabled: false      # 临时关闭测试性能
```

### 3. 历史记录污染

#### 🚫 症状
- AI回复风格异常（过度格式化）
- 出现大量符号如 `[状态]`、`☔️`、`📍` 等
- AI回复不自然

#### 🔍 可能原因
- 旧版本历史记录影响AI学习

#### 💡 解决方案

**立即解决：清空历史记录**
```bash
/aichat clear       # 清空历史记录
/aichat reload      # 重载配置
```

**验证修复效果：**
```bash
# 测试对话
玩家: 你好
AI: 你好！有什么我可以帮助你的吗？  # 应该是自然回复
```

### 4. 权限和命令问题

#### 🚫 症状
- 命令无法识别
- 提示权限不足
- 帮助信息显示配置键名

#### 🔍 可能原因
1. **权限配置错误**
2. **命令注册失败**
3. **配置文件格式错误**

#### 💡 解决方案

**方案1：检查权限**
```yaml
# 权限插件配置
permissions:
  - aichat.use        # 基础使用权限
  - aichat.admin      # 管理员权限
  - aichat.vip        # VIP权限
```

**方案2：重新加载插件**
```bash
/reload             # 重载所有插件
# 或
/plugman reload AIChatPlugin
```

**方案3：检查配置格式**
```bash
# 使用YAML验证器检查配置文件语法
# 或者重新生成配置文件
/aichat reload
```

### 5. 内存泄漏/性能下降

#### 🚫 症状
- 服务器内存使用持续增长
- 长时间运行后性能下降
- OutOfMemoryError错误

#### 🔍 可能原因
1. **历史记录无限积累**
2. **缓存清理失效**
3. **线程池未正确关闭**

#### 💡 解决方案

**方案1：启用自动清理**
```yaml
# config.yml
history:
  auto-cleanup: true
  max-messages: 30
  message-timeout: 7200000  # 2小时

cache:
  cache-ttl: 600           # 10分钟缓存
```

**方案2：定期清理**
```bash
# 设置定时任务
/aichat clear       # 每天清理一次历史记录
```

**方案3：监控内存使用**
```bash
/aichat status      # 查看内存使用情况
/performance status # 详细性能信息
```

### 6. API调用失败

#### 🚫 症状
- 频繁的API错误日志
- AI回复"抱歉，我暂时无法回应"
- 网络超时错误

#### 🔍 可能原因
1. **API密钥过期或无效**
2. **网络连接问题**
3. **API配额耗尽**
4. **请求参数错误**

#### 💡 解决方案

**方案1：验证API密钥**
```bash
# 测试API连接
curl -X POST https://api.deepseek.com/v1/chat/completions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"deepseek-chat","messages":[{"role":"user","content":"test"}]}'
```

**方案2：调整网络配置**
```yaml
# config.yml
advanced:
  network:
    connect-timeout: 15     # 增加连接超时
    read-timeout: 45        # 增加读取超时
    retry-attempts: 5       # 增加重试次数
```

**方案3：检查API配额**
- 登录DeepSeek控制台查看API使用情况
- 检查账单和配额限制

## 🔍 高级调试

### 启用详细日志
```yaml
# config.yml
debug:
  enabled: true
  log-level: "DEBUG"
  log-ai-requests: true
  log-environment: true
  performance-logging: true
```

### 日志文件位置
```
plugins/AIChatPlugin/logs/
├── debug.log           # 调试日志
├── performance.log     # 性能日志
├── ai-requests.log     # AI请求日志
└── error.log          # 错误日志
```

### 性能分析
```bash
# 查看详细性能信息
/aichat status
/performance monitor    # 实时监控模式

# 检查线程使用
jstack <server-pid>     # Java线程转储

# 检查内存使用
jmap -histo <server-pid>  # 内存直方图
```

## 🚑 紧急处理

### 服务器严重卡顿
```bash
# 立即执行以下命令
/performance manual enable   # 启用手动模式
/performance set EMERGENCY   # 切换到应急模式
/aichat clear               # 清空历史记录

# 在config.yml中临时关闭功能
environment:
  enabled: false
chat:
  respond-to:
    damage: false
```

### 插件崩溃处理
```bash
# 1. 停止服务器
/stop

# 2. 备份数据
cp -r plugins/AIChatPlugin plugins/AIChatPlugin.backup

# 3. 重置配置
rm plugins/AIChatPlugin/config.yml
# 重启后会生成默认配置

# 4. 重新配置
# 只配置必要的API密钥，其他保持默认
```

### 数据恢复
```bash
# 恢复历史记录
cp plugins/AIChatPlugin.backup/history/* plugins/AIChatPlugin/history/

# 恢复配置
cp plugins/AIChatPlugin.backup/config.yml plugins/AIChatPlugin/config.yml

# 重载插件
/aichat reload
```

## 📊 性能基准

### 正常性能指标
- **TPS**: ≥18.0
- **内存使用**: <80%
- **CPU使用**: <70%
- **AI响应时间**: <3秒

### 异常性能指标
- **TPS**: <15.0（需要优化）
- **内存使用**: >85%（需要清理）
- **CPU使用**: >80%（需要降级）
- **AI响应时间**: >5秒（网络或配置问题）

## 🔬 问题诊断流程

### 步骤1：基础检查
```bash
/plugins                # 检查插件状态
/version AIChatPlugin   # 检查版本
/aichat status         # 检查运行状态
```

### 步骤2：配置验证
```bash
/aichat debug          # 开启调试模式
/aichat reload         # 重载配置
```

### 步骤3：功能测试
```bash
# 测试基础对话
玩家: 你好

# 测试命令功能
/aichat help
/aichat clear
```

### 步骤4：性能检查
```bash
/performance status    # 检查性能状态
/performance monitor   # 实时监控
```

### 步骤5：日志分析
- 查看 `plugins/AIChatPlugin/logs/` 下的日志文件
- 重点关注ERROR和WARN级别的消息

## 📞 获取支持

### 问题报告格式
当遇到无法解决的问题时，请提供以下信息：

```
**环境信息**
- 服务器版本：
- Java版本：
- 插件版本：v1.1.0618
- 其他相关插件：

**问题描述**
- 具体症状：
- 复现步骤：
- 预期结果：
- 实际结果：

**配置信息**
- 相关配置项（去除敏感信息如API密钥）
- 是否修改过默认配置

**日志信息**
- 相关错误日志
- 调试日志片段
- 性能监控信息
```

### 联系方式
- **GitHub Issues**: [提交问题](https://github.com/zsbooody/ai-chat-plugin/issues)
- **QQ**: 1731477919@qq.com

---

**📅 最后更新**: 2025-06-20  
**🔖 故障排除版本**: v1.1.0618  
**📖 更多帮助**: [配置文档](CONFIG.md) | [API文档](API.md) 