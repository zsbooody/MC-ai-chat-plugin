package com.example.aichatplugin.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.example.aichatplugin.status.PluginStatus.Feature;

/**
 * 状态报告生成器
 * 负责将插件状态转换为格式化的自然语言报告
 */
public class StatusReporter {
    
    /**
     * 生成状态报告
     * @param status 插件状态
     * @return 格式化的报告文本
     */
    public String generateReport(PluginStatus status) {
        StringBuilder report = new StringBuilder();
        
        // 添加标题
        report.append("📊 当前插件状态报告\n\n");
        
        // 功能状态部分
        appendFeatureStatus(report, status);
        
        // 配置参数部分
        appendConfigParams(report, status);
        
        // 性能统计部分
        appendPerformanceStats(report, status);
        
        // 优化建议部分
        appendOptimizationSuggestions(report, status);
        
        return report.toString();
    }
    
    /**
     * 添加功能状态部分
     */
    private void appendFeatureStatus(StringBuilder sb, PluginStatus status) {
        sb.append("📊 功能启用状态:\n");
        
        // 玩家事件响应
        appendFeatureStatus(sb, "玩家加入事件响应", status.getFeatureStatus(Feature.JOIN));
        appendFeatureStatus(sb, "玩家退出事件响应", status.getFeatureStatus(Feature.QUIT));
        appendFeatureStatus(sb, "玩家重生事件响应", status.getFeatureStatus(Feature.RESPAWN));
        appendFeatureStatus(sb, "玩家升级事件响应", status.getFeatureStatus(Feature.LEVEL_UP));
        appendFeatureStatus(sb, "玩家受伤事件响应", status.getFeatureStatus(Feature.DAMAGE), 
            " (冷却时间: " + status.getConfigParam("damage_cooldown") + "ms)");
        appendFeatureStatus(sb, "玩家死亡事件响应", status.getFeatureStatus(Feature.DEATH));
        appendFeatureStatus(sb, "成就事件响应", status.getFeatureStatus(Feature.ADVANCEMENT));
        appendFeatureStatus(sb, "药水效果变更响应", status.getFeatureStatus(Feature.POTION_EFFECT));
        
        sb.append("\n");
    }
    
    /**
     * 添加单个功能状态
     */
    private void appendFeatureStatus(StringBuilder sb, String name, boolean enabled) {
        appendFeatureStatus(sb, name, enabled, "");
    }
    
    /**
     * 添加带额外信息的功能状态
     */
    private void appendFeatureStatus(StringBuilder sb, String name, boolean enabled, String extra) {
        String icon = enabled ? "✅" : "⛔";
        String status = enabled ? "" : " (已禁用)";
        sb.append(icon).append(" ").append(name).append(status).append(extra).append("\n");
    }
    
    /**
     * 添加配置参数部分
     */
    private void appendConfigParams(StringBuilder sb, PluginStatus status) {
        sb.append("⚙️ 配置参数:\n");
        Map<String, Object> params = status.getConfigParams();
        
        // 添加关键配置参数
        appendConfigParam(sb, "全局事件冷却", params.get("global_cooldown"), "ms");
        appendConfigParam(sb, "伤害事件冷却", params.get("damage_cooldown"), "ms");
        appendConfigParam(sb, "药水事件冷却", params.get("potion_cooldown"), "ms");
        appendConfigParam(sb, "伤害阈值", params.get("damage_threshold"), "");
        
        sb.append("\n");
    }
    
    /**
     * 添加单个配置参数
     */
    private void appendConfigParam(StringBuilder sb, String name, Object value, String unit) {
        sb.append("- ").append(name).append(": ").append(value).append(unit).append("\n");
    }
    
