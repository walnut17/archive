import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
import './assets/main.css'

const app = createApp(App)

// 全局错误处理器: 避免单个 Vue 组件未捕获异常拖死整站 RouterView (T-0611-16/17)
app.config.errorHandler = (err, _instance, info) => {
  console.warn('[GlobalErrorHandler] Caught:', err, info)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
