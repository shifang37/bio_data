<template>
  <div id="app">
    <el-container style="height: 100vh;">
      <el-header style="background-color: #545c64; color: white; padding: 0 20px;">
        <div style="display: flex; align-items: center; height: 100%;">
          <h1 style="margin: 0; font-size: 24px;">数据库可视化系统</h1>
          <div style="margin-left: auto;">
            <el-tag v-if="connectionStatus.status === 'UP'" type="success">
              数据库已连接: {{ connectionStatus.database }}
            </el-tag>
            <el-tag v-else type="danger">数据库连接失败</el-tag>
          </div>
        </div>
      </el-header>
      
      <el-container>
        <el-aside width="250px" style="background-color: #f5f5f5;">
          <el-menu
            default-active="dashboard"
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
              <span>数据表</span>
            </el-menu-item>
            <el-menu-item index="/query">
              <el-icon><Search /></el-icon>
              <span>SQL查询</span>
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
import { ref, onMounted } from 'vue'
import { DataBoard, Grid, Search } from '@element-plus/icons-vue'
import api from './utils/api'

export default {
  name: 'App',
  components: {
    DataBoard,
    Grid,  
    Search
  },
  setup() {
    const connectionStatus = ref({
      status: 'DOWN',
      database: 'Disconnected'
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

    onMounted(() => {
      checkConnection()
      // 每30秒检查一次连接状态
      setInterval(checkConnection, 30000)
    })

    return {
      connectionStatus
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
}
</style> 