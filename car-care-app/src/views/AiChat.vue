<template>
  <div class="ai-page">
    <van-nav-bar title="AI 养车顾问" left-arrow @click-left="$router.back()">
      <template #right>
        <span class="nav-btn" @click="newSession">新对话</span>
        <van-icon name="bars" size="18" style="margin-left:14px" @click="openHistory" />
      </template>
    </van-nav-bar>

    <div ref="listEl" class="msg-list">
      <!-- 空态：给出示例问题，降低首次使用的门槛 -->
      <div v-if="!messages.length" class="intro">
        <div class="intro-title">你好，我是车管家的 AI 养车顾问</div>
        <div class="intro-sub">可以帮你查门店与价格、看订单和工单进度、判断保养时机、解答养车问题</div>
        <div class="chips">
          <span v-for="q in samples" :key="q" class="chip" @click="quickAsk(q)">{{ q }}</span>
        </div>
      </div>

      <div v-for="(m, i) in messages" :key="i" class="row" :class="m.role">
        <!-- 工具调用过程：让用户看到「它在干什么」，而不是干等 -->
        <div v-if="m.tools && m.tools.length" class="tools">
          <div v-for="(t, ti) in m.tools" :key="ti" class="tool" :class="{ fail: t.ok === false }">
            <van-loading v-if="t.ok === null" size="12" />
            <span v-else class="tool-mark">{{ t.ok ? '✓' : '!' }}</span>
            <span class="tool-text">{{ t.ok === null ? t.label : (t.summary || t.label) }}</span>
          </div>
        </div>

        <div v-if="m.content" class="bubble" :class="m.role">
          <span v-html="render(m.content)"></span><span v-if="m.streaming" class="caret">▍</span>
        </div>
        <div v-else-if="m.streaming" class="bubble assistant thinking">
          <van-loading size="14" /> <span style="margin-left:6px">正在思考…</span>
        </div>

        <!-- 预约草稿卡片：AI 只准备草稿，真正下单由用户点确认 -->
        <div v-if="m.draft" class="draft-card">
          <div class="draft-head">预约草稿</div>
          <div class="draft-row"><span>门店</span><b>{{ m.draft.storeName }}</b></div>
          <div class="draft-row"><span>项目</span><b>{{ (m.draft.itemNames || []).join('、') }}</b></div>
          <div class="draft-row" v-if="m.draft.appointmentTime"><span>到店时间</span><b>{{ m.draft.appointmentTime }}</b></div>
          <div class="draft-row"><span>预估金额</span><b class="price">¥{{ Number(m.draft.estimatedAmount || 0).toFixed(2) }}</b></div>
          <div v-if="m.draft.note" class="draft-note">{{ m.draft.note }}</div>
          <div v-if="(m.draft.itemIds || []).length > 1" class="draft-note">
            平台一张订单只包含一个项目，这里会先为你下第一项，其余项目请分别下单
          </div>
          <van-button block round size="small" color="#0d9488" @click="goBooking(m.draft)">确认预约</van-button>
        </div>

        <div v-if="m.role === 'assistant' && !m.streaming && m.messageId" class="actions">
          <span class="act" :class="{ on: m.feedback === 1 }" @click="rate(m, 1)">👍</span>
          <span class="act" :class="{ on: m.feedback === 2 }" @click="rate(m, 2)">👎</span>
          <span v-if="m.latencyMs" class="meta">{{ (m.latencyMs / 1000).toFixed(1) }}s · {{ m.outputTokens || 0 }} tokens</span>
        </div>
      </div>
    </div>

    <div class="input-bar">
      <van-field v-model="text" rows="1" autosize type="textarea" placeholder="问问你的车…"
                 :disabled="streaming" @keyup.enter.exact.prevent="send" />
      <van-button v-if="streaming" round size="small" color="#94a3b8" @click="stop">停止</van-button>
      <van-button v-else round size="small" color="#0d9488" :disabled="!text.trim()" @click="send">发送</van-button>
    </div>

    <!-- 历史会话抽屉 -->
    <van-popup v-model:show="showHistory" position="left" :style="{ width: '78%', height: '100%' }">
      <div class="history">
        <div class="history-head">历史对话</div>
        <van-empty v-if="!conversations.length" description="还没有历史对话" image-size="60" />
        <div v-for="c in conversations" :key="c.sessionId" class="history-item" @click="loadSession(c)">
          <div class="history-title">{{ c.title || '新对话' }}</div>
          <div class="history-meta">{{ c.messageCount }} 条 · {{ fmtTime(c.lastMessageAt) }}</div>
          <van-icon name="delete-o" class="history-del" @click.stop="removeSession(c)" />
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import {
  aiChatStream, aiConversationsApi, aiDeleteConversationApi,
  aiFeedbackApi, aiMessagesApi
} from '../api/ai'

const router = useRouter()
const listEl = ref(null)
const text = ref('')
const messages = ref([])
const sessionId = ref(null)
const streaming = ref(false)
const showHistory = ref(false)
const conversations = ref([])
let controller = null

