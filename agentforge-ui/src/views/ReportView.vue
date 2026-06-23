<template>
  <div class="af-page">
    <!-- 加载骨架屏 -->
    <LoadingSkeleton v-if="loading" :rows="6" />

    <template v-if="report && !loading">
      <div class="view-header">
        <div class="af-page-head__main">
          <h2 class="af-page-title">{{ report.name }}</h2>
          <p v-if="report.description" class="af-page-desc">{{ report.description }}</p>
        </div>
        <div class="view-actions">
          <button class="btn-icon" @click="handleExportPdf" title="导出PDF">PDF</button>
          <button class="btn-icon" @click="handleRefresh" title="刷新数据" :disabled="refreshing">
            {{ refreshing ? '...' : '刷新' }}
          </button>
        </div>
      </div>

      <div class="report-content" ref="reportRef">
        <!-- 筛选面板 -->
        <FilterPanel
          v-if="report.filterConfig && report.filterConfig.length"
          :filters="report.filterConfig"
          :loading="filterLoading"
          @change="handleFilterChange"
        />

        <!-- 数字卡片 -->
        <NumberCards
          v-if="report.chartConfig && report.chartConfig.type === 'number_card'"
          :cards="report.chartConfig.numberCards"
          :data="rows[0] || {}"
        />

        <!-- 图表 -->
        <ChartRenderer
          v-if="report.chartConfig && report.chartConfig.type !== 'number_card'"
          :chart-config="report.chartConfig"
          :rows="rows"
        />

        <!-- 数据表格 -->
        <DataTable
          :rows="rows"
          :columns="report.columnConfig || []"
        />

        <div class="report-footer">数据更新时间：{{ updateTime }}</div>
      </div>
    </template>

    <div v-if="error" class="error-msg">{{ error }}</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { viewReport, executeParamSql, buildReportFromSql } from '@/api/report'
import { exportToPdf } from '@/utils/pdfExport'
import FilterPanel from '@/components/report/FilterPanel.vue'
import ChartRenderer from '@/components/report/ChartRenderer.vue'
import DataTable from '@/components/report/DataTable.vue'
import NumberCards from '@/components/report/NumberCards.vue'
import LoadingSkeleton from '@/components/report/LoadingSkeleton.vue'

const route = useRoute()
const report = ref(null)
const rows = ref([])
const loading = ref(true)
const refreshing = ref(false)
const filterLoading = ref(false)
const error = ref('')
const reportRef = ref(null)

const updateTime = computed(() => new Date().toLocaleString('zh-CN'))

onMounted(async () => {
  const token = route.params.shareToken
  if (!token) {
    error.value = '无效的报表链接'
    loading.value = false
    return
  }

  try {
    const res = await viewReport(token)
    report.value = res.report
    rows.value = res.rows || []
  } catch (e) {
    error.value = '报表加载失败'
  } finally {
    loading.value = false
  }
})

/**
 * 筛选联动：替换SQL模板中的占位符，重新执行
 */
async function handleFilterChange(params) {
  if (!report.value?.sqlText) return
  filterLoading.value = true
  error.value = ''

  try {
    const res = await executeParamSql(report.value.sqlText, params)
    if (res.rows) {
      rows.value = res.rows
    }
  } catch (e) {
    error.value = '筛选查询失败'
  } finally {
    filterLoading.value = false
  }
}

/**
 * 刷新数据：重新执行当前报表SQL
 */
async function handleRefresh() {
  if (!report.value?.sqlText) return
  refreshing.value = true
  try {
    const res = await buildReportFromSql(report.value.name, report.value.sqlText)
    rows.value = res.sampleRows || []
  } catch (e) {
    error.value = '刷新失败'
  } finally {
    refreshing.value = false
  }
}

/**
 * PDF导出
 */
async function handleExportPdf() {
  if (!reportRef.value) return
  try {
    await exportToPdf(reportRef.value, report.value?.name || '报表')
  } catch (e) {
    error.value = 'PDF导出失败'
  }
}
</script>

<style scoped>
.view-header {
  display: flex; justify-content: space-between; align-items: flex-start;
  gap: var(--af-sp-4); margin-bottom: var(--af-sp-5); flex-wrap: wrap;
}
.view-actions { display: flex; gap: var(--af-sp-2); flex-shrink: 0; }
.report-footer { margin-top: var(--af-sp-4); font-size: var(--af-fs-xs); color: var(--af-text-3); text-align: right; }
.btn-icon {
  padding: 7px 14px; background: var(--af-bg-card); border: 1px solid var(--af-border);
  border-radius: var(--af-radius-sm); cursor: pointer; font-size: var(--af-fs-xs);
  color: var(--af-text-2); transition: all var(--af-transition);
}
.btn-icon:hover { background: var(--af-bg-hover); border-color: var(--el-color-primary-light-5); color: var(--af-primary); }
.btn-icon:disabled { cursor: not-allowed; opacity: 0.6; }
.error-msg {
  margin-top: var(--af-sp-3); padding: var(--af-sp-3) var(--af-sp-4);
  background: rgba(245, 63, 63, 0.08); color: var(--af-danger);
  border-radius: var(--af-radius-sm); border-left: 3px solid var(--af-danger);
  font-size: var(--af-fs-sm);
}
@media (max-width: 768px) {
  .view-header { flex-direction: column; gap: var(--af-sp-3); }
}
</style>
