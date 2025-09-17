import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Tables from '../views/Tables.vue'
import Import from '../views/Import.vue'
import KnowledgeGraph from '../views/KnowledgeGraph.vue'
import PermissionManagement from '../components/PermissionManagement.vue'
import { userState } from '../utils/api.js'

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: Login
  },

  {
    path: '/tables',
    name: 'Tables',
    component: Tables
  },

  {
    path: '/import',
    name: 'Import',
    component: Import,
    meta: { 
      requiresAuth: true, 
      allowedRoles: ['admin', 'internal'] 
    }
  },
  {
    path: '/knowledge-graph',
    name: 'KnowledgeGraph',
    component: KnowledgeGraph
  },
  {
    path: '/permissions',
    name: 'PermissionManagement',
    component: PermissionManagement,
    meta: { 
      requiresAuth: true, 
      adminOnly: true 
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  // 检查路由是否需要认证
  if (to.matched.some(record => record.meta.requiresAuth)) {
    const userInfo = userState.getUserInfo()
    
    // 检查是否已登录
    if (!userState.isLoggedIn()) {
      console.warn('未登录用户尝试访问需要认证的页面:', to.path)
      next('/login')
      return
    }
    
    // 检查是否需要管理员权限
    if (to.matched.some(record => record.meta.adminOnly)) {
      if (!userState.isAdmin()) {
        console.warn('非管理员用户尝试访问管理员页面:', to.path, '用户权限:', userInfo)
        next('/tables') // 跳转到默认页面
        return
      }
    }
    
    // 检查角色权限
    const allowedRolesRoute = to.matched.find(record => record.meta.allowedRoles)
    if (allowedRolesRoute) {
      const allowedRoles = allowedRolesRoute.meta.allowedRoles
      const currentUserType = userInfo.userType || userInfo.permission
      
      if (!allowedRoles.includes(currentUserType)) {
        console.warn(`用户角色 ${currentUserType} 无权访问页面:`, to.path, '允许的角色:', allowedRoles)
        next('/tables') // 跳转到默认页面
        return
      }
    }
  }
  
  next()
})

export default router 