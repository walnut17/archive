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

async function onAsk(question: string) {
  // 添加用户消息
  messages.value.push({ role: 'user', content: question })
  // 添加占位 loading
  const idx = messages.value.length
  messages.value.push({ role: 'assistant', content: '', loading: true })

  loading.value = true
  try {
    let resp: any
    if (turnCount.value === 0) {
      // 首次提问走 /qa/ask
      resp = await http.post<any>('/qa/ask', {
        question,
        topN: 10,
        rerank: true,
        agentMode: true,
      })
    } else {
      // 多轮走 /qa/turn/{sessionId}
      resp = await http.post<any>(`/qa/turn/${sessionId.value}`, { question })
    }

    const data = getData<QaResponse>(resp)
    turnCount.value++

    // 替换 loading 为真实消息
    messages.value[idx] = {
      role: 'assistant',
      content: data.answer ?? '',
      steps: data.steps ?? null,
      sources: safeSources(data.sources) as Source[],
      confidenceBadge: data.confidenceBadge ?? null,
    }
  } catch (e: any) {
    messages.value[idx] = {
      role: 'assistant',
      content: '问答失败: ' + (e.message || '请稍后重试'),
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
