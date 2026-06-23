<template>
  <div class="chat-layout">
    <!-- 左侧：导航 + 会话列表 -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <el-button type="primary" class="new-session-btn" @click="newSession">+ 新对话</el-button>
      </div>
      <div class="session-list">
        <div v-for="s in sessions" :key="s.sessionId"
             :class="['session-item', { active: currentSessionId === s.sessionId }]"
             @click="switchSession(s.sessionId)">
          <span class="session-title">{{ s.title || '新对话' }}</span>
          <el-icon class="session-del" @click.stop="deleteSession(s.sessionId)"><Close /></el-icon>
        </div>
        <div v-if="!sessions.length" class="session-empty">暂无会话</div>
      </div>
    </aside>

    <!-- 中间：对话区域 -->
    <main class="chat-main">
      <div class="message-list" ref="msgList">
        <div v-if="messages.length === 0" class="empty-hint">
          <div class="empty-logo">◆</div>
          <h2 class="empty-title">AgentForge 智能对话</h2>
          <p class="empty-sub">向 AgentForge 提问，自动生成 SQL 并执行</p>
          <div class="empty-examples">
            <button class="example-chip" @click="useExample('查询总交易额')">📊 查询总交易额</button>
            <button class="example-chip" @click="useExample('各城市商户数排名')">🏙️ 各城市商户数排名</button>
            <button class="example-chip" @click="useExample('本月环比增长情况')">📈 本月环比增长情况</button>
          </div>
        </div>
        <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
          <div class="message-bubble">
            <div v-if="msg.role === 'user'" class="msg-text">{{ msg.content }}</div>
            <div v-else class="msg-text" v-html="renderMarkdown(msg.content)"></div>
            <!-- 内嵌图表/表格 -->
            <!-- TODO(后端): ReportConfig 需新增 sampleRows 字段，并在 FormatterAgent /
                 ReportBuilderService.doBuild 中把查询结果写入该字段，否则下方表格/图表无数据可渲染。 -->
            <div v-if="msg.report" class="msg-report">
              <div class="report-card">
                <div class="report-header">
                  <span>{{ msg.report.name }}</span>
                  <el-tag size="small">{{ msg.report.chartConfig?.type || 'table' }}</el-tag>
                </div>
                <ChartRenderer v-if="msg.report.chartConfig?.type !== 'number_card'"
                               :chart-config="msg.report.chartConfig"
                               :rows="msg.report.sampleRows" />
                <NumberCards v-else-if="msg.report.chartConfig?.numberCards"
                             :cards="msg.report.chartConfig.numberCards"
                             :data="msg.report.sampleRows?.[0] || {}" />
                <DataTable v-if="msg.report.sampleRows?.length"
                           :columns="msg.report.columnConfig"
                           :rows="msg.report.sampleRows" />
                <div class="report-sql">
                  <el-text type="info" size="small">SQL: {{ msg.report.sqlText }}</el-text>
                </div>
              </div>
            </div>
            <!-- 审查提示（ReviewAgent 推送的 review 事件） -->
            <el-alert v-if="msg.reviewNote"
                      class="msg-review"
                      type="warning"
                      :title="'数据审查提示'"
                      :description="msg.reviewNote"
                      :closable="false" show-icon />
          </div>
        </div>
        <div v-if="loading" class="message assistant">
          <div class="message-bubble"><el-icon class="is-loading"><Loading /></el-icon> 思考中...</div>
        </div>
      </div>

      <div class="input-bar">
        <div class="chat-input-wrap">
          <input v-model="inputText" class="chat-input"
                 placeholder="输入问题，按 Enter 发送"
                 @keydown.enter="sendMessage" :disabled="loading" />
          <button class="chat-send" @click="sendMessage" :disabled="loading" aria-label="发送">
            <el-icon><Promotion /></el-icon>
          </button>
        </div>
      </div>
    </main>
  </div>
</template>

<script>
export default { name: 'ChatView' }
</script>

