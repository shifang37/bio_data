<template>
  <div class="permission-management">
    <div class="header">
      <h2>权限管理</h2>
      <p class="subtitle">管理内部用户的表写权限</p>
    </div>

    <!-- 操作按钮区域 -->
    <div class="actions">
      <el-button type="primary" @click="showCreateUserDialog = true">
        <el-icon><Plus /></el-icon>
        创建内部用户
      </el-button>
      <el-button type="success" @click="showGrantDialog = true">
        <el-icon><Key /></el-icon>
        授权权限
      </el-button>
      <el-button @click="refreshData">
        <el-icon><Refresh /></el-icon>
        刷新数据
      </el-button>
    </div>

    <!-- 内部用户列表 -->
    <div class="user-section">
      <h3>内部用户列表</h3>
      <el-table :data="internalUsers" style="width: 100%" v-loading="loadingUsers">
        <el-table-column prop="id" label="用户ID" width="80" />
        <el-table-column prop="name" label="用户名" />
        <el-table-column prop="role" label="角色" width="100" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="viewUserPermissions(row.id)">查看权限</el-button>
            <el-button size="small" type="primary" @click="grantPermissionForUser(row.id)">授权</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 权限记录列表 -->
    <div class="permissions-section">
      <h3>权限记录</h3>
      <el-table :data="permissions" style="width: 100%" v-loading="loadingPermissions">
        <el-table-column prop="id" label="记录ID" width="80" />
        <el-table-column prop="userName" label="用户名" width="120" />
        <el-table-column prop="permissionType" label="权限类型" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.permissionType === 'database'" type="success" size="small">
              数据库级
            </el-tag>
            <el-tag v-else type="info" size="small">
              表级
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="displayName" label="权限范围" width="200">
          <template #default="{ row }">
            {{ row.displayName }}
          </template>
        </el-table-column>
        <el-table-column prop="grantedByName" label="授权人" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="expiresAt" label="过期时间" width="180">
          <template #default="{ row }">
            <span v-if="row.expiresAt">
              {{ formatDateTime(row.expiresAt) }}
              <el-tag v-if="row.isExpired" type="danger" size="small">已过期</el-tag>
            </span>
            <span v-else>永久有效</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button 
              size="small" 
              type="danger" 
              @click="revokePermission(row)"
              :disabled="row.isExpired">
              撤销
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 创建内部用户对话框 -->
    <el-dialog v-model="showCreateUserDialog" title="创建内部用户" width="500px">
      <el-form :model="createUserForm" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="createUserForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="createUserForm.password" type="password" placeholder="请输入密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateUserDialog = false">取消</el-button>
        <el-button type="primary" @click="createInternalUser" :loading="creating">创建</el-button>
      </template>
    </el-dialog>

    <!-- 授权对话框 -->
    <el-dialog v-model="showGrantDialog" title="授权写权限" width="600px" @open="onGrantDialogOpen">
      <el-form :model="grantForm" label-width="100px">
        <el-form-item label="选择用户">
          <el-select v-model="grantForm.internalUserId" placeholder="请选择内部用户">
            <el-option 
              v-for="user in internalUsers" 
              :key="user.id" 
              :label="user.name" 
              :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="权限类型">
          <el-radio-group v-model="grantForm.permissionType" @change="onPermissionTypeChange">
            <el-radio-button label="database">数据库级权限</el-radio-button>
            <el-radio-button label="table">表级权限</el-radio-button>
          </el-radio-group>
          <div class="permission-type-hint">
            <p v-if="grantForm.permissionType === 'database'" class="hint-text">
              <el-icon><InfoFilled /></el-icon>
              数据库级权限：用户可以在该数据库中创建表、删除表、导入数据等所有操作
            </p>
            <p v-if="grantForm.permissionType === 'table'" class="hint-text">
              <el-icon><InfoFilled /></el-icon>
              表级权限：用户只能对指定表进行数据操作，无法创建或删除表
            </p>
          </div>
        </el-form-item>
        <el-form-item label="选择数据库">
          <el-select 
            v-model="grantForm.databaseName" 
            placeholder="请选择数据库"
            :loading="loadingDatabases"
            @change="onDatabaseChange">
            <el-option 
              v-for="database in availableDatabases" 
              :key="database.name" 
              :label="database.name" 
              :value="database.name" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="grantForm.permissionType === 'table'" label="选择表">
          <el-select 
            v-model="grantForm.tableName" 
            placeholder="请先选择数据库"
            :loading="loadingTables"
            :disabled="!grantForm.databaseName">
            <el-option 
              v-for="table in availableTables" 
              :key="table.name" 
              :label="table.name" 
              :value="table.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="grantForm.expiresAt"
            type="datetime"
            placeholder="留空表示永久有效"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeGrantDialog">取消</el-button>
        <el-button type="primary" @click="grantPermission" :loading="granting">授权</el-button>
      </template>
    </el-dialog>

    <!-- 用户权限查看对话框 -->
    <el-dialog v-model="showUserPermissionsDialog" title="用户权限详情" width="800px">
      <el-table :data="userPermissions" style="width: 100%" v-loading="loadingUserPermissions">
        <el-table-column prop="databaseName" label="数据库" width="120" />
        <el-table-column prop="tableName" label="表名" width="150" />
        <el-table-column prop="grantedByName" label="授权人" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="expiresAt" label="过期时间" width="180">
          <template #default="{ row }">
            <span v-if="row.expiresAt">
              {{ formatDateTime(row.expiresAt) }}
              <el-tag v-if="row.isExpired" type="danger" size="small">已过期</el-tag>
            </span>
            <span v-else>永久有效</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button 
              size="small" 
              type="danger" 
              @click="revokePermission(row.userId, row.databaseName, row.tableName)"
              :disabled="row.isExpired">
              撤销
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Key, Refresh, InfoFilled } from '@element-plus/icons-vue'
import { permissionApi, userState } from '../utils/api.js'

