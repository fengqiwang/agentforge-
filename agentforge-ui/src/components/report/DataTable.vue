<template>
  <div class="data-table-wrapper">
    <table class="data-table">
      <thead>
        <tr>
          <th v-for="col in visibleColumns" :key="col.field">
            {{ col.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, rowIndex) in rows" :key="rowIndex">
          <td v-for="col in visibleColumns" :key="col.field"
              :class="getCellClass(col)">
            {{ formatCell(row[col.field], col) }}
          </td>
        </tr>
      </tbody>
      <tfoot v-if="hasSummarize">
        <tr class="summary-row">
          <td v-for="(col, index) in visibleColumns" :key="col.field">
            <template v-if="index === 0">合计</template>
            <template v-else-if="col.summarize">
              {{ formatCell(summaryData[col.field], col) }}
            </template>
          </td>
        </tr>
      </tfoot>
    </table>
    <div class="table-footer">
      共 {{ rows.length }} 条数据
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  rows: { type: Array, default: () => [] },
  columns: { type: Array, default: () => [] }
})

const visibleColumns = computed(() => props.columns.filter(c => c.visible !== false))

const hasSummarize = computed(() => visibleColumns.value.some(c => c.summarize))

const summaryData = computed(() => {
  const summary = {}
  for (const col of visibleColumns.value) {
    if (!col.summarize) continue
    let sum = 0
    for (const row of props.rows) {
      const val = Number(row[col.field])
      if (!isNaN(val)) sum += val
    }
    summary[col.field] = sum
  }
  return summary
})

function formatCell(value, col) {
  if (value == null) return '-'
  const num = Number(value)
  if (isNaN(num)) return value

  switch (col.format) {
    case 'money':
      return formatMoney(num)
    case 'percent':
      return num.toFixed(2) + '%'
    case 'date':
      return formatDate(String(value))
    case 'number':
      return num.toLocaleString('zh-CN')
    default:
      return value
  }
}

function formatMoney(num) {
  if (Math.abs(num) >= 100000000) {
    return (num / 100000000).toFixed(2) + ' 亿'
  }
  if (Math.abs(num) >= 10000) {
    return (num / 10000).toFixed(2) + ' 万'
  }
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatDate(val) {
  if (val.length === 8 && /^\d{8}$/.test(val)) {
    return `${val.slice(0, 4)}-${val.slice(4, 6)}-${val.slice(6, 8)}`
  }
  return val
}

function getCellClass(col) {
  return {
    'cell-money': col.format === 'money',
    'cell-percent': col.format === 'percent',
    'cell-number': col.format === 'number',
    'cell-right': ['money', 'percent', 'number'].includes(col.format)
  }
}
</script>

<style scoped>
.data-table-wrapper {
  background: var(--af-bg-card);
  border-radius: var(--af-radius-md);
  border: 1px solid var(--af-border-light);
  box-shadow: var(--af-shadow-sm);
  overflow-x: auto;   /* 窄屏横向滚动，不撑破布局 */
}
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
.data-table th {
  background: var(--af-bg-soft);
  padding: 12px 16px;
  text-align: left;
  font-weight: 600;
  color: var(--af-text-1);
  border-bottom: 1px solid var(--af-border-light);
}
.data-table td {
  padding: 11px 16px;
  border-bottom: 1px solid var(--af-border-light);
  color: var(--af-text-2);
}
.data-table tr:last-child td { border-bottom: none; }
.data-table tr:hover td {
  background: var(--af-bg-hover);
}
.summary-row td {
  font-weight: 600;
  background: var(--af-bg-soft);
  color: var(--af-text-1);
}
.cell-right {
  text-align: right;
}
.cell-money {
  color: var(--af-warning);
  font-weight: 500;
}
.cell-percent {
  color: var(--af-success);
}
.table-footer {
  padding: 10px 16px;
  font-size: 13px;
  color: var(--af-text-3);
  border-top: 1px solid var(--af-border-light);
}
</style>
