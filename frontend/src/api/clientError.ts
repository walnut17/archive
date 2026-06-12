/**
 * 客户端错误上报 SDK.
 * 由全局 errorHandler 调用，上报到后端 /api/client-error.
 */
import { ElMessage } from 'element-plus'

interface ClientErrorPayload {
  message: string
  stack: string
  url: string
  userId?: string
  timestamp: string
}

/**
 * 上报客户端异常到后端（异步，不阻塞主流程）.
 * 同时显示 toast 提示用户.
 */
export function reportError(error: unknown, context?: string): void {
  // 1. 控制台输出完整堆栈
  console.error('[ClientError]', error, context || '')

  // 2. Toast 提示用户
  ElMessage.error('操作失败，请刷新或联系运维')

  // 3. 异步上报到后端
  const token = localStorage.getItem('archive-token') ?? ''
  const payload: ClientErrorPayload = {
    message: error instanceof Error ? error.message : String(error),
    stack: error instanceof Error ? (error.stack ?? '') : '',
    url: window.location.href,
    userId: token ? token.substring(0, 20) : undefined,
    timestamp: new Date().toISOString(),
  }

  // 静默上报，不阻塞用户操作
  fetch('/api/client-error', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }).catch(() => {
    // 上报失败不处理 —— 避免递归 errorHandler
  })
}