export default {
  name: 'PermissionManagement',
  components: {
    Plus,
    Key, 
    Refresh,
    InfoFilled
  },
  data() {
    return {
      internalUsers: [],
      permissions: [],
      userPermissions: [],
      loadingUsers: false,
      loadingPermissions: false,
      loadingUserPermissions: false,
      creating: false,
      granting: false,
      
      showCreateUserDialog: false,
      showGrantDialog: false,
      showUserPermissionsDialog: false,
      
      createUserForm: {
        username: '',
        password: ''
      },
      
      grantForm: {
        internalUserId: '',
        databaseName: '',
        tableName: '',
        expiresAt: null,
        permissionType: 'table' // 默认为表级权限
      },
      
      // 数据库和表的选项
      availableDatabases: [],
      availableTables: [],
      loadingDatabases: false,
      loadingTables: false,
      
      currentViewUserId: null
    }
  },
  
  computed: {
    adminId() {
      // 从userState获取用户ID，更安全
      const userInfo = userState.getUserInfo() || {}
      if (userInfo.userId && userInfo.userId !== null) {
        return parseInt(userInfo.userId)
      }
      
      // 备用方案：从localStorage获取
      const userId = localStorage.getItem('userId') || localStorage.getItem('userInfo')
      if (userId && userId !== 'null' && userId !== 'undefined') {
        try {
          // 如果是JSON字符串，解析它
          if (userId.startsWith('{')) {
            const userObj = JSON.parse(userId)
            return parseInt(userObj.userId)
          }
          return parseInt(userId)
        } catch (e) {
          console.error('解析userId失败:', e)
        }
      }
      
      console.error('无法获取有效的adminId，请重新登录')
      return null
    }
  },
  
  mounted() {
    this.refreshData()
  },
  
  methods: {
    checkAdminId() {
      if (!this.adminId || isNaN(this.adminId)) {
        ElMessage.error('无法获取管理员ID，请重新登录')
        return false
      }
      return true
    },
    
    async refreshData() {
      if (!this.checkAdminId()) {
        return
      }
      await Promise.all([
        this.loadInternalUsers(),
        this.loadPermissions()
      ])
    },
    
    async loadInternalUsers() {
      if (!this.checkAdminId()) return
      
      this.loadingUsers = true
      try {
        const response = await permissionApi.getAllInternalUsers(this.adminId)
        if (response.success) {
          this.internalUsers = response.users
        } else {
          ElMessage.error(response.error || '获取内部用户列表失败')
        }
      } catch (error) {
        ElMessage.error('获取内部用户列表失败')
        console.error(error)
      }
      this.loadingUsers = false
    },
    
    async loadPermissions() {
      if (!this.checkAdminId()) return
      
      this.loadingPermissions = true
      try {
        const response = await permissionApi.getAllPermissions(this.adminId)
        if (response.success) {
          this.permissions = response.permissions
        } else {
          ElMessage.error(response.error || '获取权限记录失败')
        }
      } catch (error) {
        ElMessage.error('获取权限记录失败')
        console.error(error)
      }
      this.loadingPermissions = false
    },
    
    async createInternalUser() {
      if (!this.checkAdminId()) return
      
      if (!this.createUserForm.username || !this.createUserForm.password) {
        ElMessage.warning('请填写完整信息')
        return
      }
      
      this.creating = true
      try {
        const response = await permissionApi.createInternalUser({
          adminId: this.adminId,
          username: this.createUserForm.username,
          password: this.createUserForm.password
        })
        
        if (response.success) {
          ElMessage.success('创建内部用户成功')
          this.showCreateUserDialog = false
          this.createUserForm = { username: '', password: '' }
          this.loadInternalUsers()
        } else {
          ElMessage.error(response.error || '创建用户失败')
        }
      } catch (error) {
        ElMessage.error('创建用户失败')
        console.error(error)
      }
      this.creating = false
    },
    
    async grantPermission() {
      if (!this.checkAdminId()) return
      
      // 验证必填字段
      if (!this.grantForm.internalUserId || !this.grantForm.databaseName) {
        ElMessage.warning('请选择用户和数据库')
        return
      }
      
      // 表级权限需要选择表
      if (this.grantForm.permissionType === 'table' && !this.grantForm.tableName) {
        ElMessage.warning('表级权限需要选择具体的表')
        return
      }
      
      this.granting = true
      try {
        const requestData = {
          adminId: this.adminId,
          internalUserId: this.grantForm.internalUserId,
          databaseName: this.grantForm.databaseName
        }
        
        if (this.grantForm.expiresAt) {
          requestData.expiresAt = this.grantForm.expiresAt
        }
        
        let response
        if (this.grantForm.permissionType === 'database') {
          // 数据库级权限
          response = await permissionApi.grantDatabaseWriteAccess(requestData)
        } else {
          // 表级权限
          requestData.tableName = this.grantForm.tableName
          response = await permissionApi.grantTableWriteAccess(requestData)
        }
        
        if (response.success) {
          const permissionTypeText = this.grantForm.permissionType === 'database' ? '数据库级' : '表级'
          ElMessage.success(`${permissionTypeText}权限授权成功`)
          this.closeGrantDialog()
          this.loadPermissions()
        } else {
          ElMessage.error(response.error || '权限授权失败')
        }
      } catch (error) {
        ElMessage.error('权限授权失败')
        console.error(error)
      }
      this.granting = false
    },
    
    async revokePermission(permission) {
      try {
        const permissionTypeText = permission.permissionType === 'database' ? '数据库级' : '表级'
        const confirmMessage = permission.permissionType === 'database' 
          ? `确定要撤销用户对数据库 ${permission.databaseName} 的${permissionTypeText}权限吗？`
          : `确定要撤销用户对表 ${permission.databaseName}.${permission.tableName} 的${permissionTypeText}权限吗？`
        
        await ElMessageBox.confirm(
          confirmMessage,
          '确认撤销',
          {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
          }
        )
        
        let response
        const requestData = {
          adminId: this.adminId,
          internalUserId: permission.userId,
          databaseName: permission.databaseName
        }
        
        if (permission.permissionType === 'database') {
          response = await permissionApi.revokeDatabaseWriteAccess(requestData)
        } else {
          requestData.tableName = permission.tableName
          response = await permissionApi.revokeTableWriteAccess(requestData)
        }
        
        if (response.success) {
          ElMessage.success(`${permissionTypeText}权限撤销成功`)
          this.loadPermissions()
          if (this.showUserPermissionsDialog && this.currentViewUserId === permission.userId) {
            this.viewUserPermissions(permission.userId) // 刷新用户权限详情
          }
        } else {
          ElMessage.error(response.error || '权限撤销失败')
        }
        
      } catch (error) {
        if (error !== 'cancel') {
          ElMessage.error('权限撤销失败')
          console.error(error)
        }
      }
    },
    
    async viewUserPermissions(userId) {
      if (!this.checkAdminId()) return
      
      this.currentViewUserId = userId
      this.loadingUserPermissions = true
      this.showUserPermissionsDialog = true
      
      try {
        const response = await permissionApi.getUserPermissions(userId, this.adminId)
        if (response.success) {
          this.userPermissions = response.permissions
        } else {
          ElMessage.error(response.error || '获取用户权限失败')
        }
      } catch (error) {
        ElMessage.error('获取用户权限失败')
        console.error(error)
      }
      this.loadingUserPermissions = false
    },
    
    grantPermissionForUser(userId) {
      this.grantForm.internalUserId = userId
      this.showGrantDialog = true
    },
    
    // 授权对话框打开时的处理
    async onGrantDialogOpen() {
      await this.loadAvailableDatabases()
    },
    
    // 关闭授权对话框
    closeGrantDialog() {
      this.showGrantDialog = false
      this.grantForm = { internalUserId: '', databaseName: '', tableName: '', expiresAt: null, permissionType: 'table' }
      this.availableTables = []
    },
    
    // 权限类型改变时的处理
    onPermissionTypeChange() {
      // 如果切换到数据库级权限，清空表选择
      if (this.grantForm.permissionType === 'database') {
        this.grantForm.tableName = ''
        this.availableTables = []
      }
    },
    
    // 加载可用数据库列表
    async loadAvailableDatabases() {
      if (!this.checkAdminId()) return
      
      this.loadingDatabases = true
      try {
        const response = await permissionApi.getAllDatabases(this.adminId)
        if (response.success) {
          this.availableDatabases = response.databases
        } else {
          ElMessage.error(response.error || '获取数据库列表失败')
        }
      } catch (error) {
        ElMessage.error('获取数据库列表失败')
        console.error(error)
      }
      this.loadingDatabases = false
    },
    
    // 数据库选择变化时的处理
    async onDatabaseChange(databaseName) {
      // 清空表选择
      this.grantForm.tableName = ''
      this.availableTables = []
      
      if (!databaseName) return
      
      await this.loadTablesInDatabase(databaseName)
    },
    
    // 加载指定数据库中的表列表
    async loadTablesInDatabase(databaseName) {
      if (!this.checkAdminId() || !databaseName) return
      
      this.loadingTables = true
      try {
        const response = await permissionApi.getTablesInDatabase(databaseName, this.adminId)
        if (response.success) {
          this.availableTables = response.tables
        } else {
          ElMessage.error(response.error || '获取表列表失败')
        }
      } catch (error) {
        ElMessage.error('获取表列表失败')
        console.error(error)
      }
      this.loadingTables = false
    },
    
    formatDateTime(timestamp) {
      if (!timestamp) return ''
      const date = new Date(timestamp)
      return date.toLocaleString('zh-CN')
    }
  }
}
</script>