<script setup>
import { ref, nextTick, onMounted, onActivated, onDeactivated } from 'vue'
import { Close, Promotion, Loading } from '@element-plus/icons-vue'
import { marked } from 'marked'
import ChartRenderer from '@/components/report/ChartRenderer.vue'
import NumberCards from '@/components/report/NumberCards.vue'
import DataTable from '@/components/report/DataTable.vue'
import request from '@/utils/request'

const sessions = ref([])
const currentSessionId = ref('')
const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const msgList = ref(null)

onMounted(async () => {
  await loadSessions()
  if (sessions.value.length === 0) {
    newSession()
  } else {
    // 恢复上次查看的会话（localStorage 持久化，切页面回来不掉）
    const last = localStorage.getItem('agentforge.currentSession')
    const exists = last && sessions.value.some(s => s.sessionId === last)
    switchSession(exists ? last : sessions.value[0].sessionId)
  }
})

async function loadSessions() {
  try {
    const data = await request.get('/session/list', { params: { userId: 1 } })
    sessions.value = data || []
  } catch { sessions.value = [] }
}

async function newSession() {
  try {
    const data = await request.post('/session/create', null, {
      params: { userId: 1, title: '新对话' }
    })
    sessions.value.unshift(data)
    switchSession(data.sessionId)
  } catch (e) {
    // fallback: use local session id
    const sid = 'local-' + Date.now()
    sessions.value.unshift({ sessionId: sid, title: '新对话' })
    switchSession(sid)
  }
}

async function switchSession(sid) {
  currentSessionId.value = sid
  localStorage.setItem('agentforge.currentSession', sid)
  messages.value = []
  // 从后端加载历史消息（af_message 持久化，切页面/刷新/重启都不丢）
  await loadHistory(sid)
}

/** 加载会话历史：GET /session/{id} 返回 {messages:[{role,content,...}]} */
async function loadHistory(sid) {
  try {
    const data = await request.get(`/session/${sid}`)
    const msgs = data?.messages || []
    messages.value = msgs
      .filter(m => m.role === 'user' || m.role === 'assistant')
      .map(m => ({ role: m.role, content: m.content || '' }))
    await scrollBottom()
  } catch (e) {
    // 会话无历史或查询失败，保留空消息列表
  }
}

async function deleteSession(sid) {
  try { await request.delete(`/session/${sid}`) } catch {}
  sessions.value = sessions.value.filter(s => s.sessionId !== sid)
  if (currentSessionId.value === sid && sessions.value.length) {
    switchSession(sessions.value[0].sessionId)
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || loading.value) return
  inputText.value = ''

  messages.value.push({ role: 'user', content: text })
  loading.value = true
  await scrollBottom()

  try {
    // Use SSE endpoint
    const res = await fetch(
      `/api/chat/stream?sessionId=${encodeURIComponent(currentSessionId.value)}&question=${encodeURIComponent(text)}`
    )
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let assistantMsg = { role: 'assistant', content: '', report: null, reviewNote: '' }
    messages.value.push(assistantMsg)
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // SSE 以空行(\n\n)分隔事件块；一个事件内可能含多个 data: 行（JSON 带换行时）
      let sep
      while ((sep = buffer.indexOf('\n\n')) !== -1) {
        const rawEvent = buffer.slice(0, sep)
        buffer = buffer.slice(sep + 2)

        // 合并事件块内所有 data: 行的内容，忽略注释行(如心跳 :hb)
        const dataLines = []
        for (const line of rawEvent.split('\n')) {
          if (line.startsWith(':')) continue          // SSE 注释 / 心跳
          if (line.startsWith('data:')) {
            // data: 紧跟内容（Spring SseEmitter 不带空格）；兼容 'data: ' 带空格写法
            dataLines.push(line.slice(5).replace(/^\s/, ''))
          }
        }
        if (dataLines.length === 0) continue
        const payload = dataLines.join('\n')

        let evt
        try {
          evt = JSON.parse(payload)
        } catch {
          continue   // 非法 JSON 静默跳过，避免拖垮整条流
        }

        if (evt.type === 'status') {
          assistantMsg.content = evt.message
        } else if (evt.type === 'sql_result') {
          assistantMsg.content = `**SQL 生成完成**\n\n级别: ${evt.level} | 置信度: ${evt.confidence}\n\n\`\`\`sql\n${evt.sql}\n\`\`\`\n\n${evt.explanation}`
        } else if (evt.type === 'report') {
          assistantMsg.report = evt.data
        } else if (evt.type === 'review') {
          // ReviewAgent 的审查意见（空结果/异常大额/结果集过大等），追加展示不覆盖正文
          assistantMsg.reviewNote = evt.note
        } else if (evt.type === 'done') {
          if (!assistantMsg.content && !assistantMsg.report) {
            assistantMsg.content = '处理完成'
          }
        } else if (evt.type === 'error') {
          assistantMsg.content = `错误: ${evt.message}`
        }
      }
      await scrollBottom()
    }
  } catch (e) {
    messages.value.push({ role: 'assistant', content: `请求失败: ${e.message}` })
  } finally {
    loading.value = false
    await scrollBottom()
  }
}

