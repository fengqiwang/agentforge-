import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/chat' },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue')
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/ChatView.vue')
  },
  {
    path: '/report/builder',
    name: 'ReportBuilder',
    component: () => import('@/views/ReportBuilder.vue')
  },
  {
    path: '/report/list',
    name: 'ReportList',
    component: () => import('@/views/ReportList.vue')
  },
  {
    path: '/report/view/:shareToken',
    name: 'ReportView',
    component: () => import('@/views/ReportView.vue')
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/AdminView.vue')
  },
  {
    path: '/monitor',
    name: 'Monitor',
    component: () => import('@/views/MonitorView.vue')
  },
  {
    path: '/reconciliation/upload',
    name: 'ReconciliationUpload',
    component: () => import('@/views/ReconciliationUpload.vue')
  },
  {
    path: '/reconciliation/result/:id',
    name: 'ReconciliationResult',
    component: () => import('@/views/ReconciliationResult.vue')
  },
  {
    path: '/reconciliation/history',
    name: 'ReconciliationHistory',
    component: () => import('@/views/ReconciliationHistory.vue')
  },
  {
    path: '/business-report',
    name: 'BusinessReportList',
    component: () => import('@/views/BusinessReportList.vue')
  },
  {
    path: '/business-report/:id',
    name: 'BusinessReportView',
    component: () => import('@/views/BusinessReportView.vue')
  },
  {
    path: '/code-review/history',
    name: 'ReviewHistory',
    component: () => import('@/views/ReviewHistoryView.vue')
  },
  {
    path: '/insight',
    name: 'InsightDashboard',
    component: () => import('@/views/InsightDashboard.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
