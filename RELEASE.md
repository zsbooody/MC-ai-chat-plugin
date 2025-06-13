# AI Chat Plugin 发布说明

## 文件说明
1. `ai-chat-plugin-1.0.0.jar` - 编译好的插件文件
2. `ai-chat-plugin-src.zip` - 源代码压缩包
3. `README.md` - 详细使用说明
4. `config.yml` - 默认配置文件

## 安装步骤
1. 将 `ai-chat-plugin-1.0.0.jar` 放入服务器的 `plugins` 文件夹
2. 启动服务器，插件会自动生成 `config.yml`
3. 在 `config.yml` 中配置你的 API 密钥
4. 重启服务器或使用 `/reload` 命令

## 源代码说明
如果你想查看或修改源代码：
1. 解压 `ai-chat-plugin-src.zip`
2. 使用 IDE（如 IntelliJ IDEA）打开项目
3. 确保安装了 Java 17 或更高版本
4. 使用 Maven 构建项目：`mvn clean package`

## 注意事项
- 确保服务器运行 Minecraft 1.16.5 或更高版本
- 需要稳定的网络连接以使用 AI 功能
- 建议定期备份配置文件

## 更新日志
### 1.0.0
- 初始版本发布
- 支持基础对话功能
- 支持玩家状态响应
- 支持环境感知 