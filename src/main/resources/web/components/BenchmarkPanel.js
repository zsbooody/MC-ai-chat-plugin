// BenchmarkPanel组件 - 性能基准测试面板
const BenchmarkPanel = {
    emits: ['close'],
    setup(props, { emit }) {
        const { ref, reactive, onUnmounted } = Vue;
        
        // 测试状态
        const testState = reactive({
            selectedType: 'quick',
            customDuration: 5,
            running: false,
            progress: 0,
            progressText: '准备开始测试...',
            results: null
        });
        
        // 轮询定时器
        let pollingInterval = null;
        
        // 测试类型映射
        const testTypes = {
            quick: { name: '快速测试', duration: 2 },
            standard: { name: '标准测试', duration: 5 },
            comprehensive: { name: '深度测试', duration: 10 },
            custom: { name: '自定义测试', duration: null }
        };
        
        // 选择测试类型
        const selectTestType = (type) => {
            testState.selectedType = type;
        };
        
        // 开始测试
        const startBenchmark = async () => {
            try {
                const type = testState.selectedType;
                const duration = type === 'custom' ? testState.customDuration : testTypes[type].duration;
                
                testState.running = true;
                testState.progress = 0;
                testState.progressText = '正在启动测试...';
                
                const result = await window.api.benchmark.start(type, duration);
                
                if (result.success) {
                    // 开始轮询状态
                    startPolling();
                } else {
                    throw new Error(result.message || '启动测试失败');
                }
            } catch (error) {
                console.error('启动测试失败:', error);
                alert('启动测试失败: ' + error.message);
                testState.running = false;
            }
        };
        
        // 停止测试
        const stopBenchmark = async () => {
            try {
                await window.api.benchmark.stop();
                stopPolling();
                testState.running = false;
                testState.progressText = '测试已停止';
            } catch (error) {
                console.error('停止测试失败:', error);
            }
        };
        
        // 开始轮询
        const startPolling = () => {
            pollingInterval = setInterval(async () => {
                try {
                    const status = await window.api.benchmark.getStatus();
                    
                    if (status.running) {
                        testState.running = true;
                        testState.progress = status.progress || 0;
                        testState.progressText = status.message || '测试进行中...';
                        
                        if (status.completed) {
                            testState.running = false;
                            testState.progress = 100;
                            testState.progressText = '测试完成';
                            testState.results = status.results;
                            stopPolling();
                        }
                    } else {
                        testState.running = false;
                        if (status.results) {
                            testState.results = status.results;
                        }
                    }
                } catch (error) {
                    console.error('获取测试状态失败:', error);
                }
            }, 2000);
        };
        
        // 停止轮询
        const stopPolling = () => {
            if (pollingInterval) {
                clearInterval(pollingInterval);
                pollingInterval = null;
            }
        };
        
        // 组件卸载时清理
        onUnmounted(() => {
            stopPolling();
        });
        
        return {
            testState,
            testTypes,
            selectTestType,
            startBenchmark,
            stopBenchmark
        };
    },
    template: `
        <div class="panel-overlay" @click.self="$emit('close')">
            <div class="panel" style="max-width: 600px;">
                <div class="panel-header">
                    <h2 class="panel-title">
                        <span>🔥</span>
                        性能基准测试
                    </h2>
                    <button class="panel-close" @click="$emit('close')" :disabled="testState.running">&times;</button>
                </div>
                
                <div class="panel-body">
                    <div v-if="!testState.running">
                        <p class="mb-3">选择测试类型来评估服务器性能并生成优化建议：</p>
                        
                        <!-- 测试选项 -->
                        <div class="test-options">
                            <label class="test-option" :class="{ selected: testState.selectedType === 'quick' }">
                                <input 
                                    type="radio" 
                                    name="test-type" 
                                    value="quick"
                                    v-model="testState.selectedType">
                                <div class="test-info">
                                    <div class="test-title">⚡ 快速测试 (2分钟)</div>
                                    <div class="test-description">基础性能评估，适合日常检查</div>
                                </div>
                            </label>
                            
                            <label class="test-option" :class="{ selected: testState.selectedType === 'standard' }">
                                <input 
                                    type="radio" 
                                    name="test-type" 
                                    value="standard"
                                    v-model="testState.selectedType">
                                <div class="test-info">
                                    <div class="test-title">🔍 标准测试 (5分钟)</div>
                                    <div class="test-description">全面性能分析，包含负载测试和优化建议</div>
                                </div>
                            </label>
                            
                            <label class="test-option" :class="{ selected: testState.selectedType === 'comprehensive' }">
                                <input 
                                    type="radio" 
                                    name="test-type" 
                                    value="comprehensive"
                                    v-model="testState.selectedType">
                                <div class="test-info">
                                    <div class="test-title">🔬 深度测试 (10分钟)</div>
                                    <div class="test-description">详细的性能基准测试，包含压力测试和详细报告</div>
                                </div>
                            </label>
                            
                            <label class="test-option" :class="{ selected: testState.selectedType === 'custom' }">
                                <input 
                                    type="radio" 
                                    name="test-type" 
                                    value="custom"
                                    v-model="testState.selectedType">
                                <div class="test-info">
                                    <div class="test-title">⚙️ 自定义测试</div>
                                    <div class="test-description">自定义测试持续时间和参数</div>
                                </div>
                            </label>
                        </div>
                        
                        <!-- 自定义时长 -->
                        <div v-if="testState.selectedType === 'custom'" class="form-group mt-3">
                            <label class="form-label">测试持续时间 (分钟):</label>
                            <input 
                                type="number" 
                                class="form-control" 
                                v-model.number="testState.customDuration"
                                min="1" 
                                max="60">
                        </div>
                    </div>
                    
                    <!-- 进度显示 -->
                    <div v-else>
                        <div class="text-center mb-3">
                            <div class="loading-spinner" style="width: 60px; height: 60px; margin: 0 auto;"></div>
                        </div>
                        
                        <div class="progress-bar">
                            <div 
                                class="progress-fill" 
                                :style="{ width: testState.progress + '%' }">
                            </div>
                        </div>
                        
                        <p class="text-center mt-3">{{ testState.progressText }}</p>
                        <p class="text-center text-secondary">{{ testState.progress.toFixed(0) }}%</p>
                    </div>
                </div>
                
                <div class="panel-footer">
                    <template v-if="!testState.running">
                        <button class="btn btn-success" @click="startBenchmark">
                            🚀 开始测试
                        </button>
                        <button class="btn btn-secondary" @click="$emit('close')">
                            ❌ 取消
                        </button>
                    </template>
                    <template v-else>
                        <button class="btn btn-danger" @click="stopBenchmark">
                            ⏹️ 停止测试
                        </button>
                    </template>
                </div>
            </div>
        </div>
    `,
    
    // 内部样式
    mounted() {
        // 添加测试选项的样式
        const style = document.createElement('style');
        style.textContent = `
            .test-options {
                display: flex;
                flex-direction: column;
                gap: 1rem;
            }
            
            .test-option {
                display: flex;
                align-items: center;
                padding: 1rem;
                border: 2px solid var(--border-color);
                border-radius: var(--radius-md);
                cursor: pointer;
                transition: all var(--transition-fast);
                position: relative;
            }
            
            .test-option:hover {
                border-color: var(--primary-color);
                background: var(--bg-secondary);
            }
            
            .test-option.selected {
                border-color: var(--primary-color);
                background: rgba(102, 126, 234, 0.1);
            }
            
            .test-option input[type="radio"] {
                margin-right: 1rem;
            }
            
            .test-info {
                flex: 1;
            }
            
            .test-title {
                font-weight: 600;
                color: var(--text-primary);
                margin-bottom: 0.25rem;
            }
            
            .test-description {
                font-size: 0.875rem;
                color: var(--text-secondary);
            }
        `;
        document.head.appendChild(style);
    }
}; 