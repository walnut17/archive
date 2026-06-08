<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getProject, createProject, updateProject,
  type Project,
  projectStatusOptions, projectCategoryOptions,
} from '../api/archive'

const route = useRoute()
const router = useRouter()
const isEdit = ref(false)
const form = ref<Project>({
  code: '',
  name: '',
  category: '股权类',
  status: '草稿',
  amountWan: 0,
  summary: '',
  remark: '',
})
const loading = ref(false)

onMounted(async () => {
  const id = route.params.id
  if (id) {
    isEdit.value = true
    loading.value = true
    try {
      form.value = await getProject(Number(id))
    } finally {
      loading.value = false
    }
  }
})

async function onSubmit() {
  if (!form.value.code || !form.value.name) {
    ElMessage.error('编号和名称必填')
    return
  }
  loading.value = true
  try {
    if (isEdit.value) {
      await updateProject(form.value.id!, form.value)
      ElMessage.success('更新成功')
    } else {
      await createProject(form.value)
      ElMessage.success('创建成功')
    }
    router.push({ name: 'project-list' })
  } finally {
    loading.value = false
  }
}

function onCancel() {
  router.back()
}
</script>

<template>
  <div>
    <h2>{{ isEdit ? '编辑项目' : '新建项目' }}</h2>

    <el-form :model="form" label-width="120px" style="max-width: 800px" v-loading="loading">
      <el-form-item label="项目编号" required>
        <el-input v-model="form.code" :disabled="isEdit" placeholder="如 PRJ-2026-001" />
      </el-form-item>
      <el-form-item label="项目名称" required>
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item label="业务类别">
        <el-select v-model="form.category" style="width: 200px">
          <el-option v-for="c in projectCategoryOptions" :key="c" :label="c" :value="c" />
        </el-select>
      </el-form-item>
      <el-form-item label="投资金额(万)">
        <el-input-number v-model="form.amountWan" :min="0" :step="100" style="width: 200px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="form.status" style="width: 200px">
          <el-option v-for="s in projectStatusOptions" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item label="审议日期">
        <el-date-picker v-model="form.scheduledMeetingAt" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
      </el-form-item>
      <el-form-item label="摘要">
        <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="2000" show-word-limit />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="2000" show-word-limit />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="onSubmit">保存</el-button>
        <el-button @click="onCancel">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>
