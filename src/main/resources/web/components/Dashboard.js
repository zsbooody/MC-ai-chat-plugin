// Dashboard组件 - 主仪表盘
const Dashboard = {
    props: ['status', 'config'],
    emits: ['open-config', 'open-benchmark', 'open-memory', 'reload-config', 'run-gc', 'apply-optimizations'],
    template: `
        <div class="dashboard-grid">
            <!-- 系统状态卡片 -->
            <div class="card">
                <div class="card-header">
                    <div class="card-icon">📊</div>
                    <div class="card-title">系统状态</div>
                </div>
                <div class="card-content">
                    <div class="status-grid">
                        <div class="status-item">
                            <span class="status-label">插件版本</span>
                            <span class="status-value">{{ status.version }}</span>
                        </div>
                        <div class="status-item">
                            <span class="status-label">运行状态</span>
                            <span class="status-value">{{ status.status }}</span>
                        </div>
                        <div class="status-item">
                            <span class="status-label">当前TPS</span>
                            <span class="status-value">{{ status.tps.toFixed(1) }}</span>
                        </div>
                        <div class="status-item">
                            <span class="status-label">运行模式</span>
                            <span class="status-value">{{ formatMode(status.operationMode) }}</span>
                        </div>
                        <div class="status-item">
                            <span class="status-label">CPU使用率</span>
                            <span class="status-value">{{ (status.cpuUsage * 100).toFixed(1) }}%</span>
                        </div>
                        <div class="status-item">
                            <span class="status-label">在线玩家</span>
                            <span class="status-value">{{ status.onlinePlayers }}</span>
                        </div>
                    </div>
                </div>
            </div>
            
            <!-- 快速操作卡片 -->
            <div class="card">
                <div class="card-header">
                    <div class="card-icon">⚡</div>
                    <div class="card-title">快速操作</div>
                </div>
                <div class="card-content">
                    <div class="btn-group">
                        <button class="btn btn-success" @click="$emit('open-benchmark')">
                            🔥 性能测试
                        </button>
                        <button class="btn btn-warning" @click="$emit('reload-config')">
                            🔄 重载配置
                        </button>
                        <button class="btn btn-primary" @click="$emit('open-config')">
                            ⚙️ 配置管理
                        </button>
                    </div>
                </div>
            </div>
            
            <!-- 配置概览卡片 -->
            <div class="card">
                <div class="card-header">
                    <div class="card-icon">⚙️</div>
                    <div class="card-title">配置概览</div>
                </div>
                <div class="card-content">
                    <div class="status-grid">
                        <div class="status-item">
                            <span class="status-label">调试模式</span>
                            <span class="status-value">{{ config.basic.debugEnabled ? '✅ 开启' : '❌ 关闭' }}</span>
                        </div>
                        <div class="status-item">
                            <span class="status-label">聊天功能</span>
                            <span class="status-value">{{ config.basic.chatEnabled ? '✅ 开启' : '❌ 关闭' }}</span>
                        </div>
                        <div class="status-item">
                            <span class="status-label">性能优化</span>
                            <span class="status-value">{{ config.performance.autoOptimizeEnabled ? '✅ 自动' : '❌ 手动' }}</span>
                        </div>
                        <div class="status-item">
                            <span class="status-label">聊天前缀</span>
                            <span class="status-value">{{ config.basic.chatPrefix || '无' }}</span>
                        </div>
                    </div>
                </div>
            </div>
            
            <!-- 性能基准测试卡片 -->
            <div class="card">
                <div class="card-header">
                    <div class="card-icon">🔥</div>
                    <div class="card-title">性能基准测试</div>
                </div>
                <div class="card-content">
                    <div class="status-grid">
                        <div class="status-item">
                            <span class="status-label">测试状态</span>
                            <span class="status-value">{{ status.benchmark.status }}</span>
                        </div>
                        <div class="status-item">
                            <span class="status-label">最后测试</span>
                            <span class="status-value">{{ formatLastBenchmark(status.benchmark.lastRun) }}</span>
                        </div>
                        <div class="status-item">
                            <span class="status-label">测试评分</span>
                            <span class="status-value">{{ status.benchmark.score || 'N/A' }}</span>
                        </div>
                        <div class="status-item">
                            <span class="status-label">优化建议</span>
                            <span class="status-value">{{ status.benchmark.optimizationCount || 0 }} 项</span>
                        </div>
                    </div>
                    <div class="btn-group">
                        <button class="btn btn-success" @click="$emit('open-benchmark')">
                            🚀 运行测试
                        </button>
                        <button 
                            class="btn btn-warning" 
                            @click="$emit('apply-optimizations')"
                            :disabled="!status.benchmark.optimizationCount">
                            ⚡ 应用优化
                        </button>
                    </div>
                </div>
            </div>
            
            <!-- 内存监控卡片 -->
            <div class="card">
                <div class="card-header">
                    <div class="card-icon">💾</div>
                    <div class="card-title">内存池监控</div>
                </div>
                <div class="card-content">
                    <div class="memory-monitors">
                        <memory-bar 
                            label="堆内存" 
                            :used="status.memoryPools.heap.used" 
                            :max="status.memoryPools.heap.max"
                            :percentage="status.memoryPools.heap.percentage">
                        </memory-bar>
                        <memory-bar 
                            label="非堆内存" 
                            :used="status.memoryPools.nonHeap.used" 
                            :max="status.memoryPools.nonHeap.max"
                            :percentage="status.memoryPools.nonHeap.percentage">
                        </memory-bar>
                        <memory-bar 
                            label="Eden空间" 
                            :used="status.memoryPools.eden.used" 
                            :max="status.memoryPools.eden.max"
                            :percentage="status.memoryPools.eden.percentage">
                        </memory-bar>
                        <memory-bar 
                            label="老年代" 
                            :used="status.memoryPools.oldGen.used" 
                            :max="status.memoryPools.oldGen.max"
                            :percentage="status.memoryPools.oldGen.percentage">
                        </memory-bar>
                    </div>
                    <div class="btn-group">
                        <button class="btn btn-danger" @click="$emit('run-gc')">
                            🗑️ 垃圾回收
                        </button>
                        <button class="btn btn-secondary" @click="$emit('open-memory')">
                            📊 详细信息
                        </button>
                    </div>
                </div>
            </div>
        </div>
    `,
    methods: {
        formatMode(mode) {
            const modeMap = {
                'FULL': '全功能',
                'LITE': '精简模式',
                'BASIC': '基础模式',
                'EMERGENCY': '应急模式'
            };
            return modeMap[mode] || mode;
        },
        formatLastBenchmark(timestamp) {
            if (!timestamp) return 'N/A';
            
            const date = new Date(timestamp);
            const now = new Date();
            const diff = now - date;
            
            if (diff < 60000) return '刚刚';
            if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`;
            if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`;
            
            return date.toLocaleDateString();
        }
    },
    components: {
        // 内存条组件
        'memory-bar': {
            props: ['label', 'used', 'max', 'percentage'],
            template: `
                <div class="memory-item">
                    <div class="memory-header">
                        <span class="memory-label">{{ label }}</span>
                        <span class="memory-value">{{ formatMemory(used) }} / {{ formatMemory(max) }}</span>
                    </div>
                    <div class="memory-bar">
                        <div 
                            class="memory-bar-fill"
                            :class="getBarClass(percentage)"
                            :style="{ width: percentage + '%' }">
                        </div>
                    </div>
                </div>
            `,
            methods: {
                formatMemory(bytes) {
                    if (!bytes || bytes < 0) return '0 MB';
                    
                    const mb = bytes / (1024 * 1024);
                    if (mb < 1024) {
                        return mb.toFixed(1) + ' MB';
                    }
                    
                    const gb = mb / 1024;
                    return gb.toFixed(2) + ' GB';
                },
                getBarClass(percentage) {
                    if (percentage >= 90) return 'danger';
                    if (percentage >= 75) return 'warning';
                    return '';
                }
            }
        }
    }
}; 