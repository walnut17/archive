<script setup lang="ts">
interface AgentStep {
  iteration: number
  thought: string
  tool: string
  toolArgs: string
  observation: string
}

defineProps<{ steps: AgentStep[] }>()
</script>

<template>
  <el-collapse v-if="steps && steps.length > 0">
    <el-collapse-item title="查看 agent 思考过程" name="agent">
      <el-steps direction="vertical" :active="steps.length">
        <el-step v-for="(step, i) in steps" :key="i">
          <template #title>
            <span style="color: #909399">💭 {{ step.thought }}</span>
          </template>
          <template #description>
            <div>🔧 {{ step.tool }}<span v-if="step.toolArgs"> {{ step.toolArgs }}</span></div>
            <div>👁 {{ step.observation?.substring(0, 200) }}{{ step.observation?.length > 200 ? '...' : '' }}</div>
          </template>
        </el-step>
      </el-steps>
    </el-collapse-item>
  </el-collapse>
</template>
