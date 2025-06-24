<template>
  <el-card class="connection-test">
    <template #header>
      <div class="card-header">
        <span>数据库连接测试</span>
        <el-button @click="testConnection" :loading="testing">测试连接</el-button>
      </div>
    </template>
    
    <div v-if="connectionResult">
      <el-alert 
        :title="connectionResult.status === 'UP' ? '连接成功' : '连接失败'"
        :type="connectionResult.status === 'UP' ? 'success' : 'error'"
        style="margin-bottom: 15px;"
      />
      
      <el-descriptions title="连接信息" :column="2" border>
        <el-descriptions-item label="状态">
          <el-tag :type="connectionResult.status === 'UP' ? 'success' : 'danger'">
            {{ connectionResult.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="数据库">
          {{ connectionResult.database || 'N/A' }}
        </el-descriptions-item>
        <el-descriptions-item label="时间戳">
          {{ formatTimestamp(connectionResult.timestamp) }}
        </el-descriptions-item>
        <el-descriptions-item label="错误信息" v-if="connectionResult.error">
          <el-text type="danger">{{ connectionResult.error }}</el-text>
        </el-descriptions-item>
      </el-descriptions>
    </div>
    
    <el-empty v-else description="点击测试连接按钮检查数据库状态" />
  </el-card>
</template>

<script>
import { ref } from 'vue'
import api from '../utils/api'

export default {
  name: 'DatabaseConnection',
  setup() {
    const testing = ref(false)
    const connectionResult = ref(null)

    const testConnection = async () => {
      testing.value = true
      try {
        const response = await api.get('/api/database/health')
        connectionResult.value = response.data
      } catch (error) {
        connectionResult.value = {
          status: 'DOWN',
          database: 'Connection Failed',
          error: error.response?.data?.error || error.message,
          timestamp: Date.now()
        }
      } finally {
        testing.value = false
      }
    }

    const formatTimestamp = (timestamp) => {
      return new Date(timestamp).toLocaleString('zh-CN')
    }

    return {
      testing,
      connectionResult,
      testConnection,
      formatTimestamp
    }
  }
}
</script>

<style scoped>
.connection-test {
  margin: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style> 