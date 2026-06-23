<template>
  <div class="af-page af-page--wide">
    <div class="af-page-head">
      <div class="af-page-head__main">
        <h2 class="af-page-title">监控面板</h2>
        <p class="af-page-desc">实时观测系统运行指标与基础设施状态</p>
      </div>
      <div class="af-page-head__actions">
        <el-button @click="refresh" :loading="loading">刷新</el-button>
        <el-button @click="$router.push('/chat')">返回对话</el-button>
      </div>
    </div>

    <!-- 顶部指标卡 -->
    <el-row :gutter="20" class="kpi-row" v-loading="loading">
      <el-col :span="6">
        <div class="af-kpi" :class="promUp ? 'af-kpi--positive' : 'af-kpi--negative'">
          <div class="af-kpi-label">Prometheus</div>
          <div class="af-kpi-value" :style="{ color: promUp ? 'var(--af-status-positive)' : 'var(--af-status-negative)' }">
            {{ promUp ? 'UP' : 'DOWN' }}
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="af-kpi af-kpi--primary">
          <div class="af-kpi-label">Pipeline 执行</div>
          <div class="af-kpi-value">{{ metrics['agentforge_pipeline_total_count'] || 0 }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="af-kpi af-kpi--primary">
          <div class="af-kpi-label">LLM 调用</div>
          <div class="af-kpi-value">{{ metrics['agentforge_llm_call_total_count'] || 0 }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="af-kpi af-kpi--primary">
          <div class="af-kpi-label">缓存命中率</div>
          <div class="af-kpi-value">{{ cacheHitLabel }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 指标详情 -->
    <el-row :gutter="20" class="kpi-row">
      <el-col :span="12">
        <el-card>
          <template #header>📊 性能指标</template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="Pipeline 平均耗时">{{ fmtMs(metrics['agentforge_report_generation_duration_seconds_mean_ms']) }}</el-descriptions-item>
            <el-descriptions-item label="LLM 平均耗时">{{ fmtMs(metrics['agentforge_llm_call_duration_seconds_mean_ms']) }}</el-descriptions-item>
            <el-descriptions-item label="Pipeline 总次数">{{ metrics['agentforge_pipeline_total_count'] || 0 }}</el-descriptions-item>
            <el-descriptions-item label="LLM 总次数">{{ metrics['agentforge_llm_call_total_count'] || 0 }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>💾 缓存统计</template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="命中">{{ cacheStats.hits || 0 }}</el-descriptions-item>
            <el-descriptions-item label="未命中">{{ cacheStats.misses || 0 }}</el-descriptions-item>
            <el-descriptions-item label="总请求">{{ cacheStats.total || 0 }}</el-descriptions-item>
            <el-descriptions-item label="命中率">{{ cacheHitLabel }}</el-descriptions-item>
          </el-descriptions>
          <el-progress :percentage="cachePercent" :stroke-width="10" class="cache-bar" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 外部链接 -->
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card>
          <template #header>Prometheus</template>
          <el-link :href="config.prometheusUrl" target="_blank" type="primary">打开 Prometheus →</el-link>
          <p class="link-meta">{{ config.prometheusUrl }}</p>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>Grafana</template>
          <el-link :href="config.grafanaUrl" target="_blank" type="primary">打开 Grafana Dashboard →</el-link>
          <p class="link-meta">admin / admin</p>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>原始指标</template>
          <el-link href="/actuator/prometheus" target="_blank" type="primary">Prometheus 端点 →</el-link>
          <p class="link-meta">/actuator/prometheus</p>
        </el-card>
      </el-col>
    </el-row>

    <!-- Grafana iframe (需要 Grafana 允许跨域嵌入，默认不允许则只显示链接) -->
    <el-card v-if="false">
      <template #header>📈 Grafana 面板</template>
      <iframe :src="config.grafanaUrl + '/d/agentforge/agentforgejian-kong?kiosk=tv'" class="grafana-frame" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '@/utils/request'

const config = ref({})
const loading = ref(false)
const promUp = ref(false)
const metrics = ref({})
const cacheStats = ref({})

async function loadConfig() {
  try { config.value = await request.get('/config') || {} }
  catch { /* 失败用默认空 */ }
}

const cacheHitLabel = computed(() => {
  const h = cacheStats.value.hits || 0
  const m = cacheStats.value.misses || 0
  const t = h + m
  return t === 0 ? '0%' : Math.round(h * 100 / t) + '%'
})

const cachePercent = computed(() => {
  const h = cacheStats.value.hits || 0
  const m = cacheStats.value.misses || 0
  const t = h + m
  return t === 0 ? 0 : Math.round(h * 100 / t)
})

onMounted(() => { loadConfig(); refresh() })

async function refresh() {
  loadConfig()
  loading.value = true
  try {
    const [m, c] = await Promise.all([
      request.get('/monitor/metrics-summary').catch(() => ({})),
      request.get('/monitor/cache-stats').catch(() => ({})),
      fetch('/actuator/health').then(r => r.json()).catch(() => ({ status: 'DOWN' }))
    ])
    metrics.value = m || {}
    cacheStats.value = c || {}
    promUp.value = true
  } catch {
    promUp.value = false
  } finally {
    loading.value = false
  }
}

function fmtMs(v) {
  if (v == null) return '-'
  const n = Number(v)
  return n >= 1000 ? (n / 1000).toFixed(1) + 's' : n.toFixed(0) + 'ms'
}
</script>

<style scoped>
.kpi-row { margin-bottom: var(--af-sp-5); }
.cache-bar { margin-top: var(--af-sp-3); }
.link-meta { color: var(--af-text-3); font-size: var(--af-fs-xs); margin-top: var(--af-sp-2); }
.grafana-frame { width: 100%; height: 600px; border: 0; }
</style>
