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
        <el-button type="success" @click="showAddDataDialog" style="margin-left: 10px;">
          新增数据
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

    <!-- 新增数据对话框 -->
    <el-dialog v-model="addDataDialogVisible" :title="`新增数据 - ${selectedTable}`" width="70%">
      <!-- 主键值提示 -->
      <el-alert 
        v-if="hasPrimaryKey"
        title="提示：此表有主键字段，请确保输入的值是唯一的"
        type="warning"
        :closable="false"
        style="margin-bottom: 20px;"
      />
      
      <el-form :model="newRowData" label-width="120px">
        <el-form-item 
          v-for="column in tableColumns" 
          :key="column.COLUMN_NAME"
          :label="column.COLUMN_NAME"
          :required="column.IS_NULLABLE === 'NO' && !column.EXTRA.includes('auto_increment')"
        >
          <div style="display: flex; align-items: center; gap: 10px;">
            <el-input 
              v-model="newRowData[column.COLUMN_NAME]"
              :placeholder="getColumnPlaceholder(column)"
              :disabled="column.EXTRA.includes('auto_increment')"
              style="flex: 1;"
            />
            <el-tag size="small" :type="getColumnTagType(column)">
              {{ column.DATA_TYPE }}
            </el-tag>
          </div>
          <div style="font-size: 12px; color: #666; margin-top: 5px;">
            {{ getColumnDescription(column) }}
          </div>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="addDataDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitNewData" :loading="submitting">
          提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../utils/api'

export default {
  name: 'Tables',
  setup() {
    const tableList = ref([])
    const selectedTable = ref('')
    const structureDialogVisible = ref(false)
    const dataDialogVisible = ref(false)
    const addDataDialogVisible = ref(false)
    const tableColumns = ref([])
    const tableData = ref([])
    const dataLimit = ref(100)
    const newRowData = ref({})
    const submitting = ref(false)
    const hasPrimaryKey = ref(false)

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

    const showAddDataDialog = async () => {
      try {
        // 先获取表结构
        const response = await api.get(`/api/database/tables/${selectedTable.value}/columns`)
        tableColumns.value = response.data
        
        // 检查是否有主键
        hasPrimaryKey.value = tableColumns.value.some(col => col.COLUMN_KEY === 'PRI')
        
        // 初始化新行数据
        newRowData.value = {}
        tableColumns.value.forEach(column => {
          if (column.COLUMN_DEFAULT !== null) {
            newRowData.value[column.COLUMN_NAME] = column.COLUMN_DEFAULT
          } else {
            newRowData.value[column.COLUMN_NAME] = ''
          }
        })
        
        addDataDialogVisible.value = true
      } catch (error) {
        console.error('获取表结构失败:', error)
        ElMessage.error('获取表结构失败')
      }
    }

    const submitNewData = async () => {
      try {
        submitting.value = true
        
        // 验证必填字段
        const requiredFields = tableColumns.value.filter(col => 
          col.IS_NULLABLE === 'NO' && !col.EXTRA.includes('auto_increment')
        )
        
        for (const field of requiredFields) {
          const value = newRowData.value[field.COLUMN_NAME]
          if (!value || value.toString().trim() === '') {
            ElMessage.error(`字段 ${field.COLUMN_NAME} 是必填项`)
            return
          }
        }
        
        // 过滤掉空值和自增字段
        const dataToSubmit = {}
        tableColumns.value.forEach(column => {
          const value = newRowData.value[column.COLUMN_NAME]
          if (!column.EXTRA.includes('auto_increment') && value !== '' && value !== null) {
            dataToSubmit[column.COLUMN_NAME] = value
          }
        })

        const response = await api.post(`/api/database/tables/${selectedTable.value}/data`, dataToSubmit)
        
        if (response.data.success) {
          ElMessage.success(response.data.message)
          addDataDialogVisible.value = false
          // 刷新表数据
          await loadTableData()
        } else {
          ElMessage.error(response.data.error || '数据插入失败')
        }
      } catch (error) {
        const errorMsg = error.response?.data?.error || error.message
        
        // 特殊处理常见错误
        if (errorMsg.includes('Duplicate entry')) {
          const match = errorMsg.match(/Duplicate entry '(.+?)' for key '(.+?)'/);
          if (match) {
            ElMessage.error(`值 "${match[1]}" 已存在，请使用不同的值`)
          } else {
            ElMessage.error('数据已存在，请检查主键或唯一字段的值')
          }
        } else if (errorMsg.includes('cannot be null')) {
          ElMessage.error('必填字段不能为空，请检查表单')
        } else if (errorMsg.includes('Data too long')) {
          ElMessage.error('输入的数据过长，请缩短内容')
        } else {
          ElMessage.error('数据插入失败: ' + errorMsg)
        }
      } finally {
        submitting.value = false
      }
    }

    const getColumnPlaceholder = (column) => {
      if (column.EXTRA.includes('auto_increment')) {
        return '自动生成'
      }
      if (column.COLUMN_DEFAULT !== null) {
        return `默认值: ${column.COLUMN_DEFAULT}`
      }
      if (column.IS_NULLABLE === 'YES') {
        return '可选填'
      }
      return '必填'
    }

    const getColumnTagType = (column) => {
      if (column.COLUMN_KEY === 'PRI') return 'warning'
      if (column.EXTRA.includes('auto_increment')) return 'info'
      if (column.IS_NULLABLE === 'NO') return 'danger'
      return 'success'
    }

    const getColumnDescription = (column) => {
      let desc = []
      if (column.COLUMN_KEY === 'PRI') desc.push('主键')
      if (column.EXTRA.includes('auto_increment')) desc.push('自动递增')
      if (column.IS_NULLABLE === 'NO') desc.push('必填')
      if (column.COLUMN_COMMENT) desc.push(column.COLUMN_COMMENT)
      return desc.join(' | ')
    }

    onMounted(() => {
      loadTables()
    })

    return {
      tableList,
      selectedTable,
      structureDialogVisible,
      dataDialogVisible,
      addDataDialogVisible,
      tableColumns,
      tableData,
      dataLimit,
      newRowData,
      submitting,
      hasPrimaryKey,
      loadTables,
      selectTable,
      viewTableStructure,
      viewTableData,
      loadTableData,
      showAddDataDialog,
      submitNewData,
      getColumnPlaceholder,
      getColumnTagType,
      getColumnDescription,
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