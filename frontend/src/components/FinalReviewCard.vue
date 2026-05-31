<template>
  <el-card class="section-card wide-card final-card" shadow="never">
    <template #header>
      <div class="section-title">
        <span class="section-title__icon">
          <Finished />
        </span>
        <span>最终 Review 结论</span>
      </div>
    </template>

    <el-alert
      v-if="isHighRisk"
      title="建议修改高风险问题后再合并。"
      type="error"
      :closable="false"
      show-icon
    />

    <p>{{ finalReview || fallbackText }}</p>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { Finished } from '@element-plus/icons-vue'

const props = defineProps({
  finalReview: {
    type: String,
    default: ''
  },
  riskLevel: {
    type: String,
    default: 'LOW'
  }
})

const isHighRisk = computed(() => ['CRITICAL', 'HIGH'].includes(String(props.riskLevel || '').toUpperCase()))

const fallbackText = computed(() => {
  if (isHighRisk.value) {
    return '建议修改高风险问题后再合并。'
  }
  return '当前暂无明确最终结论，请结合风险项和人工 Review 结果判断。'
})
</script>

<style scoped>
.section-title {
  margin-bottom: 0;
}

.final-card :deep(.el-alert) {
  margin-bottom: 14px;
}

p {
  margin: 0;
  color: #334158;
  font-size: 16px;
  font-weight: 650;
  line-height: 1.7;
}
</style>
