import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

// 创建一个 axios 实例,统一拦截 401
const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

http.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('archive-token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

http.interceptors.response.use(
  (response) => {
    const data = response.data
    // 业务码非 0 视为错误
    if (data && typeof data === 'object' && 'code' in data && data.code !== 0) {
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      ElMessage.error('登录已过期,请重新登录')
      localStorage.removeItem('archive-token')
      // 跳登录
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    } else if (error.response?.status === 403) {
      ElMessage.error('权限不足')
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export function getData<T>(response: { data: ApiResponse<T> }): T {
  return response.data.data
}

export default http

// 导出可配置的请求方法(其他 api 模块用)
export function request<T = any>(config: AxiosRequestConfig): Promise<T> {
  return http.request<ApiResponse<T>>(config).then((r) => r.data.data)
}
