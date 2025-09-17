import axios from 'axios'
import { ref, reactive } from 'vue'

const api = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 600000, // 10分钟超时，匹配后端完整搜索时间
  headers: {
    'Content-Type': 'application/json'
  }
})

// 用户状态管理 - 使用响应式对象
export const userState = reactive({
  userId: null,
  username: null,
  userType: null,
  permission: null,
  canAccessLogin: false,
  canModifyLogin: false,
  
  // 设置用户信息
  setUserInfo(userInfo) {
    // 安全地转换userId
    let userId = userInfo.userId
    if (typeof userId === 'string') {
      userId = parseInt(userId)
    } else if (typeof userId === 'number') {
      userId = Math.floor(userId) // 确保是整数
    }
    
    this.userId = userId
    this.username = userInfo.username
    this.userType = userInfo.userType
    this.permission = userInfo.permission
    this.canAccessLogin = userInfo.canAccessLogin || false
    this.canModifyLogin = userInfo.canModifyLogin || false
    
    // 保存到localStorage，确保userId是数字类型
    const userInfoToSave = {
      ...userInfo,
      userId: userId
    }
    localStorage.setItem('userInfo', JSON.stringify(userInfoToSave))
    localStorage.setItem('user', JSON.stringify(userInfoToSave)) // 向后兼容
  },
  
  // 获取用户信息
  getUserInfo() {
    if (!this.userId) {
      const saved = localStorage.getItem('userInfo') || localStorage.getItem('user')
      if (saved) {
        try {
          const userInfo = JSON.parse(saved)
          this.setUserInfo(userInfo)
        } catch (e) {
          console.warn('解析用户信息失败:', e)
          localStorage.removeItem('userInfo')
          localStorage.removeItem('user')
        }
      }
    }
    return {
      userId: this.userId,
      username: this.username,
      userType: this.userType,
      permission: this.permission,
      canAccessLogin: this.canAccessLogin || false,
      canModifyLogin: this.canModifyLogin || false
    }
  },
  
  // 清除用户信息
  clearUserInfo() {
    this.userId = null
    this.username = null
    this.userType = null
    this.permission = null
    this.canAccessLogin = false
    this.canModifyLogin = false
    localStorage.removeItem('userInfo')
    localStorage.removeItem('user')
  },
  
  // 检查是否已登录
  isLoggedIn() {
    return this.userId !== null && this.userType !== null
  },
  
  // 检查是否为管理员
  isAdmin() {
    return this.userType === 'admin' && (this.permission === 'admin' || this.permission === 'super_admin')
  },
  
  // 检查是否可以访问login数据库
  canAccessLoginDatabase() {
    return this.canAccessLogin
  },
  
  // 检查是否可以修改login数据库
  canModifyLoginDatabase() {
    return this.canModifyLogin
  }
})

// 权限检查函数
export const checkPermission = (dataSource, operation = 'read') => {
  console.log(`权限检查: 数据库=${dataSource}, 操作=${operation}, 用户登录状态=${userState.isLoggedIn()}`)
  
  if (!userState.isLoggedIn()) {
    console.log('权限检查失败: 用户未登录')
    return { allowed: false, reason: '用户未登录' }
  }
  
  const userInfo = userState.getUserInfo()
  console.log(`用户信息: userType=${userInfo.userType}, canAccessLogin=${userInfo.canAccessLogin}, canModifyLogin=${userInfo.canModifyLogin}`)
  
  // 对于login数据库的特殊检查
  if (dataSource === 'login') {
    if (operation === 'read' && !userState.canAccessLoginDatabase()) {
      console.log('权限检查失败: 无法访问login数据库')
      return { allowed: false, reason: '只有管理员可以访问login数据库' }
    }
    if (operation === 'write' && !userState.canModifyLoginDatabase()) {
      console.log('权限检查失败: 无法修改login数据库')
      return { allowed: false, reason: '只有管理员可以修改login数据库' }
    }
  }
  
  // 对于其他数据库，所有已登录用户都可以访问和修改
  console.log(`权限检查通过: 数据库=${dataSource}, 操作=${operation}`)
  return { allowed: true }
}

