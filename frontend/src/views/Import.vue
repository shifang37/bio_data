<template>
  <div class="import-page">
    <div class="page-header">
      <h1>数据导入</h1>
      <p>支持通过CSV文件批量导入数据到数据库表中</p>
    </div>

    <div class="main-content">
      <CsvImporter 
        :userId="userState.userId"
        :userType="userState.userType"
        @import-complete="handleImportComplete"
      />
    </div>
    
    <!-- 导入历史记录 -->
    <div class="import-history" v-if="importHistory.length > 0">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>导入历史记录</span>
            <el-button size="small" @click="clearHistory">清空历史</el-button>
          </div>
        </template>
        
        <el-table :data="importHistory" style="width: 100%">
          <el-table-column prop="timestamp" label="导入时间" width="180">
            <template #default="scope">
              {{ formatDate(scope.row.timestamp) }}
            </template>
          </el-table-column>
          <el-table-column prop="dataSource" label="数据库" width="150" />
          <el-table-column prop="tableName" label="目标表" width="150" />
          <el-table-column prop="totalRecords" label="总记录数" width="100" />
          <el-table-column prop="successCount" label="成功" width="100" />
          <el-table-column prop="failureCount" label="失败" width="100" />
          <el-table-column prop="duration" label="耗时(ms)" width="100" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="scope">
              <el-tag 
                :type="scope.row.status === 'success' ? 'success' : 'danger'"
                size="small"
              >
                {{ scope.row.status === 'success' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="scope">
              <el-button 
                size="small" 
                @click="showImportDetails(scope.row)"
              >
                详情
              </el-button>
              <el-button 
                size="small" 
                type="danger"
                @click="removeHistoryItem(scope.$index)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- 导入详情对话框 -->
    <el-dialog 
      v-model="detailsVisible" 
      title="导入详情" 
      width="600px"
    >
      <div v-if="selectedImport">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="导入时间">
            {{ formatDate(selectedImport.timestamp) }}
          </el-descriptions-item>
          <el-descriptions-item label="数据库">
            {{ selectedImport.dataSource }}
          </el-descriptions-item>
          <el-descriptions-item label="目标表">
            {{ selectedImport.tableName }}
          </el-descriptions-item>
          <el-descriptions-item label="总记录数">
            {{ selectedImport.totalRecords }}
          </el-descriptions-item>
          <el-descriptions-item label="成功记录数">
            {{ selectedImport.successCount }}
          </el-descriptions-item>
          <el-descriptions-item label="失败记录数">
            {{ selectedImport.failureCount }}
          </el-descriptions-item>
          <el-descriptions-item label="耗时">
            {{ selectedImport.duration }}ms
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag 
              :type="selectedImport.status === 'success' ? 'success' : 'danger'"
              size="small"
            >
              {{ selectedImport.status === 'success' ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        
        <div v-if="selectedImport.errors && selectedImport.errors.length > 0" style="margin-top: 20px;">
          <h4>错误信息：</h4>
          <el-alert
            v-for="(error, index) in selectedImport.errors"
            :key="index"
            :title="error"
            type="error"
            show-icon
            :closable="false"
            style="margin-bottom: 10px;"
          />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CsvImporter from '../components/CsvImporter.vue'
import { userState } from '../utils/api'

export default {
  name: 'ImportPage',
  components: {
    CsvImporter
  },
  setup() {
    const importHistory = ref([])
    const detailsVisible = ref(false)
    const selectedImport = ref(null)

    // 加载导入历史记录
    const loadImportHistory = () => {
      try {
        const stored = localStorage.getItem('importHistory')
        if (stored) {
          const historyData = JSON.parse(stored)
          importHistory.value = historyData
        }
      } catch (e) {
        console.error('加载导入历史失败:', e)
        // 如果localStorage数据损坏，尝试清理并重新开始
        try {
          localStorage.removeItem('importHistory')
          importHistory.value = []
          ElMessage.warning('导入历史记录已重置，原因：数据格式错误')
        } catch (cleanError) {
          console.error('清理localStorage失败:', cleanError)
          importHistory.value = []
        }
      }
    }

    // 保存导入历史记录（优化存储，防止超出限制）
    const saveImportHistory = () => {
      try {
        const dataToStore = JSON.stringify(importHistory.value)
        
        // 检查数据大小，localStorage 通常限制为 5-10MB
        const dataSize = new Blob([dataToStore]).size
        const maxSize = 4 * 1024 * 1024 // 4MB 限制，留出余量
        
        if (dataSize > maxSize) {
          // 如果数据过大，删除最旧的记录直到满足大小限制
          while (importHistory.value.length > 10 && new Blob([JSON.stringify(importHistory.value)]).size > maxSize) {
            importHistory.value.pop()
          }
          
          // 如果还是太大，进一步压缩错误信息
          if (new Blob([JSON.stringify(importHistory.value)]).size > maxSize) {
            importHistory.value = importHistory.value.map(item => ({
              ...item,
              errors: item.errors ? item.errors.slice(0, 3).map(err => 
                err.length > 200 ? err.substring(0, 200) + '...' : err
              ) : []
            }))
          }
        }
        
        localStorage.setItem('importHistory', JSON.stringify(importHistory.value))
        
      } catch (e) {
        console.error('保存导入历史失败:', e)
        
        if (e.name === 'QuotaExceededError') {
          // 存储空间不足，清理旧记录
          try {
            importHistory.value = importHistory.value.slice(0, 5) // 只保留最近5条
            // 进一步压缩错误信息
            importHistory.value = importHistory.value.map(item => ({
              ...item,
              errors: item.errors ? ['已省略详细错误信息，原因：存储空间不足'] : []
            }))
            localStorage.setItem('importHistory', JSON.stringify(importHistory.value))
            ElMessage.warning('导入历史记录已自动清理，原因：存储空间不足')
          } catch (retryError) {
            console.error('重试保存失败:', retryError)
            ElMessage.error('无法保存导入历史记录：存储空间不足')
          }
        } else {
          ElMessage.error('保存导入历史失败：' + e.message)
        }
      }
    }

    // 处理导入完成（优化错误信息存储）
    const handleImportComplete = (result) => {
      // 限制错误信息的数量和长度
      let errors = result.errors || []
      if (errors.length > 10) {
        errors = errors.slice(0, 10)
        errors.push(`...还有 ${result.errors.length - 10} 个错误未显示`)
      }
      
      // 限制每个错误信息的长度
      errors = errors.map(err => 
        typeof err === 'string' && err.length > 300 ? err.substring(0, 300) + '...' : err
      )
      
      const historyItem = {
        timestamp: new Date().toISOString(),
        dataSource: result.dataSource,
        tableName: result.tableName,
        totalRecords: result.totalRecords,
        successCount: result.successCount,
        failureCount: result.failureCount,
        duration: result.duration,
        status: result.successCount > 0 ? 'success' : 'failed',
        errors: errors
      }
      
      importHistory.value.unshift(historyItem)
      
      // 限制历史记录数量为30条（原来是50条）
      if (importHistory.value.length > 30) {
        importHistory.value = importHistory.value.slice(0, 30)
      }
      
      saveImportHistory()
      
      if (result.successCount > 0) {
        ElMessage.success('导入记录已保存到历史记录')
      } else {
        ElMessage.warning('导入记录已保存到历史记录（存在错误）')
      }
    }

    // 显示导入详情
    const showImportDetails = (item) => {
      selectedImport.value = item
      detailsVisible.value = true
    }

    // 删除历史记录项
    const removeHistoryItem = async (index) => {
      try {
        await ElMessageBox.confirm('确定要删除这条导入记录吗？', '确认删除', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        
        importHistory.value.splice(index, 1)
        saveImportHistory()
        ElMessage.success('导入记录已删除')
      } catch (error) {
        // 用户取消删除
      }
    }

    // 清空历史记录
    const clearHistory = async () => {
      try {
        await ElMessageBox.confirm('确定要清空所有导入历史记录吗？', '确认清空', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        
        importHistory.value = []
        saveImportHistory()
        ElMessage.success('历史记录已清空')
      } catch (error) {
        // 用户取消清空
      }
    }

    // 格式化日期
    const formatDate = (dateStr) => {
      const date = new Date(dateStr)
      return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      })
    }

    onMounted(() => {
      loadImportHistory()
    })

    return {
      userState,
      importHistory,
      detailsVisible,
      selectedImport,
      handleImportComplete,
      showImportDetails,
      removeHistoryItem,
      clearHistory,
      formatDate
    }
  }
}
</script>

<style scoped>
.import-page {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 30px;
  text-align: center;
}

.page-header h1 {
  color: #303133;
  font-size: 28px;
  margin-bottom: 10px;
}

.page-header p {
  color: #606266;
  font-size: 16px;
}

.main-content {
  margin-bottom: 40px;
}

.import-history {
  margin-top: 40px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.el-table {
  margin-top: 20px;
}

.el-descriptions {
  margin-bottom: 20px;
}

.el-alert {
  margin-bottom: 10px;
}
</style> 