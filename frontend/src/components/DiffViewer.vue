<script setup lang="ts">
import { ref, watch } from 'vue'
import { create } from 'jsondiffpatch'
import * as htmlFormatter from 'jsondiffpatch/formatters/html'
import DOMPurify from 'dompurify'
import { getFactEventDiff, type FactEventDiff } from '@/api/archive'

const props = defineProps<{
  visible: boolean
  projectId: number
  eventId?: number
}>()

const emit = defineEmits<{ 'update:visible': [boolean] }>()

const diff = ref<FactEventDiff | null>(null)
const htmlDiff = ref('')
const loading = ref(false)

watch(() => [props.visible, props.eventId], async ([vis, id]) => {
  if (!vis || !id) return
  loading.value = true
  try {
    diff.value = await getFactEventDiff(props.projectId, Number(id))
    try {
      const before = diff.value.before ? JSON.parse(diff.value.before) : null
      const after = diff.value.after ? JSON.parse(diff.value.after) : null
      const instance = create()
      const delta = instance.diff(before, after)
      htmlDiff.value = DOMPurify.sanitize(delta
        ? (htmlFormatter.format(delta, before) ?? '<p>无差异</p>')
        : '<p>无差异</p>')
    } catch {
      htmlDiff.value = ''
    }
  } finally {
    loading.value = false
  }
}, { immediate: true })

function close() {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog :model-value="visible" title="事实变更对比" width="800px" @close="close">
    <div v-loading="loading">
      <template v-if="diff">
        <div v-if="htmlDiff" class="diff-html" v-html="htmlDiff" />
        <template v-else>
          <h4>变更前</h4>
          <pre class="diff-pre">{{ diff.before || '—' }}</pre>
          <h4>变更后</h4>
          <pre class="diff-pre">{{ diff.after || '—' }}</pre>
        </template>
        <h4>证据引用</h4>
        <p>{{ diff.evidenceSnippet || '—' }}</p>
      </template>
    </div>
  </el-dialog>
</template>

<style scoped>
.diff-pre {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  overflow: auto;
  max-height: 200px;
}
.diff-html :deep(del) { background: #ffeef0; }
.diff-html :deep(ins) { background: #e6ffed; }
</style>
