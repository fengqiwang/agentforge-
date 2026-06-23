<template>
  <div class="af-page">
    <div class="af-page-head">
      <div class="af-page-head__main">
        <h2 class="af-page-title">报表管理</h2>
        <p class="af-page-desc">查看、分享与管理已生成的报表</p>
      </div>
      <div class="af-page-head__actions">
        <el-button @click="$router.push('/chat')">返回对话</el-button>
        <el-button type="primary" @click="$router.push('/report/builder')">+ 新建报表</el-button>
      </div>
    </div>

    <div v-if="loading" class="state-hint">
      <el-icon class="is-loading"><Loading /></el-icon> 加载中...
    </div>

    <el-empty v-else-if="reports.length === 0" description="暂无报表">
      <el-button type="primary" @click="$router.push('/report/builder')">去创建</el-button>
    </el-empty>

    <div v-else class="report-grid">
      <div v-for="report in reports" :key="report.id" class="report-card">
        <div class="card-body">
          <div class="card-title-row">
            <span class="card-icon">📊</span>
            <h3>{{ report.name }}</h3>
          </div>
          <p v-if="report.description" class="desc">{{ report.description }}</p>
          <div class="meta">
            <span><el-icon><User /></el-icon> {{ report.createdBy || '-' }}</span>
            <span><el-icon><Clock /></el-icon> {{ report.createdAt || '-' }}</span>
          </div>
        </div>
        <div class="card-actions">
          <el-button v-if="report.shareToken" size="small" type="primary"
                     @click="openReport(report.shareToken)">查看</el-button>
          <el-button size="small" type="danger" plain @click="handleDelete(report.id)">删除</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Loading, User, Clock } from '@element-plus/icons-vue'
import { listReports, deleteReport } from '@/api/report'

const reports = ref([])
const loading = ref(true)

onMounted(async () => {
  await loadReports()
})

async function loadReports() {
  loading.value = true
  try {
    reports.value = await listReports()
  } catch (e) {
    console.error('加载报表列表失败', e)
  } finally {
    loading.value = false
  }
}

async function handleDelete(id) {
  if (!confirm('确定删除该报表？')) return
  try {
    await deleteReport(id)
    reports.value = reports.value.filter(r => r.id !== id)
  } catch (e) {
    alert('删除失败')
  }
}

function openReport(token) {
  window.open('/report/view/' + token, '_blank')
}
</script>

<style scoped>
.state-hint {
  text-align: center;
  padding: 96px 0;
  color: var(--af-text-3);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--af-sp-2);
}
.report-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--af-sp-4);
}
.report-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background: var(--af-bg-card);
  border: 1px solid var(--af-border-light);
  border-radius: var(--af-radius-lg);
  padding: var(--af-sp-5);
  box-shadow: var(--af-shadow-sm);
  transition: box-shadow var(--af-transition), transform var(--af-transition), border-color var(--af-transition);
  position: relative;
  overflow: hidden;
}
.report-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: var(--af-primary-grad);
  opacity: 0;
  transition: opacity var(--af-transition);
}
.report-card:hover {
  box-shadow: var(--af-shadow-md);
  transform: translateY(-3px);
  border-color: var(--el-color-primary-light-7);
}
.report-card:hover::before { opacity: 1; }
.card-body { flex: 1; }
.card-title-row { display: flex; align-items: center; gap: var(--af-sp-2); margin-bottom: var(--af-sp-2); }
.card-icon { font-size: 18px; }
.card-body h3 { margin: 0; font-size: var(--af-fs-md); font-weight: 600; color: var(--af-text-1); }
.desc {
  margin: 0 0 var(--af-sp-3);
  font-size: var(--af-fs-xs);
  color: var(--af-text-3);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.meta { display: flex; flex-wrap: wrap; gap: var(--af-sp-3); font-size: var(--af-fs-xs); color: var(--af-text-3); }
.meta span { display: inline-flex; align-items: center; gap: 4px; }
.card-actions {
  display: flex;
  gap: var(--af-sp-2);
  margin-top: var(--af-sp-4);
  padding-top: var(--af-sp-4);
  border-top: 1px solid var(--af-border-light);
}
</style>
