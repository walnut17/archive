<script setup lang="ts">
import { computed } from 'vue'

interface AgentStep {
  iteration: number
  thought: string
  tool: string
  toolArgs: string
  observation: string
}

interface Source {
  versionId: number
  materialTitle: string
  snippet: string
}

const props = defineProps<{
  role: 'user' | 'assistant'
  content: string
  loading?: boolean
  steps?: AgentStep[] | null
  sources?: Source[] | null
  confidenceBadge?: string | null
}>()

const bubbleClass = computed(() => ({
  'chat-bubble': true,
  'chat-bubble-user': props.role === 'user',
  'chat-bubble-assistant': props.role === 'assistant',
}))

const tagType = computed(() => {
  const map: Record<string, string> = {
    CONFIRMED: 'success',
    AI_INFERRED: 'warning',
    PENDING_REVIEW: 'info',
  }
  return map[props.confidenceBadge ?? ''] || ''
})

const tagText = computed(() => {
  const map: Record<string, string> = {
    CONFIRMED: '高置信',
    AI_INFERRED: 'AI 推测',
    PENDING_REVIEW: '待人工确认',
  }
  return map[props.confidenceBadge ?? ''] || ''
})
</script>

<template>
  <div class="chat-message" :class="`chat-message-${role}`">
    <div class="chat-avatar">
      <el-avatar :size="32" :icon="role === 'user' ? 'UserFilled' : 'ChatDotSquare'" />
    </div>
    <div :class="bubbleClass">
      <div v-if="loading" class="chat-typing">
        <span class="dot">.</span><span class="dot">.</span><span class="dot">.</span>
      </div>
      <div v-else class="chat-content" style="white-space: pre-wrap">{{ content }}</div>

      <div v-if="confidenceBadge && tagType" class="confidence-tag" style="margin-top: 8px">
        <el-tag :type="tagType" size="small">{{ tagText }}</el-tag>
      </div>

      <div v-if="steps && steps.length > 0" class="agent-steps" style="margin-top: 8px">
        <el-collapse>
          <el-collapse-item title="查看 agent 思考过程" name="steps">
            <el-steps direction="vertical" :active="steps.length">
              <el-step v-for="(s, i) in steps" :key="i">
                <template #title><span style="color:#909399">💭 {{ s.thought }}</span></template>
                <template #description>
                  <div>🔧 {{ s.tool }}</div>
                  <div>👁 {{ s.observation?.substring(0, 200) }}{{ s.observation?.length > 200 ? '...' : '' }}</div>
                </template>
              </el-step>
            </el-steps>
          </el-collapse-item>
        </el-collapse>
      </div>

      <div v-if="sources && sources.length > 0" class="sources" style="margin-top: 8px">
        <el-divider content-position="left">来源</el-divider>
        <div v-for="(s, i) in sources" :key="s.versionId" class="source-item">
          <el-tag size="small" style="margin-right: 4px">[{{ i + 1 }}]</el-tag>
          <span style="font-size: 12px">{{ s.materialTitle }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-message {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  max-width: 100%;
}
.chat-message-user {
  flex-direction: row-reverse;
}
.chat-avatar {
  flex-shrink: 0;
}
.chat-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  max-width: 75%;
  word-break: break-word;
}
.chat-bubble-user {
  background: #ecf5ff;
  color: #303133;
}
.chat-bubble-assistant {
  background: #f5f5f5;
  color: #303133;
}
.chat-typing .dot {
  animation: blink 1.4s infinite both;
  font-size: 24px;
  line-height: 1;
}
.chat-typing .dot:nth-child(2) { animation-delay: 0.2s; }
.chat-typing .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink {
  0%, 80%, 100% { opacity: 0; }
  40% { opacity: 1; }
}
</style>
