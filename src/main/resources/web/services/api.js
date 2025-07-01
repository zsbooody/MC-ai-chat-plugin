// API服务模块 - 统一管理所有后端通信
const ApiService = {
    // 基础API URL
    baseUrl: window.location.origin,
    token: null,
    
    // 初始化
    init() {
        // 从localStorage恢复令牌
        this.token = localStorage.getItem('ai_chat_token');
    },
    
    // 获取请求头
    getHeaders() {
        const headers = {
            'Content-Type': 'application/json'
        };
        
        if (this.token) {
            headers['Authorization'] = `Bearer ${this.token}`;
        }
        
        return headers;
    },
    
    // 处理响应
    async handleResponse(response) {
        if (response.status === 401) {
            // 令牌无效，清除并重定向到登录
            this.logout();
            throw new Error('认证失败，请重新登录');
        }
        
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error || '请求失败');
        }
        
        return data;
    },
    
    // 登录
    async login(accessToken) {
        try {
            const response = await fetch(`${this.baseUrl}/api/auth/login`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ token: accessToken })
            });
            
            const data = await this.handleResponse(response);
            
            if (data.success) {
                this.token = data.token;
                localStorage.setItem('ai_chat_token', this.token);
                return true;
            }
            
            return false;
        } catch (error) {
            console.error('登录失败:', error);
            throw error;
        }
    },
    
    // 验证令牌
    async verifyToken() {
        if (!this.token) {
            return false;
        }
        
        try {
            const response = await fetch(`${this.baseUrl}/api/auth/verify`, {
                method: 'POST',
                headers: this.getHeaders()
            });
            
            const data = await this.handleResponse(response);
            return data.valid === true;
        } catch (error) {
            console.error('令牌验证失败:', error);
            return false;
        }
    },
    
    // 登出
    logout() {
        this.token = null;
        localStorage.removeItem('ai_chat_token');
    },
    
    // 通用请求方法
    async request(url, options = {}) {
        try {
            const response = await fetch(`${this.baseUrl}${url}`, {
                ...options,
                headers: {
                    ...this.getHeaders(),
                    ...options.headers
                }
            });
            
            return await this.handleResponse(response);
        } catch (error) {
            console.error('API请求错误:', error);
            throw error;
        }
    },
    
    // 获取系统状态
    async getStatus() {
        return this.request('/api/status');
    },
    
    // 获取配置
    async getConfig() {
        return this.request('/api/config');
    },
    
    // 更新多个配置项
    async updateMultipleConfigs(configs) {
        return this.request('/api/config', {
            method: 'POST',
            body: JSON.stringify(configs)
        });
    },
    
    // 重载配置
    async reloadConfig() {
        return this.request('/api/config/reload', {
            method: 'POST'
        });
    },
    
    // 保存历史记录
    async saveHistory() {
        return this.request('/api/actions/save-history', {
            method: 'POST'
        });
    },
    
    // 应用优化
    async applyOptimization() {
        return this.request('/api/benchmark/apply-optimizations', {
            method: 'POST'
        });
    },

    // 状态相关API
    status: {
        // 获取系统状态
        async get() {
            return ApiService.request('/api/status');
        },
        
        // 获取内存详情
        async getMemoryDetails() {
            return ApiService.request('/api/status/memory-details');
        }
    },
    
    // 配置相关API
    config: {
        // 获取配置
        async get() {
            return ApiService.request('/api/config');
        },
        
        // 更新配置
        async update(configData) {
            return ApiService.request('/api/config', {
                method: 'POST',
                body: JSON.stringify(configData)
            });
        },
        
        // 重载配置
        async reload() {
            return ApiService.request('/api/config/reload', {
                method: 'POST'
            });
        },
        
        // 重置配置
        async reset() {
            return ApiService.request('/api/config/reset', {
                method: 'POST'
            });
        }
    },
    
    // 基准测试相关API
    benchmark: {
        // 开始测试
        async start(type, duration) {
            return ApiService.request('/api/benchmark/start', {
                method: 'POST',
                body: JSON.stringify({ type, duration })
            });
        },
        
        // 获取测试状态
        async getStatus() {
            return ApiService.request('/api/benchmark/status');
        },
        
        // 停止测试
        async stop() {
            return ApiService.request('/api/benchmark/stop', {
                method: 'POST'
            });
        },
        
        // 应用优化
        async applyOptimizations() {
            return ApiService.request('/api/benchmark/apply-optimizations', {
                method: 'POST'
            });
        }
    },
    
    // 操作相关API
    actions: {
        // 运行垃圾回收
        async runGC() {
            return ApiService.request('/api/actions/run-gc', {
                method: 'POST'
            });
        }
    }
};

// 状态轮询管理器
const StatusPoller = {
    intervalId: null,
    callbacks: [],
    
    // 添加回调
    addCallback(callback) {
        this.callbacks.push(callback);
    },
    
    // 移除回调
    removeCallback(callback) {
        this.callbacks = this.callbacks.filter(cb => cb !== callback);
    },
    
    // 开始轮询
    start(interval = 5000) {
        if (this.intervalId) return;
        
        // 立即执行一次
        this.poll();
        
        // 设置定时器
        this.intervalId = setInterval(() => {
            this.poll();
        }, interval);
    },
    
    // 停止轮询
    stop() {
        if (this.intervalId) {
            clearInterval(this.intervalId);
            this.intervalId = null;
        }
    },
    
    // 执行轮询
    async poll() {
        try {
            const status = await ApiService.status.get();
            // 支持异步回调函数
            for (const callback of this.callbacks) {
                try {
                    await callback(status);
                } catch (error) {
                    console.error('轮询回调执行失败:', error);
                }
            }
        } catch (error) {
            console.error('状态轮询失败:', error);
        }
    }
};

// 将API服务暴露到全局
window.api = ApiService;
window.StatusPoller = StatusPoller;

// 初始化API服务
ApiService.init(); 