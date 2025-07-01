// MemoryPanel组件 - 内存详细信息面板
const MemoryPanel = {
    emits: ['close'],
    setup(props, { emit }) {
        const { reactive, onMounted } = Vue;
        
        // 内存详情数据
        const memoryDetails = reactive({
            heap: {
                eden: { used: 0, max: 0, percentage: 0 },
                survivor: { used: 0, max: 0, percentage: 0 },
                oldGen: { used: 0, max: 0, percentage: 0 }
            },
            nonHeap: {
                metaspace: { used: 0, max: 0, percentage: 0 },
                codeCache: { used: 0, max: 0, percentage: 0 },
                compressedClass: { used: 0, max: 0, percentage: 0 }
            },
            gc: {
                minorCount: 0,
                majorCount: 0,
                totalTime: 0
            }
        });
        
        // 格式化内存大小
        const formatMemory = (bytes) => {
            if (!bytes || bytes < 0) return '0 MB';
            
            const mb = bytes / (1024 * 1024);
            if (mb < 1024) {
                return mb.toFixed(1) + ' MB';
            }
            
            const gb = mb / 1024;
            return gb.toFixed(2) + ' GB';
        };
        
        // 格式化时间
        const formatTime = (ms) => {
            if (ms < 1000) return ms + ' ms';
            return (ms / 1000).toFixed(2) + ' s';
        };
        
        // 加载内存详情
        const loadMemoryDetails = async () => {
            try {
                const details = await window.api.status.getMemoryDetails();
                
                // 更新堆内存数据
                if (details.heap) {
                    Object.assign(memoryDetails.heap, details.heap);
                }
                
                // 更新非堆内存数据
                if (details.nonHeap) {
                    Object.assign(memoryDetails.nonHeap, details.nonHeap);
                }
                
                // 更新GC数据
                if (details.gc) {
                    Object.assign(memoryDetails.gc, details.gc);
                }
                
            } catch (error) {
                console.error('加载内存详情失败:', error);
            }
        };
        
        // 组件挂载时加载数据
        onMounted(() => {
            loadMemoryDetails();
        });
        
        return {
            memoryDetails,
            formatMemory,
            formatTime
        };
    },
    template: `
        <div class="panel-overlay" @click.self="$emit('close')">
            <div class="panel" style="max-width: 700px;">
                <div class="panel-header">
                    <h2 class="panel-title">
                        <span>💾</span>
                        内存池详细信息
                    </h2>
                    <button class="panel-close" @click="$emit('close')">&times;</button>
                </div>
                
                <div class="panel-body">
                    <!-- 堆内存池 -->
                    <div class="memory-section">
                        <h3 class="section-title">🏠 堆内存池</h3>
                        <div class="memory-detail-grid">
                            <div class="memory-detail-item">
                                <div class="detail-header">
                                    <span class="detail-label">年轻代 (Eden)</span>
                                    <span class="detail-value">{{ formatMemory(memoryDetails.heap.eden.used) }} / {{ formatMemory(memoryDetails.heap.eden.max) }}</span>
                                </div>
                                <div class="memory-bar">
                                    <div 
                                        class="memory-bar-fill"
                                        :class="{ warning: memoryDetails.heap.eden.percentage > 75, danger: memoryDetails.heap.eden.percentage > 90 }"
                                        :style="{ width: memoryDetails.heap.eden.percentage + '%' }">
                                    </div>
                                </div>
                            </div>
                            
                            <div class="memory-detail-item">
                                <div class="detail-header">
                                    <span class="detail-label">年轻代 (Survivor)</span>
                                    <span class="detail-value">{{ formatMemory(memoryDetails.heap.survivor.used) }} / {{ formatMemory(memoryDetails.heap.survivor.max) }}</span>
                                </div>
                                <div class="memory-bar">
                                    <div 
                                        class="memory-bar-fill"
                                        :class="{ warning: memoryDetails.heap.survivor.percentage > 75, danger: memoryDetails.heap.survivor.percentage > 90 }"
                                        :style="{ width: memoryDetails.heap.survivor.percentage + '%' }">
                                    </div>
                                </div>
                            </div>
                            
                            <div class="memory-detail-item">
                                <div class="detail-header">
                                    <span class="detail-label">老年代</span>
                                    <span class="detail-value">{{ formatMemory(memoryDetails.heap.oldGen.used) }} / {{ formatMemory(memoryDetails.heap.oldGen.max) }}</span>
                                </div>
                                <div class="memory-bar">
                                    <div 
                                        class="memory-bar-fill"
                                        :class="{ warning: memoryDetails.heap.oldGen.percentage > 75, danger: memoryDetails.heap.oldGen.percentage > 90 }"
                                        :style="{ width: memoryDetails.heap.oldGen.percentage + '%' }">
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    
                    <!-- 非堆内存池 -->
                    <div class="memory-section">
                        <h3 class="section-title">⚙️ 非堆内存池</h3>
                        <div class="memory-detail-grid">
                            <div class="memory-detail-item">
                                <div class="detail-header">
                                    <span class="detail-label">方法区 (Metaspace)</span>
                                    <span class="detail-value">{{ formatMemory(memoryDetails.nonHeap.metaspace.used) }} / {{ formatMemory(memoryDetails.nonHeap.metaspace.max) }}</span>
                                </div>
                                <div class="memory-bar">
                                    <div 
                                        class="memory-bar-fill"
                                        :style="{ width: memoryDetails.nonHeap.metaspace.percentage + '%' }">
                                    </div>
                                </div>
                            </div>
                            
                            <div class="memory-detail-item">
                                <div class="detail-header">
                                    <span class="detail-label">代码缓存</span>
                                    <span class="detail-value">{{ formatMemory(memoryDetails.nonHeap.codeCache.used) }} / {{ formatMemory(memoryDetails.nonHeap.codeCache.max) }}</span>
                                </div>
                                <div class="memory-bar">
                                    <div 
                                        class="memory-bar-fill"
                                        :style="{ width: memoryDetails.nonHeap.codeCache.percentage + '%' }">
                                    </div>
                                </div>
                            </div>
                            
                            <div class="memory-detail-item">
                                <div class="detail-header">
                                    <span class="detail-label">压缩类空间</span>
                                    <span class="detail-value">{{ formatMemory(memoryDetails.nonHeap.compressedClass.used) }} / {{ formatMemory(memoryDetails.nonHeap.compressedClass.max) }}</span>
                                </div>
                                <div class="memory-bar">
                                    <div 
                                        class="memory-bar-fill"
                                        :style="{ width: memoryDetails.nonHeap.compressedClass.percentage + '%' }">
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    
                    <!-- GC统计 -->
                    <div class="memory-section">
                        <h3 class="section-title">📊 垃圾回收统计</h3>
                        <div class="gc-stats">
                            <div class="stat-item">
                                <span class="stat-label">Minor GC 次数</span>
                                <span class="stat-value">{{ memoryDetails.gc.minorCount }}</span>
                            </div>
                            <div class="stat-item">
                                <span class="stat-label">Major GC 次数</span>
                                <span class="stat-value">{{ memoryDetails.gc.majorCount }}</span>
                            </div>
                            <div class="stat-item">
                                <span class="stat-label">GC 总耗时</span>
                                <span class="stat-value">{{ formatTime(memoryDetails.gc.totalTime) }}</span>
                            </div>
                        </div>
                    </div>
                </div>
                
                <div class="panel-footer">
                    <button class="btn btn-secondary" @click="$emit('close')">
                        ❌ 关闭
                    </button>
                </div>
            </div>
        </div>
    `
}; 