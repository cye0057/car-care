import http from './http'

/**
 * AI 助手接口层。
 *
 * 流式对话单独放在这里而不是复用 http.js（axios），有两个原因：
 * 1) axios 的 XHR 适配器拿不到增量响应，必须用 fetch + ReadableStream 才能边收边渲染；
 * 2) EventSource 用不了——鉴权走的是自定义请求头 token，而 EventSource 不支持自定义头。
 *
 * 其余接口仍是普通请求，继续走 axios，保持与项目其它模块一致的错误处理。
 */

/* 非流式接口：会话列表、历史消息、删除、反馈 */
export const aiHealthApi = () => http.get('/ai/health')
export const aiConversationsApi = (params) => http.get('/ai/conversations', { params })
export const aiMessagesApi = (sessionId) => http.get(`/ai/conversations/${sessionId}/messages`)
export const aiDeleteConversationApi = (sessionId) => http.delete(`/ai/conversations/${sessionId}`)
export const aiFeedbackApi = (messageId, feedback) => http.post(`/ai/messages/${messageId}/feedback`, { feedback })

/**
 * 流式对话。每收到一帧就回调 onEvent(event)，event 结构见后端 AiChatEventVO。
 *
 * @param {object}   options
 * @param {string}   options.message    用户问题
 * @param {string?}  options.sessionId  会话 id，不传则新建
 * @param {Function} options.onEvent    事件回调
 * @param {AbortSignal?} options.signal 用于「停止生成」
 */
export async function aiChatStream({ message, sessionId, onEvent, signal }) {
  const token = localStorage.getItem('app_token')
  const resp = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', token: token || '' },
    body: JSON.stringify({ message, sessionId: sessionId || undefined }),
    signal
  })

  if (resp.status === 401) {
    localStorage.removeItem('app_token')
    throw new Error('登录已过期，请重新登录')
  }
  if (!resp.ok || !resp.body) {
    throw new Error(`AI 助手请求失败（${resp.status}）`)
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    // 服务端已声明 charset=UTF-8，这里流式解码，避免中文在多字节边界被截断
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')

    // SSE 以空行分隔事件；一次 read 可能拿到半帧，也可能拿到多帧，所以要循环切分
    let sep
    while ((sep = buffer.indexOf('\n\n')) !== -1) {
      const frame = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)
      for (const line of frame.split('\n')) {
        if (!line.startsWith('data:')) continue // 忽略注释行（sse-starlette 的心跳 ping）
        const payload = line.slice(5).trim()
        if (!payload) continue
        try {
          onEvent(JSON.parse(payload))
        } catch (e) {
          console.warn('[ai] 无法解析的事件帧', payload, e)
        }
      }
    }
  }
}