    /**
     * 添加性能统计部分
     */
    private void appendPerformanceStats(StringBuilder sb, PluginStatus status) {
        sb.append("📈 性能统计 (最近1分钟):\n");
        Map<String, Object> stats = status.getPerformanceStats();
        
        // 硬件状态（简化版 - 仅内存检测）
        sb.append("💻 硬件状态:\n");
        appendPerformanceStat(sb, "可用内存", stats.get("free_memory"), "GB");
        appendPerformanceStat(sb, "系统内存", stats.get("system_memory"), "GB");
        appendPerformanceStat(sb, "CPU核心数", stats.get("cpu_cores"), "个");
        
        // 服务器状态
        sb.append("\n🎮 服务器状态:\n");
        appendPerformanceStat(sb, "当前TPS", stats.get("tps"), "");
        appendPerformanceStat(sb, "实体数量", stats.get("entity_count"), "");
        appendPerformanceStat(sb, "区块数量", stats.get("chunk_count"), "");
        appendPerformanceStat(sb, "当前运行模式", stats.get("current_mode"), "");
        
        // 事件统计
        sb.append("\n📊 事件统计:\n");
        appendPerformanceStat(sb, "总事件数", stats.get("total_events"), "");
        appendPerformanceStat(sb, "总响应数", stats.get("total_responses"), "");
        appendPerformanceStat(sb, "平均响应时间", stats.get("avg_response_time"), "ms");
        
        // 各类型事件计数
        sb.append("\n📝 事件类型统计:\n");
        appendEventTypeStat(sb, "玩家加入", stats.get("event_join"));
        appendEventTypeStat(sb, "玩家退出", stats.get("event_quit"));
        appendEventTypeStat(sb, "玩家重生", stats.get("event_respawn"));
        appendEventTypeStat(sb, "玩家升级", stats.get("event_level_up"));
        appendEventTypeStat(sb, "玩家受伤", stats.get("event_damage"));
        appendEventTypeStat(sb, "玩家死亡", stats.get("event_death"));
        appendEventTypeStat(sb, "成就事件", stats.get("event_advancement"));
        appendEventTypeStat(sb, "药水效果", stats.get("event_potion_effect"));
        
        sb.append("\n");
    }
    
    /**
     * 添加单个性能统计
     */
    private void appendPerformanceStat(StringBuilder sb, String name, Object value, String unit) {
        sb.append("- ").append(name).append(": ").append(value).append(unit).append("\n");
    }
    
    /**
     * 添加事件类型统计
     */
    private void appendEventTypeStat(StringBuilder sb, String name, Object value) {
        sb.append("- ").append(name).append(": ").append(value).append("\n");
    }
    
    /**
     * 添加优化建议部分
     */
    private void appendOptimizationSuggestions(StringBuilder sb, PluginStatus status) {
        sb.append("💡 优化建议:\n");
        List<String> suggestions = generateSuggestions(status);
        
        for (int i = 0; i < suggestions.size(); i++) {
            sb.append(i + 1).append(". ").append(suggestions.get(i)).append("\n");
        }
    }
    
    /**
     * 生成优化建议
     */
    private List<String> generateSuggestions(PluginStatus status) {
        List<String> suggestions = new ArrayList<>();
        Map<String, Object> stats = status.getPerformanceStats();
        
        // 基于内存使用情况的建议
        double freeMemory = (double) stats.getOrDefault("free_memory", 0.0);
        if (freeMemory < 2.0) {
            suggestions.add("⚠️ 可用内存不足(" + String.format("%.1f", freeMemory) + "GB)，建议增加服务器内存");
        }
        
        // 基于功能状态的建议
        if (!status.getFeatureStatus(Feature.DEATH)) {
            suggestions.add("💡 死亡事件响应已禁用，启用后可增强玩家死亡时的互动体验");
        }
        
        // 基于运行模式的建议
        Object currentMode = stats.get("current_mode");
        if (currentMode != null && currentMode.toString().equals("EMERGENCY")) {
            suggestions.add("🔧 当前处于应急模式，请检查服务器性能问题");
        }
        
        return suggestions;
    }
} 