<template>
  <div class="af-page af-page--wide">
    <div class="af-page-head">
      <div class="af-page-head__main">
        <h2 class="af-page-title">业务报告</h2>
        <p class="af-page-desc">管理报告模板、执行历史与多期对比</p>
      </div>
      <div class="af-page-head__actions">
        <el-button @click="$router.push('/chat')">返回对话</el-button>
      </div>
    </div>

    <!-- 模板列表 -->
    <el-card style="margin-bottom:var(--af-sp-5)">
      <template #header>
        <div class="card-header">
          <span>报告模板</span>
          <el-button size="small" type="primary" @click="showCreate = true">+ 新建模板</el-button>
        </div>
      </template>
      <el-table :data="templates" stripe v-loading="loadingTpl">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="模板名称" min-width="140" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="查询数" width="90">
          <template #default="{ row }">{{ row.queries?.length || 0 }}</template>
        </el-table-column>
        <el-table-column label="定时" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.schedule" type="success" size="small">{{ row.schedule }}</el-tag>
            <span v-else class="muted-text">手动</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" :loading="executing === row.id"
                       @click="executeTemplate(row)">执行</el-button>
            <el-button size="small" @click="editTpl(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="removeTpl(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 报告历史 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>报告历史</span>
          <el-button size="small" @click="loadReports" :loading="loadingRpt">刷新</el-button>
        </div>
      </template>
      <el-table :data="reports" stripe v-loading="loadingRpt">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="templateName" label="模板" min-width="140" />
        <el-table-column prop="reportDate" label="报告日期" width="110" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="AI分析" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.aiAnalysis" type="success" size="small">已生成</el-tag>
            <el-tag v-else type="info" size="small">无</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="生成时间" width="170" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="$router.push(`/business-report/${row.id}`)">查看</el-button>
            <el-button size="small" @click="exportHtml(row.id)">导出HTML</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 对比区 -->
      <div class="compare-bar" v-if="reports.length >= 2">
        <span class="muted-text">报告对比：</span>
        <el-select v-model="compareId1" placeholder="选择报告1" size="small" style="width:140px">
          <el-option v-for="r in reports" :key="r.id" :label="`#${r.id} ${r.templateName}`" :value="r.id" />
        </el-select>
        <span class="muted-text">vs</span>
        <el-select v-model="compareId2" placeholder="选择报告2" size="small" style="width:140px">
          <el-option v-for="r in reports" :key="r.id" :label="`#${r.id} ${r.templateName}`" :value="r.id" />
        </el-select>
        <el-button size="small" type="primary" @click="doCompare" :loading="comparing">对比</el-button>
      </div>
    </el-card>

    <!-- 新建/编辑模板弹窗 -->
    <el-dialog v-model="showCreate" :title="editingId ? '编辑报告模板' : '新建报告模板'" width="640px"
               @closed="editingId = null; resetForm()">
      <el-form :model="newTpl" label-width="90px">
        <el-form-item label="模板名称">
          <el-input v-model="newTpl.name" placeholder="如：月度交易报告" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="newTpl.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="查询列表">
          <div v-for="(q, i) in newTpl.queries" :key="i" class="query-row">
            <el-input v-model="q.name" placeholder="查询名" style="width:140px" />
            <el-input v-model="q.sql" placeholder="SELECT ..." type="textarea" :rows="2" style="flex:1" />
            <el-button size="small" type="danger" link @click="newTpl.queries.splice(i,1)">✕</el-button>
          </div>
          <el-button size="small" @click="newTpl.queries.push({ name:'', sql:'' })">+ 添加查询</el-button>
        </el-form-item>
        <el-form-item label="定时cron">
          <el-input v-model="newTpl.schedule" placeholder="留空=手动，如 0 0 2 1 * ?" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="editingId ? updateTpl() : createTpl()">
          {{ editingId ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 对比结果弹窗 -->
    <el-dialog v-model="showCompare" title="报告对比" width="640px">
      <el-descriptions v-if="compareResult" :column="3" border>
        <el-descriptions-item v-for="(v, k) in compareResult.comparison" :key="k" :label="k">
          <div>{{ v.period1_count }} → {{ v.period2_count }}</div>
          <div :style="{ color: v.change>0 ? 'var(--af-status-positive)' : v.change<0 ? 'var(--af-status-negative)' : 'var(--af-text-3)' }">
            {{ v.change > 0 ? '↑' : v.change < 0 ? '↓' : '→' }} {{ v.change }} ({{ v.changeRate }})
          </div>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()

const templates = ref([])
const reports = ref([])
const loadingTpl = ref(false)
const loadingRpt = ref(false)
const executing = ref(null)

const showCreate = ref(false)
const creating = ref(false)
const editingId = ref(null)
const newTpl = reactive({
  name: '', description: '', schedule: '', enabled: true,
  queries: [{ name: '', sql: '' }]
})

const showCompare = ref(false)
const comparing = ref(false)
const compareId1 = ref(null)
const compareId2 = ref(null)
const compareResult = ref(null)

onMounted(() => {
  loadTemplates()
  loadReports()
})

async function loadTemplates() {
  loadingTpl.value = true
  try {
    templates.value = (await request.get('/admin/report-template')) || []
  } catch (e) {
    ElMessage.error('加载模板失败')
  } finally {
    loadingTpl.value = false
  }
}

async function loadReports() {
  loadingRpt.value = true
  try {
    reports.value = (await request.get('/admin/report-template/reports', { params: { limit: 20 } })) || []
  } catch (e) {
    ElMessage.error('加载报告失败')
  } finally {
    loadingRpt.value = false
  }
}

async function executeTemplate(row) {
  executing.value = row.id
  try {
    const r = await request.post(`/admin/report-template/${row.id}/execute`)
    ElMessage.success(`执行完成：${r.status}${r.aiAnalysis ? '，AI分析已生成' : ''}`)
    loadReports()
  } catch (e) {
    ElMessage.error('执行失败：' + (e.response?.data?.message || e.message))
  } finally {
    executing.value = null
  }
}

async function createTpl() {
  if (!newTpl.name || !newTpl.queries.length) {
    ElMessage.warning('请填写模板名和至少一个查询')
    return
  }
  creating.value = true
  try {
    await request.post('/admin/report-template', newTpl)
    ElMessage.success('模板已创建')
    showCreate.value = false
    editingId.value = null
    resetForm()
    loadTemplates()
  } catch (e) {
    ElMessage.error('创建失败')
  } finally {
    creating.value = false
  }
}

async function removeTpl(id) {
  try {
    await ElMessageBox.confirm('确认删除该模板？', '提示', { type: 'warning' })
    await request.delete(`/admin/report-template/${id}`)
    ElMessage.success('已删除')
    loadTemplates()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

function editTpl(row) {
  editingId.value = row.id
  newTpl.name = row.name || ''
  newTpl.description = row.description || ''
  newTpl.schedule = row.schedule || ''
  newTpl.enabled = row.enabled !== false
  newTpl.queries = (row.queries && row.queries.length)
    ? row.queries.map(q => ({ name: q.name || '', sql: q.sql || '' }))
    : [{ name: '', sql: '' }]
  showCreate.value = true
}

async function updateTpl() {
  if (!newTpl.name || !newTpl.queries.length) {
    ElMessage.warning('请填写模板名和至少一个查询')
    return
  }
  creating.value = true
  try {
    await request.put(`/admin/report-template/${editingId.value}`, newTpl)
    ElMessage.success('模板已更新')
    showCreate.value = false
    editingId.value = null
    resetForm()
    loadTemplates()
  } catch (e) {
    ElMessage.error('更新失败')
  } finally {
    creating.value = false
  }
}

function resetForm() {
  newTpl.name = ''
  newTpl.description = ''
  newTpl.schedule = ''
  newTpl.queries = [{ name: '', sql: '' }]
}

function exportHtml(id) {
  window.open(`/api/admin/report-template/reports/${id}/export/html`)
}

async function doCompare() {
  if (!compareId1.value || !compareId2.value || compareId1.value === compareId2.value) {
    ElMessage.warning('请选择两个不同的报告')
    return
  }
  comparing.value = true
  try {
    compareResult.value = await request.get('/admin/report-template/reports/compare', {
      params: { id1: compareId1.value, id2: compareId2.value }
    })
    showCompare.value = true
  } catch (e) {
    ElMessage.error('对比失败')
  } finally {
    comparing.value = false
  }
}

function statusColor(s) {
  return { COMPLETED: 'success', FAILED: 'danger', GENERATING: 'warning' }[s] || ''
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; color: var(--af-text-1); }
.muted-text { color: var(--af-text-3); }
.query-row { display: flex; gap: var(--af-sp-2); margin-bottom: var(--af-sp-2); align-items: flex-start; }
.compare-bar {
  display: flex; align-items: center; gap: var(--af-sp-2);
  margin-top: var(--af-sp-4); padding-top: var(--af-sp-4);
  border-top: 1px solid var(--af-border-light);
}
</style>
