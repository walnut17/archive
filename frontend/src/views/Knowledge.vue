<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http, { getData } from '../api/http'
import AgentStepsPanel from '../components/AgentStepsPanel.vue'

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
  sources: Source[]
  reranked: boolean
  elapsedMs: number
  agentMode?: boolean | null
  steps?: AgentStep[] | null
  toolCalls?: number | null
  projectSwitchHint?: string | null
  confidenceBadge?: string | null
}

const question = ref('')
const loading = ref(false)
const useRerank = ref(true)
const agentMode = ref(true)
const result = ref<QaResponse | null>(null)

function switchHintText(hint: string) {
  const map: Record<string, string> = {
    SAME_PROBABLY: '当前问题可能仍属于当前锁定项目, 请确认',
    DIFFERENT_PROBABLY: '检测到不同项目, 是否切换?',
    UNCLEAR: '项目上下文不清晰, 请明确说明',
  }
  return map[hint] || hint
}

function hintType(hint: string): 'success' | 'warning' | 'info' {
  if (hint === 'DIFFERENT_PROBABLY') return 'warning'
  if (hint === 'UNCLEAR') return 'info'
  return 'success'
}

function confidenceTagType(badge: string): 'success' | 'warning' | 'info' | '' {
  const map: Record<string, 'success' | 'warning' | 'info'> = {
    CONFIRMED: 'success',
    AI_INFERRED: 'warning',
    PENDING_REVIEW: 'info',
  }
  return map[badge] || ''
}

function confidenceBadgeText(badge: string) {
  const map: Record<string, string> = {
    CONFIRMED: '高置信',
    AI_INFERRED: 'AI 推测',
    PENDING_REVIEW: '待人工确认',
  }
  return map[badge] || badge
}

async function onAsk() {
  if (!question.value.trim()) {
    ElMessage.error('请输入问题')
    return
  }
  loading.value = true
  try {
    const body = { question: question.value, topN: 10, rerank: useRerank.value }
    const resp = await http.post<any>('/qa/ask', body)
    result.value = getData<QaResponse>(resp)
  } catch (e: any) {
    ElMessage.error(e.message || '问答失败')
  } finally {
    loading.value = false
  }
}

const exampleQuestions = [
  '某项目的尽调报告主要风险点是什么?',
  '最近一次审议否决的项目有哪些?为什么?',
  '投委会对固收类项目的审议结论一般是什么?',
]
</script>

<template>
  <div>
    <h2>知识库问答</h2>
    <p style="color: #909399; font-size: 13px">
      基于 MySQL FULLTEXT 全文检索 + 智谱 GLM-4-Flash 智能重排
    </p>

    <el-card style="margin-bottom: 16px">
      <el-input
        v-model="question"
        type="textarea"
        :rows="3"
        placeholder="输入你的问题,例如:某项目的尽调报告主要风险点是什么?"
        maxlength="500"
        show-word-limit
        @keydown.enter.prevent="!$event.shiftKey && onAsk()"
      />
      <div style="margin-top: 12px; display: flex; align-items: center; gap: 16px">
        <el-checkbox v-model="useRerank">使用 LLM 重排(更准,需要智谱 API key)</el-checkbox>
        <el-switch v-model="agentMode" active-text="Agent 模式" inactive-text="简单检索" />
        <el-button type="primary" :loading="loading" @click="onAsk">提问</el-button>
      </div>
    </el-card>

    <div style="margin-bottom: 16px">
      <span style="color: #909399; font-size: 12px; margin-right: 8px">试试问:</span>
      <el-link
        v-for="(q, i) in exampleQuestions"
        :key="i"
        type="primary"
        style="margin-right: 12px"
        @click="question = q"
      >
        {{ q }}
      </el-link>
    </div>

    <div v-if="result" v-loading="loading">
      <div v-if="result.projectSwitchHint" class="switch-hint-bar" style="margin-bottom: 16px">
        <el-alert
          :title="switchHintText(result.projectSwitchHint)"
          :type="hintType(result.projectSwitchHint)"
          :closable="false"
          show-icon
        />
      </div>

      <el-card v-if="result.answer" style="margin-bottom: 16px">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <span style="font-weight: 500">答案</span>
            <div>
              <el-tag v-if="result.reranked" size="small" type="success" style="margin-right: 8px">已 LLM 重排</el-tag>
              <el-tag size="small">{{ result.elapsedMs }}ms</el-tag>
            </div>
          </div>
        </template>
        <div style="white-space: pre-wrap">{{ result.answer }}</div>
        <div v-if="result.confidenceBadge" class="confidence-badge" style="margin-top: 12px">
          <el-tag :type="confidenceTagType(result.confidenceBadge)">
            {{ confidenceBadgeText(result.confidenceBadge) }}
          </el-tag>
        </div>
        <AgentStepsPanel v-if="result.steps" :steps="result.steps" />
      </el-card>

      <el-card>
        <template #header>
          <div>
            <span style="font-weight: 500">参考来源({{ result.sources.length }})</span>
          </div>
        </template>
        <div v-if="result.sources.length === 0" style="color: #909399">未检索到匹配材料</div>
        <el-collapse>
          <el-collapse-item v-for="(s, i) in result.sources" :key="s.versionId" :name="i">
            <template #title>
              <div style="width: 100%; display: flex; justify-content: space-between; align-items: center">
                <div>
                  <el-tag size="small" style="margin-right: 8px">[{{ i + 1 }}]</el-tag>
                  <span v-if="s.projectCode" style="color: #909399; margin-right: 8px">{{ s.projectCode }}</span>
                  <span style="font-weight: 500">{{ s.materialTitle }}</span>
                  <el-tag size="small" type="info" style="margin-left: 8px">v{{ s.versionNo }}</el-tag>
                </div>
                <el-tag size="small" type="warning">score: {{ s.score.toFixed(2) }}</el-tag>
              </div>
            </template>
            <div style="color: #909399; font-size: 12px; margin-bottom: 8px">
              {{ s.projectName }} / {{ s.proposalTitle }} / {{ s.originalFilename }}
            </div>
            <pre style="white-space: pre-wrap; background: #f5f5f5; padding: 12px; border-radius: 4px; font-size: 12px">{{ s.snippet }}</pre>
          </el-collapse-item>
        </el-collapse>
      </el-card>
    </div>
  </div>
</template>
