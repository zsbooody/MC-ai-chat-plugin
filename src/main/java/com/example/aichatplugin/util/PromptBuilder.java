package com.example.aichatplugin.util;

import com.example.aichatplugin.ConfigLoader;
import com.example.aichatplugin.Message;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI提示词构建器
 * 负责根据配置和上下文，以链式调用的方式构建最终的提示词。
 */
public class PromptBuilder {

    private final StringBuilder prompt = new StringBuilder();
    private final ConfigLoader config;

    private String systemRole;
    private List<Message> history;
    private String environmentContext;
    private String eventContext;
    private String playerStatus;

    public PromptBuilder(ConfigLoader config) {
        this.config = config;
        // 使用外部化的系统角色提示词
        this.systemRole = config.getSystemRole(); 
    }

    public PromptBuilder withSystemRole(String role) {
        if (role != null && !role.trim().isEmpty()) {
            this.systemRole = role;
        }
        return this;
    }

    public PromptBuilder withHistory(List<Message> history) {
        this.history = history;
        return this;
    }

    public PromptBuilder withEnvironmentContext(String environmentContext) {
        this.environmentContext = environmentContext;
        return this;
    }

    public PromptBuilder withEventContext(String eventContext) {
        this.eventContext = eventContext;
        return this;
    }
    
    public PromptBuilder withPlayerStatus(String playerStatus) {
        this.playerStatus = playerStatus;
        return this;
    }

    /**
     * 构建最终的提示词字符串
     * @return 格式化后的完整提示词
     */
    public String build() {
        // 1. 系统角色设定 (永远在最前)
        if (systemRole != null && !systemRole.isEmpty()) {
            prompt.append(systemRole).append("\n\n");
        }
        
        // 2. 角色保护提示（如果启用）
        if (config.isRoleProtectionEnabled()) {
            String roleProtection = config.getRoleProtectionPrompt();
            if (roleProtection != null && !roleProtection.isEmpty()) {
                prompt.append(roleProtection).append("\n\n");
            }
        }

        // 3. 环境和事件上下文 - 🔧 简洁的上下文提供
        if (environmentContext != null && !environmentContext.isEmpty()) {
            String envTemplate = config.getConversationPrompt("with-environment");
            if (envTemplate != null && !envTemplate.isEmpty()) {
                // 使用模板
                prompt.append(envTemplate.replace("{environment_info}", environmentContext)).append("\n\n");
            } else {
                // 🔧 极简格式：纯粹的上下文信息
                prompt.append("环境：").append(environmentContext).append("\n\n");
            }
        }
        
        // 4. 事件上下文
        if (eventContext != null && !eventContext.isEmpty()) {
            prompt.append("【当前事件】\n");
            prompt.append(eventContext).append("\n");
        }
        
        // 5. 玩家状态
        if (playerStatus != null && !playerStatus.isEmpty()) {
            prompt.append("【玩家状态】\n");
            prompt.append(playerStatus).append("\n");
        }
        
        if ((eventContext != null && !eventContext.isEmpty()) || 
            (playerStatus != null && !playerStatus.isEmpty())) {
            prompt.append("\n");
        }

        // 6. 对话历史
        if (history != null && !history.isEmpty()) {
            // 根据配置限制历史记录长度
            int maxHistory = config.getMaxHistoryInfluence();
            List<Message> limitedHistory = history.size() > maxHistory 
                ? history.subList(history.size() - maxHistory, history.size()) 
                : history;
            
            String historyTemplate = config.getConversationPrompt("with-history");
            if (historyTemplate != null && !historyTemplate.isEmpty()) {
                // 构建历史记录字符串
                StringBuilder historyStr = new StringBuilder();
                for (Message msg : limitedHistory) {
                    String role = msg.isAI() ? "AI" : "玩家";
                    // 对AI的回复进行摘要处理
                    if (msg.isAI()) {
                        int summaryLength = config.getAiResponseSummaryLength();
                        String content = msg.getContent();
                        if (content.length() > summaryLength) {
                            content = content.substring(0, summaryLength) + "...";
                        }
                        historyStr.append(role).append(": ").append(content).append("\n");
                    } else {
                        historyStr.append(role).append(": ").append(msg.getContent()).append("\n");
                    }
                }
                
                // 使用模板
                prompt.append(historyTemplate.replace("{history}", historyStr.toString())).append("\n\n");
            } else {
                // 使用默认格式
                prompt.append("【对话历史】\n");
                for (Message msg : limitedHistory) {
                    String role = msg.isAI() ? "你" : "玩家";
                    prompt.append(role).append(": ").append(msg.getContent()).append("\n");
                }
                prompt.append("\n");
            }
        }
        
        // 7. 最终指示（不再硬编码，让AI根据系统角色自行判断）
        // 系统角色和角色保护提示已经包含了所有必要的指示

        return prompt.toString();
    }
} 