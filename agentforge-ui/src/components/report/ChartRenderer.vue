<template>
  <div v-if="chartConfig && chartConfig.type !== 'number_card'" class="chart-container">
    <h3 v-if="chartConfig.title" class="chart-title">{{ chartConfig.title }}</h3>
    <div ref="chartRef" :style="{ height: chartHeight }"></div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  chartConfig: { type: Object, default: null },
  rows: { type: Array, default: () => [] }
})

const chartRef = ref(null)
let chartInstance = null
let resizeHandler = null

const chartHeight = computed(() => {
  if (!props.chartConfig) return '300px'
  if (props.chartConfig.type === 'pie') return '350px'
  return '350px'
})

onMounted(() => {
  if (props.chartConfig && props.chartConfig.type !== 'number_card') {
    nextTick(() => renderChart())
  }
})

onUnmounted(() => {
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
  }
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})

watch(() => [props.chartConfig, props.rows], () => {
  if (props.chartConfig && props.chartConfig.type !== 'number_card') {
    nextTick(() => renderChart())
  }
}, { deep: true })

function renderChart() {
  if (!chartRef.value || !props.chartConfig) return

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
    resizeHandler = () => chartInstance?.resize()
    window.addEventListener('resize', resizeHandler)
  }

  const option = buildOption()
  chartInstance.setOption(option, true)
}

function buildOption() {
  const type = props.chartConfig.type
  const rows = props.rows || []

  // 智谱风配色：深蓝主 + 蓝紫辅 + 数据语义色
  const colors = ['#1677ff', '#7b61ff', '#36cfc9', '#00b42a', '#ff7d00',
    '#4096ff', '#9270ff', '#5ad8c4', '#23c343', '#ff9a2e']

  if (type === 'bar') return buildBarOption(rows, colors)
  if (type === 'line') return buildLineOption(rows, colors)
  if (type === 'pie') return buildPieOption(rows, colors)
  return {}
}

/* 统一质感：弱化轴线、虚线网格、玻璃卡 tooltip、圆角图例 */
function axisTheme() {
  return {
    axisLine: { lineStyle: { color: '#e5e6eb' } },
    axisTick: { show: false },
    axisLabel: { color: '#86909c', fontSize: 12 },
    splitLine: { lineStyle: { color: '#f0f2f5', type: 'dashed' } }
  }
}
function tooltipTheme(trigger) {
  return {
    trigger,
    backgroundColor: 'rgba(255,255,255,0.96)',
    borderColor: '#e5e6eb',
    borderWidth: 1,
    padding: [8, 12],
    textStyle: { color: '#1d2129', fontSize: 13 },
    extraCssText: 'box-shadow: 0 4px 16px rgba(29,33,41,0.10); border-radius: 8px;'
  }
}
function legendTheme(data) {
  return data ? {
    data, top: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 10,
    textStyle: { color: '#4e5969', fontSize: 12 }
  } : undefined
}

function buildBarOption(rows, colors) {
  const xField = props.chartConfig.xField
  const yFields = props.chartConfig.yFields || []
  const xData = rows.map(r => r[xField])

  const series = yFields.map((yField, index) => ({
    name: yField,
    type: 'bar',
    data: rows.map(r => Number(r[yField]) || 0),
    barMaxWidth: 36,
    itemStyle: { color: colors[index % colors.length], borderRadius: [6, 6, 0, 0] }
  }))

  return {
    color: colors,
    tooltip: tooltipTheme('axis'),
    legend: legendTheme(yFields.length > 1 ? yFields : undefined),
    grid: { left: '3%', right: '4%', bottom: '3%', top: yFields.length > 1 ? 40 : 16, containLabel: true },
    xAxis: { type: 'category', data: xData, ...axisTheme() },
    yAxis: { type: 'value', ...axisTheme() },
    series
  }
}

function buildLineOption(rows, colors) {
  const xField = props.chartConfig.xField
  const yFields = props.chartConfig.yFields || []
  const xData = rows.map(r => formatXAxis(r[xField]))

  const series = yFields.map((yField, index) => {
    const c = colors[index % colors.length]
    return {
      name: yField,
      type: 'line',
      data: rows.map(r => Number(r[yField]) || 0),
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { color: c, width: 2.5 },
      itemStyle: { color: c },
      areaStyle: {
        color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [{ offset: 0, color: c + '40' }, { offset: 1, color: c + '03' }] }
      }
    }
  })

  return {
    color: colors,
    tooltip: tooltipTheme('axis'),
    legend: legendTheme(yFields.length > 1 ? yFields : undefined),
    grid: { left: '3%', right: '4%', bottom: '3%', top: yFields.length > 1 ? 40 : 16, containLabel: true },
    xAxis: { type: 'category', data: xData, boundaryGap: false, ...axisTheme() },
    yAxis: { type: 'value', ...axisTheme() },
    series
  }
}

function buildPieOption(rows, colors) {
  const nameField = props.chartConfig.nameField
  const valueField = props.chartConfig.valueField

  const data = rows.map(r => ({
    name: r[nameField],
    value: Number(r[valueField]) || 0
  }))

  return {
    color: colors,
    tooltip: { ...tooltipTheme('item'), formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', left: 'left', icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: '#4e5969', fontSize: 12 } },
    series: [{
      type: 'pie',
      radius: ['45%', '72%'],
      center: ['58%', '50%'],
      avoidLabelOverlap: false,
      itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 6 },
      label: { show: true, formatter: '{b}: {d}%', color: '#4e5969', fontSize: 12 },
      data
    }]
  }
}

function formatXAxis(val) {
  if (val == null) return ''
  const str = String(val)
  if (str.length === 8 && /^\d{8}$/.test(str)) {
    return `${str.slice(0, 4)}-${str.slice(4, 6)}-${str.slice(6, 8)}`
  }
  return str
}
</script>

<style scoped>
.chart-container {
  background: var(--af-bg-card);
  border-radius: var(--af-radius-md);
  padding: var(--af-sp-4);
  box-shadow: var(--af-shadow-sm);
  margin-bottom: var(--af-sp-5);
  transition: box-shadow var(--af-transition);
}
.chart-container:hover { box-shadow: var(--af-shadow-md); }
.chart-title {
  font-size: var(--af-fs-md);
  font-weight: 600;
  color: var(--af-text-1);
  margin: 0 0 var(--af-sp-3) 0;
}
</style>
