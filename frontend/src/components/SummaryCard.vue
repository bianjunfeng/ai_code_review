<template>
  <el-card class="section-card wide-card" shadow="never">
    <template #header>
      <div class="section-title">
        <span class="section-title__icon">
          <Memo />
        </span>
        <span>PR 总结</span>
      </div>
    </template>

    <p class="summary-text">{{ summary || '后端暂未返回 PR 总结。' }}</p>

    <div class="changes-block">
      <h3>主要变更</h3>
      <el-empty v-if="changes.length === 0" description="暂无主要变更" />
      <ul v-else>
        <li v-for="(item, index) in changes" :key="index">
          <span>{{ index + 1 }}</span>
          <p>{{ item }}</p>
        </li>
      </ul>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { Memo } from '@element-plus/icons-vue'

const props = defineProps({
  summary: {
    type: String,
    default: ''
  },
  mainChanges: {
    type: Array,
    default: () => []
  }
})

const changes = computed(() => {
  return Array.isArray(props.mainChanges) ? props.mainChanges.filter(Boolean) : []
})
</script>

<style scoped>
.section-title {
  margin-bottom: 0;
}

.summary-text {
  margin: 0;
  color: #334158;
  font-size: 15px;
  line-height: 1.8;
}

.changes-block {
  margin-top: 20px;
}

h3 {
  margin: 0 0 12px;
  color: #172033;
  font-size: 15px;
}

ul {
  display: grid;
  gap: 10px;
  padding: 0;
  margin: 0;
  list-style: none;
}

li {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 10px;
  align-items: flex-start;
  padding: 12px;
  background: #f7f9fc;
  border: 1px solid #e5ebf4;
  border-radius: 8px;
}

li span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  color: #1f63d8;
  background: #e8f1ff;
  border-radius: 8px;
  font-weight: 800;
}

li p {
  margin: 3px 0 0;
  color: #334158;
}
</style>
