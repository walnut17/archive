import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
import './assets/main.css'

const app = createApp(App)

// 全局错误处理器 (T-0611-16/17 + plan-frontend-error-standard)
import { reportError } from './api/clientError'
app.config.errorHandler = (err, _instance, info) => {
  reportError(err, `Vue error: ${info}`)
}
// 浏览器未捕获 Promise rejection
window.addEventListener('unhandledrejection', (event) => {
  reportError(event.reason, 'Unhandled Promise rejection')
})

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
