<template>
  <div class="af-page af-page--wide">
    <div class="af-page-head">
      <div class="af-page-head__main">
        <h2 class="af-page-title">管理后台</h2>
        <p class="af-page-desc">Schema 索引管理、业务报告与系统状态总览</p>
      </div>
      <div class="af-page-head__actions">
        <el-button @click="$router.push('/chat')">返回对话</el-button>
      </div>
    </div>

    <el-tabs v-model="tab" @tab-change="onTabChange">
      <!-- ===== Schema 管理 ===== -->
      <el-tab-pane label="Schema 管理" name="schema">
        <el-alert :title="schemaMsg" :type="schemaMsgType" :closable="false" v-if="schemaMsg" class="block-gap" />
        <el-space class="block-gap">
          <el-button type="primary" @click="reindexAll()" :loading="reindexing">全量重建索引</el-button>
          <el-button @click="detectChanges()">检测变更</el-button>
          <el-button @click="syncSchema()" :loading="syncing">同步快照</el-button>
        </el-space>

        <el-card>
          <el-table :data="schemaTables" stripe v-loading="schemaLoading">
            <el-table-column prop="table_name" label="表名" min-width="180" />
            <el-table-column prop="table_comment" label="注释" min-width="140" show-overflow-tooltip />
            <el-table-column prop="column_count" label="列数" width="80" />
            <el-table-column prop="index_status" label="索引状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.index_status === 1 ? 'success' : 'warning'" size="small">
                  {{ row.index_status === 1 ? '已索引' : '待索引' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updated_at" label="更新时间" width="160" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button size="small" link @click="reindexTable(row.table_name)">重建索引</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 变更检测结果 -->
        <el-card v-if="diffs.length" class="block-gap-top">
          <template #header>变更检测结果</template>
          <el-table :data="diffs" stripe size="small">
            <el-table-column prop="tableName" label="表名" />
            <el-table-column prop="changeType" label="变更类型" width="140">
              <template #default="{ row }">
                <el-tag :type="diffColor(row.changeType)" size="small">{{ row.changeType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="currentColumnCount" label="当前列数" width="90" />
            <el-table-column prop="storedColumnCount" label="快照列数" width="90" />
            <el-table-column prop="detail" label="详情" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ===== 业务报告 ===== -->
      <el-tab-pane label="业务报告" name="report">
        <el-card>
          <el-empty description="请前往业务报告页面管理模板和执行历史">
            <el-button type="primary" @click="$router.push('/business-report')">前往业务报告</el-button>
          </el-empty>
        </el-card>
      </el-tab-pane>

      <!-- ===== 系统状态 ===== -->
      <el-tab-pane label="系统状态" name="system">
        <el-row :gutter="20" v-loading="sysLoading" class="block-gap">
          <el-col :span="8">
            <div class="af-kpi" :class="healthOk ? 'af-kpi--positive' : 'af-kpi--negative'">
              <div class="af-kpi-label">后端状态</div>
              <div class="af-kpi-value" :style="{ color: healthOk ? 'var(--af-status-positive)' : 'var(--af-status-negative)' }">
                {{ healthOk ? 'UP' : 'DOWN' }}
              </div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="af-kpi af-kpi--primary">
              <div class="af-kpi-label">Pipeline 执行</div>
              <div class="af-kpi-value">{{ sysMetrics.pipeline || 0 }}</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="af-kpi af-kpi--primary">
              <div class="af-kpi-label">LLM 调用</div>
              <div class="af-kpi-value">{{ sysMetrics.llm || 0 }}</div>
            </div>
          </el-col>
        </el-row>

        <el-card class="block-gap-top">
          <template #header>核心组件</template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="Prometheus">
              <el-link :href="config.prometheusUrl" target="_blank" type="primary">{{ config.prometheusUrl }}</el-link>
            </el-descriptions-item>
            <el-descriptions-item label="Grafana">
              <el-link :href="config.grafanaUrl" target="_blank" type="primary">{{ config.grafanaUrl }}</el-link>
            </el-descriptions-item>
            <el-descriptions-item label="Redis">
              <el-tag type="success" size="small">{{ config.redisHost }}:{{ config.redisPort }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="MySQL">
              <el-tag type="success" size="small">{{ mysqlHost }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="ChromaDB">
              <el-tag type="success" size="small">{{ config.chromadbUrl }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="LLM API">
              <el-tag size="small">DeepSeek v4-flash</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card class="block-gap-top">
          <template #header>指标端点</template>
          <el-space>
            <el-link href="/actuator/health" target="_blank" type="primary">Health</el-link>
            <el-link href="/actuator/prometheus" target="_blank" type="primary">Prometheus</el-link>
            <el-link href="/actuator/metrics" target="_blank" type="primary">Metrics JSON</el-link>
          </el-space>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const tab = ref('schema')
const schemaTables = ref([])
const schemaLoading = ref(false)
const schemaMsg = ref('')
const schemaMsgType = ref('info')
const reindexing = ref(false)
const syncing = ref(false)
const diffs = ref([])

const sysLoading = ref(false)
const healthOk = ref(false)
const sysMetrics = ref({ pipeline: 0, llm: 0 })
const config = ref({})

const mysqlHost = computed(() => {
  const url = config.value.mysqlJdbcUrl || ''
  const m = url.match(/mysql:\/\/([^:/]+:\d+)/)
  return m ? m[1] : ''
})

async function loadConfig() {
  try { config.value = await request.get('/config') || {} }
  catch { /* 失败用默认空 */ }
}

function onTabChange(name) {
  if (name === 'schema') loadSchema()
  else if (name === 'system') { loadConfig(); loadSystem() }
}

// ========== Schema 管理 ==========
async function loadSchema() {
  schemaLoading.value = true
  try {
    schemaTables.value = (await request.get('/admin/schema/tables')) || []
  } catch { schemaTables.value = [] }
  finally { schemaLoading.value = false }
}

async function reindexAll() {
  reindexing.value = true
  try {
    const r = await request.post('/admin/schema/reindex')
    schemaMsg.value = r.message
    schemaMsgType.value = 'success'
    loadSchema()
  } catch (e) {
    schemaMsg.value = '重建失败'
    schemaMsgType.value = 'error'
  } finally { reindexing.value = false }
}

async function reindexTable(tableName) {
  try {
    const r = await request.post(`/admin/schema/reindex/${tableName}`)
    ElMessage.success(r.message)
    loadSchema()
  } catch (e) {
    ElMessage.error('重建失败: ' + tableName)
  }
}

async function detectChanges() {
  try {
    const r = await request.get('/admin/schema/detect')
    diffs.value = r.diffs || []
    schemaMsg.value = r.inSync ? '无变更，快照与当前库一致' : `发现 ${r.changedCount} 处变更`
    schemaMsgType.value = r.inSync ? 'success' : 'warning'
  } catch {
    ElMessage.error('检测失败')
  }
}

async function syncSchema() {
  syncing.value = true
  try {
    const r = await request.post('/admin/schema/sync')
    ElMessage.success(`已同步 ${r.syncedTables} 张表`)
    diffs.value = []
    loadSchema()
  } catch {
    ElMessage.error('同步失败')
  } finally { syncing.value = false }
}

function diffColor(type) {
  return { ADDED: 'success', REMOVED: 'danger', COLUMN_CHANGED: 'warning' }[type] || 'info'
}

// ========== 系统状态 ==========
async function loadSystem() {
  sysLoading.value = true
  try {
    const [health, metrics] = await Promise.all([
      fetch('/actuator/health').then(r => r.json()).catch(() => ({ status: 'DOWN' })),
      request.get('/monitor/metrics-summary').catch(() => ({}))
    ])
    healthOk.value = health.status === 'UP'
    sysMetrics.value = {
      pipeline: metrics['agentforge_pipeline_total_count'] || 0,
      llm: metrics['agentforge_llm_call_total_count'] || 0
    }
  } catch { healthOk.value = false }
  finally { sysLoading.value = false }
}

// 默认加载 schema
loadSchema()
</script>

<style scoped>
.block-gap { margin-bottom: var(--af-sp-4); display: flex; }
.block-gap-top { margin-top: var(--af-sp-5); }
</style>