function useExample(text) {
  inputText.value = text
}

function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(text)
}

async function scrollBottom() {
  await nextTick()
  if (msgList.value) msgList.value.scrollTop = msgList.value.scrollHeight
}
</script>

<style scoped>
.chat-layout { display: flex; height: calc(100vh - var(--af-header-h)); position: relative; }

/* ============ 左侧会话栏 ============ */
.sidebar {
  width: var(--af-sidebar-w);
  background: var(--af-sidebar-bg, #fff);
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--af-border-light);
  flex-shrink: 0;
}
.sidebar-header { padding: var(--af-sp-4); }
.new-session-btn { width: 100%; }
.session-list { flex: 1; overflow-y: auto; padding: var(--af-sp-2); }
.session-item {
  padding: 11px var(--af-sp-3);
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--af-fs-sm);
  color: var(--af-text-2);
  border-radius: var(--af-radius-sm);
  margin-bottom: 2px;
  transition: all var(--af-transition);
}
.session-item:hover { background: var(--af-bg-soft); color: var(--af-text-1); }
.session-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--af-primary);
  font-weight: 600;
}
.session-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-del { opacity: 0; color: var(--af-text-3); transition: all var(--af-transition); }
.session-item:hover .session-del { opacity: 1; }
.session-del:hover { color: var(--af-danger); }
.session-empty { text-align: center; color: var(--af-text-3); font-size: var(--af-fs-xs); padding: var(--af-sp-5) 0; }

/* ============ 中间对话区 ============ */
.chat-main { flex: 1; display: flex; flex-direction: column; background: var(--af-bg-page); min-width: 0; }
.message-list { flex: 1; overflow-y: auto; padding: var(--af-sp-6) var(--af-sp-5); }

/* 欢迎空状态 */
.empty-hint { max-width: 640px; margin: 0 auto; padding-top: 8vh; text-align: center; }
.empty-logo {
  width: 64px; height: 64px; margin: 0 auto var(--af-sp-4);
  border-radius: var(--af-radius-lg);
  background: var(--af-brand-grad);
  display: flex; align-items: center; justify-content: center;
  font-size: 28px; color: #fff;
  box-shadow: var(--af-shadow-primary);
}
.empty-title { font-size: var(--af-fs-xl); font-weight: 700; color: var(--af-text-1); margin-bottom: var(--af-sp-2); }
.empty-sub { font-size: var(--af-fs-sm); color: var(--af-text-3); margin-bottom: var(--af-sp-6); }
.empty-examples { display: flex; flex-wrap: wrap; gap: var(--af-sp-2); justify-content: center; }
.example-chip {
  padding: 8px var(--af-sp-3);
  background: var(--af-bg-card);
  border: 1px solid var(--af-border);
  border-radius: 100px;
  font-size: var(--af-fs-xs);
  color: var(--af-text-2);
  cursor: pointer;
  transition: all var(--af-transition);
}
.example-chip:hover { border-color: var(--el-color-primary-light-5); color: var(--af-primary); background: var(--af-bg-hover); transform: translateY(-1px); }

