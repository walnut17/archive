<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { stagingUploadProject } from '../api/archive'

const router = useRouter()
const loading = ref(false)
const file = ref<File | null>(null)

function onFileChange(uploadFile: { raw?: File }) {
  file.value = uploadFile.raw ?? null
}

async function onSubmit() {
  if (!file.value) {
    ElMessage.warning('请先选择材料文件')
    return
  }
  loading.value = true
  try {
    const result = await stagingUploadProject(file.value)
    ElMessage.success('材料上传成功，正在解析并预填…')
    router.push({
      name: 'project-form',
      query: {
        materialVersionId: String(result.materialVersionId),
        draftProjectId: String(result.draftProjectId),
      },
    })
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
  } finally {
    loading.value = false
  }
}

function onCancel() {
  router.push({ name: 'project-list' })
}
</script>

<template>
  <div class="create-upload">
    <h2>新建项目 — 上传立项材料</h2>
    <p class="hint">请先上传尽调报告或立项申请书，系统将自动抽取关键信息预填表单。</p>

    <el-upload
      drag
      :auto-upload="false"
      :limit="1"
      accept=".pdf,.doc,.docx,.txt"
      :on-change="onFileChange"
      :on-exceed="() => ElMessage.warning('每次仅上传 1 个文件')"
    >
      <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
      <template #tip>
        <div class="el-upload__tip">支持 PDF / Word / TXT，单文件 ≤ 100MB</div>
      </template>
    </el-upload>

    <div class="actions">
      <el-button @click="onCancel">取消</el-button>
      <el-button type="primary" :loading="loading" @click="onSubmit">上传并继续</el-button>
    </div>
  </div>
</template>

<style scoped>
.create-upload {
  max-width: 560px;
}
.hint {
  color: #606266;
  margin-bottom: 16px;
}
.actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
}
</style>
