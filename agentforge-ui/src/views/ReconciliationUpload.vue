<template>
  <div class="af-page">
    <div class="af-page-head">
      <div class="af-page-head__main">
        <h2 class="af-page-title">数据对账 — 上传文件</h2>
        <p class="af-page-desc">上传第三方账单文件，配置列映射后发起对账</p>
      </div>
      <div class="af-page-head__actions">
        <el-button text @click="$router.push('/reconciliation/history')">历史记录</el-button>
      </div>
    </div>

    <el-card class="upload-card">
      <el-upload
        drag
        :auto-upload="false"
        :on-change="handleFileChange"
        :show-file-list="false"
        accept=".csv,.xlsx"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          拖拽文件到此处，或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">支持 CSV / Excel 文件，最大 10MB</div>
        </template>
      </el-upload>

      <el-alert v-if="parseError" :title="parseError" type="error" :closable="false" class="block-gap" />
    </el-card>

    <!-- 列映射配置 -->
    <el-card v-if="previewData.columns.length" class="mapping-card">
      <template #header>
        <div class="card-header">
          <span>列映射配置（共 {{ previewData.rowCount }} 行）</span>
          <el-tag size="small" type="success">自动识别</el-tag>
        </div>
      </template>

      <el-form :model="mapping" label-width="140px" label-position="right">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="商户号列">
              <el-select v-model="mapping.cusid" placeholder="选择列" clearable>
                <el-option v-for="c in previewData.columns" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="金额列">
              <el-select v-model="mapping.amount" placeholder="选择列" clearable>
                <el-option v-for="c in previewData.columns" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="日期列">
              <el-select v-model="mapping.date" placeholder="选择列" clearable>
                <el-option v-for="c in previewData.columns" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="流水号列（可选）">
              <el-select v-model="mapping.tranno" placeholder="选择列" clearable>
                <el-option v-for="c in previewData.columns" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态列（可选）">
              <el-select v-model="mapping.status" placeholder="选择列" clearable>
                <el-option v-for="c in previewData.columns" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 数据预览 -->
      <div class="preview-wrap">
        <h4 class="preview-title">数据预览（前5行）</h4>
        <el-table :data="previewData.rows.slice(0, 5)" size="small" max-height="240">
          <el-table-column v-for="c in previewData.columns" :key="c" :prop="c" :label="c" />
        </el-table>
      </div>

      <!-- 执行按钮 -->
      <div class="action-bar">
        <el-form-item label="批次名称" class="batch-field">
          <el-input v-model="batchName" placeholder="如：6月份对账" style="width:220px" />
        </el-form-item>
        <el-radio-group v-model="strategy">
          <el-radio-button label="FUZZY">模糊匹配</el-radio-button>
          <el-radio-button label="EXACT">精确匹配</el-radio-button>
        </el-radio-group>
        <el-button type="primary" size="large" :loading="matching" @click="startMatch">开始对账</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const file = ref(null)
const parseError = ref('')
const matching = ref(false)
const batchName = ref('')
const strategy = ref('FUZZY')
const previewData = reactive({ columns: [], rows: [], rowCount: 0, autoMapping: {} })
const mapping = reactive({ tranno: '', cusid: '', amount: '', date: '', status: '' })

async function handleFileChange(uploadFile) {
  file.value = uploadFile.raw
  parseError.value = ''
  const formData = new FormData()
  formData.append('file', uploadFile.raw)
  try {
    const data = await request.post('/reconciliation/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    previewData.columns = data.columns || []
    previewData.rows = data.previewRows || []
    previewData.rowCount = data.rowCount || 0
    previewData.autoMapping = data.autoMapping || {}
    // 应用自动映射
    Object.assign(mapping, {
      tranno: previewData.autoMapping.tranno || '',
      cusid: previewData.autoMapping.cusid || '',
      amount: previewData.autoMapping.amount || '',
      date: previewData.autoMapping.date || '',
      status: previewData.autoMapping.status || ''
    })
    if (!batchName.value) {
      batchName.value = uploadFile.name.replace(/\.(csv|xlsx)$/i, '')
    }
    ElMessage.success(`解析成功：${data.rowCount} 行`)
  } catch (e) {
    parseError.value = e.response?.data?.message || '文件解析失败'
    previewData.columns = []
    previewData.rows = []
  }
}

async function startMatch() {
  if (!file.value) {
    ElMessage.warning('请先上传文件')
    return
  }
  if (!mapping.cusid || !mapping.amount || !mapping.date) {
    ElMessage.warning('请至少配置商户号、金额、日期三列映射')
    return
  }
  if (!batchName.value.trim()) {
    ElMessage.warning('请填写批次名称')
    return
  }
  matching.value = true
  const mappingJson = JSON.stringify(
    Object.fromEntries(Object.entries(mapping).filter(([, v]) => v))
  )
  const formData = new FormData()
  formData.append('file', file.value)
  formData.append('name', batchName.value)
  formData.append('matchStrategy', strategy.value)
  formData.append('columnMapping', mappingJson)
  try {
    const data = await request.post('/reconciliation/match', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })
    ElMessage.success(`对账完成：匹配 ${data.matchedCount} / ${data.totalExternal} 笔`)
    router.push(`/reconciliation/result/${data.reconciliationId}`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '对账失败')
  } finally {
    matching.value = false
  }
}
</script>

<style scoped>
.upload-card { margin-bottom: var(--af-sp-5); }
.upload-card :deep(.el-upload-dragger) {
  border: 2px dashed var(--af-border);
  border-radius: var(--af-radius-md);
  background: var(--af-bg-soft);
  transition: border-color var(--af-transition), background var(--af-transition);
  padding: var(--af-sp-7) var(--af-sp-6);
}
.upload-card :deep(.el-upload-dragger:hover) {
  border-color: var(--af-primary);
  background: var(--af-bg-hover);
}
.upload-card :deep(.el-icon--upload) {
  color: var(--af-primary);
  font-size: 48px;
  margin-bottom: var(--af-sp-2);
}
.upload-card :deep(.el-upload__text em) {
  color: var(--af-primary);
  font-style: normal;
}
.block-gap { margin-top: var(--af-sp-3); }
.mapping-card { margin-bottom: var(--af-sp-5); }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; color: var(--af-text-1); }
.preview-wrap { margin-top: var(--af-sp-4); }
.preview-title { margin: 0 0 var(--af-sp-2); font-size: var(--af-fs-sm); color: var(--af-text-2); }
.action-bar {
  margin-top: var(--af-sp-5);
  padding: var(--af-sp-4);
  background: var(--af-bg-soft);
  border-radius: var(--af-radius-md);
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--af-sp-3);
}
.batch-field { margin: 0 !important; }
</style>
