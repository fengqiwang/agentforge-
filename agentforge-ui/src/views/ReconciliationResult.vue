<template>
  <div class="af-page af-page--wide">
    <div class="af-page-head">
      <div class="af-page-head__main">
        <h2 class="af-page-title">对账结果详情</h2>
        <p class="af-page-desc">批次 {{ batch.name || '' }} 的对账明细与分析</p>
      </div>
      <div class="af-page-head__actions">
        <el-button @click="$router.push('/reconciliation/history')">← 返回</el-button>
        <el-button :icon="Download" :loading="exporting" @click="exportExcel">导出 Excel</el-button>
        <el-button type="primary" :loading="summaryLoading" @click="loadSummary">生成 AI 摘要</el-button>
      </div>
    </div>

    <!-- 概览卡片 -->
    <el-row :gutter="16" class="overview">
      <el-col :span="4">
        <div class="af-kpi">
          <div class="af-kpi-label">内部总数</div>
          <div class="af-kpi-value">{{ batch.total_internal ?? '-' }}</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="af-kpi">
          <div class="af-kpi-label">第三方总数</div>
          <div class="af-kpi-value">{{ batch.total_external ?? '-' }}</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="af-kpi af-kpi--positive">
          <div class="af-kpi-label">匹配成功</div>
          <div class="af-kpi-value" style="color:var(--af-status-positive)">{{ batch.matched_count ?? '-' }}</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="af-kpi af-kpi--negative">
          <div class="af-kpi-label">金额差异</div>
          <div class="af-kpi-value" style="color:var(--af-status-negative)">{{ batch.amount_diff_count ?? '-' }}</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="af-kpi af-kpi--warning">
          <div class="af-kpi-label">仅我方</div>
          <div class="af-kpi-value" style="color:var(--af-status-warning)">{{ batch.only_internal_count ?? '-' }}</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="af-kpi af-kpi--warning">
          <div class="af-kpi-label">仅第三方</div>
          <div class="af-kpi-value" style="color:var(--af-status-warning)">{{ batch.only_external_count ?? '-' }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 元信息 -->
    <el-card class="meta-card">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="批次名称">{{ batch.name }}</el-descriptions-item>
        <el-descriptions-item label="源文件">{{ batch.source_file }}</el-descriptions-item>
        <el-descriptions-item label="匹配策略">
          <el-tag size="small">{{ batch.match_strategy }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="差异总额">
          <span class="diff-amount">¥ {{ fmt(batch.diff_amount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="batch.status === 'COMPLETED' ? 'success' : 'warning'" size="small">
            {{ batch.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ batch.created_at }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- AI 摘要 -->
    <el-card v-if="summary" class="summary-card">
      <template #header>
        <div class="card-header">
          <span>AI 分析摘要</span>
          <el-tag size="small" type="success">DeepSeek</el-tag>
        </div>
      </template>
      <div class="summary-body" v-html="renderMarkdown(summary)"></div>
    </el-card>

    <!-- 明细表 -->
    <el-card class="detail-card">
      <template #header>
        <div class="card-header">
          <span>差异明细（共 {{ total }} 条）</span>
          <el-radio-group v-model="filterType" size="small" @change="resetAndLoad">
            <el-radio-button label="">全部</el-radio-button>
            <el-radio-button label="MATCHED">匹配</el-radio-button>
            <el-radio-button label="AMOUNT_DIFF">金额差异</el-radio-button>
            <el-radio-button label="ONLY_INTERNAL">仅我方</el-radio-button>
            <el-radio-button label="ONLY_EXTERNAL">仅第三方</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-table :data="details" size="small" v-loading="loading" max-height="540">
        <el-table-column prop="match_type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="typeColor(row.match_type)" size="small">{{ typeLabel(row.match_type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="我方交易">
          <el-table-column prop="internal_cusid" label="商户号" width="130" />
          <el-table-column prop="internal_amount" label="金额" width="110">
            <template #default="{ row }">{{ fmt(row.internal_amount) }}</template>
          </el-table-column>
          <el-table-column prop="internal_date" label="日期" width="100" />
          <el-table-column prop="internal_tranno" label="流水号" width="150" />
        </el-table-column>
        <el-table-column label="第三方交易">
          <el-table-column prop="external_cusid" label="商户号" width="130" />
          <el-table-column prop="external_amount" label="金额" width="110">
            <template #default="{ row }">{{ fmt(row.external_amount) }}</template>
          </el-table-column>
          <el-table-column prop="external_date" label="日期" width="100" />
          <el-table-column prop="external_tranno" label="流水号" width="150" />
        </el-table-column>
        <el-table-column prop="diff_amount" label="差异金额" width="120">
          <template #default="{ row }">
            <span :class="{ 'diff-cell': row.diff_amount && Number(row.diff_amount) !== 0 }">
              {{ fmt(row.diff_amount) }}
            </span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="prev, pager, next, jumper, total"
          :total="total"
          :page-size="pageSize"
          :current-page="page + 1"
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import request from '@/utils/request'

const route = useRoute()
const reconciliationId = route.params.id

const batch = ref({})
const details = ref([])
const total = ref(0)
const page = ref(0)
const pageSize = 20
const filterType = ref('')
const loading = ref(false)
const exporting = ref(false)

const summary = ref('')
const summaryLoading = ref(false)

onMounted(() => {
  loadBatch()
  loadDetails()
})

async function loadBatch() {
  try {
    const data = await request.get(`/reconciliation/${reconciliationId}`)
    batch.value = data || {}
  } catch (e) {
    ElMessage.error('加载批次信息失败')
  }
}

async function loadDetails() {
  loading.value = true
  try {
    const data = await request.get(`/reconciliation/${reconciliationId}/details`, {
      params: { type: filterType.value, page: page.value, size: pageSize }
    })
    details.value = data.details || []
    total.value = data.total || 0
  } catch (e) {
    ElMessage.error('加载明细失败')
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  page.value = 0
  loadDetails()
}

function onPageChange(p) {
  page.value = p - 1
  loadDetails()
}

async function loadSummary() {
  summaryLoading.value = true
  try {
    const data = await request.get(`/reconciliation/${reconciliationId}/summary`, { timeout: 60000 })
    summary.value = data.summary || ''
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '摘要生成失败')
  } finally {
    summaryLoading.value = false
  }
}

async function exportExcel() {
  exporting.value = true
  try {
    const res = await request.get(`/reconciliation/${reconciliationId}/export/excel`, {
      responseType: 'blob',
      timeout: 120000
    })
    const blob = new Blob([res], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${batch.value.name || 'reconciliation'}_对账报告.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

function fmt(v) {
  if (v == null || v === '') return '-'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function typeLabel(t) {
  return { MATCHED: '匹配', AMOUNT_DIFF: '金额差异', ONLY_INTERNAL: '仅我方', ONLY_EXTERNAL: '仅第三方' }[t] || t
}

function typeColor(t) {
  return { MATCHED: 'success', AMOUNT_DIFF: 'danger', ONLY_INTERNAL: 'warning', ONLY_EXTERNAL: 'warning' }[t] || ''
}

function renderMarkdown(text) {
  return text ? marked.parse(text) : ''
}
</script>

<style scoped>
.overview { margin-bottom: var(--af-sp-5); }
.meta-card { margin-bottom: var(--af-sp-5); }
.summary-card { margin-bottom: var(--af-sp-5); }
.summary-body { line-height: 1.7; font-size: var(--af-fs-sm); color: var(--af-text-2); }
.summary-body :deep(h1),
.summary-body :deep(h2),
.summary-body :deep(h3) { color: var(--af-text-1); margin: var(--af-sp-3) 0 var(--af-sp-2); }
.summary-body :deep(code) { background: var(--af-bg-soft); padding: 2px 6px; border-radius: 4px; font-family: var(--af-font-mono); }
.detail-card { margin-bottom: var(--af-sp-5); }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; color: var(--af-text-1); }
.diff-amount { color: var(--af-danger); font-weight: 700; font-size: var(--af-fs-md); font-family: var(--af-font-mono); }
.diff-cell { color: var(--af-danger); font-weight: 700; font-family: var(--af-font-mono); }
.pager { margin-top: var(--af-sp-4); display: flex; justify-content: flex-end; }
</style>
