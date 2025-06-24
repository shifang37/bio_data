import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import Tables from '../views/Tables.vue'
import Query from '../views/Query.vue'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
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
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router 