<template>
  <div class="af-page">
    <div class="af-page-head">
      <div class="af-page-head__main">
        <h2 class="af-page-title">智能报表构建</h2>
        <p class="af-page-desc">自然语言生成 SQL 并实时预览报表</p>
      </div>
      <div class="af-page-head__actions">
        <el-button @click="$router.back()">← 返回</el-button>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="af-card input-section">
      <div class="input-row">
        <input v-model="question" placeholder="输入问题，如：各城市交易额排名"
               class="question-input" @keyup.enter="handleBuild" />
        <button class="btn-primary" @click="handleBuild" :disabled="loading">
          <span v-if="loading" class="btn-loading"></span>
          {{ loading ? '生成中...' : '生成报表' }}
        </button>
      </div>

      <!-- SQL编辑区 -->
      <div v-if="reportConfig" class="sql-section">
        <label>生成的 SQL（可手动修改后重新生成）：</label>
        <textarea v-model="editableSql" class="sql-editor" rows="4" />
        <div class="sql-actions">
          <button class="btn-secondary" @click="handleRebuild" :disabled="loading">重新生成</button>
          <span class="sql-level">级别：{{ reportConfig.level || '-' }}</span>
        </div>
      </div>
    </div>

    <!-- 加载骨架屏 -->
    <LoadingSkeleton v-if="loading && !reportConfig" :rows="6" />

    <!-- 报表预览 -->
    <div v-if="reportConfig && !loading" class="report-preview" ref="reportRef">
      <div class="preview-header">
        <h3>{{ reportConfig.name }}</h3>
        <div class="preview-actions">
          <button class="btn-icon" @click="handleExportPdf" title="导出PDF">PDF</button>
          <button class="btn-icon" @click="handleRefresh" title="刷新数据" :disabled="refreshing">
            {{ refreshing ? '...' : '刷新' }}
          </button>
        </div>
      </div>

      <!-- 筛选面板 -->
      <FilterPanel
        v-if="reportConfig.filterConfig && reportConfig.filterConfig.length"
        :filters="reportConfig.filterConfig"
        :loading="filterLoading"
        @change="handleFilterChange"
      />

      <!-- 数字卡片 -->
      <NumberCards
        v-if="reportConfig.chartConfig && reportConfig.chartConfig.type === 'number_card'"
        :cards="reportConfig.chartConfig.numberCards"
        :data="tableData[0] || {}"
      />

      <!-- 图表 -->
      <ChartRenderer
        v-if="reportConfig.chartConfig && reportConfig.chartConfig.type !== 'number_card'"
        :chart-config="reportConfig.chartConfig"
        :rows="tableData"
      />

      <!-- 数据表格 -->
      <DataTable
        :rows="tableData"
        :columns="reportConfig.columnConfig || []"
      />

      <!-- 保存区域 -->
      <div class="save-section">
        <input v-model="reportName" placeholder="报表名称" class="name-input" />
        <button class="btn-primary btn-sm" @click="handleSave">保存并生成分享链接</button>
      </div>

      <!-- 分享链接 -->
      <div v-if="shareUrl" class="share-section">
        <label>分享链接：</label>
        <a :href="shareUrl" target="_blank" class="share-link">{{ shareUrl }}</a>
        <button class="btn-copy" @click="copyShareUrl">复制</button>
      </div>
    </div>

    <!-- 错误提示 -->
    <div v-if="error" class="error-msg">{{ error }}</div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { buildReport, buildReportFromSql, saveReport, executeParamSql } from '@/api/report'
import { exportToPdf } from '@/utils/pdfExport'
import FilterPanel from '@/components/report/FilterPanel.vue'
import ChartRenderer from '@/components/report/ChartRenderer.vue'
import DataTable from '@/components/report/DataTable.vue'
import NumberCards from '@/components/report/NumberCards.vue'
import LoadingSkeleton from '@/components/report/LoadingSkeleton.vue'

const question = ref('')
const editableSql = ref('')
const reportConfig = ref(null)
const tableData = ref([])
const reportName = ref('')
const shareUrl = ref('')
const loading = ref(false)
const refreshing = ref(false)
const filterLoading = ref(false)
const error = ref('')
const reportRef = ref(null)