/* 消息气泡 */
.message { display: flex; margin-bottom: var(--af-sp-4); }
.message.user { justify-content: flex-end; }
.message.assistant { justify-content: flex-start; }
.message-bubble {
  max-width: 75%;
  padding: var(--af-sp-3) var(--af-sp-4);
  border-radius: var(--af-radius-md);
  font-size: var(--af-fs-sm);
  line-height: 1.7;
}
.msg-text :deep(code) { background: var(--af-bg-soft); padding: 2px 6px; border-radius: 4px; font-family: var(--af-font-mono); font-size: 13px; }
.msg-text :deep(pre) { background: var(--af-bg-soft); padding: var(--af-sp-3); border-radius: var(--af-radius-sm); overflow-x: auto; margin: var(--af-sp-2) 0; }
.msg-text :deep(pre code) { background: none; padding: 0; }
/* 用户气泡：主色渐变 */
.message.user .message-bubble {
  background-image: var(--af-primary-grad);
  color: #fff;
  box-shadow: 0 4px 14px rgba(22, 119, 255, 0.28);
  border-bottom-right-radius: 4px;
}
/* AI 气泡：白卡 */
.message.assistant .message-bubble {
  background: var(--af-bg-card);
  color: var(--af-text-1);
  border: 1px solid var(--af-border-light);
  box-shadow: var(--af-shadow-sm);
  border-bottom-left-radius: 4px;
}
.msg-report { margin-top: var(--af-sp-4); }
.msg-review { margin-top: var(--af-sp-3); }
.report-card {
  background: var(--af-bg-card);
  border: 1px solid var(--af-border-light);
  border-radius: var(--af-radius-lg);
  padding: var(--af-sp-4);
  box-shadow: var(--af-shadow-sm);
  transition: box-shadow var(--af-transition);
}
.report-card:hover { box-shadow: var(--af-shadow-md); }
.report-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: var(--af-sp-3); padding-bottom: var(--af-sp-2);
  border-bottom: 1px solid var(--af-border-light);
  font-size: var(--af-fs-md); font-weight: 600; color: var(--af-text-1);
}
.report-sql { margin-top: var(--af-sp-3); padding-top: var(--af-sp-2); border-top: 1px dashed var(--af-border); font-family: var(--af-font-mono); }

/* 输入栏：统一胶囊容器，输入框与发送按钮等高对齐，聚焦态尺寸不变 */
.input-bar {
  padding: var(--af-sp-4) var(--af-sp-5);
  background: var(--af-bg-card);
  border-top: 1px solid var(--af-border-light);
}
.chat-input-wrap {
  display: flex;
  align-items: center;
  gap: var(--af-sp-2);
  max-width: 800px;
  margin: 0 auto;
  background: var(--af-bg-card);
  border: 1px solid var(--af-border);
  border-radius: 100px;
  padding: 6px 6px 6px 22px;
  box-shadow: var(--af-shadow-sm);
  transition: border-color var(--af-transition), box-shadow var(--af-transition);
}
.chat-input-wrap:focus-within {
  border-color: var(--af-primary);
  box-shadow: var(--af-shadow-focus);
}
.chat-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--af-fs-sm);
  color: var(--af-text-1);
  height: 36px;
  font-family: var(--af-font-sans);
}
.chat-input::placeholder { color: var(--af-text-3); }
.chat-send {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  background-image: var(--af-primary-grad);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.3);
  transition: all var(--af-transition);
}
.chat-send:hover:not(:disabled) {
  box-shadow: var(--af-shadow-primary);
  transform: scale(1.05);
}
.chat-send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

@media (max-width: 768px) {
  .sidebar { display: none; }
}
</style>
