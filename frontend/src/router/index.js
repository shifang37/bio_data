import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'
import Tables from '../views/Tables.vue'
import Query from '../views/Query.vue'
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
    path: '/dashboard',
    name: 'Dashboard',
    component: Dashboard
  },
  {
    path: '/tables',
    name: 'Tables',
    component: Tables
  },
  {
    path: '/query',
    name: 'Query',
    component: Query
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