async function handleBuild() {
  if (!question.value.trim()) return
  error.value = ''
  loading.value = true
  shareUrl.value = ''
  reportConfig.value = null

  try {
    const res = await buildReport(question.value)
    reportConfig.value = res
    editableSql.value = res.sqlText
    reportName.value = res.name || question.value
    tableData.value = res.sampleRows || []
  } catch (e) {
    error.value = e.response?.data?.error || e.response?.data?.message || '生成失败'
  } finally {
    loading.value = false
  }
}

async function handleRebuild() {
  if (!editableSql.value.trim()) return
  loading.value = true
  error.value = ''
  try {
    const res = await buildReportFromSql(reportName.value, editableSql.value)
    reportConfig.value = res
    tableData.value = res.sampleRows || []
  } catch (e) {
    error.value = '重新生成失败'
  } finally {
    loading.value = false
  }
}

/**
 * 筛选联动：用户修改筛选条件后，替换SQL模板中的占位符，重新执行
 */
async function handleFilterChange(params) {
  if (!reportConfig.value?.sqlText) return
  filterLoading.value = true
  error.value = ''

  try {
    // 调后端参数化执行接口
    const res = await executeParamSql(reportConfig.value.sqlText, params)
    if (res.rows) {
      tableData.value = res.rows
    }
  } catch (e) {
    error.value = '筛选查询失败'
  } finally {
    filterLoading.value = false
  }
}

/**
 * 刷新数据：重新执行当前SQL
 */
async function handleRefresh() {
  if (!reportConfig.value?.sqlText) return
  refreshing.value = true
  try {
    const res = await buildReportFromSql(reportName.value, reportConfig.value.sqlText)
    tableData.value = res.sampleRows || []
  } catch (e) {
    error.value = '刷新失败'
  } finally {
    refreshing.value = false
  }
}

async function handleSave() {
  if (!reportConfig.value) return
  try {
    const saved = await saveReport({
      ...reportConfig.value,
      name: reportName.value
    })
    const token = saved.shareToken
    shareUrl.value = `${window.location.origin}/report/view/${token}`
  } catch (e) {
    error.value = '保存失败'
  }
}

/**
 * PDF导出：截取报表区域生成PDF
 */
async function handleExportPdf() {
  if (!reportRef.value) return
  try {
    await exportToPdf(reportRef.value, reportName.value || '报表')
  } catch (e) {
    error.value = 'PDF导出失败'
  }
}

function copyShareUrl() {
  navigator.clipboard.writeText(shareUrl.value)
}
</script>

