<template>
  <el-card class="section-card score-card" shadow="never">
    <template #header>
      <div class="section-title">
        <span class="section-title__icon">
          <TrendCharts />
        </span>
        <span>风险评分</span>
      </div>
    </template>

    <div class="score-main">
      <div>
        <div class="score-number" :style="{ color: progressColor }">{{ normalizedScore }}</div>
        <div class="score-label">/ 100</div>
      </div>
      <el-tag size="large" effect="dark" :color="progressColor" class="level-tag">
        {{ normalizedLevel }}
      </el-tag>
    </div>

    <el-progress
      :percentage="normalizedScore"
      :stroke-width="14"
      :color="progressColor"
      :show-text="false"
    />

    <p class="score-desc">
      {{ riskDescription }}
    </p>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { TrendCharts } from '@element-plus/icons-vue'

const props = defineProps({
  riskScore: {
    type: [Number, String],
    default: 0
  },
  riskLevel: {
    type: String,
    default: 'LOW'
  }
})

const normalizedScore = computed(() => {
  const numberValue = Number(props.riskScore)
  if (!Number.isFinite(numberValue)) {
    return 0
  }
  return Math.min(100, Math.max(0, Math.round(numberValue)))
})

const normalizedLevel = computed(() => {
  return String(props.riskLevel || 'LOW').toUpperCase()
})

const progressColor = computed(() => {
  if (normalizedLevel.value === 'HIGH') {
    return '#d93025'
  }
  if (normalizedLevel.value === 'MEDIUM') {
    return '#e68a00'
  }
  if (normalizedLevel.value === 'LOW') {
    return '#138a56'
  }
  return '#66748a'
})

const riskDescription = computed(() => {
  if (normalizedLevel.value === 'HIGH') {
    return '当前 PR 存在高风险问题，建议优先修复后再进入合并流程。'
  }
  if (normalizedLevel.value === 'MEDIUM') {
    return '当前 PR 存在中等风险，建议检查关键分支和测试覆盖。'
  }
  return '当前 PR 风险较低，可结合人工 Review 继续确认。'
})
</script>

<style scoped>
.section-title {
  margin-bottom: 0;
}

.score-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.score-number {
  font-size: 54px;
  font-weight: 820;
  line-height: 0.95;
}

.score-label {
  margin-top: 4px;
  color: #66748a;
  font-weight: 700;
}

.level-tag {
  border: 0;
  font-weight: 800;
}

.score-desc {
  margin: 16px 0 0;
  color: #5f6f86;
}
</style>
