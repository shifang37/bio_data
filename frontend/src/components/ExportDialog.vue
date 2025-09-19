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
          <h4>{{ searchValue ? '搜索结果导出信息' : '表信息' }}</h4>
          <div class="info-grid">
            <div class="info-item">
              <label>表名:</label>
              <span>{{ exportInfo.tableName }}</span>
            </div>
            <div class="info-item">
              <label>数据源:</label>
              <span>{{ exportInfo.dataSource }}</span>
            </div>
            <div class="info-item" v-if="searchValue">
              <label>搜索值:</label>
              <span>{{ searchValue }}</span>
            </div>
            <div class="info-item" v-if="searchValue">
              <label>搜索类型:</label>
              <span>{{ searchType === 'exact' ? '精确匹配' : '模糊匹配' }}</span>
            </div>
            <div class="info-item">
              <label>列数:</label>
              <span>{{ exportInfo.columnCount }}</span>
            </div>
            <div class="info-item">
              <label>{{ searchValue ? '匹配行数' : '总行数' }}:</label>
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

          <!-- 导出路径选择 -->
          <div class="option-group">
            <label for="exportPath">导出到:</label>
            <div class="path-selection">
              <div class="path-input-container">
                <input 
                  type="text" 
                  id="exportPath" 
                  v-model="exportPath" 
                  placeholder="点击右侧按钮选择保存位置..."
                  readonly
                  class="path-input"
                >
                <div class="path-display">
                  <span v-if="exportPath" class="selected-path">{{ exportPath }}</span>
                  <span v-else class="placeholder-text">默认下载文件夹</span>
                </div>
              </div>
              <button type="button" @click="selectExportPath" class="btn-path">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M10 6L16 12L10 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                选择位置
              </button>
            </div>
            <small class="help-text">选择文件保存位置，如不选择将保存到默认下载文件夹</small>
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
import { ref, computed, watch } from 'vue'
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
    },
    searchValue: {
      type: String,
      default: ''
    },
    searchType: {
      type: String,
      default: 'fuzzy'
    }
  },
  emits: ['close'],
  setup(props, { emit }) {
    const exportFormat = ref('csv')
    const limit = ref(10000)
    const exportPath = ref('')
    const selectedColumns = ref([])
    const exportInfo = ref(null)
    const isExporting = ref(false)
    const progress = ref(0)
    const progressText = ref('')

    // 创建对 props 的响应式引用，供模板使用
    const searchValue = computed(() => props.searchValue)
    const searchType = computed(() => props.searchType)

    // 计算属性
    const canExport = computed(() => {
      return exportInfo.value && selectedColumns.value.length > 0
    })

    // 监听表名变化，重新加载导出信息
    watch(() => props.tableName, (newTableName) => {
      if (newTableName && props.visible) {
        loadExportInfo()
      }
    })

    // 监听对话框显示状态
    watch(() => props.visible, (newVisible) => {
      if (newVisible && props.tableName) {
        loadExportInfo()
      }
    })

    // 监听搜索参数变化
    watch(() => [props.searchValue, props.searchType], () => {
      if (props.visible && props.tableName) {
        loadExportInfo()
      }
    })

    // 加载导出信息
    const loadExportInfo = async () => {
      try {
        const userInfo = userState.getUserInfo()
        
        let response
        if (props.searchValue) {
          // 如果有搜索值，获取搜索结果信息
          response = await databaseApi.getSearchResultExportInfo(
            props.tableName,
            props.dataSource,
            userInfo.userId,
            userInfo.userType,
            props.searchValue,
            props.searchType
          )
        } else {
          // 否则获取全表信息
          response = await databaseApi.getExportInfo(
            props.tableName,
            props.dataSource,
            userInfo.userId,
            userInfo.userType
          )
        }
        
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

    // 选择的文件夹句柄
    const selectedDirHandle = ref(null)

    // 默认下载函数
    const downloadToDefault = (blob, fileName) => {
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = fileName
      
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      
      progress.value = 100
      progressText.value = '文件已下载到默认文件夹'
    }

    // 选择导出路径
    const selectExportPath = async () => {
      try {
        // 使用File System Access API来选择目录（Chrome 86+支持）
        if ('showDirectoryPicker' in window) {
          const dirHandle = await window.showDirectoryPicker()
          selectedDirHandle.value = dirHandle
          
          // 显示文件夹名称作为路径信息
          exportPath.value = dirHandle.name
          
          // 检查是否有写入权限
          try {
            await dirHandle.requestPermission({ mode: 'readwrite' })
          } catch (permError) {
            console.warn('写入权限检查失败:', permError)
            // 继续尝试，可能仍然可以写入
          }
        } else {
          // 对于不支持的浏览器，显示提示信息
          alert('您的浏览器不支持文件夹选择功能。\n文件将保存到默认下载文件夹中。')
        }
      } catch (error) {
        if (error.name !== 'AbortError') {
          console.error('选择路径失败:', error)
          // 对于其他错误，也显示友好的提示
          alert('无法选择保存位置，文件将保存到默认下载文件夹中。')
        }
      }
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
          if (props.searchValue) {
            // 导出搜索结果
            response = await databaseApi.exportSearchResultToCsv(
              props.tableName,
              props.dataSource,
              userInfo.userId,
              userInfo.userType,
              props.searchValue,
              props.searchType,
              limit.value || null
            )
          } else {
            // 导出全表数据
            response = await databaseApi.exportTableToCsv(
              props.tableName,
              props.dataSource,
              userInfo.userId,
              userInfo.userType,
              limit.value || null
            )
          }
        } else {
          if (props.searchValue) {
            // 导出搜索结果
            response = await databaseApi.exportSearchResultToExcel(
              props.tableName,
              props.dataSource,
              userInfo.userId,
              userInfo.userType,
              props.searchValue,
              props.searchType,
              limit.value || null
            )
          } else {
            // 导出全表数据
            response = await databaseApi.exportTableToExcel(
              props.tableName,
              props.dataSource,
              userInfo.userId,
              userInfo.userType,
              limit.value || null
            )
          }
        }

        progress.value = 80
        progressText.value = '正在保存文件...'

        // 创建文件数据
        const blob = new Blob([response.data], {
          type: exportFormat.value === 'csv' ? 'text/csv' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        })
        
        const timestamp = new Date().toISOString().slice(0, 19).replace(/:/g, '-')
        const extension = exportFormat.value === 'csv' ? 'csv' : 'xlsx'
        const filePrefix = props.searchValue ? `${props.tableName}_search_result_export` : `${props.tableName}_export`
        const fileName = `${filePrefix}_${timestamp}.${extension}`

        // 如果用户选择了文件夹，使用File System Access API保存
        if (selectedDirHandle.value) {
          try {
            // 先检查权限
            const permission = await selectedDirHandle.value.requestPermission({ mode: 'readwrite' })
            if (permission !== 'granted') {
              throw new Error('没有写入权限')
            }
            
            const fileHandle = await selectedDirHandle.value.getFileHandle(fileName, { create: true })
            const writable = await fileHandle.createWritable()
            await writable.write(blob)
            await writable.close()
            
            progress.value = 100
            progressText.value = `文件已保存到: ${exportPath.value}/${fileName}`
          } catch (error) {
            console.error('保存到指定文件夹失败:', error)
            
            // 根据错误类型提供不同的提示
            let errorMessage = '保存失败，将使用默认下载'
            if (error.message.includes('权限') || error.message.includes('permission')) {
              errorMessage = '没有写入权限，将使用默认下载'
            } else if (error.message.includes('网络') || error.message.includes('network')) {
              errorMessage = '网络错误，将使用默认下载'
            }
            
            alert(`${errorMessage}`)
            
            // 如果保存失败，回退到默认下载
            downloadToDefault(blob, fileName)
          }
        } else {
          // 如果没有选择文件夹，使用默认下载
          downloadToDefault(blob, fileName)
        }

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
      exportPath,
      selectedColumns,
      exportInfo,
      isExporting,
      progress,
      progressText,
      searchValue,
      searchType,
      canExport,
      selectAllColumns,
      deselectAllColumns,
      selectExportPath,
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
  z-index: 2100;
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

.path-selection {
  display: flex;
  gap: 12px;
  align-items: stretch;
}

.path-input-container {
  flex: 1;
  position: relative;
  background: white;
  border: 2px solid #e1e5e9;
  border-radius: 8px;
  overflow: hidden;
  transition: all 0.2s ease;
}

.path-input-container:hover {
  border-color: #007bff;
  box-shadow: 0 2px 8px rgba(0, 123, 255, 0.1);
}

.path-input {
  width: 100%;
  padding: 12px 16px;
  border: none;
  background: transparent;
  font-size: 14px;
  color: #495057;
  cursor: pointer;
  outline: none;
}

.path-display {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  padding: 12px 16px;
  pointer-events: none;
  background: white;
}

.selected-path {
  color: #28a745;
  font-weight: 500;
  font-size: 14px;
}

.placeholder-text {
  color: #6c757d;
  font-style: italic;
  font-size: 14px;
}

.btn-path {
  background: linear-gradient(135deg, #007bff 0%, #0056b3 100%);
  color: white;
  border: none;
  padding: 12px 16px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 0.2s ease;
  box-shadow: 0 2px 4px rgba(0, 123, 255, 0.2);
}

.btn-path:hover {
  background: linear-gradient(135deg, #0056b3 0%, #004085 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 123, 255, 0.3);
}

.btn-path:active {
  transform: translateY(0);
  box-shadow: 0 2px 4px rgba(0, 123, 255, 0.2);
}

.btn-path svg {
  transition: transform 0.2s ease;
}

.btn-path:hover svg {
  transform: translateX(2px);
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
