<template>
  <div class="af-page af-page--narrow">
    <div class="af-page-head">
      <div class="af-page-head__main">
        <h2 class="af-page-title">业务报告</h2>
        <p class="af-page-desc">AI 生成的结构化业务分析报告</p>
      </div>
      <div class="af-page-head__actions">
        <el-button @click="$router.back()">← 返回</el-button>
        <el-button @click="exportHtml">导出 HTML</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <div v-loading="loading" class="report-paper">
      <!-- 标题区 -->
      <div class="report-header">
        <h1>{{ report.templateName || '业务报告' }}</h1>
        <div class="report-meta">
          <span>报告日期：{{ report.reportDate || '-' }}</span>
          <span>生成时间：{{ report.createdAt || '-' }}</span>
          <el-tag :type="statusColor(report.status)" size="small">{{ report.status || '-' }}</el-tag>
        </div>
      </div>

      <!-- AI 分析章节 -->
      <div v-if="aiSections.length" class="report-body">
        <div v-for="(section, idx) in aiSections" :key="idx" class="report-section">
          <h2>
            {{ section.title }}
            <span class="chart-type">[{{ section.chartType || 'table' }}]</span>
          </h2>
          <p class="analysis-text">{{ section.content }}</p>

          <!-- 图表 -->
          <div v-if="section.chartData" class="chart-area">
            <div :ref="el => setRef(el, idx)" class="chart-dom"></div>
          </div>

          <!-- 数据表格 -->
          <el-table
            v-if="getSectionRows(idx)"
            :data="getSectionRows(idx)"
            stripe size="small"
            class="section-table">
            <el-table-column
              v-for="col in cols(getSectionRows(idx))"
              :key="col" :prop="col" :label="col" />
          </el-table>
        </div>
      </div>

      <!-- 无 AI 分析：直接展示原始数据 -->
      <div v-else-if="report.dataResult && Object.keys(report.dataResult).length" class="report-body">
        <div v-for="(result, name) in report.dataResult" :key="name" class="report-section">
          <h2>{{ name }}</h2>
          <p class="analysis-text muted">记录数：{{ result.count }}</p>
          <el-table :data="result.rows" stripe size="small" class="section-table">
            <el-table-column
              v-for="col in cols(result.rows)"
              :key="col" :prop="col" :label="col" />
          </el-table>
        </div>
      </div>

      <el-empty v-else description="暂无数据" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import * as echarts from 'echarts'

const route = useRoute()
const report = ref({})
const loading = ref(true)
const chartRefs = new Map()
const chartInstances = []

const aiSections = computed(() => report.value.aiAnalysis?.sections || [])

onMounted(async () => {
  await load()
})

onBeforeUnmount(() => {
  chartInstances.forEach(c => c.dispose())
})

async function load() {
  loading.value = true
  try {
    report.value = await request.get(`/admin/report-template/reports/${route.params.id}/full`)
    await nextTick()
    renderCharts()
  } catch (e) {
    ElMessage.error('加载报告失败')
  } finally {
    loading.value = false
  }
}

function setRef(el, idx) {
  if (el) chartRefs.set(idx, el)
}

function renderCharts() {
  aiSections.value.forEach((section, idx) => {
    const dom = chartRefs.get(idx)
    if (!dom || !section.chartData) return
    const chart = echarts.init(dom)
    chartInstances.push(chart)
    chart.setOption(buildOption(section))
  })
}

