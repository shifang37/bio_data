<template>
  <div class="tables-view">
    <el-card style="margin-bottom: 20px;">
      <template #header>
        <div class="card-header">
          <span>数据表管理</span>
          <el-button type="primary" @click="loadTables">刷新</el-button>
        </div>
      </template>
      
      <el-table :data="tableList" style="width: 100%" @row-click="selectTable">
        <el-table-column prop="TABLE_NAME" label="表名" width="200" />
        <el-table-column prop="TABLE_ROWS" label="行数" width="100" />
        <el-table-column prop="DATA_LENGTH" label="数据大小" width="120">
          <template #default="scope">
            {{ formatBytes(scope.row.DATA_LENGTH) }}
          </template>
        </el-table-column>
        <el-table-column prop="CREATE_TIME" label="创建时间" width="180">
          <template #default="scope">
            {{ formatDate(scope.row.CREATE_TIME) }}
          </template>
        </el-table-column>
        <el-table-column prop="TABLE_COMMENT" label="注释" />
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button size="small" @click="viewTableData(scope.row.TABLE_NAME)">
              查看数据
            </el-button>
            <el-button size="small" @click="viewTableStructure(scope.row.TABLE_NAME)">
              表结构
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 表结构对话框 -->
    <el-dialog v-model="structureDialogVisible" :title="`表结构 - ${selectedTable}`" width="70%">
      <el-table :data="tableColumns" style="width: 100%">
        <el-table-column prop="COLUMN_NAME" label="列名" width="150" />
        <el-table-column prop="DATA_TYPE" label="数据类型" width="120" />
        <el-table-column prop="IS_NULLABLE" label="是否可空" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.IS_NULLABLE === 'YES' ? 'success' : 'danger'">
              {{ scope.row.IS_NULLABLE }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="COLUMN_DEFAULT" label="默认值" width="120" />
        <el-table-column prop="COLUMN_KEY" label="键类型" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.COLUMN_KEY === 'PRI'" type="warning">主键</el-tag>
            <el-tag v-else-if="scope.row.COLUMN_KEY === 'UNI'" type="info">唯一</el-tag>
            <el-tag v-else-if="scope.row.COLUMN_KEY === 'MUL'" type="success">索引</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="EXTRA" label="额外信息" width="120" />
        <el-table-column prop="COLUMN_COMMENT" label="注释" />
      </el-table>
    </el-dialog>

    <!-- 表数据对话框 -->
    <el-dialog v-model="dataDialogVisible" :title="`表数据 - ${selectedTable}`" width="90%">
      <div style="margin-bottom: 10px;">
        <el-input-number v-model="dataLimit" :min="10" :max="1000" :step="10" />
        <el-button type="primary" @click="loadTableData" style="margin-left: 10px;">
          刷新数据
        </el-button>
      </div>
      
      <el-table :data="tableData" style="width: 100%" max-height="400">
        <el-table-column 
          v-for="column in Object.keys(tableData[0] || {})" 
          :key="column"
          :prop="column" 
          :label="column"
          :width="getColumnWidth(column)"
          show-overflow-tooltip
        />
      </el-table>
      
      <div style="margin-top: 10px; text-align: center;">
        <el-text type="info">显示前 {{ dataLimit }} 行数据</el-text>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import api from '../utils/api'

export default {
  name: 'Tables',
  setup() {
    const tableList = ref([])
    const selectedTable = ref('')
    const structureDialogVisible = ref(false)
    const dataDialogVisible = ref(false)
    const tableColumns = ref([])
    const tableData = ref([])
    const dataLimit = ref(100)

    const loadTables = async () => {
      try {
        const response = await api.get('/api/database/tables')
        tableList.value = response.data
      } catch (error) {
        console.error('加载表列表失败:', error)
      }
    }

    const selectTable = (row) => {
      selectedTable.value = row.TABLE_NAME
    }

    const viewTableStructure = async (tableName) => {
      try {
        selectedTable.value = tableName
        const response = await api.get(`/api/database/tables/${tableName}/columns`)
        tableColumns.value = response.data
        structureDialogVisible.value = true
      } catch (error) {
        console.error('加载表结构失败:', error)
      }
    }

    const viewTableData = async (tableName) => {
      selectedTable.value = tableName
      dataDialogVisible.value = true
      await loadTableData()
    }

    const loadTableData = async () => {
      try {
        const response = await api.get(`/api/database/tables/${selectedTable.value}/data?limit=${dataLimit.value}`)
        tableData.value = response.data
      } catch (error) {
        console.error('加载表数据失败:', error)
      }
    }

    const formatBytes = (bytes) => {
      if (!bytes) return '0 B'
      const k = 1024
      const sizes = ['B', 'KB', 'MB', 'GB']
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
    }

    const formatDate = (dateStr) => {
      if (!dateStr) return 'N/A'
      return new Date(dateStr).toLocaleString('zh-CN')
    }

    const getColumnWidth = (columnName) => {
      // 根据列名长度和数据类型调整列宽
      const baseWidth = Math.max(columnName.length * 10, 100)
      return Math.min(baseWidth, 200)
    }

    onMounted(() => {
      loadTables()
    })

    return {
      tableList,
      selectedTable,
      structureDialogVisible,
      dataDialogVisible,
      tableColumns,
      tableData,
      dataLimit,
      loadTables,
      selectTable,
      viewTableStructure,
      viewTableData,
      loadTableData,
      formatBytes,
      formatDate,
      getColumnWidth
    }
  }
}
</script>

<style scoped>
.tables-view {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.el-table .el-table__row {
  cursor: pointer;
}

.el-table .el-table__row:hover {
  background-color: #f5f7fa;
}
</style> 