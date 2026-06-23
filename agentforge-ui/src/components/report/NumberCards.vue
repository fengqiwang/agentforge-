<template>
  <div class="number-cards">
    <div v-for="card in cards" :key="card.field" class="number-card">
      <div class="card-label">{{ card.label }}</div>
      <div class="card-value">
        {{ formatValue(card) }}
        <span class="card-unit">{{ card.unit }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  cards: { type: Array, default: () => [] },
  data: { type: Object, default: () => ({}) }
})

function formatValue(card) {
  const raw = props.data[card.field]
  if (raw == null) return '-'

  const num = Number(raw)
  if (isNaN(num)) return raw

  if (card.format === 'money') {
    if (Math.abs(num) >= 100000000) return (num / 100000000).toFixed(2)
    if (Math.abs(num) >= 10000) return (num / 10000).toFixed(2)
    return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  }
  if (card.format === 'percent') {
    return num.toFixed(2) + '%'
  }
  return num.toLocaleString('zh-CN')
}
</script>

<style scoped>
.number-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}
.number-card {
  background: var(--af-bg-card);
  border-radius: var(--af-radius-md);
  border: 1px solid var(--af-border-light);
  padding: var(--af-sp-4) var(--af-sp-5);
  box-shadow: var(--af-shadow-sm);
  text-align: left;
  transition: box-shadow var(--af-transition), transform var(--af-transition), border-color var(--af-transition);
  position: relative;
  overflow: hidden;
}
.number-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: var(--af-primary-grad);
}
.number-card:hover {
  box-shadow: var(--af-shadow-md);
  transform: translateY(-2px);
  border-color: var(--el-color-primary-light-7);
}
.card-label {
  font-size: var(--af-fs-xs);
  color: var(--af-text-3);
  margin-bottom: var(--af-sp-2);
  letter-spacing: 0.02em;
  text-align: left;
}
.card-value {
  font-size: var(--af-fs-xl);
  font-weight: 600;
  color: var(--af-text-1);
  font-family: var(--af-font-mono);
  text-align: left;
  line-height: 1.2;
}
.card-unit {
  font-size: var(--af-fs-sm);
  font-weight: 400;
  color: var(--af-text-3);
  margin-left: var(--af-sp-1);
}
</style>
