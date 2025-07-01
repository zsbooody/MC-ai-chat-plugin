// ConfigPanel组件 - 配置管理面板
const ConfigPanel = {
    props: ['config'],
    emits: ['close', 'save'],
    setup(props, { emit }) {
        const { ref, reactive, computed } = Vue;
        
        // 当前激活的标签
        const activeTab = ref('basic');
        
        // 创建配置的本地副本
        const localConfig = reactive(JSON.parse(JSON.stringify(props.config)));
        
        // 切换标签
        const switchTab = (tabName) => {
            activeTab.value = tabName;
        };
        
        // 保存配置
        const handleSave = () => {
            emit('save', localConfig);
        };
        
        // 重置配置
        const handleReset = async () => {
            if (!confirm('确定要将所有配置重置为默认值吗？此操作不可撤销！')) {
                return;
            }
            
            try {
                const result = await window.api.config.reset();
                if (result.success) {
                    emit('close');
                    window.location.reload();
                }
            } catch (error) {
                alert('重置失败：' + error.message);
            }
        };
        
        return {
            activeTab,
            localConfig,
            switchTab,
            handleSave,
            handleReset
        };
    },
    template: `
        <div class="config-modal-overlay" @click.self="$emit('close')">
            <div class="config-modal">
                <div class="config-header">
                    <h2>🔧 配置管理</h2>
                    <div class="config-actions">
                        <button class="btn btn-secondary" @click="handleReset">重置配置</button>
                        <button class="btn btn-primary" @click="handleSave">保存配置</button>
                        <button class="btn btn-close" @click="$emit('close')">×</button>
                    </div>
                </div>
                
                <div class="config-content">
                    <!-- 标签导航 -->
                    <div class="config-tabs">
                        <button 
                            class="config-tab" 
                            :class="{ active: activeTab === 'basic' }"
                            @click="switchTab('basic')">
                            🔧 基础设置
                        </button>
                        <button 
                            class="config-tab" 
                            :class="{ active: activeTab === 'ai' }"
                            @click="switchTab('ai')">
                            🤖 AI配置
                        </button>
                        <button 
                            class="config-tab" 
                            :class="{ active: activeTab === 'performance' }"
                            @click="switchTab('performance')">
                            ⚡ 性能优化
                        </button>
                        <button 
                            class="config-tab" 
                            :class="{ active: activeTab === 'environment' }"
                            @click="switchTab('environment')">
                            🌍 环境检测
                        </button>
                        <button 
                            class="config-tab" 
                            :class="{ active: activeTab === 'chat' }"
                            @click="switchTab('chat')">
                            💬 聊天管理
                        </button>
                        <button 
                            class="config-tab" 
                            :class="{ active: activeTab === 'events' }"
                            @click="switchTab('events')">
                            ⚡ 事件响应
                        </button>
                    </div>
                    
                    <!-- 配置面板 -->
                    <div class="config-panels">
                        <!-- 基础设置 -->
                        <div v-show="activeTab === 'basic'" class="config-panel">
                            <h3>基础设置</h3>
                            <div class="config-group">
                                <label class="config-item">
                                    <span class="config-label">启用调试模式</span>
                                    <input type="checkbox" v-model="localConfig.basic.debugEnabled">
                                    <span class="config-desc">开启后将显示详细的调试信息</span>
                                </label>
                                
                                <label class="config-item">
                                    <span class="config-label">启用聊天功能</span>
                                    <input type="checkbox" v-model="localConfig.basic.chatEnabled">
                                    <span class="config-desc">是否允许玩家与AI聊天</span>
                                </label>
                                
                                <label class="config-item">
                                    <span class="config-label">聊天前缀</span>
                                    <input type="text" v-model="localConfig.basic.chatPrefix" placeholder="[AI助手]">
                                    <span class="config-desc">AI回复消息的前缀</span>
                                </label>
                                
                                <label class="config-item">
                                    <span class="config-label">启用广播</span>
                                    <input type="checkbox" v-model="localConfig.basic.broadcastEnabled">
                                    <span class="config-desc">是否向所有玩家广播AI消息</span>
                                </label>
                            </div>
                        </div>
                        
                        <!-- AI配置 -->
                        <div v-show="activeTab === 'ai'" class="config-panel">
                            <h3>AI配置</h3>
                            <div class="config-group">
                                <!-- 🔧 修复：将API密钥输入框包装在form标签内 -->
                                <form class="config-item">
                                    <label for="api-key-input">
                                        <span class="config-label">API密钥</span>
                                        <input 
                                            id="api-key-input"
                                            type="password" 
                                            v-model="localConfig.ai.apiKey" 
                                            placeholder="sk-xxxxxxxxxxxxxxxx"
                                            autocomplete="current-password">
                                        <span class="config-desc">DeepSeek API密钥</span>
                                    </label>
                                </form>
                                
                                <label class="config-item">
                                    <span class="config-label">API地址</span>
                                    <input type="url" v-model="localConfig.ai.apiUrl" placeholder="https://api.deepseek.com">
                                    <span class="config-desc">AI服务的API端点</span>
                                </label>
                                
                                <label class="config-item">
                                    <span class="config-label">模型名称</span>
                                    <input type="text" v-model="localConfig.ai.model" placeholder="deepseek-chat">
                                    <span class="config-desc">使用的AI模型</span>
                                </label>
                                
                                <label class="config-item">
                                    <span class="config-label">温度参数</span>
                                    <input type="range" v-model="localConfig.ai.temperature" min="0" max="2" step="0.1">
                                    <span class="config-value">{{ localConfig.ai.temperature }}</span>
                                    <span class="config-desc">控制回复的随机性 (0-2)</span>
                                </label>
                                
                                <label class="config-item">
                                    <span class="config-label">最大Token数</span>
                                    <input type="range" v-model="localConfig.ai.maxTokens" min="50" max="1000" step="50">
                                    <span class="config-value">{{ localConfig.ai.maxTokens }}</span>
                                    <span class="config-desc">单次回复的最大长度</span>
                                </label>
                            </div>
                        </div>
                        
                        <!-- 性能优化 -->
                        <div v-show="activeTab === 'performance'" class="config-panel">
                            <h3>性能优化</h3>
                            <div class="config-group">
                                <label class="config-item">
                                    <span class="config-label">启用自动优化</span>
                                    <input type="checkbox" v-model="localConfig.performance.autoOptimizeEnabled">
                                    <span class="config-desc">根据服务器性能自动调整功能</span>
                                </label>
                                
                                <label class="config-item">
                                    <span class="config-label">全功能模式TPS阈值</span>
                                    <input type="range" v-model="localConfig.performance.tpsThresholdFull" min="15" max="20" step="0.5">
                                    <span class="config-value">{{ localConfig.performance.tpsThresholdFull }}</span>
                                    <span class="config-desc">维持全功能模式的最低TPS</span>
                                </label>
                                
                                <label class="config-item">
                                    <span class="config-label">精简模式TPS阈值</span>
                                    <input type="range" v-model="localConfig.performance.tpsThresholdLite" min="12" max="18" step="0.5">
                                    <span class="config-value">{{ localConfig.performance.tpsThresholdLite }}</span>
                                    <span class="config-desc">切换到精简模式的TPS阈值</span>
                                </label>
                                
                                <label class="config-item">
                                    <span class="config-label">基础模式TPS阈值</span>
                                    <input type="range" v-model="localConfig.performance.tpsThresholdBasic" min="8" max="15" step="0.5">
                                    <span class="config-value">{{ localConfig.performance.tpsThresholdBasic }}</span>
                                    <span class="config-desc">切换到基础模式的TPS阈值</span>
                                </label>
                            </div>
                        </div>
                        
                        <!-- 环境检测 -->
                        <div v-show="activeTab === 'environment'" class="config-panel">
                            <h3>环境检测</h3>
                            <div class="config-group">
                                <label class="config-item">
                                    <span class="config-label">实体检测范围</span>
                                    <input type="range" v-model="localConfig.environment.entityRange" min="5" max="50" step="5">
                                    <span class="config-value">{{ localConfig.environment.entityRange }}格</span>
                                    <span class="config-desc">检测周围实体的范围</span>
                                </label>
                                
                                <label class="config-item">
                                    <span class="config-label">方块扫描范围</span>
                                    <input type="range" v-model="localConfig.environment.blockScanRange" min="3" max="20" step="1">
                                    <span class="config-value">{{ localConfig.environment.blockScanRange }}格</span>
                                    <span class="config-desc">扫描周围方块的范围</span>
                                </label>
                                
                                <label class="config-item">
                                    <span class="config-label">显示天气信息</span>
                                    <input type="checkbox" v-model="localConfig.environment.showWeather">
                                    <span class="config-desc">是否在环境信息中包含天气</span>
                                </label>
                                
                                <label class="config-item">
                                    <span class="config-label">显示时间信息</span>
                                    <input type="checkbox" v-model="localConfig.environment.showTime">
                                    <span class="config-desc">是否在环境信息中包含时间</span>
                                </label>
                            </div>
                        </div>
                        
                        <!-- 聊天管理 -->
                        <div v-show="activeTab === 'chat'" class="config-panel">
                            <h3>聊天管理</h3>
                            <div class="config-group">
                                <label class="config-item">
                                    <span class="config-label">普通用户冷却时间</span>
                                    <input type="range" v-model="localConfig.chat.normalUserCooldown" min="1" max="60" step="1">
                                    <span class="config-value">{{ localConfig.chat.normalUserCooldown }}秒</span>
                                    <span class="config-desc">普通玩家两次AI对话的间隔</span>
                                </label>
                                
                                <label class="config-item">
                                    <span class="config-label">VIP用户冷却时间</span>
                                    <input type="range" v-model="localConfig.chat.vipUserCooldown" min="1" max="30" step="1">
                                    <span class="config-value">{{ localConfig.chat.vipUserCooldown }}秒</span>
                                    <span class="config-desc">VIP玩家两次AI对话的间隔</span>
                                </label>
                                
                                <label class="config-item">
                                    <span class="config-label">每分钟最大消息数</span>
                                    <input type="range" v-model="localConfig.chat.maxMessagesPerMinute" min="1" max="20" step="1">
                                    <span class="config-value">{{ localConfig.chat.maxMessagesPerMinute }}条</span>
                                    <span class="config-desc">单个玩家每分钟最多发送的消息数</span>
                                </label>
                                
                                <label class="config-item">
                                    <span class="config-label">启用内容过滤</span>
                                    <input type="checkbox" v-model="localConfig.chat.contentFilterEnabled">
                                    <span class="config-desc">是否过滤不当内容</span>
                                </label>
                            </div>
                        </div>
                        
                        <!-- 事件响应 -->
                        <div v-show="activeTab === 'events'" class="config-panel">
                            <h3>事件响应配置</h3>
                            <div class="config-group">
                                <!-- 玩家加入事件 -->
                                <div class="config-section">
                                    <h4>🚪 玩家加入事件</h4>
                                    <label class="config-item">
                                        <span class="config-label">启用加入响应</span>
                                        <input type="checkbox" v-model="localConfig.events.joinEnabled">
                                        <span class="config-desc">AI是否对玩家加入服务器做出回应</span>
                                    </label>
                                    
                                    <label class="config-item">
                                        <span class="config-label">加入事件冷却时间</span>
                                        <input type="range" v-model="localConfig.events.joinCooldown" min="10" max="300" step="10">
                                        <span class="config-value">{{ Math.round(localConfig.events.joinCooldown / 1000) }}秒</span>
                                        <span class="config-desc">防止频繁加入时重复响应的冷却时间</span>
                                    </label>
                                </div>
                                
                                <!-- 玩家退出事件 -->
                                <div class="config-section">
                                    <h4>🚪 玩家退出事件</h4>
                                    <label class="config-item">
                                        <span class="config-label">启用退出响应</span>
                                        <input type="checkbox" v-model="localConfig.events.quitEnabled">
                                        <span class="config-desc">AI是否对玩家离开服务器做出回应</span>
                                    </label>
                                    
                                    <label class="config-item">
                                        <span class="config-label">退出事件冷却时间</span>
                                        <input type="range" v-model="localConfig.events.quitCooldown" min="10" max="300" step="10">
                                        <span class="config-value">{{ Math.round(localConfig.events.quitCooldown / 1000) }}秒</span>
                                        <span class="config-desc">防止频繁退出时重复响应的冷却时间</span>
                                    </label>
                                </div>
                                
                                <!-- 玩家受伤事件 -->
                                <div class="config-section">
                                    <h4>❤️ 玩家受伤事件</h4>
                                    <label class="config-item">
                                        <span class="config-label">启用受伤响应</span>
                                        <input type="checkbox" v-model="localConfig.events.damageEnabled">
                                        <span class="config-desc">AI是否对玩家受伤做出关心回应</span>
                                    </label>
                                    
                                    <label class="config-item">
                                        <span class="config-label">受伤响应阈值</span>
                                        <input type="range" v-model="localConfig.events.damageThreshold" min="1" max="19" step="1">
                                        <span class="config-value">{{ localConfig.events.damageThreshold }}❤️</span>
                                        <span class="config-desc">玩家血量低于此值时AI才会响应（满血20❤️）</span>
                                    </label>
                                    
                                    <label class="config-item">
                                        <span class="config-label">受伤事件冷却时间</span>
                                        <input type="range" v-model="localConfig.events.damageCooldown" min="5" max="60" step="5">
                                        <span class="config-value">{{ Math.round(localConfig.events.damageCooldown / 1000) }}秒</span>
                                        <span class="config-desc">防止频繁受伤时重复响应的冷却时间</span>
                                    </label>
                                </div>
                                
                                <!-- 事件响应说明 -->
                                <div class="config-note">
                                    <h4>💡 事件响应说明</h4>
                                    <ul>
                                        <li><strong>加入响应</strong>：新玩家加入时AI会发送欢迎消息</li>
                                        <li><strong>退出响应</strong>：玩家离开时AI会发送告别消息（默认关闭）</li>
                                        <li><strong>受伤响应</strong>：玩家血量过低时AI会表达关心并给出建议</li>
                                        <li><strong>冷却机制</strong>：防止相同事件频繁触发，避免刷屏</li>
                                        <li><strong>性能影响</strong>：事件响应会消耗一定的AI调用次数，请根据服务器负载调整</li>
                                    </ul>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `
}; 