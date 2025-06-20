# AI Chat Plugin v1.1.0618

**🤖 智能Minecraft聊天助手 | 基于DeepSeek AI | 重构版**

一个功能强大的Minecraft插件，为您的服务器带来智能AI对话体验。基于DeepSeek AI API，支持环境感知、自动性能优化和自然对话交互。

## ✨ 核心特性

### 🧠 智能对话
- **自然交互** - 像真人朋友一样对话，告别机械化回复
- **环境感知** - AI能理解游戏环境，提供针对性建议
- **上下文记忆** - 智能记住对话历史，保持连贯性

### ⚡ 性能优化
- **自适应模式** - 根据服务器TPS自动切换性能模式
- **异步处理** - 完全异步化，不阻塞游戏主线程
- **智能缓存** - 环境信息缓存，减少重复计算

### 🔧 易用管理
- **配置驱动** - 所有功能均可通过配置文件调整
- **命令管理** - 丰富的管理命令，支持实时调试
- **历史管理** - 一键清空历史记录，重置对话风格

## 🚀 快速开始

### 环境要求
- **Minecraft** 1.20.x+
- **Java** 17+
- **内存** 最少2GB可用
- **DeepSeek API密钥**

### 安装步骤

1. **下载插件**
   ```bash
   # 从releases目录下载最新版本
   wget https://github.com/zsbooody/ai-chat-plugin/releases/v1.1.0618/ai-chat-plugin-1.1.0618.jar
   ```

2. **安装插件**
   ```bash
   # 将JAR文件放入plugins目录
   cp ai-chat-plugin-1.1.0618.jar /path/to/server/plugins/
   ```

3. **配置API密钥**
   ```yaml
   # 编辑 plugins/AIChatPlugin/config.yml
   settings:
     api-key: "your-deepseek-api-key"
   ```

4. **启动服务器**
   ```bash
   # 重启或重载插件
   /aichat reload
   ```

## 💬 使用方法

### 基础对话
```
玩家: 你好
AI: 你好！有什么我可以帮助你的吗？

玩家: 我在森林里迷路了
AI: 我看到你在森林中。建议你制作指南针或者找一个高点观察地形，也可以跟着太阳的方向找到出路。
```

### 管理命令
```bash
/aichat help        # 显示帮助
/aichat clear       # 清空历史记录
/aichat reload      # 重载配置
/aichat status      # 查看状态
/aichat debug       # 切换调试模式
```

## 📊 性能模式

插件会根据服务器TPS自动切换运行模式：

| 模式 | TPS阈值 | 功能状态 | 适用场景 |
|------|---------|----------|----------|
| **FULL** | ≥18.0 | 全功能启用 | 性能良好 |
| **LITE** | ≥15.0 | 精简模式 | 轻微卡顿 |
| **BASIC** | ≥10.0 | 基础功能 | 中度卡顿 |
| **EMERGENCY** | <10.0 | 最小化运行 | 严重卡顿 |

## 🔧 配置说明

### 核心配置
```yaml
settings:
  api-key: ""                    # DeepSeek API密钥
  model: "deepseek-chat"         # AI模型
  max-tokens: 200                # 最大回复长度
  temperature: 0.7               # 创造性水平

chat:
  prefix: ""                     # 聊天前缀（空=监听所有）
  enabled: true                  # 启用聊天功能

performance:
  auto-optimize: true            # 自动性能优化
  check-interval: 10             # 检查间隔（秒）
```

完整配置说明请查看 [CONFIG.md](docs/CONFIG.md)

## 🔄 版本更新

### v1.1.0618 重构版 (2025-06-20)

**🎯 重大更新：从约束思维到赋能思维**

#### ✨ 新增功能
- **自然对话风格** - AI回复更加自然，告别格式化
- **历史记录管理** - 新增`/aichat clear`命令清空历史
- **异步优化** - 完全异步化处理，零卡顿
- **TPS精确检测** - 修复TPS误报问题

#### 🔧 核心优化
- **主线程保护** - 环境收集完全异步，不再阻塞
- **智能缓存** - 环境信息智能缓存，性能提升50%+
- **配置驱动** - 所有限制可通过配置文件调整
- **理念转变** - 从"规则执行器"到"智能助手"

#### 🚨 破坏性变更
- 旧版本历史记录需要手动清空（`/aichat clear`）
- 配置文件结构调整，需要重新配置

详细更新记录请查看 [CHANGELOG.md](CHANGELOG.md)

## 📚 文档

- [📖 API文档](docs/API.md) - 开发者接口说明
- [⚙️ 配置文档](docs/CONFIG.md) - 详细配置说明  
- [🔧 故障排除](docs/TROUBLESHOOTING.md) - 常见问题解决

## 🤝 贡献

欢迎提交Issue和Pull Request！

1. Fork项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送分支 (`git push origin feature/AmazingFeature`)
5. 创建Pull Request

## 📄 许可证

本项目基于MIT许可证开源 - 查看 [LICENSE](LICENSE) 文件了解详情

## 📞 支持

- **GitHub Issues**: [提交问题](https://github.com/zsbooody/ai-chat-plugin/issues)
- **QQ**: 1731477919@qq.com
- **版本**: v1.1.0618 (重构版)

---

**⭐ 如果这个项目对您有帮助，请给个Star支持一下！** 