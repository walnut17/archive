<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{
  send: [text: string]
}>()

const text = ref('')
const props = defineProps<{ loading: boolean }>()

function onSend() {
  if (!text.value.trim() || props.loading) return
  emit('send', text.value.trim())
  text.value = ''
}
</script>

<template>
  <div class="chat-input-bar">
    <el-input
      v-model="text"
      type="textarea"
      :rows="2"
      placeholder="输入你的问题,例如:新能源那个项目今年盈利怎么样?"
      maxlength="500"
      show-word-limit
      :disabled="props.loading"
      @keydown.enter.prevent="!$event.shiftKey && onSend()"
    />
    <el-button
      type="primary"
      :loading="props.loading"
      :disabled="!text.trim()"
      class="send-btn"
      @click="onSend"
    >
      发送
    </el-button>
  </div>
</template>

<style scoped>
.chat-input-bar {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.send-btn {
  flex-shrink: 0;
  margin-top: 4px;
}
</style>
