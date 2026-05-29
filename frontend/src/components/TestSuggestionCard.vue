<template>
  <el-card class="section-card wide-card" shadow="never">
    <template #header>
      <div class="section-title">
        <span class="section-title__icon">
          <CircleCheckFilled />
        </span>
        <span>测试建议</span>
      </div>
    </template>

    <el-empty v-if="suggestions.length === 0" description="暂无测试建议。" />
    <ul v-else class="test-list">
      <li v-for="(item, index) in suggestions" :key="index">
        <el-icon><CircleCheck /></el-icon>
        <span>{{ item }}</span>
      </li>
    </ul>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { CircleCheck, CircleCheckFilled } from '@element-plus/icons-vue'

const props = defineProps({
  testSuggestions: {
    type: Array,
    default: () => []
  }
})

const suggestions = computed(() => {
  return Array.isArray(props.testSuggestions) ? props.testSuggestions.filter(Boolean) : []
})
</script>

<style scoped>
.section-title {
  margin-bottom: 0;
}

.test-list {
  display: grid;
  gap: 10px;
  padding: 0;
  margin: 0;
  list-style: none;
}

li {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  gap: 10px;
  align-items: flex-start;
  padding: 12px;
  color: #334158;
  background: #f7fbf9;
  border: 1px solid #dceee6;
  border-radius: 8px;
}

.el-icon {
  margin-top: 2px;
  color: #138a56;
}
</style>
