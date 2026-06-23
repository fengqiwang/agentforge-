<template>
  <div class="login-page">
    <!-- 左侧品牌 Hero -->
    <div class="login-hero">
      <div class="hero-content">
        <div class="hero-logo">◆ AgentForge</div>
        <h1 class="hero-title">智能报表<br />对话引擎</h1>
        <p class="hero-sub">自然语言驱动 · 自动生成 SQL · 一键可视化分析</p>
        <div class="hero-features">
          <div class="feature"><span class="feature-icon">💬</span> 智能对话问答</div>
          <div class="feature"><span class="feature-icon">📊</span> 自动报表构建</div>
          <div class="feature"><span class="feature-icon">⚖️</span> 数据智能对账</div>
        </div>
      </div>
      <div class="hero-glow hero-glow--1"></div>
      <div class="hero-glow hero-glow--2"></div>
    </div>

    <!-- 右侧登录卡 -->
    <div class="login-panel">
      <div class="login-card">
        <div class="brand-mobile">◆ AgentForge</div>
        <h2>欢迎回来</h2>
        <p class="login-tip">登录以开始使用 AgentForge</p>
        <el-button type="primary" size="large" class="login-btn" @click="handleLogin">
          开发模式登录
        </el-button>
        <p class="login-foot">演示环境 · 一键体验全部功能</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
const handleLogin = () => {
  localStorage.setItem('user', JSON.stringify({ id: 1, username: 'admin', role: 'ADMIN' }))
  router.push('/chat')
}
</script>

<style scoped>
.login-page { display: flex; min-height: 100vh; }

/* 左侧品牌区 */
.login-hero {
  flex: 1;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #0f1a3a 0%, #1a3a8f 50%, #1677ff 100%);
  color: #fff;
  display: flex;
  align-items: center;
  padding: var(--af-sp-7);
}
.hero-content { position: relative; z-index: 2; max-width: 480px; }
.hero-logo { font-size: var(--af-fs-lg); font-weight: 700; margin-bottom: var(--af-sp-6); letter-spacing: 0.02em; }
.hero-title { font-size: 48px; font-weight: 800; line-height: 1.2; margin-bottom: var(--af-sp-4); letter-spacing: 0.01em; }
.hero-sub { font-size: var(--af-fs-md); color: rgba(255, 255, 255, 0.82); margin-bottom: var(--af-sp-7); line-height: 1.6; }
.hero-features { display: flex; flex-direction: column; gap: var(--af-sp-3); }
.feature { display: flex; align-items: center; gap: var(--af-sp-2); font-size: var(--af-fs-sm); color: rgba(255, 255, 255, 0.92); }
.feature-icon { font-size: 18px; }
.hero-glow { position: absolute; border-radius: 50%; filter: blur(90px); opacity: 0.45; }
.hero-glow--1 { width: 420px; height: 420px; background: #1677ff; top: -120px; right: -90px; }
.hero-glow--2 { width: 320px; height: 320px; background: #7b61ff; bottom: -100px; left: 8%; }

/* 右侧登录区 */
.login-panel {
  flex: 0 0 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--af-bg-page);
  padding: var(--af-sp-6);
}
.login-card { width: 100%; max-width: 360px; }
.brand-mobile { display: none; font-size: var(--af-fs-lg); font-weight: 700; margin-bottom: var(--af-sp-5); }
.brand-mobile { background: var(--af-brand-grad); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; }
.login-card h2 { font-size: var(--af-fs-2xl); font-weight: 700; color: var(--af-text-1); margin-bottom: var(--af-sp-2); }
.login-tip { font-size: var(--af-fs-sm); color: var(--af-text-3); margin-bottom: var(--af-sp-6); }
.login-btn { width: 100%; }
.login-foot { text-align: center; font-size: var(--af-fs-xs); color: var(--af-text-3); margin-top: var(--af-sp-5); }

@media (max-width: 860px) {
  .login-page { flex-direction: column; }
  .login-hero { flex: none; padding: var(--af-sp-6); min-height: 220px; }
  .hero-title { font-size: 30px; }
  .login-panel { flex: 1; }
  .brand-mobile { display: block; }
}
</style>
