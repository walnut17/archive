<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http, { getData } from '@/api/http'

const health = ref<string>('检查中...')
const userInfo = ref<any>(null)
const now = ref(new Date().toLocaleString('zh-CN'))

async function checkHealth() {
  try {
    const r = await http.get('/health').then(getData<{ status: string; time: string }>)
    health.value = `✅ ${r.status} @ ${r.time}`
  } catch (e) {
    health.value = '❌ 后端连不上'
  }
}

async function fetchMe() {
  try {
    const r = await http.get('/auth/me').then(getData<any>)
    userInfo.value = r
  } catch {
    /* 拦截器已处理 */
  }
}

onMounted(() => {
  checkHealth()
  fetchMe()
})
</script>

<template>
  <div>
    <el-card>
      <template #header>
        <h3 style="margin: 0;">🎉 M0 基建完成 — 框架就绪</h3>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="后端健康">
          {{ health }}
        </el-descriptions-item>
        <el-descriptions-item label="当前时间">
          {{ now }}
        </el-descriptions-item>
        <el-descriptions-item label="当前用户">
          {{ userInfo?.username || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag v-if="userInfo?.role" type="info">{{ userInfo.role }}</el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card style="margin-top: 16px;">
      <template #header>
        <h3 style="margin: 0;">📋 后续模块进度</h3>
      </template>

      <el-timeline>
        <el-timeline-item
          v-for="(item, idx) in roadmap"
          :key="idx"
          :timestamp="item.stage"
          :type="item.type"
          :hollow="item.status === 'todo'"
        >
          <h4 style="margin: 0 0 4px;">{{ item.title }}</h4>
          <p style="margin: 0; color: #606266;">{{ item.desc }}</p>
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<script lang="ts">
export default {
  data() {
    return {
      roadmap: [
        { stage: 'M0(已就绪)', title: '后端 + 前端 + 部署', desc: '用户登录、健康检查、WinSW + Caddy 部署', type: 'success', status: 'done' },
        { stage: 'M1(下一步)', title: '档案 CRUD', desc: '项目-议案-材料三级 + Tika 解析入库', type: 'primary', status: 'next' },
        { stage: 'M2', title: '知识库问答', desc: 'MySQL FULLTEXT 检索 + 智谱 LLM 总结', type: 'info', status: 'todo' },
        { stage: 'M3', title: '时点提取 + 邮件提醒', desc: '手工/自动抽取 + SMTP 通知', type: 'info', status: 'todo' },
        { stage: 'M4', title: '规则引擎', desc: 'Aviator 表达式 + 事件订阅', type: 'info', status: 'todo' },
        { stage: 'M5', title: '打磨 + 上线', desc: '审计 + 监控 + 备份演练 + 文档', type: 'info', status: 'todo' },
      ],
    }
  },
}
</script>
