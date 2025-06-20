@echo off
chcp 65001 >nul
echo.
echo =======================================================
echo AI Chat Plugin v1.1.0618 - GitHub 重构提交脚本
echo =======================================================
echo.

:: 确认用户意图
echo ⚠️  警告：此脚本将会：
echo    1. 删除所有冗余文档文件
echo    2. 重新组织项目结构
echo    3. 提交到GitHub（会覆盖现有历史）
echo.
set /p confirm="确认执行吗？(y/N): "
if /i not "%confirm%"=="y" (
    echo 操作已取消
    pause
    exit /b
)

echo.
echo 🔄 开始清理项目...

:: 删除冗余文档
echo.
echo 📄 删除冗余文档文件...
if exist "AI_PROMPT_OPTIMIZATION.md" del "AI_PROMPT_OPTIMIZATION.md"
if exist "AI提示词优化说明.md" del "AI提示词优化说明.md"
if exist "性能模式问题修复说明.md" del "性能模式问题修复说明.md"
if exist "配置修复和手动模式功能说明.md" del "配置修复和手动模式功能说明.md"
if exist "TESTING.md" del "TESTING.md"
if exist "QUICK_START_GUIDE.md" del "QUICK_START_GUIDE.md"
if exist "API_DOCUMENTATION.md" del "API_DOCUMENTATION.md"
if exist "PROJECT_DOCUMENTATION.md" del "PROJECT_DOCUMENTATION.md"

:: 删除旧的发布目录
echo.
echo 📦 清理旧版本文件...
if exist "release-1.0.0614" rmdir /s /q "release-1.0.0614"

:: 删除开发临时文件
echo.
echo 🧹 清理开发临时文件...
if exist "BuildTools.jar" del "BuildTools.jar"
if exist "BuildTools.log.txt" del "BuildTools.log.txt"
if exist "dependency-reduced-pom.xml" del "dependency-reduced-pom.xml"
if exist "ai-chat-plugin.iml" del "ai-chat-plugin.iml"

:: 清理IDE目录
if exist ".idea" rmdir /s /q ".idea"
if exist ".vscode" rmdir /s /q ".vscode"
if exist ".trae" rmdir /s /q ".trae"

:: 清理旧的docs子目录
echo.
echo 📚 重新组织docs目录...
if exist "docs\optimization" rmdir /s /q "docs\optimization"
if exist "docs\testing" rmdir /s /q "docs\testing"
if exist "docs\deployment" rmdir /s /q "docs\deployment"
if exist "docs\modules" rmdir /s /q "docs\modules"
if exist "docs\configs" rmdir /s /q "docs\configs"
if exist "docs\architecture" rmdir /s /q "docs\architecture"
if exist "docs\README.md" del "docs\README.md"

echo.
echo ✅ 项目清理完成！

:: Git操作
echo.
echo 🔄 开始Git操作...

:: 检查Git状态
git status >nul 2>&1
if errorlevel 1 (
    echo ❌ 错误：当前目录不是Git仓库
    pause
    exit /b 1
)

:: 备份当前分支
echo.
echo 💾 备份当前工作...
git branch backup-before-restructure >nul 2>&1

:: 添加所有文件
echo.
echo 📤 添加文件到Git...
git add .

:: 提交更改
echo.
echo 💬 提交更改...
git commit -m "🔥 重构版本 v1.1.0618 - 从约束思维到赋能思维

✨ 主要更新：
- 全面重构项目文档结构
- 新增自然对话系统
- 添加历史记录管理功能
- 完全异步化性能优化
- 修复TPS检测和聊天监听问题

📁 项目结构：
- 新增 releases/v1.1.0618/ 发布目录
- 重新组织 docs/ 文档结构
- 清理所有冗余和临时文件

🚨 破坏性变更：
- 建议清空历史记录: /aichat clear
- 部分配置项需要重新配置

详见 CHANGELOG.md 和 releases/v1.1.0618/RELEASE_NOTES.md"

if errorlevel 1 (
    echo ❌ Git提交失败
    pause
    exit /b 1
)

echo.
echo ✅ Git提交成功！

:: 推送到远程
echo.
set /p push="是否立即推送到GitHub? (y/N): "
if /i "%push%"=="y" (
    echo.
    echo 🚀 推送到GitHub...
    git push origin main
    if errorlevel 1 (
        echo ❌ 推送失败，请检查网络连接和权限
        echo 💡 你可以稍后手动执行: git push origin main
    ) else (
        echo ✅ 推送成功！
    )
)

:: 显示项目结构
echo.
echo 📁 新的项目结构：
echo.
tree /f /a | findstr /v /r "\.class$ \.jar$ target\\ \.git\\"

echo.
echo =======================================================
echo 🎉 项目重构完成！
echo.
echo 📦 发布信息：
echo    版本: v1.1.0618
echo    文件: releases/v1.1.0618/ai-chat-plugin-1.1.0618.jar
echo    大小: 9.9MB
echo.
echo 📚 文档结构：
echo    - README.md (主项目介绍)
echo    - CHANGELOG.md (版本更新记录)
echo    - docs/API.md (API文档)
echo    - docs/CONFIG.md (配置说明)
echo    - docs/TROUBLESHOOTING.md (故障排除)
echo.
echo 🔗 下一步：
echo    1. 访问GitHub检查更新是否正确
echo    2. 创建v1.1.0618 Release标签
echo    3. 更新项目描述和README
echo    4. 通知用户升级到新版本
echo =======================================================
echo.
pause 