# AI Chat Plugin

[![Version](https://img.shields.io/badge/version-v1.2.0-blue.svg)](https://github.com/zsbooody/ai-chat-plugin/releases)
[![Minecraft](https://img.shields.io/badge/minecraft-1.19+-green.svg)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/license-MIT-yellow.svg)](LICENSE)
[![Language](https://img.shields.io/badge/language-Java-orange.svg)](https://www.java.com/)

🤖 **智能的Minecraft AI聊天插件**，基于DeepSeek AI提供强大的对话体验和环境感知能力。

## ✨ 核心特性

### 🎯 智能对话系统
- **🤖 DeepSeek AI驱动** - 采用先进的AI模型，提供自然流畅的对话体验
- **🌍 环境感知** - AI能感知玩家周围的环境、天气、时间和实体
- **💭 上下文记忆** - 支持对话历史记录，AI能理解对话上下文
- **🎨 个性化回复** - 智能的角色一致性保护，确保AI保持稳定友好的性格

### ⚡ 事件响应系统
- **🚪 玩家加入响应** - 智能欢迎新玩家，可配置冷却时间
- **❤️ 受伤关怀** - 玩家血量过低时AI会表达关心并给出建议
- **🎮 游戏事件感知** - 支持多种游戏事件的智能响应

### 🔧 专业Web管理界面
- **📊 实时监控** - 系统状态、内存使用、TPS等关键指标
- **⚙️ 配置管理** - 直观的Web界面管理所有插件配置
- **🔥 性能基准测试** - 内置性能测试和优化建议系统
- **📱 响应式设计** - 完美适配桌面和移动设备

### 🚀 性能优化
- **⚡ 自动性能调节** - 根据服务器TPS自动调整功能
- **💾 智能缓存** - 优化的缓存机制减少资源消耗
- **🎛️ 灵活配置** - 丰富的配置选项适应不同服务器需求

## 🎮 功能演示

### AI对话示例
```
[玩家] 我在森林里迷路了
[AI] 我看到你在森林区域，现在是夜晚。建议你快速找个安全的地方，或者制作火把照明。附近有一些树木可以砍伐制作工具。

[玩家] 血量很低怎么办
[AI] 注意到你的血量只有6❤️了！赶紧找食物恢复血量，或者寻找安全地点休息。附近如果有动物可以获取食物。
```

### Web管理界面
- 🖥️ **现代化仪表盘** - 实时显示服务器状态和AI使用情况
- 📈 **内存监控** - 详细的JVM内存池监控和GC统计
- ⚙️ **配置中心** - 6大配置分类，覆盖所有功能设置
- 🔥 **性能测试** - 一键运行基准测试并获取优化建议

## 🚀 快速开始

### 系统要求
- **Minecraft服务器**: 1.19+
- **Java版本**: 11+
- **服务器软件**: Spigot/Paper/Bukkit
- **内存要求**: 最少512MB可用内存

### 安装步骤

1. **下载插件**
   ```bash
   # 从Releases页面下载最新版本
   wget https://github.com/zsbooody/ai-chat-plugin/releases/download/v1.2.0/ai-chat-plugin-1.2.0.jar
   ```

2. **安装插件**
   ```bash
   # 将JAR文件放入plugins目录
   cp ai-chat-plugin-1.2.0.jar /path/to/your/server/plugins/
   ```

3. **获取API密钥**
   - 访问 [DeepSeek平台](https://platform.deepseek.com/)
   - 注册账号并获取API密钥

4. **配置插件**
   ```yaml
   # 编辑 plugins/AIChatPlugin/config.yml
   settings:
     api-key: "your-deepseek-api-key-here"
     chat-enabled: true
   ```

5. **启动服务器**
   ```bash
   # 重启服务器或重载插件
   /reload confirm
   # 或者
   /plugman reload AIChatPlugin
   ```

### 首次使用

1. **验证安装**
   ```
   /aichat status
   ```

2. **访问Web界面**
   - 打开浏览器访问: `http://your-server-ip:28080`
   - 使用控制台显示的访问令牌登录

3. **测试AI对话**
   ```
   # 在游戏中发送消息（如果设置了前缀，需要加前缀）
   你好AI
   ```

## 📖 详细文档

### 配置指南
- [基础配置](docs/CONFIG.md) - 详细的配置文件说明
- [Web API文档](docs/WEB_API.md) - Web接口使用指南
- [故障排除](docs/TROUBLESHOOTING.md) - 常见问题解决方案

### 命令参考

#### 基础命令
- `/aichat` - 显示插件帮助信息
- `/aichat status` - 查看插件运行状态
- `/aichat reload` - 重载插件配置
- `/aichat save` - 手动保存历史记录

#### 管理员命令
- `/performance` - 性能监控和优化
- `/benchmark run [时长]` - 运行性能基准测试
- `/profile [玩家]` - 查看玩家档案信息

#### 权限节点
```yaml
aichat.use: true          # 使用AI聊天功能
aichat.admin: op          # 管理员功能
aichat.vip: false         # VIP用户（更短冷却时间）
aichat.reload: op         # 重载配置权限
```

## 🛠️ 高级配置

### 性能优化配置
```yaml
performance:
  auto-optimize-enabled: true    # 自动性能优化
  tps-threshold-full: 18.0      # 全功能模式TPS阈值
  tps-threshold-lite: 15.0      # 精简模式TPS阈值
  rate-limit:
    normal-user: 3000           # 普通用户冷却时间(毫秒)
    vip-user: 1000             # VIP用户冷却时间(毫秒)
```

### 事件响应配置
```yaml
events:
  join:
    enabled: true               # 启用加入响应
    cooldown: 30000            # 冷却时间(毫秒)
  damage:
    enabled: true               # 启用受伤响应
    threshold: 10.0            # 血量阈值
    cooldown: 10000            # 冷却时间(毫秒)
```

### 环境感知配置
```yaml
environment:
  entity-range: 10              # 实体检测范围
  show-weather: true            # 显示天气信息
  show-time: true              # 显示时间信息
  cache-ttl: 30                # 缓存生存时间(秒)
```

## 🔧 开发指南

### 构建项目
```bash
# 克隆项目
git clone https://github.com/zsbooody/ai-chat-plugin.git
cd ai-chat-plugin

# 编译项目
mvn clean package

# 运行测试
mvn test
```

### 项目结构
```
ai-chat-plugin/
├── src/main/java/              # Java源码
│   └── com/example/aichatplugin/
├── src/main/resources/         # 资源文件
│   ├── config.yml             # 默认配置
│   ├── plugin.yml             # 插件描述
│   └── web/                   # Web界面资源
├── docs/                       # 文档目录
├── releases/                   # 发布版本
└── target/                     # 编译输出
```

### API接口
插件提供完整的REST API接口，支持：
- 配置管理 (`/api/config/*`)
- 状态监控 (`/api/status/*`)
- 基准测试 (`/api/benchmark/*`)
- 系统操作 (`/api/actions/*`)

详细API文档请参考 [Web API文档](docs/WEB_API.md)。

## 🤝 贡献指南

我们欢迎社区贡献！请参考以下步骤：

1. **Fork项目** 并创建功能分支
2. **编写代码** 并添加必要的测试
3. **确保代码质量** 通过所有测试
4. **提交Pull Request** 并详细描述更改

### 开发规范
- 使用Java 11+语法特性
- 遵循Google Java代码规范
- 为新功能添加单元测试
- 更新相关文档

## 📈 版本历史

### v1.2.0 (2024-12-22)
- ✨ **新增** 完整的事件响应配置Web界面
- 🎨 **改进** 现代化的Web界面设计
- 🔧 **修复** 配置项对接和映射问题
- 📚 **完善** 项目文档和使用指南
- 🚀 **优化** 用户体验和操作便捷性

### v1.1.0618 (2024-12-19)
- 🔧 修复配置系统路径映射问题
- ⚡ 实现性能自动优化功能
- 🌍 完善环境感知系统
- 🔒 加强安全性和错误处理

## 📄 许可证

本项目采用 [MIT许可证](LICENSE)。

## 🙏 致谢

- **DeepSeek AI** - 提供强大的AI对话能力
- **Minecraft社区** - 持续的反馈和支持
- **开源贡献者** - 代码贡献和问题反馈

## 📞 支持与反馈

- 🐛 **问题反馈**: [GitHub Issues](https://github.com/zsbooody/ai-chat-plugin/issues)
- 💡 **功能建议**: [GitHub Discussions](https://github.com/zsbooody/ai-chat-plugin/discussions)
- 📧 **联系作者**: [1731477919@qq.com](mailto:1731477919@qq.com)

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给我们一个Star！**

Made with ❤️ by AI Chat Plugin Team

</div> 