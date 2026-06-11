<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import mammoth from 'mammoth'
import { getMaterialPreviewUrl } from '@/api/archive'

const props = defineProps<{
  materialId: number
  version?: number
  mimeType?: string
}>()

const loading = ref(false)
const textContent = ref('')
const wordHtml = ref('')
const errorMsg = ref('')

const isPdf = computed(() => props.mimeType?.includes('pdf') ?? false)
const isWord = computed(() =>
  (props.mimeType?.includes('word') || props.mimeType?.includes('document') || props.mimeType?.includes('officedocument')) ?? false
)
const isImage = computed(() => props.mimeType?.startsWith('image/') ?? false)
const isText = computed(() =>
  (props.mimeType?.startsWith('text/') || props.mimeType === 'application/json') ?? false
)

const previewUrl = computed(() => getMaterialPreviewUrl(props.materialId, props.version))

async function loadContent() {
  loading.value = true
  errorMsg.value = ''
  wordHtml.value = ''
  textContent.value = ''
  try {
    const token = localStorage.getItem('archive-token')
    const res = await fetch(previewUrl.value, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!res.ok) {
      errorMsg.value = await res.text() || '预览失败'
      return
    }
    if (isWord.value) {
      const buf = await res.arrayBuffer()
      const result = await mammoth.convertToHtml({ arrayBuffer: buf })
      wordHtml.value = result.value
      if (result.messages.length) {
        errorMsg.value = '复杂 Word 格式可能显示不完整,建议下载查看'
      }
    } else if (isText.value) {
      textContent.value = await res.text()
    }
  } catch (e: any) {
    errorMsg.value = e?.message || '预览加载失败'
  } finally {
    loading.value = false
  }
}

watch(() => [props.materialId, props.version], loadContent)
onMounted(loadContent)
</script>

<template>
  <div class="preview-frame" v-loading="loading">
    <el-alert v-if="errorMsg" :title="errorMsg" type="warning" show-icon :closable="false" style="margin-bottom: 8px" />
    <iframe v-if="isPdf" :src="previewUrl" width="100%" height="600" />
    <div v-else-if="isWord" class="word-preview" v-html="wordHtml" />
    <img v-else-if="isImage" :src="previewUrl" style="max-width: 100%" alt="preview" />
    <pre v-else-if="isText" class="text-preview">{{ textContent }}</pre>
    <el-empty v-else description="不支持预览,请下载查看" />
  </div>
</template>

<style scoped>
.preview-frame { min-height: 400px; }
.word-preview { padding: 12px; background: #fff; border: 1px solid #ebeef5; max-height: 600px; overflow: auto; }
.text-preview { background: #f5f7fa; padding: 12px; max-height: 600px; overflow: auto; }
</style>
