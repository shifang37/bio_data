<template>
  <div class="dashboard">
    <!-- 数据库连接测试 -->
    <DatabaseConnection />
    
    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="8">
        <el-card>
          <div class="stat-card">
            <div class="stat-number">{{ dbStats.tableCount || 0 }}</div>
            <div class="stat-label">数据表总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <div class="stat-card">
            <div class="stat-number">{{ dbStats.databaseSizeMB || 0 }} MB</div>
            <div class="stat-label">数据库大小</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <div class="stat-card">
            <div class="stat-number">{{ dbStats.databaseName || 'N/A' }}</div>
            <div class="stat-label">数据库名称</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>表大小分布</span>
            </div>
          </template>
          <div ref="tableSizeChart" style="height: 300px;"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>数据表列表</span>
            </div>
          </template>
          <el-table :data="tableList" style="width: 100%" max-height="300">
            <el-table-column prop="TABLE_NAME" label="表名" width="200" />
            <el-table-column prop="TABLE_ROWS" label="行数" width="80" />
            <el-table-column prop="DATA_LENGTH" label="大小(字节)" width="100" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { ref, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import api from '../utils/api'
import DatabaseConnection from '../components/DatabaseConnection.vue'

export default {
  name: 'Dashboard',
  components: {
    DatabaseConnection
  },
  setup() {
    const dbStats = ref({})
    const tableList = ref([])
    const tableSizeChart = ref(null)

    const loadDashboardData = async () => {
      try {
        // 加载数据库统计信息
        const statsResponse = await api.get('/api/database/stats')
        dbStats.value = statsResponse.data

        // 加载表列表
        const tablesResponse = await api.get('/api/database/tables')
        tableList.value = tablesResponse.data

        // 渲染图表
        await nextTick()
        renderTableSizeChart()
      } catch (error) {
        console.error('加载仪表板数据失败:', error)
      }
    }

    const renderTableSizeChart = () => {
      if (!tableSizeChart.value || !tableList.value.length) return

      const chart = echarts.init(tableSizeChart.value)
      
      // 准备图表数据
      const data = tableList.value
        .map(table => ({
          name: table.TABLE_NAME,
          value: table.DATA_LENGTH || 0
        }))
        .sort((a, b) => b.value - a.value)
        .slice(0, 10) // 只显示前10个最大的表

      const option = {
        title: {
          text: '表大小分布',
          left: 'center'
        },
        tooltip: {
          trigger: 'item',
          formatter: '{a} <br/>{b} : {c} ({d}%)'
        },
        series: [
          {
            name: '表大小',
            type: 'pie',
            radius: '60%',
            data: data,
            emphasis: {
              itemStyle: {
                shadowBlur: 10,
                shadowOffsetX: 0,
                shadowColor: 'rgba(0, 0, 0, 0.5)'
              }
            }
          }
        ]
      }

      chart.setOption(option)
    }

    onMounted(() => {
      loadDashboardData()
    })

    return {
      dbStats,
      tableList,
      tableSizeChart
    }
  }
}
</script>

<style scoped>
.dashboard {
  padding: 20px;
}

.stat-card {
  text-align: center;
  padding: 20px;
}

.stat-number {
  font-size: 32px;
  font-weight: bold;
  color: #409EFF;
  margin-bottom: 10px;
}

.stat-label {
  font-size: 14px;
  color: #666;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style> 