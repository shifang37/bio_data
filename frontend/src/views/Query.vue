<template>
  <div class="query-view">
    <el-card style="margin-bottom: 20px;">
      <template #header>
        <div class="card-header">
          <span>SQL查询工具</span>
          <div>
            <el-button @click="clearQuery">清空</el-button>
            <el-button type="primary" @click="executeQuery" :loading="executing">
              执行查询
            </el-button>
          </div>
        </div>
      </template>
      
      <div style="margin-bottom: 15px;">
        <el-text type="info">
          提示：只支持SELECT查询，限制返回最多1000行数据
        </el-text>
      </div>
      
      <el-input
        v-model="sqlQuery"
        type="textarea"
        :rows="8"
        placeholder="请输入SQL查询语句，例如：SELECT * FROM table_name LIMIT 10"
        style="margin-bottom: 15px;"
      />
      
      <div style="display: flex; align-items: center; gap: 15px;">
        <el-text>结果限制：</el-text>
        <el-input-number v-model="queryLimit" :min="10" :max="1000" :step="10" />
        <el-text type="info">行</el-text>
      </div>
    </el-card>

    <!-- 查询结果 -->
    <el-card v-if="queryResult.data || queryError">
      <template #header>
        <div class="card-header">
          <span>查询结果</span>
          <div v-if="queryResult.data">
            <el-tag type="success">
              返回 {{ queryResult.count }} 行数据
            </el-tag>
          </div>
        </div>
      </template>
      
      <!-- 错误信息 -->
      <el-alert 
        v-if="queryError" 
        :title="queryError" 
        type="error" 
        style="margin-bottom: 15px;"
      />
      
      <!-- 执行的SQL -->
      <div v-if="queryResult.sql" style="margin-bottom: 15px;">
        <el-text type="info">执行的SQL：</el-text>
        <el-text class="sql-text">{{ queryResult.sql }}</el-text>
      </div>
      
      <!-- 数据表格 -->
      <div v-if="queryResult.data && queryResult.data.length > 0">
        <el-table :data="queryResult.data" style="width: 100%" max-height="500">
          <el-table-column 
            v-for="column in Object.keys(queryResult.data[0])" 
            :key="column"
            :prop="column" 
            :label="column"
            :width="getColumnWidth(column)"
            show-overflow-tooltip
          />
        </el-table>
      </div>
      
      <!-- 无数据提示 -->
      <el-empty v-else-if="queryResult.data && queryResult.data.length === 0" 
                description="查询结果为空" />
    </el-card>

    <!-- 常用查询示例 -->
    <el-card style="margin-top: 20px;">
      <template #header>
        <span>常用查询示例</span>
      </template>
      
      <el-row :gutter="15">
        <el-col :span="12">
          <el-card shadow="hover" class="example-card" @click="setExampleQuery(examples.showTables)">
            <h4>显示所有表</h4>
            <pre class="example-sql">{{ examples.showTables }}</pre>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="hover" class="example-card" @click="setExampleQuery(examples.showColumns)">
            <h4>显示表结构</h4>
            <pre class="example-sql">{{ examples.showColumns }}</pre>
          </el-card>
        </el-col>
      </el-row>
      
      <el-row :gutter="15" style="margin-top: 15px;">
        <el-col :span="12">
          <el-card shadow="hover" class="example-card" @click="setExampleQuery(examples.countRows)">
            <h4>统计表行数</h4>
            <pre class="example-sql">{{ examples.countRows }}</pre>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="hover" class="example-card" @click="setExampleQuery(examples.dataPreview)">
            <h4>数据预览</h4>
            <pre class="example-sql">{{ examples.dataPreview }}</pre>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script>
import { ref } from 'vue'
import api from '../utils/api'

export default {
  name: 'Query',
  setup() {
    const sqlQuery = ref('')
    const queryLimit = ref(100)
    const executing = ref(false)
    const queryResult = ref({})
    const queryError = ref('')

    const examples = {
      showTables: 'SHOW TABLES',
      showColumns: 'DESCRIBE table_name',
      countRows: 'SELECT COUNT(*) as total_rows FROM table_name',
      dataPreview: 'SELECT * FROM table_name LIMIT 10'
    }

    const executeQuery = async () => {
      if (!sqlQuery.value.trim()) {
        queryError.value = 'SQL查询语句不能为空'
        return
      }

      executing.value = true
      queryError.value = ''
      queryResult.value = {}

      try {
        const response = await api.post('/api/database/query', {
          sql: sqlQuery.value,
          limit: queryLimit.value
        })
        
        queryResult.value = response.data
      } catch (error) {
        queryError.value = error.response?.data?.error || '查询执行失败: ' + error.message
      } finally {
        executing.value = false
      }
    }

    const clearQuery = () => {
      sqlQuery.value = ''
      queryResult.value = {}
      queryError.value = ''
    }

    const setExampleQuery = (exampleSql) => {
      sqlQuery.value = exampleSql
    }

    const getColumnWidth = (columnName) => {
      const baseWidth = Math.max(columnName.length * 10, 100)
      return Math.min(baseWidth, 200)
    }

    return {
      sqlQuery,
      queryLimit,
      executing,
      queryResult,
      queryError,
      examples,
      executeQuery,
      clearQuery,
      setExampleQuery,
      getColumnWidth
    }
  }
}
</script>

<style scoped>
.query-view {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sql-text {
  font-family: 'Courier New', monospace;
  background-color: #f5f5f5;
  padding: 5px 10px;
  border-radius: 4px;
  display: inline-block;
  margin-left: 10px;
}

.example-card {
  cursor: pointer;
  transition: all 0.3s;
}

.example-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0,0,0,0.1);
}

.example-sql {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #666;
  margin: 10px 0 0 0;
  background-color: #f8f9fa;
  padding: 8px;
  border-radius: 4px;
}

h4 {
  margin: 0 0 10px 0;
  color: #409EFF;
}
</style> 