function buildOption(section) {
  const type = (section.chartType || 'bar').toLowerCase()
  const data = section.chartData || {}
  const labels = data.labels || []
  const values = data.values || []
  const colors = ['#1677ff', '#7b61ff', '#36cfc9', '#00b42a', '#ff7d00']
  const axisTheme = {
    axisLine: { lineStyle: { color: '#e5e6eb' } },
    axisTick: { show: false },
    axisLabel: { color: '#86909c', fontSize: 12 },
    splitLine: { lineStyle: { color: '#f0f2f5', type: 'dashed' } }
  }
  const tooltip = {
    backgroundColor: 'rgba(255,255,255,0.96)', borderColor: '#e5e6eb', borderWidth: 1,
    padding: [8, 12], textStyle: { color: '#1d2129', fontSize: 13 },
    extraCssText: 'box-shadow: 0 4px 16px rgba(29,33,41,0.10); border-radius: 8px;'
  }

  // number_card 用大数字仪表盘模拟
  if (type === 'number_card') {
    const num = values[0] ?? 0
    return {
      series: [{
        type: 'gauge', startAngle: 90, endAngle: -270,
        radius: '90%', pointer: { show: false },
        progress: { show: true, overlap: false, roundCap: true, clip: false, itemStyle: { color: '#1677ff' } },
        axisLine: { lineStyle: { width: 20, color: [[1, '#f0f2f5']] } },
        splitLine: { show: false }, axisTick: { show: false }, axisLabel: { show: false },
        data: [{ value: num, name: section.title,
          title: { fontSize: 14, color: '#86909c' },
          detail: { formatter: '{value}', fontSize: 32, color: '#1d2129' } }]
      }]
    }
  }

  if (type === 'pie') {
    return {
      color: colors,
      tooltip: { ...tooltip, trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: '#4e5969', fontSize: 12 } },
      series: [{
        type: 'pie', radius: ['45%', '72%'],
        itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 6 },
        label: { formatter: '{b}: {c} ({d}%)', color: '#4e5969', fontSize: 12 },
        data: labels.map((l, i) => ({ name: l, value: values[i] }))
      }]
    }
  }

  // bar / line / table
  const c = colors[0]
  const seriesBase = { type: type === 'line' ? 'line' : 'bar', data: values }
  if (type === 'line') {
    Object.assign(seriesBase, {
      smooth: true, symbol: 'circle', symbolSize: 6,
      lineStyle: { color: c, width: 2.5 }, itemStyle: { color: c },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [{ offset: 0, color: c + '40' }, { offset: 1, color: c + '03' }] } }
    })
  } else {
    Object.assign(seriesBase, { barMaxWidth: 36, itemStyle: { color: c, borderRadius: [6, 6, 0, 0] } })
  }
  return {
    color: colors,
    tooltip: { ...tooltip, trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: labels, ...axisTheme,
      axisLabel: { color: '#86909c', fontSize: 12, rotate: labels.length > 6 ? 30 : 0 } },
    yAxis: { type: 'value', ...axisTheme },
    series: [seriesBase]
  }
}

// 用章节标题匹配 dataResult 中的查询名，取表格数据
function getSectionRows(idx) {
  const section = aiSections.value[idx]
  if (!section || !report.value.dataResult) return null
  for (const [name, result] of Object.entries(report.value.dataResult)) {
    if (section.title.includes(name) || name.includes(section.title.slice(0, 4))) {
      return (result.rows || []).slice(0, 20)
    }
  }
  return null
}

function cols(rows) {
  if (!rows || !rows.length) return []
  return Object.keys(rows[0])
}

function statusColor(s) {
  return { COMPLETED: 'success', FAILED: 'danger', GENERATING: 'warning' }[s] || ''
}

function exportHtml() {
  window.open(`/api/admin/report-template/reports/${route.params.id}/export/html`)
}
</script>

<style scoped>
.report-paper {
  background: var(--af-bg-card);
  padding: var(--af-sp-7) var(--af-sp-6);
  box-shadow: var(--af-shadow-md);
  border: 1px solid var(--af-border-light);
  border-radius: var(--af-radius-lg);
}
.report-header { text-align: center; border-bottom: 2px solid transparent; padding-bottom: var(--af-sp-5); margin-bottom: var(--af-sp-6);
  background-image: linear-gradient(var(--af-bg-card), var(--af-bg-card)), var(--af-primary-grad);
  background-origin: border-box; background-clip: padding-box, border-box;
  border-bottom: 2px solid transparent;
  position: relative;
}
.report-header::after {
  content: ''; position: absolute; left: 20%; right: 20%; bottom: -2px; height: 2px;
  background: var(--af-primary-grad);
}
.report-header h1 { font-size: var(--af-fs-xl); margin: 0; color: var(--af-text-1); font-weight: 700; }
.report-meta { color: var(--af-text-3); font-size: var(--af-fs-xs); margin-top: var(--af-sp-2); display: flex; gap: var(--af-sp-4); justify-content: center; align-items: center; flex-wrap: wrap; }
.report-section { margin-bottom: var(--af-sp-6); }
.report-section h2 {
  font-size: var(--af-fs-md); color: var(--af-text-1);
  border-left: 4px solid; border-image: var(--af-primary-grad) 1;
  padding-left: var(--af-sp-3); margin-bottom: var(--af-sp-3);
}
.chart-type { font-size: var(--af-fs-xs); color: var(--af-primary); font-weight: normal; }
.analysis-text { color: var(--af-text-2); line-height: 1.8; font-size: var(--af-fs-sm); text-indent: 2em; }
.analysis-text.muted { color: var(--af-text-3); }
.chart-area { margin: var(--af-sp-4) 0; }
.chart-dom { width: 100%; height: 340px; }
.section-table { margin-top: var(--af-sp-3); }
</style>
