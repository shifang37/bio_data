<template>
  <div v-if="visible" class="export-dialog-overlay" @click="handleOverlayClick">
    <div class="export-dialog" @click.stop>
      <div class="export-dialog-header">
        <h3>导出表数据</h3>
        <button class="close-btn" @click="close">×</button>
      </div>
      
      <div class="export-dialog-content">
        <!-- 表信息显示 -->
        <div class="table-info" v-if="exportInfo">
          <h4>表信息</h4>
          <div class="info-grid">
            <div class="info-item">
              <label>表名:</label>
              <span>{{ exportInfo.tableName }}</span>
            </div>
            <div class="info-item">
              <label>数据源:</label>
              <span>{{ exportInfo.dataSource }}</span>
            </div>
            <div class="info-item">
              <label>列数:</label>
              <span>{{ exportInfo.columnCount }}</span>
            </div>
            <div class="info-item">
              <label>总行数:</label>
              <span>{{ exportInfo.totalRows }}</span>
            </div>
          </div>
        </div>

        <!-- 导出选项 -->
        <div class="export-options">
          <h4>导出选项</h4>
          
          <!-- 导出格式选择 -->
          <div class="option-group">
            <label>导出格式:</label>
            <div class="format-options">
              <label class="format-option">
                <input type="radio" v-model="exportFormat" value="csv">
                <span>CSV (.csv)</span>
              </label>
              <label class="format-option">
                <input type="radio" v-model="exportFormat" value="excel">
                <span>Excel (.xlsx)</span>
              </label>
            </div>
          </div>

          <!-- 数据量限制 -->
          <div class="option-group">
            <label for="limit">导出行数限制:</label>
            <select id="limit" v-model="limit">
              <option value="1000">1,000 行</option>
              <option value="5000">5,000 行</option>
              <option value="10000">10,000 行</option>
              <option value="50000">50,000 行</option>
              <option value="100000">100,000 行</option>
              <option value="0">全部数据</option>
            </select>
          </div>

          <!-- WHERE条件 -->
          <div class="option-group">
            <label for="whereClause">WHERE条件 (可选):</label>
            <textarea 
              id="whereClause" 
              v-model="whereClause" 
              placeholder="例如: id > 100 AND status = 'active'"
              rows="3"
            ></textarea>
            <small class="help-text">输入SQL WHERE条件来过滤要导出的数据</small>
          </div>

          <!-- 列选择 -->
          <div class="option-group" v-if="exportInfo && exportInfo.columns">
            <label>选择要导出的列:</label>
            <div class="column-selection">
              <div class="column-controls">
                <button @click="selectAllColumns" class="btn-small">全选</button>
                <button @click="deselectAllColumns" class="btn-small">全不选</button>
              </div>
              <div class="column-list">
                <label v-for="column in exportInfo.columns" :key="column.name" class="column-item">
                  <input 
                    type="checkbox" 
                    :value="column.name" 
                    v-model="selectedColumns"
                  >
                  <span class="column-name">{{ column.name }}</span>
                  <span class="column-type">({{ column.type }})</span>
                </label>
              </div>
            </div>
          </div>
        </div>

        <!-- 导出进度 -->
        <div class="export-progress" v-if="isExporting">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: progress + '%' }"></div>
          </div>
          <p>{{ progressText }}</p>
        </div>
      </div>

      <div class="export-dialog-footer">
        <button @click="close" class="btn btn-secondary" :disabled="isExporting">取消</button>
        <button @click="startExport" class="btn btn-primary" :disabled="isExporting || !canExport">
          {{ isExporting ? '导出中...' : '开始导出' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, watch, onMounted } from 'vue'
import { databaseApi } from '../utils/api'
import { userState } from '../utils/api'

export default {
  name: 'ExportDialog',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    tableName: {
      type: String,
      required: true
    },
    dataSource: {
      type: String,
      default: 'login'
    }
  },
  emits: ['close'],
  setup(props, { emit }) {
    const exportFormat = ref('csv')
    const limit = ref(10000)
    const whereClause = ref('')
    const selectedColumns = ref([])
    const exportInfo = ref(null)
    const isExporting = ref(false)
    const progress = ref(0)
    const progressText = ref('')

    // 计算属性
    const canExport = computed(() => {
      return exportInfo.value && selectedColumns.value.length > 0
    })

    // 监听表名变化，重新加载导出信息
    watch(() => props.tableName, async (newTableName) => {
      if (newTableName && props.visible) {
        await loadExportInfo()
      }
    })

    // 监听对话框显示状态
    watch(() => props.visible, async (newVisible) => {
      if (newVisible && props.tableName) {
        await loadExportInfo()
      }
    })

    // 加载导出信息
    const loadExportInfo = async () => {
      try {
        const userInfo = userState.getUserInfo()
        const response = await databaseApi.getExportInfo(
          props.tableName,
          props.dataSource,
          userInfo.userId,
          userInfo.userType,
          whereClause.value || null
        )
        
        if (response.data.success) {
          exportInfo.value = response.data.exportInfo
          // 默认选择所有列
          selectedColumns.value = exportInfo.value.columns.map(col => col.name)
        }
      } catch (error) {
        console.error('加载导出信息失败:', error)
        alert('加载导出信息失败: ' + (error.response?.data?.error || error.message))
      }
    }

    // 选择所有列
    const selectAllColumns = () => {
      if (exportInfo.value) {
        selectedColumns.value = exportInfo.value.columns.map(col => col.name)
      }
    }

    // 取消选择所有列
    const deselectAllColumns = () => {
      selectedColumns.value = []
    }

    // 开始导出
    const startExport = async () => {
      if (!canExport.value) return

      isExporting.value = true
      progress.value = 0
      progressText.value = '准备导出...'

      try {
        const userInfo = userState.getUserInfo()
        progress.value = 20
        progressText.value = '正在生成文件...'

        let response
        if (exportFormat.value === 'csv') {
          response = await databaseApi.exportTableToCsv(
            props.tableName,
            props.dataSource,
            userInfo.userId,
            userInfo.userType,
            limit.value || null,
            whereClause.value || null
          )
        } else {
          response = await databaseApi.exportTableToExcel(
            props.tableName,
            props.dataSource,
            userInfo.userId,
            userInfo.userType,
            limit.value || null,
            whereClause.value || null
          )
        }

        progress.value = 80
        progressText.value = '正在下载文件...'

        // 创建下载链接
        const blob = new Blob([response.data], {
          type: exportFormat.value === 'csv' ? 'text/csv' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        })
        
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        
        const timestamp = new Date().toISOString().slice(0, 19).replace(/:/g, '-')
        const extension = exportFormat.value === 'csv' ? 'csv' : 'xlsx'
        link.download = `${props.tableName}_export_${timestamp}.${extension}`
        
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)

        progress.value = 100
        progressText.value = '导出完成！'

        setTimeout(() => {
          close()
        }, 1000)

      } catch (error) {
        console.error('导出失败:', error)
        alert('导出失败: ' + (error.response?.data?.error || error.message))
      } finally {
        isExporting.value = false
      }
    }

    // 关闭对话框
    const close = () => {
      emit('close')
    }

    // 处理遮罩层点击
    const handleOverlayClick = (event) => {
      if (event.target === event.currentTarget) {
        close()
      }
    }

    return {
      exportFormat,
      limit,
      whereClause,
      selectedColumns,
      exportInfo,
      isExporting,
      progress,
      progressText,
      canExport,
      selectAllColumns,
      deselectAllColumns,
      startExport,
      close,
      handleOverlayClick
    }
  }
}
</script>

