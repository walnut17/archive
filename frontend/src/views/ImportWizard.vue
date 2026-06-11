<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { importExcel, getImportErrors, type ImportBatch, type ImportError } from '@/api/archive'

const importType = ref<'project' | 'material' | 'proposal' | 'fact'>('project')
const uploading = ref(false)
const batch = ref<ImportBatch | null>(null)
const errors = ref<ImportError[]>([])

async function onUpload(file: File) {
  uploading.value = true
  errors.value = []
  batch.value = null
  try {
    batch.value = await importExcel(importType.value, file)
    ElMessage.success(`导入完成: 成功 ${batch.value.success} / 失败 ${batch.value.failed}`)
    if (batch.value.failed > 0) {
      errors.value = await getImportErrors(batch.value.id)
    }
  } finally {
    uploading.value = false
  }
  return false
}
</script>

<template>
  <div>
    <h2>旧系统 Excel 导入</h2>
    <el-alert title="支持 project / material / proposal / fact 四类模板" type="info" show-icon :closable="false" style="margin-bottom: 16px" />

    <el-form label-width="100px" style="max-width: 600px">
      <el-form-item label="导入类型">
        <el-select v-model="importType" style="width: 200px">
          <el-option label="项目" value="project" />
          <el-option label="材料" value="material" />
          <el-option label="议案" value="proposal" />
          <el-option label="事实" value="fact" />
        </el-select>
      </el-form-item>
      <el-form-item label="上传文件">
        <el-upload
          :auto-upload="true"
          :show-file-list="false"
          accept=".xlsx,.xls"
          :before-upload="onUpload"
        >
          <el-button type="primary" :loading="uploading">选择 Excel 文件</el-button>
        </el-upload>
      </el-form-item>
    </el-form>

    <el-descriptions v-if="batch" :column="3" border style="margin-top: 16px; max-width: 600px">
      <el-descriptions-item label="批次 ID">{{ batch.id }}</el-descriptions-item>
      <el-descriptions-item label="总数">{{ batch.total }}</el-descriptions-item>
      <el-descriptions-item label="成功">{{ batch.success }}</el-descriptions-item>
      <el-descriptions-item label="失败">{{ batch.failed }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ batch.status }}</el-descriptions-item>
    </el-descriptions>

    <el-table v-if="errors.length" :data="errors" stripe style="margin-top: 16px">
      <el-table-column prop="row" label="行号" width="80" />
      <el-table-column prop="column" label="列号" width="80" />
      <el-table-column prop="errorMsg" label="错误信息" />
    </el-table>
  </div>
</template>
