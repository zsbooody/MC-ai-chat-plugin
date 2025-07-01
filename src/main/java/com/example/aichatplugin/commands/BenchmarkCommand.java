package com.example.aichatplugin.commands;

import com.example.aichatplugin.AIChatPlugin;
import com.example.aichatplugin.performance.PerformanceBenchmark;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 性能基准测试命令处理器
 * 
 * 支持的命令：
 * /benchmark run [duration] - 运行基准测试
 * /benchmark apply - 应用优化建议
 * /benchmark status - 查看测试状态
 * /benchmark help - 显示帮助
 */
public class BenchmarkCommand implements CommandExecutor, TabCompleter {
    
    private final AIChatPlugin plugin;
    private PerformanceBenchmark benchmark;
    private CompletableFuture<PerformanceBenchmark.BenchmarkReport> currentTest;
    
    public BenchmarkCommand(AIChatPlugin plugin) {
        this.plugin = plugin;
        this.benchmark = new PerformanceBenchmark(plugin);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aichat.admin")) {
            sender.sendMessage("§c你没有权限使用此命令");
            return true;
        }
        
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "run":
                return handleRunCommand(sender, args);
            case "apply":
                return handleApplyCommand(sender, args);
            case "status":
                return handleStatusCommand(sender, args);
            case "help":
                sendHelpMessage(sender);
                return true;
            default:
                sender.sendMessage("§c未知的子命令: " + subCommand);
                sendHelpMessage(sender);
                return true;
        }
    }
    
    /**
     * 处理运行测试命令
     */
    private boolean handleRunCommand(CommandSender sender, String[] args) {
        if (currentTest != null && !currentTest.isDone()) {
            sender.sendMessage("§c基准测试正在运行中，请等待完成");
            return true;
        }
        
        // 解析测试持续时间
        int duration = 60; // 默认60秒
        if (args.length > 1) {
            try {
                duration = Integer.parseInt(args[1]);
                if (duration < 10 || duration > 300) {
                    sender.sendMessage("§c测试持续时间必须在10-300秒之间");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§c无效的持续时间: " + args[1]);
                return true;
            }
        }
        
        sender.sendMessage("§6开始性能基准测试...");
        sender.sendMessage("§7测试持续时间: " + duration + "秒");
        sender.sendMessage("§7这可能需要几分钟时间，请耐心等待...");
        
        // 创建测试配置
        PerformanceBenchmark.BenchmarkConfig config = new PerformanceBenchmark.BenchmarkConfig();
        config.testDuration = duration;
        
        // 启动测试
        currentTest = benchmark.runBenchmark(config);
        
        // 异步处理测试结果
        currentTest.thenAccept(report -> {
            // 回到主线程发送消息
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage("§a=== 基准测试完成 ===");
                sender.sendMessage("§7系统信息:");
                sender.sendMessage("§7  服务器版本: " + report.systemInfo.serverVersion);
                sender.sendMessage("§7  Java版本: " + report.systemInfo.javaVersion);
                sender.sendMessage("§7  最大内存: " + (report.systemInfo.maxMemory / (1024*1024)) + " MB");
                sender.sendMessage("§7  在线玩家: " + report.systemInfo.onlinePlayerCount);
                
                sender.sendMessage("§7基线性能:");
                sender.sendMessage("§7  配置加载: " + String.format("%.2f", report.baselineMetrics.configLoadTime) + " ms");
                sender.sendMessage("§7  环境收集: " + String.format("%.2f", report.baselineMetrics.environmentCollectionTime) + " ms");
                sender.sendMessage("§7  监控开销: " + String.format("%.2f", report.baselineMetrics.monitoringOverhead) + " μs");
                
                if (!report.loadTestResults.isEmpty()) {
                    sender.sendMessage("§7负载测试结果:");
                    for (PerformanceBenchmark.LoadTestResult result : report.loadTestResults) {
                        sender.sendMessage(String.format("§7  %d消息/分钟: 成功率%.1f%%, TPS%.1f, 响应%.0fms",
                            result.messageRate, result.successRate * 100, result.averageTPS, result.averageResponseTime));
                    }
                }
                
                if (!report.optimizationSuggestions.isEmpty()) {
                    sender.sendMessage("§e优化建议 (" + report.optimizationSuggestions.size() + "项):");
                    for (PerformanceBenchmark.OptimizationSuggestion suggestion : report.optimizationSuggestions) {
                        String priority = "§7";
                        switch (suggestion.priority) {
                            case HIGH: priority = "§c"; break;
                            case MEDIUM: priority = "§e"; break;
                            case LOW: priority = "§a"; break;
                        }
                        sender.sendMessage(priority + "  [" + suggestion.priority + "] " + suggestion.title);
                        sender.sendMessage("§7    " + suggestion.description);
                    }
                    sender.sendMessage("§7使用 /benchmark apply 应用这些优化建议");
                } else {
                    sender.sendMessage("§a当前配置已经很好，无需优化！");
                }
                
                sender.sendMessage("§7详细报告已保存到 plugins/AIChatPlugin/performance-reports/");
            });
        }).exceptionally(throwable -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage("§c基准测试失败: " + throwable.getMessage());
                plugin.getLogger().severe("基准测试异常: " + throwable.getMessage());
            });
            return null;
        });
        
        return true;
    }
    
    /**
     * 处理应用优化命令
     */
    private boolean handleApplyCommand(CommandSender sender, String[] args) {
        if (currentTest == null) {
            sender.sendMessage("§c没有可用的基准测试结果");
            sender.sendMessage("§7请先运行 /benchmark run");
            return true;
        }
        
        if (!currentTest.isDone()) {
            sender.sendMessage("§c基准测试尚未完成，请等待");
            return true;
        }
        
        try {
            PerformanceBenchmark.BenchmarkReport report = currentTest.get();
            
            if (report.optimizationSuggestions.isEmpty()) {
                sender.sendMessage("§a当前配置已经很好，无需应用优化");
                return true;
            }
            
            sender.sendMessage("§6开始应用性能优化建议...");
            
            // 显示将要应用的优化
            for (PerformanceBenchmark.OptimizationSuggestion suggestion : report.optimizationSuggestions) {
                sender.sendMessage("§7应用: " + suggestion.configPath + " = " + suggestion.recommendedValue);
            }
            
            // 应用优化
            benchmark.applyOptimizations(report.optimizationSuggestions);
            
            sender.sendMessage("§a成功应用 " + report.optimizationSuggestions.size() + " 项优化建议！");
            sender.sendMessage("§7配置已保存，优化将在下次重载时生效");
            sender.sendMessage("§7建议运行 /aichat reload 重新加载配置");
            
        } catch (Exception e) {
            sender.sendMessage("§c应用优化失败: " + e.getMessage());
            plugin.getLogger().severe("应用优化异常: " + e.getMessage());
        }
        
        return true;
    }
    
    /**
     * 处理状态查询命令
     */
    private boolean handleStatusCommand(CommandSender sender, String[] args) {
        sender.sendMessage("§6=== 基准测试状态 ===");
        
        if (currentTest == null) {
            sender.sendMessage("§7当前状态: 无测试记录");
            sender.sendMessage("§7使用 /benchmark run 开始新的测试");
        } else if (!currentTest.isDone()) {
            sender.sendMessage("§e当前状态: 测试进行中...");
            sender.sendMessage("§7请等待测试完成");
        } else {
            try {
                PerformanceBenchmark.BenchmarkReport report = currentTest.get();
                sender.sendMessage("§a当前状态: 测试已完成");
                sender.sendMessage("§7测试时间: " + java.time.LocalDateTime.now());
                sender.sendMessage("§7优化建议: " + report.optimizationSuggestions.size() + " 项");
                
                if (report.optimizationSuggestions.isEmpty()) {
                    sender.sendMessage("§a配置状态: 已优化");
                } else {
                    long highPriority = report.optimizationSuggestions.stream()
                        .filter(s -> s.priority == PerformanceBenchmark.OptimizationSuggestion.Priority.HIGH)
                        .count();
                    if (highPriority > 0) {
                        sender.sendMessage("§c配置状态: 需要优化 (" + highPriority + "项高优先级)");
                    } else {
                        sender.sendMessage("§e配置状态: 可以优化");
                    }
                    sender.sendMessage("§7使用 /benchmark apply 应用优化");
                }
            } catch (Exception e) {
                sender.sendMessage("§c状态查询失败: " + e.getMessage());
            }
        }
        
        // 显示性能监控信息
        if (plugin.getPerformanceMonitor() != null) {
            sender.sendMessage("§7实时性能:");
            sender.sendMessage("§7  当前TPS: " + String.format("%.1f", plugin.getPerformanceMonitor().getCurrentTPS()));
            sender.sendMessage("§7  运行模式: " + plugin.getPerformanceMonitor().getCurrentMode());
        }
        
        return true;
    }
    
    /**
     * 发送帮助信息
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6=== AI Chat Plugin 基准测试 ===");
        sender.sendMessage("§e/benchmark run [持续时间] §7- 运行性能基准测试");
        sender.sendMessage("§7  持续时间: 10-300秒 (默认60秒)");
        sender.sendMessage("§e/benchmark apply §7- 应用优化建议");
        sender.sendMessage("§e/benchmark status §7- 查看测试状态");
        sender.sendMessage("§e/benchmark help §7- 显示此帮助");
        sender.sendMessage("§7");
        sender.sendMessage("§7基准测试将评估插件在不同负载下的性能表现，");
        sender.sendMessage("§7并生成针对您服务器的优化建议。");
        
        if (sender instanceof Player) {
            sender.sendMessage("§7建议在玩家较少时运行测试以获得准确结果。");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aichat.admin")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("run", "apply", "status", "help");
            List<String> completions = new ArrayList<>();
            
            String input = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }
            
            return completions;
        } else if (args.length == 2 && "run".equalsIgnoreCase(args[0])) {
            // 为run命令提供持续时间建议
            return Arrays.asList("30", "60", "120", "180");
        }
        
        return new ArrayList<>();
    }
    
    /**
     * 关闭基准测试系统
     */
    public void shutdown() {
        if (benchmark != null) {
            benchmark.shutdown();
        }
        
        if (currentTest != null && !currentTest.isDone()) {
            currentTest.cancel(true);
        }
    }
} 