<style scoped>
.export-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.export-dialog {
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  overflow-y: auto;
}

.export-dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #eee;
}

.export-dialog-header h3 {
  margin: 0;
  color: #333;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #666;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  color: #333;
}

.export-dialog-content {
  padding: 20px;
}

.table-info {
  margin-bottom: 20px;
  padding: 15px;
  background-color: #f8f9fa;
  border-radius: 6px;
}

.table-info h4 {
  margin: 0 0 15px 0;
  color: #333;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.info-item {
  display: flex;
  flex-direction: column;
}

.info-item label {
  font-weight: 600;
  color: #666;
  font-size: 14px;
}

.info-item span {
  color: #333;
  font-size: 14px;
}

.export-options h4 {
  margin: 0 0 15px 0;
  color: #333;
}

.option-group {
  margin-bottom: 20px;
}

.option-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
  color: #333;
}

.format-options {
  display: flex;
  gap: 20px;
}

.format-option {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.format-option input[type="radio"] {
  margin: 0;
}

select, textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}

textarea {
  resize: vertical;
  min-height: 60px;
}

.help-text {
  color: #666;
  font-size: 12px;
  margin-top: 4px;
}

.column-selection {
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 10px;
}

.column-controls {
  margin-bottom: 10px;
}

.btn-small {
  background: #007bff;
  color: white;
  border: none;
  padding: 4px 8px;
  border-radius: 3px;
  cursor: pointer;
  font-size: 12px;
  margin-right: 8px;
}

.btn-small:hover {
  background: #0056b3;
}

.column-list {
  max-height: 150px;
  overflow-y: auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 8px;
}

.column-item {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px;
  border-radius: 3px;
}

.column-item:hover {
  background-color: #f8f9fa;
}

.column-name {
  font-weight: 500;
  color: #333;
}

.column-type {
  color: #666;
  font-size: 12px;
}

.export-progress {
  margin-top: 20px;
  padding: 15px;
  background-color: #f8f9fa;
  border-radius: 6px;
}

.progress-bar {
  width: 100%;
  height: 8px;
  background-color: #e9ecef;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 10px;
}

.progress-fill {
  height: 100%;
  background-color: #007bff;
  transition: width 0.3s ease;
}

.export-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 20px;
  border-top: 1px solid #eee;
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: #6c757d;
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background: #545b62;
}

.btn-primary {
  background: #007bff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0056b3;
}
</style>
