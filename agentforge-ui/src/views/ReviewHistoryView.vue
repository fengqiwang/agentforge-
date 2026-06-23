<template>
  <div class="af-page af-page--wide">
    <div class="af-page-head">
      <div class="af-page-head__main">
        <h2 class="af-page-title">代码审查历史</h2>
        <p class="af-page-desc">查看历次代码审查结论与问题明细</p>
      </div>
      <div class="af-page-head__actions">
        <el-button @click="$router.push('/chat')">返回对话</el-button>
      </div>
    </div>

    <el-card>
      <el-table :data="reviews" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="repoUrl" label="仓库" min-width="160" show-overflow-tooltip />
        <el-table-column prop="prNumber" label="PR" width="70" />
        <el-table-column label="结论" width="100">
          <template #default="{ row }">
            <el-tag :type="verdictColor(row.reviewResult?.verdict)" size="small">
              {{ row.reviewResult?.verdict || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="问题分布" width="210">
          <template #default="{ row }">
            <el-tag type="danger" size="small">🔴 {{ row.reviewResult?.stats?.critical || 0 }}</el-tag>
            <el-tag type="warning" size="small" style="margin-left:4px">🟡 {{ row.reviewResult?.stats?.warning || 0 }}</el-tag>
            <el-tag type="info" size="small" style="margin-left:4px">🔵 {{ row.reviewResult?.stats?.suggestion || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="commentPosted" label="已评论" width="80">
          <template #default="{ row }">
            <el-tag :type="row.commentPosted ? 'success' : 'info'" size="small">{{ row.commentPosted ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="160" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openDetail(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="showDetail" title="审查详情" size="60%">
      <div v-loading="detailLoading">
        <div v-if="current">
          <el-alert :title="current.reviewResult?.summary" type="info" :closable="false" style="margin-bottom:16px" />
          <div v-for="f in current.reviewResult?.fileResults" :key="f.file" class="file-block">
            <h4>{{ f.file }} <el-tag size="small" :type="verdictColor(f.verdict)">{{ f.verdict }}</el-tag></h4>
            <div v-for="(i, idx) in f.issues" :key="idx" class="issue">
              <strong>{{ emoji(i.severity) }} [{{ i.severity }}] {{ i.dimension }}</strong>
              <span v-if="i.line" class="issue-loc"> — {{ f.file }}:{{ i.line }}</span>
              <p>{{ i.message }}</p>
              <p v-if="i.suggestion" class="suggestion">💡 {{ i.suggestion }}</p>
            </div>
            <el-empty v-if="!f.issues?.length" description="无问题" :image-size="50" />
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const reviews = ref([])
const loading = ref(false)
const showDetail = ref(false)
const detailLoading = ref(false)
const current = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    reviews.value = (await request.get('/code-review/history')) || []
  } catch (e) {
    ElMessage.error('加载历史失败')
  } finally {
    loading.value = false
  }
})

async function openDetail(id) {
  showDetail.value = true
  detailLoading.value = true
  try {
    current.value = await request.get(`/code-review/${id}`)
  } catch (e) {
    ElMessage.error('加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

function verdictColor(v) {
  return { BLOCK: 'danger', NEED_FIX: 'warning', PASS: 'success' }[v] || ''
}
function emoji(s) {
  return { CRITICAL: '🔴', WARNING: '🟡', SUGGESTION: '🔵' }[s] || '⚪'
}
</script>

<style scoped>
.file-block { margin-bottom: var(--af-sp-5); }
.file-block h4 { margin-bottom: var(--af-sp-3); display: flex; align-items: center; gap: var(--af-sp-2); color: var(--af-text-1); font-size: var(--af-fs-sm); }
.issue {
  background: var(--af-bg-soft);
  padding: var(--af-sp-3) var(--af-sp-4);
  border-radius: var(--af-radius-sm);
  margin-bottom: var(--af-sp-2);
  border-left: 3px solid var(--af-border);
}
.issue p { margin: 6px 0 0; color: var(--af-text-2); font-size: var(--af-fs-xs); line-height: 1.6; }
.issue-loc { color: var(--af-text-3); font-family: var(--af-font-mono); }
.suggestion {
  color: var(--af-primary) !important;
  background: var(--el-color-primary-light-9);
  padding: 6px 10px;
  border-radius: var(--af-radius-sm);
}
</style>
