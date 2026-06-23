<template>
  <div class="skeleton-wrapper">
    <div class="skeleton-row" v-for="n in rows" :key="n">
      <div class="skeleton-block" :style="{ width: w }" v-for="w in widths" :key="w"></div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  rows: { type: Number, default: 5 }
})

const widths = ['40%', '25%', '20%', '15%']
</script>

<style scoped>
.skeleton-wrapper {
  background: var(--af-bg-card);
  border-radius: var(--af-radius-md);
  padding: var(--af-sp-5);
  box-shadow: var(--af-shadow-sm);
}
.skeleton-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}
.skeleton-row:last-child {
  margin-bottom: 0;
}
.skeleton-block {
  height: 16px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--af-bg-soft) 25%, var(--af-border-light) 50%, var(--af-bg-soft) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}
@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
