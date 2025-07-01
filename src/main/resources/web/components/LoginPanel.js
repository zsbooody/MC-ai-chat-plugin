// 登录面板组件
const LoginPanel = {
    name: 'LoginPanel',
    template: `
        <div class="login-panel">
            <div class="login-container">
                <div class="login-header">
                    <h2>AI Chat Plugin 配置管理</h2>
                    <p class="login-subtitle">请输入控制台显示的访问令牌</p>
                </div>
                
                <div class="login-form">
                    <div class="form-group">
                        <label for="accessToken">访问令牌 (Access Token)</label>
                        <input 
                            id="accessToken"
                            v-model="accessToken"
                            type="password"
                            class="form-control"
                            placeholder="请输入访问令牌"
                            @keyup.enter="handleLogin"
                            :disabled="isLoading"
                        />
                        <small class="form-hint">
                            访问令牌在服务器启动时显示在控制台中
                        </small>
                    </div>
                    
                    <div v-if="errorMessage" class="alert alert-error">
                        <i class="fas fa-exclamation-circle"></i>
                        {{ errorMessage }}
                    </div>
                    
                    <button 
                        class="btn btn-primary btn-block"
                        @click="handleLogin"
                        :disabled="!accessToken || isLoading"
                    >
                        <span v-if="!isLoading">
                            <i class="fas fa-sign-in-alt"></i> 登录
                        </span>
                        <span v-else>
                            <i class="fas fa-spinner fa-spin"></i> 验证中...
                        </span>
                    </button>
                </div>
                
                <div class="login-footer">
                    <div class="security-notice">
                        <i class="fas fa-shield-alt"></i>
                        <span>安全连接 - 令牌仅在本地存储</span>
                    </div>
                </div>
            </div>
        </div>
    `,
    
    setup(props, { emit }) {
        const accessToken = Vue.ref('');
        const errorMessage = Vue.ref('');
        const isLoading = Vue.ref(false);
        
        const handleLogin = async () => {
            if (!accessToken.value || isLoading.value) {
                return;
            }
            
            errorMessage.value = '';
            isLoading.value = true;
            
            try {
                const success = await window.api.login(accessToken.value);
                
                if (success) {
                    emit('login-success');
                } else {
                    errorMessage.value = '无效的访问令牌';
                }
            } catch (error) {
                errorMessage.value = error.message || '登录失败，请检查网络连接';
            } finally {
                isLoading.value = false;
            }
        };
        
        // 自动聚焦输入框
        Vue.onMounted(() => {
            document.getElementById('accessToken')?.focus();
        });
        
        return {
            accessToken,
            errorMessage,
            isLoading,
            handleLogin
        };
    }
}; 