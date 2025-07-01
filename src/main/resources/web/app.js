// Vue 3 主应用
const { createApp, ref, reactive, computed, onMounted, onUnmounted } = Vue;

// 主应用组件
const AppContainer = {
    template: '#app-container-template',
    setup() {
        // 认证状态
        const isAuthenticated = ref(false);
        const isCheckingAuth = ref(true);
        
        // 响应式状态
        const systemStatus = reactive({
            version: 'v1.1.0',
            status: '运行中',
            tps: 20.0,
            operationMode: 'FULL',
            cpuUsage: 0,
            memoryUsage: 0,
            onlinePlayers: 0,
            memoryPools: {
                heap: { used: 0, max: 1024 * 1024 * 1024, percentage: 0 },
                nonHeap: { used: 0, max: 256 * 1024 * 1024, percentage: 0 },
                eden: { used: 0, max: 512 * 1024 * 1024, percentage: 0 },
                oldGen: { used: 0, max: 768 * 1024 * 1024, percentage: 0 }
            },
            benchmark: {
                status: '未运行',
                lastRun: null,
                score: null,
                optimizationCount: 0
            }
        });
        
        const systemConfig = reactive({
            basic: {
                debugEnabled: false,
                chatEnabled: true,
                chatPrefix: '',
                broadcastEnabled: false
            },
            ai: {
                apiKey: '',
                model: 'deepseek-chat',
                temperature: 0.7,
                maxTokens: 200
            },
            performance: {
                autoOptimizeEnabled: true,
                tpsThresholdFull: 18.0,
                tpsThresholdLite: 15.0,
                tpsThresholdBasic: 10.0
            },
            environment: {
                entityRange: 10,
                blockScanRange: 10,
                showWeather: true,
                showTime: true
            },
            chat: {
                normalUserCooldown: 3000,
                vipUserCooldown: 1000,
                maxMessagesPerMinute: 10,
                contentFilterEnabled: false
            },
            events: {
                joinEnabled: true,
                joinCooldown: 30000,
                quitEnabled: false,
                quitCooldown: 30000,
                damageEnabled: true,
                damageCooldown: 10000,
                damageThreshold: 10.0
            }
        });
        
        // 面板显示状态
        const showConfigPanel = ref(false);
        const showBenchmarkPanel = ref(false);
        const showMemoryPanel = ref(false);
        
        // 通知状态
        const notification = reactive({
            show: false,
            type: 'info',
            message: ''
        });
        
        // 显示通知
        const showNotification = (message, type = 'success') => {
            notification.message = message;
            notification.type = type;
            notification.show = true;
            
            // 3秒后自动隐藏
            setTimeout(() => {
                notification.show = false;
            }, 3000);
        };
        
        // 检查认证状态
        const checkAuth = async () => {
            try {
                isCheckingAuth.value = true;
                const isValid = await window.api.verifyToken();
                isAuthenticated.value = isValid;
                return isValid;
            } catch (error) {
                console.error('认证检查失败:', error);
                isAuthenticated.value = false;
                return false;
            } finally {
                isCheckingAuth.value = false;
            }
        };
        
        // 处理登录成功
        const handleLoginSuccess = async () => {
            isAuthenticated.value = true;
            showNotification('登录成功', 'success');
            
            // 登录成功后加载数据
            await Promise.all([loadStatus(), loadConfig()]);
            
            // 开始状态轮询
            startPolling();
        };
        
        // 处理登出
        const handleLogout = () => {
            window.api.logout();
            isAuthenticated.value = false;
            showNotification('已退出登录', 'info');
            
            // 停止轮询
            StatusPoller.stop();
        };
        
        // 加载系统状态
        const loadStatus = async () => {
            try {
                const status = await window.api.getStatus();
                
                // 更新基础状态
                systemStatus.version = status.version || 'v1.1.0';
                systemStatus.status = status.running ? '运行中' : '已停止';
                systemStatus.tps = status.tps || 20.0;
                systemStatus.operationMode = status.operationMode || 'FULL';
                systemStatus.cpuUsage = status.cpuUsage || 0;
                systemStatus.memoryUsage = status.memoryUsage || 0;
                systemStatus.onlinePlayers = status.onlinePlayers || 0;
                
                // 更新内存池信息
                if (status.memoryPools) {
                    Object.assign(systemStatus.memoryPools, status.memoryPools);
                }
                
                // 更新基准测试信息
                if (status.benchmark) {
                    Object.assign(systemStatus.benchmark, status.benchmark);
                }
                
            } catch (error) {
                console.error('加载状态失败:', error);
                if (error.message.includes('认证')) {
                    handleLogout();
                } else {
                    showNotification('加载状态失败', 'error');
                }
            }
        };
        
        // 加载系统配置
        const loadConfig = async () => {
            try {
                const config = await window.api.getConfig();
                
                // 深度合并配置
                if (config.basic) Object.assign(systemConfig.basic, config.basic);
                if (config.ai) Object.assign(systemConfig.ai, config.ai);
                if (config.performance) Object.assign(systemConfig.performance, config.performance);
                if (config.environment) Object.assign(systemConfig.environment, config.environment);
                if (config.chat) Object.assign(systemConfig.chat, config.chat);
                if (config.events) Object.assign(systemConfig.events, config.events);
                
            } catch (error) {
                console.error('加载配置失败:', error);
                if (error.message.includes('认证')) {
                    handleLogout();
                } else {
                    showNotification('加载配置失败', 'error');
                }
            }
        };
        
        // 将嵌套对象转换为扁平化的键值对
        const flattenConfig = (obj, prefix = '') => {
            const flattened = {};
            
            for (const key in obj) {
                if (obj.hasOwnProperty(key)) {
                    const value = obj[key];
                    const fullKey = prefix ? `${prefix}.${key}` : key;
                    
                    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
                        // 递归处理嵌套对象
                        Object.assign(flattened, flattenConfig(value, fullKey));
                    } else {
                        // 直接赋值
                        flattened[fullKey] = value;
                    }
                }
            }
            
            return flattened;
        };
        
        // 保存配置
        const saveConfig = async (updatedConfig) => {
            try {
                showNotification('正在保存配置...', 'info');
                
                // 将嵌套的配置对象转换为扁平化的键值对
                const flattenedConfig = flattenConfig(updatedConfig);
                
                const configs = Object.entries(flattenedConfig).map(([key, value]) => ({
                    key,
                    value
                }));
                
                const result = await window.api.updateMultipleConfigs(configs);
                
                if (result.success) {
                    Object.assign(systemConfig, updatedConfig);
                    showNotification('配置保存成功');
                    showConfigPanel.value = false;
                } else {
                    throw new Error(result.message || '保存失败');
                }
            } catch (error) {
                console.error('保存配置失败:', error);
                showNotification(error.message, 'error');
            }
        };
        
        // 重载配置
        const reloadConfig = async () => {
            try {
                showNotification('正在重载配置...', 'info');
                const result = await window.api.reloadConfig();
                
                if (result.success) {
                    await loadConfig();
                    showNotification('配置重载成功');
                } else {
                    throw new Error(result.message || '重载失败');
                }
            } catch (error) {
                console.error('重载配置失败:', error);
                showNotification(error.message, 'error');
            }
        };
        
        // 保存历史记录
        const saveHistory = async () => {
            try {
                showNotification('正在保存历史记录...', 'info');
                const result = await window.api.saveHistory();
                
                if (result.success) {
                    showNotification('历史记录保存成功');
                } else {
                    throw new Error(result.message || '保存失败');
                }
            } catch (error) {
                console.error('保存历史记录失败:', error);
                showNotification(error.message, 'error');
            }
        };
        
        // 应用优化
        const applyOptimizations = async () => {
            try {
                showNotification('正在应用优化...', 'info');
                const result = await window.api.applyOptimization();
                
                if (result.success) {
                    showNotification('优化应用成功');
                    await loadStatus();
                    await loadConfig();
                } else {
                    throw new Error(result.message || '应用失败');
                }
            } catch (error) {
                console.error('应用优化失败:', error);
                showNotification(error.message, 'error');
            }
        };
        
        // 运行垃圾回收
        const runGC = async () => {
            try {
                showNotification('正在运行垃圾回收...', 'info');
                const result = await window.api.actions.runGC();
                
                if (result.success) {
                    showNotification('垃圾回收完成');
                    await loadStatus();
                } else {
                    throw new Error(result.message || '垃圾回收失败');
                }
            } catch (error) {
                console.error('垃圾回收失败:', error);
                showNotification(error.message, 'error');
            }
        };
        
        // 开始状态轮询
        const startPolling = () => {
            // 设置状态轮询回调
            StatusPoller.addCallback(async () => {
                if (isAuthenticated.value) {
                    await loadStatus();
                }
            });
            
            StatusPoller.start(5000); // 每5秒轮询一次
        };
        
        // 生命周期钩子
        onMounted(async () => {
            // 检查认证状态
            const authenticated = await checkAuth();
            
            if (authenticated) {
                // 已认证，加载数据并开始轮询
                await Promise.all([loadStatus(), loadConfig()]);
                startPolling();
            }
        });
        
        onUnmounted(() => {
            // 停止轮询
            StatusPoller.stop();
        });
        
        return {
            // 认证相关
            isAuthenticated,
            isCheckingAuth,
            handleLoginSuccess,
            handleLogout,
            
            // 状态和配置
            systemStatus,
            systemConfig,
            showConfigPanel,
            showBenchmarkPanel,
            showMemoryPanel,
            notification,
            
            // 操作方法
            saveConfig,
            reloadConfig,
            saveHistory,
            applyOptimizations,
            runGC
        };
    }
};

// 创建并挂载应用
const app = createApp(AppContainer);

// 注册全局组件
app.component('login-panel', LoginPanel);
app.component('dashboard', Dashboard);
app.component('config-panel', ConfigPanel);
app.component('benchmark-panel', BenchmarkPanel);
app.component('memory-panel', MemoryPanel);
app.component('notification-toast', NotificationToast);

// 挂载应用
app.mount('#app'); 