// 为API请求添加用户信息
export const addUserInfoToRequest = (data = {}) => {
  const userInfo = userState.getUserInfo()
  if (userInfo.userId) {
    if (typeof data === 'object' && data !== null) {
      // 安全地转换userId
      let userId = userInfo.userId
      if (typeof userId === 'string') {
        userId = parseInt(userId)
      } else if (typeof userId === 'number') {
        userId = Math.floor(userId)
      }
      
      return {
        ...data,
        userId: userId,
        userType: userInfo.userType
      }
    }
  }
  return data
}

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    console.log('发送请求:', config.method?.toUpperCase(), config.url)
    
    // 为需要权限的请求自动添加用户信息
    const needsAuth = ['/api/database/tables', '/api/database/query']
    const needsAuthPath = needsAuth.some(path => config.url?.includes(path))
    
    if (needsAuthPath) {
      const userInfo = userState.getUserInfo()
      // 只有在用户已登录时才添加用户信息
      if (userInfo.userId && userInfo.userType) {
        if (config.data) {
          config.data = addUserInfoToRequest(config.data)
        }
        
        // 为GET请求添加用户信息到查询参数
        if (config.method === 'get') {
          // 安全地转换userId
          let userId = userInfo.userId
          if (typeof userId === 'string') {
            userId = parseInt(userId)
          } else if (typeof userId === 'number') {
            userId = Math.floor(userId)
          }
          
          config.params = {
            ...config.params,
            userId: userId,
            userType: userInfo.userType
          }
        }
      }
    }
    
    return config
  },
  (error) => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    console.log('响应数据:', response.status, response.data)
    return response
  },
  (error) => {
    console.error('响应错误:', error.response?.status, error.response?.data || error.message)
    
    // 处理权限相关错误
    if (error.response?.status === 401) {
      userState.clearUserInfo()
      console.warn('用户未登录，需要重新登录')
      // 不要在这里直接跳转，让组件处理
    } else if (error.response?.status === 403) {
      console.warn('权限不足:', error.response.data?.error)
      // 可以显示权限不足的提示
    }
    
    return Promise.reject(error)
  }
)

