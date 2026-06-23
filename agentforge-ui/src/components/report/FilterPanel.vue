<template>
  <div v-if="filters && filters.length" class="filter-panel">
    <div v-for="filter in filters" :key="filter.field" class="filter-item">
      <label class="filter-label">{{ filter.label }}</label>

      <template v-if="filter.type === 'daterange'">
        <input type="date" v-model="filterValues[filter.field + '_start']"
               class="filter-input" @change="emitChange" />
        <span class="filter-sep">~</span>
        <input type="date" v-model="filterValues[filter.field + '_end']"
               class="filter-input" @change="emitChange" />
      </template>

      <template v-else-if="filter.type === 'select'">
        <select v-model="filterValues[filter.field]" class="filter-select" @change="emitChange">
          <option value="">全部</option>
          <option v-for="opt in filter.options" :key="opt" :value="opt">{{ opt }}</option>
        </select>
      </template>

      <template v-else>
        <input type="text" v-model="filterValues[filter.field]"
               :placeholder="'请输入' + filter.label"
               class="filter-input" @keyup.enter="emitChange" />
      </template>
    </div>

    <button class="filter-btn filter-btn-primary" @click="emitChange">
      <span v-if="loading" class="btn-loading"></span>
      查询
    </button>
    <button v-if="hasChange" class="filter-btn filter-btn-reset" @click="handleReset">
      重置
    </button>
  </div>
</template>

<script setup>
import { reactive, ref, watch, computed } from 'vue'

const props = defineProps({
  filters: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['change'])

const filterValues = reactive({})
const initialValues = reactive({})
const hasChange = ref(false)

// 初始化默认值
watch(() => props.filters, (filters) => {
  filters.forEach(f => {
    if (f.defaultValue && !filterValues[f.field]) {
      filterValues[f.field] = f.defaultValue
      initialValues[f.field] = f.defaultValue
    }
  })
  hasChange.value = false
}, { immediate: true })

// 监听变化
watch(filterValues, () => {
  hasChange.value = Object.keys(filterValues).some(
    k => filterValues[k] !== initialValues[k]
  )
}, { deep: true })

function emitChange() {
  emit('change', { ...filterValues })
}

function handleReset() {
  Object.keys(initialValues).forEach(k => {
    filterValues[k] = initialValues[k]
  })
  // 清空没有初始值的字段
  Object.keys(filterValues).forEach(k => {
    if (!(k in initialValues)) {
      filterValues[k] = ''
    }
  })
  hasChange.value = false
  emitChange()
}
</script>

<style scoped>
.filter-panel {
  display: flex;
  flex-wrap: wrap;
  gap: var(--af-sp-4);
  align-items: center;
  background: var(--af-bg-card);
  padding: var(--af-sp-4);
  border-radius: var(--af-radius-md);
  box-shadow: var(--af-shadow-sm);
  margin-bottom: var(--af-sp-5);
}
.filter-item {
  display: flex;
  align-items: center;
  gap: var(--af-sp-2);
}
.filter-label {
  font-size: 14px;
  color: var(--af-text-2);
  white-space: nowrap;
}
.filter-input, .filter-select {
  padding: 6px 12px;
  border: 1px solid var(--af-border);
  border-radius: var(--af-radius-sm);
  font-size: 14px;
  outline: none;
  min-width: 140px;
  color: var(--af-text-1);
  background: var(--af-bg-card);
  transition: border-color var(--af-transition), box-shadow var(--af-transition);
}
.filter-input:hover, .filter-select:hover { border-color: var(--el-color-primary-light-5); }
.filter-input:focus, .filter-select:focus {
  border-color: var(--af-primary);
  box-shadow: var(--af-shadow-focus);
}
.filter-sep {
  color: var(--af-text-3);
}
.filter-btn {
  padding: 7px 20px;
  border: none;
  border-radius: var(--af-radius-sm);
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all var(--af-transition);
}
.filter-btn-primary {
  background-image: var(--af-primary-grad);
  color: #fff;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.24);
}
.filter-btn-primary:hover {
  background-image: linear-gradient(135deg, #4096ff 0%, #69b1ff 100%);
  box-shadow: 0 4px 14px rgba(22, 119, 255, 0.32);
  transform: translateY(-1px);
}
.filter-btn-reset {
  background: var(--af-bg-card);
  color: var(--af-text-2);
  border: 1px solid var(--af-border);
}
.filter-btn-reset:hover {
  background: var(--af-bg-hover);
  border-color: var(--el-color-primary-light-5);
  color: var(--af-primary);
}
.btn-loading {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 768px) {
  .filter-panel {
    flex-direction: column;
    align-items: stretch;
  }
  .filter-item {
    flex-wrap: wrap;
  }
}
</style>
