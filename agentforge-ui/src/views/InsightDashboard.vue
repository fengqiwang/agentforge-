<template>
  <div class="af-page af-page--wide">
    <div class="af-page-head">
      <div class="af-page-head__main">
        <h2 class="af-page-title">市场洞察</h2>
        <p class="af-page-desc">AI 驱动的趋势识别、异常预警与市场机会分析</p>
      </div>
      <div class="af-page-head__actions">
        <span style="margin-right:8px;font-size:13px;color:var(--af-text-3)">时间范围：</span>
        <input type="date" v-model="dateStart" class="filter-input" style="width:140px" />
        <span style="margin:0 4px;color:var(--af-text-3)">~</span>
        <input type="date" v-model="dateEnd" class="filter-input" style="width:140px" />
        <el-button type="primary" @click="gen" :loading="genLoading" style="margin-left:12px">生成洞察</el-button>
        <el-button @click="$router.push('/chat')">返回对话</el-button>
      </div>
    </div>

    <el-alert v-if="insight.summary" :title="insight.summary" type="info" :closable="false" class="summary-alert" />

    <el-row :gutter="20" v-loading="loading">
      <el-col :span="8">
        <el-card>
          <template #header>📈 关键趋势</template>
          <div v-for="(t, i) in insight.trends || []" :key="i" class="card-item">
            <strong>{{ t.finding }}</strong>
            <p v-if="t.suggestion" class="suggestion">💡 {{ t.suggestion }}</p>
            <el-progress :percentage="Math.round((t.confidence||0)*100)" :stroke-width="6" />
          </div>
          <el-empty v-if="!(insight.trends||[]).length" description="无趋势" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>🚨 异常告警</template>
          <div v-for="(a, i) in insight.anomalies || []" :key="i" class="card-item card-item--row">
            <el-tag :type="sevColor(a.severity)" size="small">{{ a.severity }}</el-tag>
            <span class="anomaly-text">{{ a.finding }}</span>
          </div>
          <el-empty v-if="!(insight.anomalies||[]).length" description="无异常" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>🎯 市场机会</template>
          <div v-for="(o, i) in insight.opportunities || []" :key="i" class="card-item">
            <strong>{{ o.finding }}</strong>
            <p class="suggestion">💡 {{ o.suggestion }}</p>
            <el-tag size="small" type="success">{{ o.targetAudience }}</el-tag>
          </div>
          <el-empty v-if="!(insight.opportunities||[]).length" description="无机会" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <el-card class="block-gap-top">
      <template #header>洞察历史</template>
      <el-table :data="list" stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="periodType" label="周期" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ row.periodType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="periodStart" label="起始" width="110" />
        <el-table-column prop="periodEnd" label="结束" width="110" />
        <el-table-column prop="summary" label="总结" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="生成时间" width="160" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="loadDetail(row.id)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const insight = ref({})
const list = ref([])
const loading = ref(false)
const genLoading = ref(false)

// 默认最近3个月
const today = new Date()
const dateEnd = ref(today.toISOString().slice(0, 10))
const dateStart = ref(new Date(today.getFullYear(), today.getMonth() - 2, 1).toISOString().slice(0, 10))

onMounted(async () => {
  loading.value = true
  try {
    list.value = (await request.get('/insight/list')) || []
    if (list.value.length) {
      insight.value = await request.get(`/insight/${list.value[0].id}`)
    }
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
})

async function gen() {
  genLoading.value = true
  try {
    const { id } = await request.post('/insight/generate', {
      periodType: 'CUSTOM',
      start: dateStart.value,
      end: dateEnd.value
    })
    insight.value = await request.get(`/insight/${id}`)
    list.value = (await request.get('/insight/list')) || []
    ElMessage.success('洞察已生成')
  } catch (e) {
    ElMessage.error('生成失败：' + (e.response?.data?.message || e.message))
  } finally {
    genLoading.value = false
  }
}

async function loadDetail(id) {
  insight.value = await request.get(`/insight/${id}`)
}

function sevColor(s) {
  return { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }[s] || ''
}
</script>

<style scoped>
.summary-alert { margin-bottom: var(--af-sp-5); }
.block-gap-top { margin-top: var(--af-sp-5); }
.filter-input {
  border: 1px solid var(--af-border-light);
  border-radius: var(--af-radius-sm);
  padding: 4px 8px;
  font-size: var(--af-fs-sm);
  color: var(--af-text-1);
  background: var(--af-bg-1);
}
.card-item {
  margin-bottom: var(--af-sp-4);
  padding-bottom: var(--af-sp-3);
  border-bottom: 1px dashed var(--af-border-light);
}
.card-item:last-child { border-bottom: none; margin-bottom: 0; padding-bottom: 0; }
.card-item strong { font-size: var(--af-fs-sm); color: var(--af-text-1); display: block; margin-bottom: var(--af-sp-1); }
.card-item--row { display: flex; align-items: center; gap: var(--af-sp-2); }
.anomaly-text { font-size: var(--af-fs-sm); color: var(--af-text-2); }
.suggestion {
  color: var(--af-primary);
  background: var(--el-color-primary-light-9);
  padding: 6px 10px;
  border-radius: var(--af-radius-sm);
  margin: var(--af-sp-2) 0;
  font-size: var(--af-fs-xs);
}
</style>
