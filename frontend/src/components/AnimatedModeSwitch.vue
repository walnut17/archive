<script setup lang="ts">
import { ref, watch } from 'vue'

export type ModeSwitchValue = 'todo-empty' | 'todo-has'

const props = defineProps<{
  mode: ModeSwitchValue
}>()

const mode = ref(props.mode)

watch(
  () => props.mode,
  (newMode) => {
    mode.value = newMode
  },
)
</script>

<template>
  <transition name="mode-switch" mode="out-in">
    <div :key="mode" class="mode-content">
      <slot :mode="mode" />
    </div>
  </transition>
</template>

<style scoped>
.mode-switch-enter-active,
.mode-switch-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.mode-switch-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}
.mode-switch-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
