<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const form = reactive({
  username: 'admin',
  password: 'admin123',
})
const loading = ref(false)
const formRef = ref()

async function onSubmit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (e: any) {
    // 错误已经在 http 拦截器里弹过了
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-container">
    <el-card class="login-card" shadow="always">
      <template #header>
        <div class="login-header">
          <h2>投委会档案管理系统</h2>
          <p class="subtitle">Investment Committee Archive System</p>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        label-position="top"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="用户名">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            size="large"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            size="large"
            autocomplete="current-password"
            show-password
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            size="large"
            style="width: 100%"
            @click="onSubmit"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="hint">
        <el-alert type="info" :closable="false" show-icon>
          <p>默认账号:<b>admin</b> &nbsp; 密码:<b>admin123</b></p>
          <p style="margin-top: 4px; color: #909399; font-size: 12px;">
            首次登录后请立即修改密码
          </p>
        </el-alert>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.login-card {
  width: 420px;
  border-radius: 8px;
}

.login-header {
  text-align: center;
}

.login-header h2 {
  margin: 0;
  font-size: 22px;
  color: #303133;
}

.subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: #909399;
}

.hint {
  margin-top: 16px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
}
</style>
