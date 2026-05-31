<template>
  <div class="app-layout">
    <aside class="side-nav">
      <div class="brand">
        <div class="brand-mark">
          <Connection />
        </div>
        <div>
          <strong>AI PR Review</strong>
          <span>Assistant</span>
        </div>
      </div>

      <el-menu class="nav-menu" :default-active="activeView" @select="handleSelect">
        <el-menu-item index="dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-menu-item index="pulls">
          <el-icon><List /></el-icon>
          <span>PR 列表</span>
        </el-menu-item>
        <el-menu-item index="tasks">
          <el-icon><Tickets /></el-icon>
          <span>任务中心</span>
        </el-menu-item>
        <el-menu-item index="model-usage">
          <el-icon><Monitor /></el-icon>
          <span>用量监控</span>
        </el-menu-item>
        <el-menu-item index="settings">
          <el-icon><Setting /></el-icon>
          <span>系统状态</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <div class="main-area">
      <header class="top-header">
        <div>
          <div class="page-kicker">{{ activeMeta.kicker }}</div>
          <h1>{{ activeMeta.title }}</h1>
        </div>
        <div class="top-actions">
          <el-tag effect="plain">GitHub PR Intelligence</el-tag>
          <el-button :icon="Refresh" plain @click="emit('refresh')">刷新</el-button>
        </div>
      </header>

      <main class="content-shell">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Connection, DataBoard, List, Refresh, Setting, Tickets, Monitor } from '@element-plus/icons-vue'

const props = defineProps({
  activeView: {
    type: String,
    default: 'dashboard'
  }
})

const emit = defineEmits(['navigate', 'refresh'])

const metaMap = {
  dashboard: {
    kicker: '工作入口',
    title: 'AI PR Review 工作台'
  },
  pulls: {
    kicker: 'GitHub Pull Requests',
    title: 'PR 列表工作台'
  },
  tasks: {
    kicker: 'Review Tasks',
    title: '评审任务中心'
  },
  report: {
    kicker: 'Review Report',
    title: '报告详情'
  },
  'model-usage': {
    kicker: 'Monitoring',
    title: '用量监控'
  },
  settings: {
    kicker: 'Runtime Status',
    title: '系统状态'
  }
}

const activeMeta = computed(() => metaMap[props.activeView] || metaMap.dashboard)

function handleSelect(viewName) {
  emit('navigate', viewName)
}
</script>

<style scoped>
.app-layout {
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  min-height: 100vh;
  background: #f4f7fb;
}

.side-nav {
  position: sticky;
  top: 0;
  height: 100vh;
  padding: 22px 16px;
  background: #10233f;
  border-right: 1px solid #0d1b31;
}

.brand {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  margin-bottom: 22px;
  color: #ffffff;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  color: #ffffff;
  background: #1f63d8;
  border-radius: 8px;
}

.brand strong,
.brand span {
  display: block;
}

.brand strong {
  font-size: 17px;
}

.brand span {
  color: #a8b5c8;
  font-size: 12px;
}

.nav-menu {
  background: transparent;
  border-right: 0;
}

.nav-menu :deep(.el-menu-item) {
  height: 42px;
  margin-bottom: 8px;
  color: #cbd7e8;
  border-radius: 8px;
}

.nav-menu :deep(.el-menu-item.is-active) {
  color: #ffffff;
  background: #1f63d8;
}

.nav-menu :deep(.el-menu-item:hover) {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.1);
}

.main-area {
  min-width: 0;
}

.top-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 92px;
  padding: 20px 28px;
  background: #ffffff;
  border-bottom: 1px solid #e3e9f2;
}

.page-kicker {
  color: #1f63d8;
  font-size: 13px;
  font-weight: 700;
}

h1 {
  margin: 4px 0 0;
  color: #172033;
  font-size: 26px;
  letter-spacing: 0;
}

.top-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.content-shell {
  width: min(1280px, calc(100% - 40px));
  margin: 0 auto;
  padding: 24px 0 42px;
}

@media (max-width: 900px) {
  .app-layout {
    grid-template-columns: 1fr;
  }

  .side-nav {
    position: relative;
    height: auto;
    padding: 14px 12px;
  }

  .brand {
    margin-bottom: 12px;
  }

  .nav-menu {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 8px;
  }

  .nav-menu :deep(.el-menu-item) {
    justify-content: center;
    margin: 0;
    padding: 0 8px;
  }

  .top-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
    padding: 18px;
  }

  .content-shell {
    width: min(100% - 24px, 1280px);
    padding-top: 16px;
  }
}

@media (max-width: 640px) {
  .nav-menu {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .top-actions :deep(.el-button),
  .top-actions :deep(.el-tag) {
    width: 100%;
  }
}
</style>
