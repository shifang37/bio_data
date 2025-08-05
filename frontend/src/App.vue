<template>
  <div id="app">
    <!-- 登录页面时不显示导航栏 -->
    <div v-if="$route.name === 'Login'">
      <router-view />
    </div>
    
    <!-- 主界面显示导航栏 -->
    <el-container v-else style="height: 100vh;">
      <el-header style="background-color: #545c64; color: white; padding: 0 20px;">
        <div style="display: flex; align-items: center; height: 100%;">
          <h1 style="margin: 0; font-size: 24px;">数据库可视化系统</h1>
          
          <div style="margin-left: auto; display: flex; align-items: center; gap: 15px;">
            <!-- 用户信息 -->
            <div v-if="currentUser" style="display: flex; align-items: center; gap: 10px;">
              <el-tag 
                :type="currentUser.userType === 'admin' ? 'warning' : 'primary'"
                effect="dark"
              >
                {{ currentUser.userType === 'admin' ? '管理员' : '用户' }}: {{ currentUser.username }}
              </el-tag>
              <el-button 
                type="danger" 
                size="small" 
                @click="logout"
                :icon="SwitchButton"
              >
                退出登录
              </el-button>
            </div>
            
            <!-- 数据库连接状态 -->
            <el-tag v-if="connectionStatus.status === 'UP'" type="success">
              数据库已连接: {{ connectionStatus.database }}
            </el-tag>
            <el-tag v-else type="danger">数据库连接失败</el-tag>
          </div>
        </div>
      </el-header>
      
      <el-container>
        <el-aside width="180px" style="background-color: #f5f5f5;">
          <el-menu
            :default-active="$route.path"
            class="el-menu-vertical"
            router
            style="border-right: none; height: 100%;"
          >
            <el-menu-item index="/dashboard">
              <el-icon><DataBoard /></el-icon>
              <span>仪表板</span>
            </el-menu-item>
            <el-menu-item index="/tables">
              <el-icon><Grid /></el-icon>
              <span>数据库管理</span>
            </el-menu-item>
            <el-menu-item index="/query">
              <el-icon><Search /></el-icon>
              <span>SQL查询</span>
            </el-menu-item>
            <el-menu-item index="/import">
              <el-icon><Upload /></el-icon>
              <span>数据导入</span>
            </el-menu-item>
            <el-menu-item index="/knowledge-graph">
              <el-icon><Share /></el-icon>
              <span>知识图谱</span>
            </el-menu-item>
          </el-menu>
        </el-aside>
        
        <el-main>
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { DataBoard, Grid, Search, SwitchButton, Upload, Share } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api, { userState } from './utils/api'

export default {
  name: 'App',
  components: {
    DataBoard,
    Grid,  
    Search,
    SwitchButton,
    Upload,
    Share
  },
  setup() {
    const router = useRouter()
    
    const connectionStatus = ref({
      status: 'DOWN',
      database: 'Disconnected'
    })

    // 使用全局userState作为当前用户信息
    const currentUser = computed(() => {
      if (userState.userId && userState.username) {
        return {
          userId: userState.userId,
          username: userState.username,
          userType: userState.userType,
          permission: userState.permission,
          canAccessLogin: userState.canAccessLogin,
          canModifyLogin: userState.canModifyLogin
        }
      }
      return null
    })

    const checkConnection = async () => {
      try {
        const response = await api.get('/api/database/health')
        connectionStatus.value = response.data
      } catch (error) {
        console.error('连接检查失败:', error)
        connectionStatus.value = {
          status: 'DOWN',
          database: 'Connection Failed'
        }
      }
    }

    // 退出登录
    const logout = async () => {
      try {
        await ElMessageBox.confirm(
          '确定要退出登录吗？',
          '确认退出',
          {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning',
          }
        )
        
        // 清除用户状态
        userState.clearUserInfo()
        
        // 显示退出成功消息
        ElMessage.success('已成功退出登录')
        
        // 跳转到登录页面
        router.push('/login')
        
      } catch (error) {
        // 用户取消退出
        console.log('用户取消退出登录')
      }
    }

    onMounted(() => {
      // 初始化时从localStorage恢复用户状态
      userState.getUserInfo()
      
      checkConnection()
      // 每30秒检查一次连接状态
      setInterval(checkConnection, 30000)
    })

    return {
      connectionStatus,
      currentUser,
      logout,
      SwitchButton
    }
  }
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

#app {
  font-family: 'Avenir', Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

.el-menu-item {
  height: 50px;
  line-height: 50px;
  padding: 0 15px !important;
}

.el-menu-item .el-icon {
  margin-right: 8px;
}

.el-menu-item span {
  font-size: 14px;
}

.el-main {
  padding: 15px 20px !important;
  background-color: #fff;
}

.el-aside {
  border-right: 1px solid #e6e6e6;
}
</style> 