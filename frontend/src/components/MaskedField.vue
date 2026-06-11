<script setup lang="ts">
import { computed } from 'vue'
import { requestUnmask } from '@/api/archive'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  label: string
  value?: string | number | null
  displayValue?: string | null
  masked?: boolean
  projectId?: number
}>()

const showValue = computed(() =>
  props.masked && props.displayValue != null ? props.displayValue : (props.value ?? '—')
)

async function onRequestUnmask() {
  if (!props.projectId) return
  try {
    const res = await requestUnmask(props.projectId)
    ElMessage.success('已提交查看申请')
    console.debug('unmask', res.unmaskRequestUrl)
  } catch {
    // handled by interceptor
  }
}
</script>

<template>
  <div class="masked-field">
    <span class="label">{{ label }}:</span>
    <span class="value" :class="{ masked: masked }">{{ showValue }}</span>
    <el-button v-if="masked && projectId" link type="primary" size="small" @click="onRequestUnmask">
      申请查看
    </el-button>
  </div>
</template>

<style scoped>
.masked-field { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.label { color: #909399; min-width: 80px; }
.value.masked { filter: blur(2px); }
</style>
