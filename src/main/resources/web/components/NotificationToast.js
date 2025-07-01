// NotificationToast组件 - 通知提示
const NotificationToast = {
    props: {
        type: {
            type: String,
            default: 'info',
            validator: (value) => ['success', 'error', 'info', 'warning'].includes(value)
        },
        message: {
            type: String,
            required: true
        }
    },
    emits: ['close'],
    setup(props, { emit }) {
        const { onMounted } = Vue;
        
        // 自动关闭定时器
        let autoCloseTimer = null;
        
        // 获取图标
        const getIcon = () => {
            const icons = {
                success: '✅',
                error: '❌',
                info: 'ℹ️',
                warning: '⚠️'
            };
            return icons[props.type] || icons.info;
        };
        
        // 获取类名
        const getClassName = () => {
            return `notification notification-${props.type}`;
        };
        
        onMounted(() => {
            // 3秒后自动关闭
            autoCloseTimer = setTimeout(() => {
                emit('close');
            }, 3000);
        });
        
        // 手动关闭
        const handleClose = () => {
            if (autoCloseTimer) {
                clearTimeout(autoCloseTimer);
            }
            emit('close');
        };
        
        return {
            getIcon,
            getClassName,
            handleClose
        };
    },
    template: `
        <div :class="getClassName()">
            <span class="notification-icon">{{ getIcon() }}</span>
            <span class="notification-message">{{ message }}</span>
            <button class="notification-close" @click="handleClose">&times;</button>
        </div>
    `
}; 