// 数据库API对象
export const databaseApi = {
  // 获取可用数据源列表
  getAvailableDataSources() {
    return api.get('/api/database/datasources')
  },

  // 获取所有可用数据库（用于CSV导入等功能）
  getAvailableDatabasesForImport(userId, userType) {
    return api.get('/api/database/datasources/databases', {
      params: { userId, userType }
    })
  },

  // 获取所有表
  getAllTables(dataSource, userId, userType) {
    return api.get('/api/database/tables', {
      params: { dataSource, userId, userType }
    })
  },

  // 获取表的列信息（用于CSV导入时的列映射）
  getTableColumnsInfo(tableName, dataSource, userId, userType) {
    return api.get(`/api/database/tables/${tableName}/columns-info`, {
      params: { dataSource, userId, userType }
    })
  },

  // 验证CSV数据格式
  validateCsvData(tableName, data) {
    return api.post(`/api/database/tables/${tableName}/validate-csv`, data)
  },

  // 批量插入表数据
  batchInsertTableData(tableName, data) {
    return api.post(`/api/database/tables/${tableName}/batch-insert`, data)
  },

  // 获取表的列信息
  getTableColumns(tableName, dataSource, userId, userType) {
    return api.get(`/api/database/tables/${tableName}/columns`, {
      params: { dataSource, userId, userType }
    })
  },

  // 获取指定表的准确行数
  getTableRowCount(tableName, dataSource, userId, userType) {
    return api.get(`/api/database/tables/${tableName}/row-count`, {
      params: { dataSource, userId, userType }
    })
  },

  // 获取表数据
  getTableData(tableName, dataSource, userId, userType, limit = 100) {
    return api.get(`/api/database/tables/${tableName}/data`, {
      params: { dataSource, userId, userType, limit }
    })
  },

  // 分页获取表数据
  getTableDataWithPagination(tableName, dataSource, userId, userType, page = 1, size = 20) {
    return api.get(`/api/database/tables/${tableName}/data/page`, {
      params: { dataSource, userId, userType, page, size }
    })
  },

  // 插入单条表数据
  insertTableData(tableName, data) {
    return api.post(`/api/database/tables/${tableName}/data`, data)
  },

  // 删除表数据
  deleteTableData(tableName, data) {
    return api.delete(`/api/database/tables/${tableName}/data`, { data })
  },

  // 更新表数据
  updateTableData(tableName, data) {
    return api.put(`/api/database/tables/${tableName}/data`, data)
  },

  // 执行SQL查询
  executeQuery(data) {
    return api.post('/api/database/query', data)
  },

  // 获取数据库统计信息
  getDatabaseStats(dataSource) {
    return api.get('/api/database/stats', {
      params: { dataSource }
    })
  },

  // 获取数据库信息
  getDatabaseInfo(database) {
    return api.get('/api/database/info', {
      params: { database }
    })
  },

  // 获取所有数据库
  getAllDatabases(dataSource, userId, userType) {
    return api.get('/api/database/all', {
      params: { dataSource, userId, userType }
    })
  },

  // 创建数据库
  createDatabase(data) {
    return api.post('/api/database/create', data)
  },

  // 删除数据库
  dropDatabase(data) {
    return api.delete('/api/database/drop', { data })
  },

  // 创建表
  createTable(data) {
    return api.post('/api/database/tables/create', data)
  },

  // 删除表
  dropTable(data) {
    return api.delete('/api/database/tables/drop', { data })
  },

  // 获取支持的数据类型
  getSupportedDataTypes() {
    return api.get('/api/database/data-types')
  },

  // 检查表是否存在
  checkTableExists(dataSource, databaseName, tableName) {
    return api.get('/api/database/tables/exists', {
      params: { dataSource, databaseName, tableName }
    })
  },

  // 按列搜索表
  findTablesByColumn(columnName, dataSource, userId, userType) {
    return api.get('/api/database/search/tables-by-column', {
      params: { columnName, dataSource, userId, userType }
    })
  },

  // 按值搜索表
  findTablesByValue(searchValue, dataSource, userId, userType, searchMode = 'auto') {
    return api.get('/api/database/search/tables-by-value', {
      params: { searchValue, dataSource, userId, userType, searchMode }
    })
  },

  // 按列获取表数据
  getTableDataByColumn(tableName, columnName, dataSource, userId, userType, limit = 100) {
    return api.get('/api/database/search/data-by-column', {
      params: { tableName, columnName, dataSource, userId, userType, limit }
    })
  },

  // 按值获取表数据
  getTableDataByValue(tableName, searchValue, dataSource, userId, userType, limit = 10000, page = 1, size = 50) {
    return api.get('/api/database/search/data-by-value', {
      params: { tableName, searchValue, dataSource, userId, userType, limit, page, size }
    })
  },

  // 诊断数据库和表状态
  diagnoseTableForImport(tableName, dataSource, userId, userType) {
    return api.get(`/api/database/tables/${tableName}/diagnose`, {
      params: { dataSource, userId, userType }
    })
  },

  // 检查数据库连接健康状态
  checkDatabaseHealth(dataSource) {
    return api.get('/api/database/health', {
      params: { dataSource }
    })
  },

  // 清除搜索缓存
  clearSearchCache(data) {
    return api.post('/api/database/cache/clear', data)
  },

  // 获取搜索缓存统计
  getSearchCacheStats(userId, userType) {
    return api.get('/api/database/cache/stats', {
      params: { userId, userType }
    })
  },

  // 自动建表并导入数据
  autoCreateTableAndImport(importData) {
    return api.post('/api/database/auto-create-table-import', importData)
  },

  // 导出表数据为CSV格式
  exportTableToCsv(tableName, dataSource, userId, userType, limit = 10000, whereClause = null) {
    const params = { dataSource, userId, userType, limit }
    if (whereClause) {
      params.whereClause = whereClause
    }
    return api.get(`/api/database/tables/${tableName}/export/csv`, {
      params,
      responseType: 'blob' // 重要：设置响应类型为blob以处理文件下载
    })
  },

  // 导出表数据为Excel格式
  exportTableToExcel(tableName, dataSource, userId, userType, limit = 10000, whereClause = null) {
    const params = { dataSource, userId, userType, limit }
    if (whereClause) {
      params.whereClause = whereClause
    }
    return api.get(`/api/database/tables/${tableName}/export/excel`, {
      params,
      responseType: 'blob' // 重要：设置响应类型为blob以处理文件下载
    })
  },

  // 获取导出文件信息
  getExportInfo(tableName, dataSource, userId, userType, whereClause = null) {
    const params = { dataSource, userId, userType }
    if (whereClause) {
      params.whereClause = whereClause
    }
    return api.get(`/api/database/tables/${tableName}/export/info`, {
      params
    })
  }
}

