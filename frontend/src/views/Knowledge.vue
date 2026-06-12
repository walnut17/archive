<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http, { getData } from '../api/http'
import ChatMessage from '../components/ChatMessage.vue'
import ChatInput from '../components/ChatInput.vue'

interface Source {
  versionId: number
  materialId: number
  materialTitle: string
  versionNo: number
  originalFilename: string
  projectCode: string | null
  projectName: string | null
  proposalCode: string
  proposalTitle: string
  snippet: string
  score: number
}

interface AgentStep {
  iteration: number
  thought: string
  tool: string
  toolArgs: string
  observation: string
}

interface QaResponse {
  question: string
  answer: string | null
  sources: Source[] | null
  reranked: boolean
  elapsedMs: number
  agentMode?: boolean | null
  steps?: AgentStep[] | null
  toolCalls?: number | null
  projectSwitchHint?: string | null
  confidenceBadge?: string | null
}

interface ChatEntry {
  role: 'user' | 'assistant'
  content: string
  loading?: boolean
  steps?: AgentStep[] | null
  sources?: Source[] | null
  confidenceBadge?: string | null
  agentSources?: any[] | null
}

const sessionId = ref(crypto.randomUUID())
const messages = ref<ChatEntry[]>([])
const loading = ref(false)
const turnCount = ref(0)

const exampleQuestions = [
  '新能源那个项目今年盈利怎么样?',
  'PRJ-2026-001 剩余金额是多少?',
  '今年否决了哪些项目?',
  '今天有哪些待办事项?',
]

function safeSources(src: Source[] | null | undefined): Source[] {
  return src ?? []
}

interface StreamEvent {
  event: 'step' | 'token' | 'source' | 'done' | 'error'
  data: Record<string, any>
}

interface AgentSource {
  type: 'PROJECT' | 'MATERIAL' | 'TODO' | 'TERM'
  id: string
  title: string
  snippet?: string
}

async function onAsk(question: string) {
  // 添加用户消息
  messages.value.push({ role: 'user', content: question })
  // 添加占位 assistant (loading 状态, content 渐追加)
  const idx = messages.value.length
  messages.value.push({
    role: 'assistant',
    content: '',
    loading: true,
    steps: [],
    sources: null,
    agentSources: null,
    confidenceBadge: null,
  })

  loading.value = true
  try {
    // v1.2: 流式 SSE 消费
    const path = turnCount.value === 0
      ? '/qa/ask/stream'
      : `/qa/turn/${sessionId.value}/stream`
    const body = turnCount.value === 0
      ? { question, topN: 10, rerank: true, agentMode: true }
      : { question }

    const resp = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })

    if (!resp.ok || !resp.body) {
      throw new Error(`HTTP ${resp.status}`)
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let accAnswer = ''
    const accSteps: AgentStep[] = []
    const accAgentSources: AgentSource[] = []
    let accBadge: string | null = null
    const seenSourceKeys = new Set<string>()

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // 按 \n\n 切 SSE 事件
      let idx2: number
      while ((idx2 = buffer.indexOf('\n\n')) !== -1) {
        const ev = buffer.substring(0, idx2)
        buffer = buffer.substring(idx2 + 2)
        if (!ev.trim()) continue
        const eventName = ev.match(/^event: (\S+)/m)?.[1]
        const dataLine = ev.match(/^data: (.+)$/m)?.[1]
        if (!eventName || !dataLine) continue
        let data: any
        try { data = JSON.parse(dataLine) } catch { continue }

        if (eventName === 'token') {
          accAnswer += data.delta || ''
          // 实时更新最后一条消息
          messages.value[idx] = {
            ...messages.value[idx],
            content: accAnswer,
            loading: false,
          }
        } else if (eventName === 'step') {
          accSteps.push({
            iteration: data.iteration,
            thought: data.thought || '',
            tool: data.tool || '',
            toolArgs: data.toolArgs || '',
            observation: data.observation || '',
          })
          messages.value[idx] = {
            ...messages.value[idx],
            steps: [...accSteps],
            loading: false,
          }
        } else if (eventName === 'source') {
          const key = `${data.type}-${data.id}`
          if (!seenSourceKeys.has(key)) {
            seenSourceKeys.add(key)
            accAgentSources.push(data as AgentSource)
            messages.value[idx] = {
              ...messages.value[idx],
              agentSources: [...accAgentSources],
              loading: false,
            }
          }
        } else if (eventName === 'done') {
          accBadge = data.confidence_badge ?? null
          messages.value[idx] = {
            role: 'assistant',
            content: data.answer ?? accAnswer,
            steps: data.steps ?? accSteps,
            sources: safeSources(data.sources) as Source[],
            confidenceBadge: accBadge,
            agentSources: data.agent_sources ?? accAgentSources,
            loading: false,
          }
        } else if (eventName === 'error') {
          accAnswer = '问答失败: ' + (data.message || '请稍后重试')
          messages.value[idx] = { ...messages.value[idx], content: accAnswer, loading: false }
        }
      }
    }

    turnCount.value++
  } catch (e: any) {
    messages.value[idx] = {
      role: 'assistant',
      content: '问答失败: ' + (e.message || '请稍后重试'),
      loading: false,
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="knowledge-chat">
    <h2>知识库问答</h2>

    <div class="chat-messages" ref="messageListRef">
      <div v-if="messages.length === 0" class="chat-empty">
        <p style="color: #909399">输入问题开始与 AI 助手对话</p>
        <div class="example-questions">
          <el-link
            v-for="(q, i) in exampleQuestions"
            :key="i"
            type="primary"
            style="display:block; margin-bottom: 8px"
            @click="onAsk(q)"
          >
            {{ q }}
          </el-link>
        </div>
      </div>
      <ChatMessage
        v-for="(msg, i) in messages"
        :key="i"
        :role="msg.role"
        :content="msg.content"
        :loading="msg.loading"
        :steps="msg.steps"
        :sources="msg.sources"
        :confidence-badge="msg.confidenceBadge"
        :agent-sources="msg.agentSources"
      />
    </div>

    <div class="chat-input-area">
      <ChatInput :loading="loading" @send="onAsk" />
    </div>
  </div>
</template>

<style scoped>
.knowledge-chat {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 0;
}
.chat-empty {
  text-align: center;
  margin-top: 80px;
}
.chat-input-area {
  flex-shrink: 0;
  padding: 12px 0;
  border-top: 1px solid #eee;
}
</style>