<style scoped>
.permission-management {
  padding: 20px;
}

.header {
  margin-bottom: 30px;
}

.header h2 {
  margin: 0 0 8px 0;
  color: #303133;
}

.subtitle {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.actions {
  margin-bottom: 20px;
}

.actions .el-button + .el-button {
  margin-left: 12px;
}

.user-section, .permissions-section {
  margin-bottom: 30px;
}

.user-section h3, .permissions-section h3 {
  margin: 0 0 15px 0;
  color: #303133;
  font-size: 16px;
}

.el-table {
  border: 1px solid #EBEEF5;
  border-radius: 4px;
}

.el-form-item {
  margin-bottom: 18px;
}

.el-dialog__body {
  padding: 20px;
}

.el-select {
  width: 100%;
}

.el-date-picker {
  width: 100%;
}


/* 加载状态优化 */
.el-select .el-input.is-focus .el-input__inner {
  border-color: #409eff;
}

/* 禁用状态的样式 */
.el-select.is-disabled .el-input__inner {
  background-color: #f5f7fa;
  border-color: #e4e7ed;
  color: #c0c4cc;
}

/* 权限类型提示样式 */
.permission-type-hint {
  margin-top: 8px;
}

.hint-text {
  display: flex;
  align-items: center;
  color: #909399;
  font-size: 12px;
  margin: 0;
  padding: 8px 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
  border-left: 3px solid #409eff;
}

.hint-text .el-icon {
  margin-right: 6px;
  color: #409eff;
}
</style>