<style scoped>
.input-section { margin-bottom: var(--af-sp-5); }
.input-row { display: flex; gap: var(--af-sp-3); }
.question-input {
  flex: 1; padding: 12px 18px; font-size: var(--af-fs-md);
  border: 1px solid var(--af-border); border-radius: var(--af-radius-sm);
  outline: none; color: var(--af-text-1);
  transition: border-color var(--af-transition), box-shadow var(--af-transition);
}
.question-input::placeholder { color: var(--af-text-3); }
.question-input:hover { border-color: var(--el-color-primary-light-5); }
.question-input:focus { border-color: var(--af-primary); box-shadow: var(--af-shadow-focus); }
.sql-section { margin-top: var(--af-sp-4); }
.sql-section label { font-size: var(--af-fs-xs); color: var(--af-text-2); }
.sql-editor {
  width: 100%; margin-top: var(--af-sp-2); padding: var(--af-sp-3);
  font-family: var(--af-font-mono); font-size: var(--af-fs-xs);
  border: 1px solid var(--af-border); border-radius: var(--af-radius-sm);
  resize: vertical; background: var(--af-bg-soft); color: var(--af-text-1);
  outline: none; transition: border-color var(--af-transition);
}
.sql-editor:focus { border-color: var(--af-primary); }
.sql-actions { display: flex; align-items: center; gap: var(--af-sp-3); margin-top: var(--af-sp-2); }
.sql-level {
  font-size: var(--af-fs-xs); color: var(--af-text-3);
  background: var(--af-bg-soft); padding: 2px var(--af-sp-2);
  border-radius: 100px;
}
.btn-primary {
  padding: 12px 28px; background-image: var(--af-primary-grad); color: #fff;
  border: none; border-radius: var(--af-radius-sm); font-size: var(--af-fs-md);
  cursor: pointer; white-space: nowrap; display: flex; align-items: center; gap: var(--af-sp-1);
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.24); transition: all var(--af-transition);
}
.btn-primary:hover {
  box-shadow: var(--af-shadow-primary); transform: translateY(-1px);
  background-image: linear-gradient(135deg, #4096ff 0%, #69b1ff 100%);
}
.btn-primary:disabled { background: var(--el-color-primary-light-5); cursor: not-allowed; box-shadow: none; transform: none; }
.btn-sm { padding: 8px 18px; font-size: var(--af-fs-sm); }
.btn-secondary {
  padding: 8px 18px; background: var(--af-bg-card); color: var(--af-text-2);
  border: 1px solid var(--af-border); border-radius: var(--af-radius-sm);
  cursor: pointer; transition: all var(--af-transition);
}
.btn-secondary:hover { color: var(--af-primary); border-color: var(--el-color-primary-light-5); background: var(--af-bg-hover); }
.btn-icon {
  padding: 7px 14px; background: var(--af-bg-card); border: 1px solid var(--af-border);
  border-radius: var(--af-radius-sm); cursor: pointer; font-size: var(--af-fs-xs);
  color: var(--af-text-2); transition: all var(--af-transition);
}
.btn-icon:hover { background: var(--af-bg-hover); border-color: var(--el-color-primary-light-5); color: var(--af-primary); }
.btn-loading {
  width: 14px; height: 14px; border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff; border-radius: 50%; animation: spin 0.6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.report-preview { margin-top: var(--af-sp-5); }
.preview-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--af-sp-4); }
.preview-header h3 { margin: 0; font-size: var(--af-fs-lg); font-weight: 600; color: var(--af-text-1); }
.preview-actions { display: flex; gap: var(--af-sp-2); }
.save-section { display: flex; gap: var(--af-sp-3); margin-top: var(--af-sp-5); padding-top: var(--af-sp-4); border-top: 1px solid var(--af-border-light); }
.name-input {
  flex: 1; padding: 9px 14px; border: 1px solid var(--af-border);
  border-radius: var(--af-radius-sm); font-size: var(--af-fs-sm); outline: none;
  transition: border-color var(--af-transition), box-shadow var(--af-transition);
}
.name-input:focus { border-color: var(--af-primary); box-shadow: var(--af-shadow-focus); }
.share-section {
  margin-top: var(--af-sp-3); padding: var(--af-sp-3) var(--af-sp-4);
  background: var(--el-color-primary-light-9); border-radius: var(--af-radius-sm);
  display: flex; align-items: center; gap: var(--af-sp-2);
}
.share-section label { font-size: var(--af-fs-xs); color: var(--af-text-2); white-space: nowrap; }
.share-link { color: var(--af-primary); word-break: break-all; flex: 1; font-size: var(--af-fs-xs); }
.btn-copy {
  padding: 5px 14px; background: var(--af-primary); color: #fff; border: none;
  border-radius: var(--af-radius-sm); cursor: pointer; font-size: var(--af-fs-xs); white-space: nowrap;
  transition: background var(--af-transition);
}
.btn-copy:hover { background: var(--el-color-primary-light-3); }
.error-msg {
  margin-top: var(--af-sp-3); padding: var(--af-sp-3) var(--af-sp-4);
  background: rgba(245, 63, 63, 0.08); color: var(--af-danger);
  border-radius: var(--af-radius-sm); border-left: 3px solid var(--af-danger);
  font-size: var(--af-fs-sm);
}
@media (max-width: 768px) {
  .input-row, .save-section { flex-direction: column; align-items: stretch; }
  .share-section { flex-direction: column; align-items: flex-start; }
}
</style>