const samples = ['我的车该保养了吗？', '小保养多少钱？', '刹车片什么时候该换？', '我的订单修到哪一步了？']

/** 极简 markdown 渲染：先转义 HTML 再套格式，避免模型输出里的标签被当 HTML 执行 */
function render(raw) {
  return String(raw || '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/^[-*]\s+/gm, '• ')
    .replace(/^#{1,6}\s+(.+)$/gm, '<b>$1</b>')
}

function fmtTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(5, 16)
}

async function scrollDown() {
  await nextTick()
  if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
}

function quickAsk(q) {
  text.value = q
  send()
}

function newSession() {
  sessionId.value = null
  messages.value = []
}

function openHistory() {
  showHistory.value = true
  loadConversations()
}

async function loadConversations() {
  const res = await aiConversationsApi({ pageNum: 1, pageSize: 30 })
  conversations.value = res.data.records || []
}

/** 打开历史会话：把服务端存的消息（含工具轨迹）还原成界面结构 */
async function loadSession(c) {
  showHistory.value = false
  const res = await aiMessagesApi(c.sessionId)
  sessionId.value = c.sessionId
  messages.value = (res.data || []).map((m) => ({
    role: m.role,
    content: m.content,
    streaming: false,
    messageId: m.id,
    feedback: m.feedback,
    latencyMs: m.latencyMs,
    outputTokens: m.outputTokens,
    tools: parseTools(m.toolTrace),
    draft: parseDraft(m.toolTrace)
  }))
  scrollDown()
}

function parseTools(trace) {
  if (!trace) return []
  try {
    return JSON.parse(trace)
      .filter((t) => t.kind === 'tool')
      .map((t) => ({ name: t.name, label: t.label, ok: t.ok, summary: t.summary }))
  } catch { return [] }
}

function parseDraft(trace) {
  if (!trace) return null
  try {
    return JSON.parse(trace).find((t) => t.kind === 'draft')?.draft || null
  } catch { return null }
}

async function removeSession(c) {
  await showConfirmDialog({ title: '删除对话', message: '删除后不可恢复，确定吗？' })
  await aiDeleteConversationApi(c.sessionId)
  if (sessionId.value === c.sessionId) newSession()
  loadConversations()
  showToast('已删除')
}

function goBooking(draft) {
  const query = { storeId: draft.storeId }
  if (draft.packageId) query.packageId = draft.packageId
  else if (draft.itemIds?.length) query.itemId = draft.itemIds[0]
  router.push({ path: '/order-create', query })
}

async function rate(m, value) {
  if (!m.messageId) return
  const next = m.feedback === value ? null : value
  if (next === null) return showToast('已取消评价')
  await aiFeedbackApi(m.messageId, next)
  m.feedback = next
  showToast(next === 1 ? '感谢反馈' : '已记录，我们会改进')
}

function stop() {
  controller?.abort()
}

async function send() {
  const question = text.value.trim()
  if (!question || streaming.value) return
  text.value = ''
  messages.value.push({ role: 'user', content: question })
  const reply = {
    role: 'assistant', content: '', streaming: true,
    tools: [], draft: null, messageId: null, feedback: null
  }
  messages.value.push(reply)
  const current = messages.value[messages.value.length - 1]
  streaming.value = true
  controller = new AbortController()
  scrollDown()

  try {
    await aiChatStream({
      message: question,
      sessionId: sessionId.value,
      signal: controller.signal,
      onEvent(event) {
        switch (event.type) {
          case 'start':
            sessionId.value = event.sessionId
            break
          case 'delta':
            current.content += event.content || ''
            scrollDown()
            break
          case 'reset':
            // 模型在调工具前说的过渡语，作废
            current.content = ''
            break
          case 'tool_start':
            current.tools.push({ name: event.toolName, label: event.toolLabel, ok: null, summary: '' })
            scrollDown()
            break
          case 'tool_end': {
            // 工具名可能重复（比如连查两次门店），从后往前找第一个还没结束的
            const slot = [...current.tools].reverse().find((t) => t.ok === null && t.name === event.toolName)
            if (slot) {
              slot.ok = event.toolOk
              slot.summary = event.toolSummary
            }
            scrollDown()
            break
          }
          case 'draft':
            current.draft = event.draft
            scrollDown()
            break
          case 'done':
            if (event.content) current.content = event.content
            current.latencyMs = event.latencyMs
            current.outputTokens = event.outputTokens
            break
          case 'error':
            current.content = current.content || ''
            current.error = event.message
            messages.value.push({ role: 'assistant', content: `⚠️ ${event.message}`, streaming: false })
            break
          default:
            break
        }
      }
    })
  } catch (e) {
    if (e.name === 'AbortError') {
      current.content += '\n\n（已停止生成）'
    } else {
      messages.value.push({ role: 'assistant', content: `⚠️ ${e.message || 'AI 助手暂时不可用'}`, streaming: false })
    }
  } finally {
    current.streaming = false
    streaming.value = false
    controller = null
    scrollDown()
    // 落库发生在服务端流结束之后，稍等一下再取 messageId，否则可能取到上一条
    if (sessionId.value) {
      setTimeout(() => attachMessageId(current), 400)
    }
  }
}

/** 从服务端拉一次历史，把刚落库的那条 assistant 消息 id 绑到本地对象上（用于点赞/点踩） */
async function attachMessageId(target) {
  if (target.messageId) return
  try {
    const res = await aiMessagesApi(sessionId.value)
    const last = [...(res.data || [])].reverse().find((m) => m.role === 'assistant')
    if (last) target.messageId = last.id
  } catch { /* 拿不到 id 只是不能评价，不影响阅读 */ }
}

onMounted(async () => {
  try {
    const { data } = await (await import('../api/ai')).aiHealthApi()
    if (!data.available) {
      showToast(data.detail || 'AI 助手暂时不可用')
    }
  } catch { /* 健康检查失败不阻塞页面 */ }
})
</script>

<style scoped>
.ai-page { display: flex; flex-direction: column; height: 100vh; background: #f5f7f8; }
/* 导航栏是青色底，文字必须用白色，否则青字压青底等于看不见 */
.nav-btn { font-size: 13px; color: #fff; }
.msg-list { flex: 1; overflow-y: auto; padding: 12px; }
.intro { padding: 28px 6px; }
.intro-title { font-size: 17px; font-weight: 600; color: #0f172a; }
.intro-sub { font-size: 13px; color: #64748b; margin-top: 8px; line-height: 1.6; }
.chips { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 18px; }
.chip { font-size: 13px; color: #0d9488; background: #e6fffb; border: 1px solid #b7ece5; border-radius: 16px; padding: 6px 12px; }

.row { display: flex; flex-direction: column; margin-bottom: 14px; }
.row.user { align-items: flex-end; }
.row.assistant { align-items: flex-start; }

.tools { margin-bottom: 6px; max-width: 88%; }
.tool { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #0d9488; background: #eefcf9; border-radius: 8px; padding: 5px 9px; margin-bottom: 4px; }
.tool.fail { color: #b45309; background: #fff7ed; }
.tool-mark { font-weight: 700; }
.tool-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.bubble { max-width: 88%; padding: 10px 13px; border-radius: 12px; font-size: 14.5px; line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
.bubble.user { background: #0d9488; color: #fff; border-bottom-right-radius: 4px; }
.bubble.assistant { background: #fff; color: #0f172a; border-bottom-left-radius: 4px; box-shadow: 0 1px 3px rgba(15, 23, 42, .06); }
.bubble.thinking { display: flex; align-items: center; color: #94a3b8; }
.bubble :deep(strong) { font-weight: 600; }
.bubble :deep(code) { background: #f1f5f9; padding: 1px 4px; border-radius: 4px; font-size: 13px; }
.caret { animation: blink 1s steps(2) infinite; color: #0d9488; }
@keyframes blink { 50% { opacity: 0; } }

.draft-card { max-width: 88%; background: #fff; border: 1px solid #99f6e4; border-radius: 12px; padding: 12px; margin-top: 8px; }
.draft-head { font-size: 13px; font-weight: 600; color: #0d9488; margin-bottom: 8px; }
.draft-row { display: flex; justify-content: space-between; gap: 12px; font-size: 13px; color: #475569; padding: 3px 0; }
.draft-row b { color: #0f172a; text-align: right; }
.draft-row .price { color: #e11d48; }
.draft-note { font-size: 12px; color: #92400e; background: #fffbeb; border-radius: 6px; padding: 6px 8px; margin: 8px 0; line-height: 1.5; }
.draft-card :deep(.van-button) { margin-top: 10px; }

.actions { display: flex; align-items: center; gap: 10px; margin-top: 6px; font-size: 12px; color: #94a3b8; }
.act { cursor: pointer; font-size: 14px; opacity: .45; }
.act.on { opacity: 1; }

.input-bar { display: flex; align-items: flex-end; gap: 8px; padding: 8px 10px; background: #fff; border-top: 1px solid #e2e8f0; }
.input-bar :deep(.van-field) { flex: 1; background: #f5f7f8; border-radius: 10px; padding: 6px 10px; }
.input-bar :deep(.van-button) { flex: none; }

.history { height: 100%; overflow-y: auto; padding: 16px 12px; }
.history-head { font-size: 15px; font-weight: 600; margin-bottom: 12px; color: #0f172a; }
.history-item { position: relative; padding: 10px 30px 10px 10px; border-radius: 8px; }
.history-item:active { background: #f1f5f9; }
.history-title { font-size: 14px; color: #0f172a; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.history-meta { font-size: 12px; color: #94a3b8; margin-top: 3px; }
.history-del { position: absolute; right: 8px; top: 50%; transform: translateY(-50%); color: #cbd5e1; font-size: 16px; }
</style>
