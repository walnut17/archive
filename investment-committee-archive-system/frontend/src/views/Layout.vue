<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const auth = useAuthStore()

onMounted(async () => {
  if (!auth.user) {
    await auth.fetchMe()
  }
})

function onLogout() {
  auth.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="header-left">
        <h3>📁 投委会档案管理系统</h3>
      </div>
      <div class="header-right">
        <el-dropdown @command="(c: string) => c === 'logout' && onLogout()">
          <span class="user-info">
            <el-avatar :size="28">{{ auth.user?.username?.[0]?.toUpperCase() }}</el-avatar>
            <span style="margin-left: 8px;">
              {{ auth.user?.username }}
              <el-tag size="small" type="info" style="margin-left: 6px;">
                {{ auth.user?.role }}
              </el-tag>
            </span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container>
      <el-aside width="220px" class="aside">
        <el-menu
          :default-active="$route.path"
          router
          background-color="#001529"
          text-color="#bfcbd9"
          active-text-color="#fff"
        >
          <el-menu-item index="/">
            <el-icon><DataLine /></el-icon>
            <span>工作台</span>
          </el-menu-item>
          <el-menu-item index="/projects">
            <el-icon><Folder /></el-icon>
            <span>项目管理</span>
          </el-menu-item>
          <!-- 后续模块加在这里 -->
          <!-- M1 启用:项目菜单已可点 -->
          <el-menu-item index="/knowledge" disabled>
            <el-icon><Document /></el-icon>
            <span>知识库(M2 启用)</span>
          </el-menu-item>
          <el-menu-item index="/timepoints" disabled>
            <el-icon><AlarmClock /></el-icon>
            <span>时点日程(M3 启用)</span>
          </el-menu-item>
          <el-menu-item index="/rules" disabled>
            <el-icon><SetUp /></el-icon>
            <span>规则引擎(M4 启用)</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.header {
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 56px;
}

.header-left h3 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: #606266;
  font-size: 14px;
}

.aside {
  background: #001529;
  height: calc(100vh - 56px);
}

.main {
  background: #f5f7fa;
  padding: 20px;
}
</style>