// 权限管理API对象
export const permissionApi = {
  // 为内部用户授权数据库级写权限
  grantDatabaseWriteAccess(data) {
    return api.post('/api/admin/permissions/grant-database', data)
      .then(response => response.data)
      .catch(error => {
        throw error.response?.data || error.message
      })
  },

  // 撤销用户的数据库级写权限
  revokeDatabaseWriteAccess(data) {
    return api.delete('/api/admin/permissions/revoke-database', { data })
      .then(response => response.data)
      .catch(error => {
        throw error.response?.data || error.message
      })
  },

  // 为内部用户授权表写权限
  grantTableWriteAccess(data) {
    return api.post('/api/admin/permissions/grant', data)
      .then(response => response.data)
      .catch(error => {
        throw error.response?.data || error.message
      })
  },

  // 撤销用户的表写权限
  revokeTableWriteAccess(data) {
    return api.post('/api/admin/permissions/revoke', data)
      .then(response => response.data)
      .catch(error => {
        throw error.response?.data || error.message
      })
  },

  // 获取所有权限授权记录
  getAllPermissions(adminId) {
    return api.get('/api/admin/permissions/all', {
      params: { adminId }
    })
      .then(response => response.data)
      .catch(error => {
        throw error.response?.data || error.message
      })
  },

  // 获取特定用户的权限记录
  getUserPermissions(internalUserId, adminId) {
    return api.get(`/api/admin/permissions/user/${internalUserId}`, {
      params: { adminId }
    })
      .then(response => response.data)
      .catch(error => {
        throw error.response?.data || error.message
      })
  },

  // 创建内部用户
  createInternalUser(data) {
    return api.post('/api/admin/permissions/create-internal-user', data)
      .then(response => response.data)
      .catch(error => {
        throw error.response?.data || error.message
      })
  },

  // 获取所有内部用户列表
  getAllInternalUsers(adminId) {
    return api.get('/api/admin/permissions/internal-users', {
      params: { adminId }
    })
      .then(response => response.data)
      .catch(error => {
        throw error.response?.data || error.message
      })
  },

  // 批量授权权限
  grantBatchTableWriteAccess(data) {
    return api.post('/api/admin/permissions/grant-batch', data)
      .then(response => response.data)
      .catch(error => {
        throw error.response?.data || error.message
      })
  },

  // 检查权限状态
  checkPermission(userId, databaseName, tableName) {
    return api.get('/api/admin/permissions/check', {
      params: { userId, databaseName, tableName }
    })
      .then(response => response.data)
      .catch(error => {
        throw error.response?.data || error.message
      })
  },

  // 获取所有可用数据库列表（仅管理员）
  getAllDatabases(adminId) {
    return api.get('/api/admin/permissions/databases', {
      params: { adminId }
    })
      .then(response => response.data)
      .catch(error => {
        throw error.response?.data || error.message
      })
  },

  // 获取指定数据库的所有表列表（仅管理员）
  getTablesInDatabase(databaseName, adminId) {
    return api.get(`/api/admin/permissions/databases/${databaseName}/tables`, {
      params: { adminId }
    })
      .then(response => response.data)
      .catch(error => {
        throw error.response?.data || error.message
      })
  }
}

export default api 