<template>
  <AppHeader v-if="!isLoginPage" />
  <main :class="['af-root', { 'af-root--fullscreen': isLoginPage }]">
    <router-view v-slot="{ Component }">
      <keep-alive :include="['ChatView', 'ReportBuilder', 'ReportList', 'BusinessReportList', 'InsightDashboard']">
        <component :is="Component" />
      </keep-alive>
    </router-view>
  </main>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'

const route = useRoute()
const isLoginPage = computed(() => route.path === '/login')
</script>

<style scoped>
.af-root {
  min-height: 100vh;
}
.af-root:not(.af-root--fullscreen) {
  padding-top: var(--af-header-h);
}
</style>
