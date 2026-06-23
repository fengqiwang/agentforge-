<template>
  <header class="app-header">
    <div class="app-header__inner">
      <!-- 左：品牌 Logo -->
      <router-link to="/chat" class="app-header__logo">
        <span class="logo-mark">◆</span>
        <span class="logo-text">AgentForge</span>
      </router-link>

      <!-- 中：水平导航菜单 -->
      <nav class="app-header__menu">
        <router-link
          v-for="item in items"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ 'is-active': isActive(item.path) }">
          <span class="nav-item__icon">{{ item.icon }}</span>
          <span class="nav-item__label">{{ item.label }}</span>
        </router-link>
      </nav>

      <!-- 右：用户区 -->
      <div class="app-header__user">
        <div class="user-avatar">{{ avatarText }}</div>
        <span class="user-name">{{ userName }}</span>
        <el-tooltip content="退出登录" placement="bottom">
          <button class="user-logout" @click="handleLogout" aria-label="退出登录">
            <el-icon><SwitchButton /></el-icon>
          </button>
        </el-tooltip>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { SwitchButton } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const items = [
  { label: '智能对话', path: '/chat', icon: '💬' },
  { label: '报表构建', path: '/report/builder', icon: '🛠️' },
  { label: '报表管理', path: '/report/list', icon: '📋' },
  { label: '业务报告', path: '/business-report', icon: '📑' },
  { label: '市场洞察', path: '/insight', icon: '💡' },
  { label: '代码审查', path: '/code-review/history', icon: '🔍' },
  { label: '数据对账', path: '/reconciliation/history', icon: '⚖️' },
  { label: '监控面板', path: '/monitor', icon: '📈' },
  { label: '管理后台', path: '/admin', icon: '⚙️' },
]

const userName = computed(() => {
  try { return JSON.parse(localStorage.getItem('user') || '{}').username || 'Admin' } catch { return 'Admin' }
})
const avatarText = computed(() => (userName.value || 'A').slice(0, 1).toUpperCase())

function isActive(path) {
  return route.path === path || route.path.startsWith(path + '/')
}
function handleLogout() {
  localStorage.removeItem('user')
  router.push('/login')
}
</script>

<style scoped>
.app-header {
  position: fixed;
  top: 0; left: 0; right: 0;
  height: var(--af-header-h);
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: saturate(180%) blur(16px);
  -webkit-backdrop-filter: saturate(180%) blur(16px);
  border-bottom: 1px solid var(--af-border-light);
  box-shadow: 0 1px 8px rgba(29, 33, 41, 0.04);
  z-index: 1000;
}
.app-header__inner {
  max-width: var(--af-content-max);
  height: 100%;
  margin: 0 auto;
  padding: 0 var(--af-sp-4);
  display: flex;
  align-items: center;
  gap: 12px;
}

/* Logo */
.app-header__logo { display: flex; align-items: center; gap: 6px; flex-shrink: 0; text-decoration: none; }
.logo-mark { font-size: 18px; background: var(--af-brand-grad); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; }
.logo-text { font-size: 15px; font-weight: 700; letter-spacing: 0.02em; background: var(--af-brand-grad); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; }

/* 菜单 */
.app-header__menu { flex: 1; display: flex; align-items: center; gap: 2px; min-width: 0; overflow-x: auto; scrollbar-width: none; }
.app-header__menu::-webkit-scrollbar { display: none; }
.nav-item {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 5px 8px;
  border-radius: var(--af-radius-sm);
  font-size: var(--af-fs-sm);
  font-weight: 500;
  color: var(--af-text-2);
  text-decoration: none;
  white-space: nowrap;
  transition: color var(--af-transition), background var(--af-transition);
}
.nav-item:hover { color: var(--af-primary); background: var(--af-bg-hover); }
.nav-item.is-active { color: var(--af-primary); background: var(--el-color-primary-light-9); font-weight: 600; }
.nav-item__icon { font-size: 14px; line-height: 1; }

/* 用户区 */
.app-header__user { display: flex; align-items: center; gap: 6px; flex-shrink: 0; padding-left: 12px; border-left: 1px solid var(--af-border-light); }
.user-avatar { width: 30px; height: 30px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600; color: #fff; background: var(--af-brand-grad); box-shadow: 0 2px 6px rgba(22, 119, 255, 0.28); }
.user-name { font-size: 13px; color: var(--af-text-1); font-weight: 500; }
.user-logout { width: 30px; height: 30px; display: flex; align-items: center; justify-content: center; border: none; background: transparent; border-radius: var(--af-radius-sm); color: var(--af-text-3); cursor: pointer; transition: all var(--af-transition); }
.user-logout:hover { color: var(--af-danger); background: rgba(245, 63, 63, 0.08); }

/* —— 响应式自适应：窄屏逐级收紧，确保 9 项带 emoji 完整显示不截断 —— */
@media (max-width: 1280px) {
  .app-header__inner { gap: 10px; padding: 0 14px; }
  .nav-item { padding: 5px 7px; gap: 3px; }
  .nav-item__icon { font-size: 13px; }
}
@media (max-width: 1100px) {
  .user-name { display: none; }
  .nav-item { font-size: 13px; padding: 5px 6px; }
  .logo-text { font-size: 14px; }
}
@media (max-width: 980px) {
  .nav-item__icon { font-size: 12px; }
  .nav-item { font-size: 12px; padding: 4px 5px; gap: 2px; }
}
</style>
