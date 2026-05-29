<template>
  <el-card class="section-card input-card" shadow="never">
    <template #header>
      <div class="input-header">
        <div class="section-title">
          <span class="section-title__icon">
            <Search />
          </span>
          <span>PR 分析入口</span>
        </div>
        <el-switch v-model="useMockModel" active-text="Mock 数据" inactive-text="真实接口" />
      </div>
    </template>

    <div class="input-row">
      <el-input
        v-model="prUrlModel"
        class="pr-input"
        size="large"
        clearable
        placeholder="https://github.com/owner/repo/pull/12"
        :prefix-icon="Link"
        @keyup.enter="emit('analyze')"
      />
      <el-button type="primary" size="large" :icon="DataAnalysis" :loading="loading" @click="emit('analyze')">
        开始分析
      </el-button>
      <el-button size="large" :icon="Delete" @click="emit('clear')">清空结果</el-button>
    </div>

    <div class="input-help">
      <el-text type="info">请输入 GitHub Pull Request 链接，格式如</el-text>
      <code>https://github.com/owner/repo/pull/12</code>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { DataAnalysis, Delete, Link, Search } from '@element-plus/icons-vue'

const props = defineProps({
  prUrl: {
    type: String,
    default: ''
  },
  useMock: {
    type: Boolean,
    default: true
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:prUrl', 'update:useMock', 'analyze', 'clear'])

const prUrlModel = computed({
  get: () => props.prUrl,
  set: (value) => emit('update:prUrl', value)
})

const useMockModel = computed({
  get: () => props.useMock,
  set: (value) => emit('update:useMock', value)
})
</script>

<style scoped>
.input-card {
  background: rgba(255, 255, 255, 0.92);
}

.input-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.input-header .section-title {
  margin-bottom: 0;
}

.input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 12px;
  align-items: center;
}

.pr-input :deep(.el-input__wrapper) {
  min-height: 44px;
}

.input-help {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-top: 12px;
}

code {
  padding: 3px 7px;
  color: #24456f;
  background: #f1f5fb;
  border: 1px solid #dbe4ef;
  border-radius: 6px;
  font-family:
    "SFMono-Regular", Consolas, "Liberation Mono", Menlo, Monaco, "Courier New", monospace;
  font-size: 13px;
}

@media (max-width: 820px) {
  .input-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .input-row {
    grid-template-columns: 1fr;
  }

  .input-row :deep(.el-button) {
    width: 100%;
  }
}
</style>
