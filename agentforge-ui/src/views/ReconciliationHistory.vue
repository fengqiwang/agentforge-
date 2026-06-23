<template>
  <div class="af-page af-page--wide">
    <div class="af-page-head">
      <div class="af-page-head__main">
        <h2 class="af-page-title">对账历史记录</h2>
        <p class="af-page-desc">管理对账批次与全局匹配规则</p>
      </div>
      <div class="af-page-head__actions">
        <el-button type="primary" @click="$router.push('/reconciliation/upload')">+ 新对账</el-button>
        <el-button @click="$router.push('/chat')">返回对话</el-button>
      </div>
    </div>

    <el-card class="list-card">
      <el-table :data="batches" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="批次名称" min-width="160" />
        <el-table-column prop="source_file" label="源文件" min-width="180" show-overflow-tooltip />
        <el-table-column prop="match_strategy" label="策略" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ row.match_strategy }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="匹配情况" width="160">
          <template #default="{ row }">
            <span class="num-match">{{ row.matched_count }}</span>
            <span class="sep"> / </span>
            <span>{{ row.total_external }}</span>
          </template>
        </el-table-column>
        <el-table-column label="差异分布" width="220">
          <template #default="{ row }">
            <el-tag type="danger" size="small">金额差 {{ row.amount_diff_count }}</el-tag>
            <el-tag type="warning" size="small" style="margin-left:4px">仅我方 {{ row.only_internal_count }}</el-tag>
            <el-tag type="warning" size="small" style="margin-left:4px">三方 {{ row.only_external_count }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="diff_amount" label="差异总额" width="130">
          <template #default="{ row }">
            <span :class="{ 'diff-red': row.diff_amount && Number(row.diff_amount) > 0 }">
              ¥ {{ fmt(row.diff_amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="160" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="view(row.id)">查看</el-button>
            <el-button size="small" type="danger" link @click="remove(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 规则配置 -->
    <el-card class="rules-card">
      <template #header>
        <div class="card-header">
          <span>规则配置</span>
          <el-button size="small" type="primary" :loading="savingRules" @click="saveRules">保存</el-button>
        </div>
      </template>
      <el-form :model="rules" label-width="160px" label-position="right">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="默认匹配策略">
              <el-select v-model="rules.defaultStrategy" style="width:100%">
                <el-option label="模糊匹配" value="FUZZY" />
                <el-option label="精确匹配" value="EXACT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="金额容差（元）">
              <el-input-number v-model="rules.amountTolerance" :precision="2" :step="0.5" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="告警阈值（元）">
              <el-input-number v-model="rules.alertThreshold" :precision="2" :step="100" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最大记录数">
              <el-input-number v-model="rules.maxRecords" :step="1000" :min="100" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const batches = ref([])
const loading = ref(false)
const savingRules = ref(false)

const rules = reactive({
  defaultStrategy: 'FUZZY',
  amountTolerance: 1.00,
  alertThreshold: 1000.00,
  maxRecords: 5000
})

onMounted(() => {
  loadBatches()
  loadRules()
})

async function loadBatches() {
  loading.value = true
  try {
    const data = await request.get('/reconciliation/list')
    batches.value = data || []
  } catch (e) {
    ElMessage.error('加载列表失败')
  } finally {
    loading.value = false
  }
}

async function loadRules() {
  try {
    const data = await request.get('/reconciliation/rules')
    Object.assign(rules, data)
  } catch (e) {
    // 静默
  }
}

async function saveRules() {
  savingRules.value = true
  try {
    await request.put('/reconciliation/rules', rules)
    ElMessage.success('规则已更新')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    savingRules.value = false
  }
}

function view(id) {
  router.push(`/reconciliation/result/${id}`)
}

async function remove(id) {
  try {
    await ElMessageBox.confirm('确认删除该对账批次？明细也会一并删除', '提示', { type: 'warning' })
    await request.delete(`/reconciliation/${id}`)
    ElMessage.success('已删除')
    loadBatches()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

function fmt(v) {
  if (v == null || v === '') return '0.00'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function statusColor(s) {
  return { COMPLETED: 'success', PROCESSING: 'warning', FAILED: 'danger' }[s] || ''
}
</script>

<style scoped>
.list-card { margin-bottom: var(--af-sp-5); }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; color: var(--af-text-1); }
.rules-card { margin-top: var(--af-sp-5); }
.num-match { color: var(--af-success); font-weight: 700; font-family: var(--af-font-mono); }
.sep { color: var(--af-text-3); margin: 0 2px; }
.diff-red { color: var(--af-danger); font-weight: 700; font-family: var(--af-font-mono); }
</style>
