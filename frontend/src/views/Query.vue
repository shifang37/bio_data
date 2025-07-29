<template>
  <div class="query-view">
    <!-- 字段值搜索功能 -->
    <el-card style="margin-bottom: 20px;">
      <template #header>
        <div class="card-header">
          <span>字段值搜索工具</span>
          <div>
            <el-select 
              v-model="selectedSearchDatabase" 
              placeholder="选择数据库" 
              style="width: 200px; margin-right: 10px;"
            >
              <el-option
                v-for="db in availableDatabases"
                :key="db.name"
                :label="db.displayName"
                :value="db.name"
              />
            </el-select>
            <el-button @click="clearFieldSearch">清空</el-button>
            <el-button type="primary" @click="searchTablesByField" :loading="fieldSearching">
              搜索表
            </el-button>
          </div>
        </div>
      </template>
      
      <div style="margin-bottom: 15px;">
        <el-text type="info">
          💡 提示：字段值搜索会遍历数据库中所有表的所有字段和数据，提供完整的搜索结果，支持分页查看全部匹配数据
        </el-text>
        <br>
        <el-text type="warning" style="margin-top: 5px; display: inline-block;">
          ⏰ 注意：首次搜索可能需要较长时间，系统会为找到的所有表建立搜索缓存，切换表查看时将非常快速
        </el-text>
      </div>
      
      <el-input
        v-model="fieldSearchQuery"
        placeholder="请输入要搜索的字段值，例如：1897, admin, protein"
        style="margin-bottom: 15px;"
        @keyup.enter="searchTablesByField"
      />
    </el-card>

    <!-- 字段值搜索结果 -->
    <el-card v-if="fieldSearchResult.tables || fieldSearchError" style="margin-bottom: 20px;">
      <template #header>
        <div class="card-header">
          <span>搜索结果</span>
          <div v-if="fieldSearchResult.tables">
            <el-tag type="success">
              找到 {{ fieldSearchResult.totalCount }} 个包含值 "{{ fieldSearchResult.searchValue }}" 的表
            </el-tag>
            <el-tag type="success" style="margin-left: 10px;">
              {{ fieldSearchResult.searchInfo || '完整搜索' }}
            </el-tag>
            <el-tag type="info" style="margin-left: 10px;">
              <el-icon><Lightning /></el-icon>
              所有表已优化缓存
            </el-tag>
          </div>
        </div>
      </template>
      
      <!-- 错误信息 -->
      <el-alert 
        v-if="fieldSearchError" 
        :title="fieldSearchError" 
        type="error" 
        style="margin-bottom: 15px;"
      />
      
      <!-- 搜索结果表格 -->
      <div v-if="fieldSearchResult.tables && fieldSearchResult.tables.length > 0">
        <el-table :data="fieldSearchResult.tables" style="width: 100%">
          <el-table-column prop="TABLE_NAME" label="表名" width="200">
            <template #default="scope">
              <el-button 
                type="primary" 
                link 
                @click="viewTableData(scope.row.TABLE_NAME, fieldSearchResult.searchValue)"
              >
                {{ scope.row.TABLE_NAME }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column prop="MATCH_COUNT" label="匹配记录数" width="180">
            <template #default="scope">
              <el-tag type="warning">{{ scope.row.MATCH_COUNT }}</el-tag>
              <el-tag v-if="scope.row.IS_COMPLETE" type="success" size="small" style="margin-left: 5px;">
                完整
              </el-tag>
              <el-tag type="info" size="small" style="margin-left: 5px;">
                <el-icon><Lightning /></el-icon>
                已缓存
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="TABLE_ROWS" label="表总行数" width="120">
            <template #default="scope">
              {{ scope.row.TABLE_ROWS || 'N/A' }}
            </template>
          </el-table-column>
          <el-table-column prop="TABLE_COMMENT" label="表注释" min-width="200" show-overflow-tooltip />
        </el-table>
      </div>
      
      <!-- 无数据提示 -->
      <el-empty v-else-if="fieldSearchResult.tables && fieldSearchResult.tables.length === 0" 
                description="未找到包含该值的表" />
    </el-card>

    <!-- 表数据查看 -->
    <el-card v-if="tableDataResult.data" style="margin-bottom: 20px;">
      <template #header>
        <div class="card-header">
          <span>表数据：{{ tableDataResult.tableName }} (搜索值：{{ tableDataResult.searchValue }})</span>
                  <div>
          <el-tag type="info">
            共 {{ tableDataResult.totalCount }} 条匹配记录
          </el-tag>
          <el-tag v-if="tableDataResult.cached" type="success" size="small" style="margin-left: 10px;">
            <el-icon><Lightning /></el-icon>
            已优化
          </el-tag>
          <el-button @click="closeTableData" style="margin-left: 10px;">关闭</el-button>
        </div>
        </div>
      </template>
      
      <!-- 分页控制 -->
      <div style="margin-bottom: 15px; display: flex; justify-content: space-between; align-items: center;">
        <div>
          <el-text type="info">
            每页显示：
          </el-text>
          <el-select v-model="tableDataPagination.pageSize" @change="changeTableDataPageSize" style="width: 80px; margin: 0 10px;">
            <el-option label="20" :value="20" />
            <el-option label="50" :value="50" />
            <el-option label="100" :value="100" />
            <el-option label="200" :value="200" />
          </el-select>
          <el-text type="info">
            条，共 {{ tableDataResult.totalCount }} 条记录
          </el-text>
          <el-tag v-if="tableDataResult.cached && tableDataPagination.currentPage > 1" 
                  type="success" size="small" style="margin-left: 10px;">
            <el-icon><Lightning /></el-icon>
            快速分页
          </el-tag>
        </div>
        <div>
          <el-button type="primary" @click="loadTableDataByValue" :loading="tableDataLoading">
            刷新数据
          </el-button>
        </div>
      </div>
      
      <div v-if="tableDataResult.data && tableDataResult.data.length > 0">
        <el-table :data="tableDataResult.data" style="width: 100%" max-height="400" v-loading="tableDataLoading">
          <el-table-column 
            v-for="column in Object.keys(tableDataResult.data[0])" 
            :key="column"
            :prop="column" 
            :label="column"
            :width="getColumnWidth(column)"
            show-overflow-tooltip
          >
            <template #default="scope">
              <span 
                v-if="cellContainsSearchValue(scope.row[column], tableDataResult.searchValue)" 
                style="background-color: #fff2e8; padding: 2px 4px; border-radius: 3px; color: #e6a23c; font-weight: bold;"
              >
                {{ scope.row[column] }}
              </span>
              <span v-else>{{ scope.row[column] }}</span>
            </template>
          </el-table-column>
        </el-table>
        
        <!-- 分页组件 -->
        <div style="margin-top: 20px; display: flex; justify-content: center;">
          <el-pagination
            v-model:current-page="tableDataPagination.currentPage"
            v-model:page-size="tableDataPagination.pageSize"
            :page-sizes="[20, 50, 100, 200]"
            :disabled="tableDataLoading"
            :background="true"
            layout="total, sizes, prev, pager, next, jumper"
            :total="tableDataResult.totalCount"
            @size-change="handleTableDataSizeChange"
            @current-change="handleTableDataCurrentChange"
          />
        </div>
      </div>
      
      <el-empty v-else-if="tableDataResult.data && tableDataResult.data.length === 0" 
                description="暂无匹配数据" />
    </el-card>

    <el-card style="margin-bottom: 20px;">
      <template #header>
        <div class="card-header">
          <span>SQL查询工具</span>
          <div>
            <el-select 
              v-model="selectedDatabase" 
              placeholder="选择数据库" 
              style="width: 200px; margin-right: 10px;"
            >
              <el-option
                v-for="db in availableDatabases"
                :key="db.name"
                :label="db.displayName"
                :value="db.name"
              />
            </el-select>
            <el-button @click="clearQuery">清空</el-button>
            <el-button type="primary" @click="executeQuery" :loading="executing">
              执行查询
            </el-button>
          </div>
        </div>
      </template>
      
      <div style="margin-bottom: 15px;">
        <el-text type="info">
          提示：只支持SELECT查询，限制返回最多10000行数据
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
        <el-input-number v-model="queryLimit" :min="10" :max="10000" :step="10" />
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

    <!-- 搜索进度弹窗 -->
    <el-dialog 
      v-model="searchProgressDialogVisible" 
      title="字段值搜索进度" 
      width="600px"
      :close-on-click-modal="false"
      :show-close="false"
      center
    >
      <div class="search-progress-content">
        <div class="progress-header">
          <el-icon class="spinning-icon"><Loading /></el-icon>
          <span class="progress-title">正在搜索数据库中的表...</span>
        </div>
        
        <div class="progress-info">
          <div class="search-info">
            <p><strong>搜索值：</strong>{{ currentSearchValue }}</p>
            <p><strong>数据库：</strong>{{ getSelectedDatabaseInfo()?.displayName || selectedSearchDatabase }}</p>
          </div>
          
          <div class="progress-details">
            <el-progress 
              :percentage="searchProgressPercentage" 
              :stroke-width="8"
              status="warning"
              striped
              striped-flow
            />
            <div class="progress-text">
              <span class="current-progress">{{ searchProgressText }}</span>
              <span class="time-info">已用时：{{ formatElapsedTime(searchElapsedTime) }}</span>
            </div>
          </div>
          
          <div v-if="currentSearchingTable" class="current-table">
            <p><strong>当前搜索表：</strong>{{ currentSearchingTable }}</p>
          </div>
          
          <div v-if="foundTablesCount > 0" class="found-info">
            <el-tag type="success">已找到 {{ foundTablesCount }} 个匹配的表</el-tag>
          </div>
        </div>
      </div>
      
      <template #footer>
        <div style="text-align: center;">
          <el-button @click="cancelSearch" type="danger" :loading="cancelling">
            取消搜索
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api, { userState } from '../utils/api'
import { Lightning, Loading } from '@element-plus/icons-vue'

export default {
  name: 'Query',
  components: {
    Lightning,
    Loading
  },
  setup() {
    const sqlQuery = ref('')
    const queryLimit = ref(100)
    const executing = ref(false)
    const queryResult = ref({})
    const queryError = ref('')
    const selectedDatabase = ref('chembl33')
    const availableDatabases = ref([])

    // 字段值搜索相关的响应式变量
    const fieldSearchQuery = ref('')
    const fieldSearching = ref(false)
    const fieldSearchResult = ref({})
    const fieldSearchError = ref('')
    const selectedSearchDatabase = ref('chembl33')
    const tableDataResult = ref({})
    const tableDataLoading = ref(false)
    
    // 表数据分页
    const tableDataPagination = ref({
      currentPage: 1,
      pageSize: 50,
      totalCount: 0
    })

    // 搜索进度相关
    const searchProgressDialogVisible = ref(false)
    const currentSearchValue = ref('')
    const searchProgressPercentage = ref(0)
    const searchProgressText = ref('')
    const currentSearchingTable = ref('')
    const foundTablesCount = ref(0)
    const totalTablesCount = ref(0)
    const searchStartTime = ref(0)
    const searchElapsedTime = ref(0)
    const searchTimer = ref(null)
    const cancelling = ref(false)
    const currentSearchAbortController = ref(null)

    const examples = {
      showTables: 'SHOW TABLES',
      showColumns: 'DESCRIBE table_name',
      countRows: 'SELECT COUNT(*) as total_rows FROM table_name',
      dataPreview: 'SELECT * FROM table_name LIMIT 10'
    }

    const loadAvailableDatabases = async () => {
      try {
        // 如果用户已登录，尝试获取完整的数据库列表
        if (checkUserLogin()) {
          const userInfo = userState.getUserInfo()
          
          // 尝试获取所有数据库（包括用户创建的）
          const response = await api.get('/api/database/all', {
            params: {
              dataSource: 'chembl33',
              userId: userInfo.userId,
              userType: userInfo.userType
            }
          })
          availableDatabases.value = response.data
          return
        }
        
        // 用户未登录时，尝试获取默认数据库列表
        const fallbackResponse = await api.get('/api/database/databases')
        availableDatabases.value = fallbackResponse.data
      } catch (error) {
        console.error('加载数据库列表失败:', error)
        // 如果都失败，使用硬编码的默认数据库
        availableDatabases.value = [
          { name: 'chembl33', displayName: 'ChEMBL 33', description: 'ChEMBL化学生物学数据库' },
          { name: 'tcrd6124expr2', displayName: 'TCRD 6.12.4', description: '目标中心研究数据库' }
        ]
      }
    }

    const executeQuery = async () => {
      if (!sqlQuery.value.trim()) {
        queryError.value = 'SQL查询语句不能为空'
        return
      }

      // 检查用户登录状态
      if (!checkUserLogin()) {
        queryError.value = '请先登录后再执行查询'
        return
      }

      executing.value = true
      queryError.value = ''
      queryResult.value = {}

      try {
        const userInfo = userState.getUserInfo()
        const response = await api.post('/api/database/query', {
          sql: sqlQuery.value,
          limit: queryLimit.value,
          dataSource: selectedDatabase.value,
          userId: userInfo.userId,
          userType: userInfo.userType
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

    // 检查用户登录状态
    const checkUserLogin = () => {
      const userInfo = userState.getUserInfo()
      return userInfo && userInfo.userId && userInfo.userType
    }

    // 字段值搜索相关方法
    const searchTablesByField = async () => {
      if (!fieldSearchQuery.value.trim()) {
        fieldSearchError.value = '搜索值不能为空'
        return
      }

      // 检查用户登录状态
      if (!checkUserLogin()) {
        fieldSearchError.value = '请先登录后再进行搜索'
        return
      }

      // 立即显示进度弹窗
      showSearchProgressDialog(fieldSearchQuery.value.trim())

      fieldSearching.value = true
      fieldSearchError.value = ''
      fieldSearchResult.value = {}
      tableDataResult.value = {} // 清空表数据结果

      try {
        // 使用SSE接收实时进度
        const userInfo = userState.getUserInfo()
        await startRealTimeSearch(fieldSearchQuery.value, selectedSearchDatabase.value, userInfo.userId, userInfo.userType)
      } catch (error) {
        closeSearchProgressDialog()
        
        // 如果是用户取消的请求，不显示错误信息
        if (error.message && error.message.includes('canceled')) {
          return
        }
        
        fieldSearchError.value = error.message || '字段值搜索失败'
      } finally {
        fieldSearching.value = false
      }
    }

    const viewTableData = async (tableName, searchValue) => {
      // 检查用户登录状态
      if (!checkUserLogin()) {
        console.error('用户未登录，无法查看表数据')
        return
      }

      // 设置当前查看的表信息，用于后续分页
      tableDataResult.value.tableName = tableName
      tableDataResult.value.searchValue = searchValue
      
      // 重置分页
      tableDataPagination.value.currentPage = 1
      tableDataPagination.value.pageSize = 50
      
      // 加载数据
      await loadTableDataByValue()
    }

    const loadTableDataByValue = async () => {
      if (!tableDataResult.value.tableName || !tableDataResult.value.searchValue) {
        return
      }

      // 检查用户登录状态
      if (!checkUserLogin()) {
        console.error('用户未登录，无法查看表数据')
        return
      }

      tableDataLoading.value = true
      try {
        const userInfo = userState.getUserInfo()
        const response = await api.get('/api/database/search/data-by-value', {
          params: {
            tableName: tableDataResult.value.tableName,
            searchValue: tableDataResult.value.searchValue,
            dataSource: selectedSearchDatabase.value,
            limit: tableDataPagination.value.pageSize,
            page: tableDataPagination.value.currentPage,
            userId: userInfo.userId,
            userType: userInfo.userType
          }
        })
        
        tableDataResult.value = {
          ...tableDataResult.value,
          ...response.data
        }
        tableDataPagination.value.totalCount = response.data.totalCount || 0
      } catch (error) {
        console.error('查看表数据失败:', error)
        // 使用ElementPlus的消息提示
        // ElMessage.error('查看表数据失败: ' + (error.response?.data?.error || error.message))
      } finally {
        tableDataLoading.value = false
      }
    }

    // 分页事件处理
    const handleTableDataSizeChange = (newSize) => {
      tableDataPagination.value.pageSize = newSize
      tableDataPagination.value.currentPage = 1
      loadTableDataByValue()
    }

    const handleTableDataCurrentChange = (newPage) => {
      tableDataPagination.value.currentPage = newPage
      loadTableDataByValue()
    }

    const changeTableDataPageSize = () => {
      tableDataPagination.value.currentPage = 1
      loadTableDataByValue()
    }

    // 判断单元格值是否包含搜索值
    const cellContainsSearchValue = (cellValue, searchValue) => {
      if (!cellValue || !searchValue) return false
      return String(cellValue).toLowerCase().includes(String(searchValue).toLowerCase())
    }

    const clearFieldSearch = () => {
      fieldSearchQuery.value = ''
      fieldSearchResult.value = {}
      fieldSearchError.value = ''
      tableDataResult.value = {}
    }

    const closeTableData = () => {
      tableDataResult.value = {}
      tableDataPagination.value.currentPage = 1
      tableDataPagination.value.totalCount = 0
    }

    // 搜索进度相关方法
    const showSearchProgressDialog = (searchValue) => {
      currentSearchValue.value = searchValue
      searchProgressDialogVisible.value = true
      searchProgressPercentage.value = 0
      searchProgressText.value = '准备开始搜索...'
      currentSearchingTable.value = ''
      foundTablesCount.value = 0
      totalTablesCount.value = 0
      searchStartTime.value = Date.now()
      searchElapsedTime.value = 0
      cancelling.value = false
      
      // 启动计时器
      startElapsedTimeTimer()
    }

    const closeSearchProgressDialog = () => {
      searchProgressDialogVisible.value = false
      stopElapsedTimeTimer()
      if (searchTimer.value) {
        clearTimeout(searchTimer.value)
        searchTimer.value = null
      }
    }

    // SSE实时搜索方法
    const startRealTimeSearch = (searchValue, dataSource, userId, userType) => {
      return new Promise((resolve, reject) => {
        const baseUrl = api.defaults.baseURL || ''
        const url = `${baseUrl}/api/database/search/tables-by-value-progress?` + new URLSearchParams({
          searchValue,
          dataSource,
          userId: userId.toString(),
          userType
        })

        const eventSource = new EventSource(url)
        currentSearchAbortController.value = { eventSource }
        let searchResult = null
        let isCompleted = false // 添加标志来跟踪是否正常完成

        // 监听各种事件
        eventSource.addEventListener('start', (event) => {
          const data = JSON.parse(event.data)
          console.log('搜索开始:', data)
          searchProgressText.value = '正在准备搜索...'
        })

        eventSource.addEventListener('total', (event) => {
          const data = JSON.parse(event.data)
          totalTablesCount.value = data.totalTables
          console.log('总表数:', data.totalTables)
        })

        eventSource.addEventListener('progress', (event) => {
          const data = JSON.parse(event.data)
          currentSearchingTable.value = data.currentTable
          searchProgressPercentage.value = data.percentage
          searchProgressText.value = `${data.searchedCount}/${data.totalCount}`
          foundTablesCount.value = data.foundCount
          
          console.log('搜索进度:', data)
        })

        eventSource.addEventListener('found', (event) => {
          const data = JSON.parse(event.data)
          foundTablesCount.value = data.foundCount
          console.log('找到匹配表:', data.table.TABLE_NAME)
        })

        eventSource.addEventListener('complete', (event) => {
          const data = JSON.parse(event.data)
          console.log('搜索完成:', data)
          
          isCompleted = true // 标记为正常完成
          
          searchProgressText.value = `${data.searchedCount}/${data.searchedCount}`
          searchProgressPercentage.value = 100
          currentSearchingTable.value = '搜索完成'
          foundTablesCount.value = data.foundCount
          
          // 保存搜索结果
          searchResult = data
          fieldSearchResult.value = data
          
          // 关闭进度弹窗
          setTimeout(() => {
            closeSearchProgressDialog()
            eventSource.close()
            resolve(searchResult)
          }, 1000)
        })

        eventSource.addEventListener('error', (event) => {
          console.error('SSE错误:', event)
          const data = JSON.parse(event.data)
          eventSource.close()
          reject(new Error(data.message || '搜索过程中发生错误'))
        })

        eventSource.addEventListener('timeout', (event) => {
          const data = JSON.parse(event.data)
          console.warn('搜索超时:', data)
          eventSource.close()
          resolve(searchResult)
        })

        eventSource.addEventListener('table_error', (event) => {
          const data = JSON.parse(event.data)
          console.warn('表搜索错误:', data)
        })

        eventSource.onerror = () => {
          // 只有在非正常完成的情况下才报告错误
          if (!isCompleted) {
            console.error('EventSource连接错误')
            eventSource.close()
            reject(new Error('网络连接错误，请重试'))
          }
        }
      })
    }

    const startElapsedTimeTimer = () => {
      const timer = setInterval(() => {
        if (!searchProgressDialogVisible.value) {
          clearInterval(timer)
          return
        }
        searchElapsedTime.value = Date.now() - searchStartTime.value
      }, 1000)
    }

    const stopElapsedTimeTimer = () => {
      // 计时器会在下次更新时自动停止
    }

    const formatElapsedTime = (ms) => {
      const seconds = Math.floor(ms / 1000)
      const minutes = Math.floor(seconds / 60)
      const remainingSeconds = seconds % 60
      
      if (minutes > 0) {
        return `${minutes}分${remainingSeconds}秒`
      } else {
        return `${remainingSeconds}秒`
      }
    }

    const cancelSearch = () => {
      cancelling.value = true
      
      // 关闭SSE连接
      if (currentSearchAbortController.value && currentSearchAbortController.value.eventSource) {
        currentSearchAbortController.value.eventSource.close()
        currentSearchAbortController.value = null
      }
      
      // 关闭进度弹窗
      closeSearchProgressDialog()
      
      // 重置搜索状态
      fieldSearching.value = false
      ElMessage.info('搜索已取消')
    }

    const getSelectedDatabaseInfo = () => {
      return availableDatabases.value.find(db => db.name === selectedSearchDatabase.value)
    }

    onMounted(() => {
      loadAvailableDatabases()
    })

    return {
      sqlQuery,
      queryLimit,
      executing,
      queryResult,
      queryError,
      selectedDatabase,
      availableDatabases,
      examples,
      executeQuery,
      clearQuery,
      setExampleQuery,
      getColumnWidth,
      loadAvailableDatabases,
      // 字段值搜索相关
      fieldSearchQuery,
      fieldSearching,
      fieldSearchResult,
      fieldSearchError,
      selectedSearchDatabase,
      tableDataResult,
      tableDataLoading,
      tableDataPagination,
      searchTablesByField,
      viewTableData,
      loadTableDataByValue,
      handleTableDataSizeChange,
      handleTableDataCurrentChange,
      changeTableDataPageSize,
      clearFieldSearch,
      closeTableData,
      checkUserLogin,
      cellContainsSearchValue,
      // 搜索进度相关
      searchProgressDialogVisible,
      currentSearchValue,
      searchProgressPercentage,
      searchProgressText,
      currentSearchingTable,
      foundTablesCount,
      totalTablesCount,
      searchElapsedTime,
      cancelling,
      showSearchProgressDialog,
      closeSearchProgressDialog,
      formatElapsedTime,
      cancelSearch,
      getSelectedDatabaseInfo
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

.search-progress-content {
  padding: 20px;
  text-align: center;
}

.progress-header {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
}

.spinning-icon {
  margin-right: 10px;
  animation: spin 2s linear infinite;
  color: #409eff;
  font-size: 20px;
}

.progress-title {
  font-size: 16px;
  font-weight: 600;
}

.progress-info {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.search-info {
  margin-bottom: 15px;
}

.progress-details {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.progress-text {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.current-progress {
  font-size: 14px;
  color: #303133;
}

.time-info {
  font-size: 12px;
  color: #909399;
}

.current-table {
  margin-top: 10px;
  text-align: left;
}

.found-info {
  margin-top: 10px;
  text-align: left;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
</style> 