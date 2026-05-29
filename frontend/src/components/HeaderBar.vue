<template>
  <header class="header-bar">
    <div class="header-inner">
      <div class="brand-block">
        <div class="brand-mark">
          <Connection />
        </div>
        <div>
          <div class="brand-kicker">GitHub PR Intelligence</div>
          <h1>AI PR Review 助手</h1>
          <p>输入 GitHub Pull Request 链接，自动生成代码评审总结、风险识别和 Review 建议</p>
        </div>
      </div>

      <div class="header-actions">
        <el-button :icon="Document" plain>项目说明</el-button>
        <el-button :icon="Link" type="primary" plain>GitHub</el-button>
      </div>
    </div>
  </header>
</template>

<script setup>
import { Connection, Document, Link } from '@element-plus/icons-vue'
</script>

<style scoped>
.header-bar {
  padding: 34px 0 24px;
}

.header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(1100px, calc(100% - 32px));
  margin: 0 auto;
  gap: 24px;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.brand-mark {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  color: #ffffff;
  background: #1f63d8;
  border-radius: 8px;
  box-shadow: 0 12px 28px rgba(31, 99, 216, 0.24);
}

.brand-mark :deep(svg) {
  width: 30px;
  height: 30px;
}

.brand-kicker {
  margin-bottom: 4px;
  color: #1f63d8;
  font-size: 13px;
  font-weight: 700;
}

h1 {
  margin: 0;
  color: #13213a;
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 0;
}

p {
  max-width: 720px;
  margin: 8px 0 0;
  color: #5f6f86;
  font-size: 15px;
}

.header-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 10px;
}

@media (max-width: 760px) {
  .header-inner {
    align-items: stretch;
    flex-direction: column;
    width: min(100% - 24px, 1100px);
  }

  .brand-block {
    align-items: flex-start;
  }

  .brand-mark {
    width: 48px;
    height: 48px;
  }

  h1 {
    font-size: 25px;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
