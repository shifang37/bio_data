import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Tables from '../views/Tables.vue'
import Import from '../views/Import.vue'
import KnowledgeGraph from '../views/KnowledgeGraph.vue'

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
    component: Import
  },
  {
    path: '/knowledge-graph',
    name: 'KnowledgeGraph',
    component: KnowledgeGraph
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router 