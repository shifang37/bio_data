import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { userState } from './utils/api'

const app = createApp(App)
app.use(ElementPlus)
app.use(router)

// 全局注入userState
app.config.globalProperties.$userState = userState

app.mount('#app') 