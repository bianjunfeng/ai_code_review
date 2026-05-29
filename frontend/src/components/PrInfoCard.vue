<template>
  <el-card class="section-card" shadow="never">
    <template #header>
      <div class="section-title">
        <span class="section-title__icon">
          <InfoFilled />
        </span>
        <span>PR 基本信息</span>
      </div>
    </template>

    <div class="pr-title">{{ safeInfo.title || '暂未返回 PR 标题' }}</div>
    <div class="meta-list">
      <div class="meta-item">
        <span>作者</span>
        <strong>{{ safeInfo.author || '-' }}</strong>
      </div>
      <div class="meta-item">
        <span>变更文件</span>
        <strong>{{ formatNumber(safeInfo.changedFiles) }}</strong>
      </div>
      <div class="meta-item">
        <span>新增行数</span>
        <strong class="additions">+{{ formatNumber(safeInfo.additions) }}</strong>
      </div>
      <div class="meta-item">
        <span>删除行数</span>
        <strong class="deletions">-{{ formatNumber(safeInfo.deletions) }}</strong>
      </div>
    </div>

    <div class="pr-link">
      <span>链接</span>
      <a v-if="safeInfo.url" class="mono" :href="safeInfo.url" target="_blank" rel="noreferrer">
        {{ safeInfo.url }}
      </a>
      <span v-else class="muted">暂未返回</span>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'

const props = defineProps({
  prInfo: {
    type: Object,
    default: () => ({})
  }
})

const safeInfo = computed(() => props.prInfo || {})

function formatNumber(value) {
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) {
    return 0
  }
  return numberValue.toLocaleString()
}
</script>

<style scoped>
.section-title {
  margin-bottom: 0;
}

.pr-title {
  margin-bottom: 16px;
  color: #172033;
  font-size: 20px;
  font-weight: 760;
}

.meta-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.meta-item {
  padding: 12px;
  background: #f6f8fb;
  border: 1px solid #e3eaf4;
  border-radius: 8px;
}

.meta-item span {
  display: block;
  margin-bottom: 5px;
  color: #66748a;
  font-size: 13px;
}

.meta-item strong {
  color: #172033;
  font-size: 19px;
}

.additions {
  color: #15905d;
}

.deletions {
  color: #d94a4a;
}

.pr-link {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  margin-top: 14px;
  color: #66748a;
  font-size: 13px;
}

.pr-link a {
  overflow: hidden;
  color: #1f63d8;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 640px) {
  .meta-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .pr-link {
    grid-template-columns: 1fr;
  }
}
</style>
