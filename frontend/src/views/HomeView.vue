<template>
  <div class="home-page">
    <HeaderBar />

    <main class="page-shell">
      <PrInputCard
        v-model:pr-url="prUrl"
        v-model:use-mock="useMock"
        :loading="loading"
        @analyze="handleAnalyze"
        @clear="handleClear"
      />

      <el-empty
        v-if="!report"
        class="empty-state"
        description="输入 GitHub Pull Request 链接后开始生成 Review 报告"
      />

      <div v-else class="report-grid">
        <PrInfoCard :pr-info="report.prInfo" />
        <RiskScoreCard :risk-score="report.riskScore" :risk-level="report.riskLevel" />
        <SummaryCard :summary="report.summary" :main-changes="report.mainChanges" />

        <el-card class="section-card risk-list-card" shadow="never">
          <template #header>
            <div class="section-title risk-list-title">
              <span class="section-title__icon">
                <WarningFilled />
              </span>
              <span>风险问题列表</span>
              <el-tag round effect="plain">{{ riskItems.length }} 项</el-tag>
            </div>
          </template>

          <el-empty v-if="riskItems.length === 0" description="暂无风险建议，可能该 PR 风险较低" />
          <div v-else class="risk-list">
            <RiskItemCard v-for="(item, index) in riskItems" :key="index" :item="item" />
          </div>
        </el-card>

        <TestSuggestionCard :test-suggestions="report.testSuggestions" />
        <FinalReviewCard :final-review="report.finalReview" :risk-level="report.riskLevel" />
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { analyzeReview, mockReport } from '../api/review'
import HeaderBar from '../components/HeaderBar.vue'
import PrInputCard from '../components/PrInputCard.vue'
import PrInfoCard from '../components/PrInfoCard.vue'
import RiskScoreCard from '../components/RiskScoreCard.vue'
import SummaryCard from '../components/SummaryCard.vue'
import RiskItemCard from '../components/RiskItemCard.vue'
import TestSuggestionCard from '../components/TestSuggestionCard.vue'
import FinalReviewCard from '../components/FinalReviewCard.vue'

const prUrl = ref('')
const loading = ref(false)
const report = ref(null)
const useMock = ref(true)

const riskItems = computed(() => {
  return Array.isArray(report.value?.riskItems) ? report.value.riskItems : []
})

function isValidPrUrl(value) {
  return /^https:\/\/github\.com\/[^/\s]+\/[^/\s]+\/pull\/\d+\/?$/i.test(value)
}

async function handleAnalyze() {
  const value = prUrl.value.trim()

  if (!value) {
    ElMessage.warning('请输入 GitHub Pull Request 链接')
    return
  }

  if (!isValidPrUrl(value)) {
    ElMessage.warning('PR 链接格式错误，请输入 GitHub Pull Request 地址')
    return
  }

  loading.value = true

  try {
    if (useMock.value) {
      await new Promise((resolve) => window.setTimeout(resolve, 450))
      report.value = {
        ...mockReport,
        prInfo: {
          ...mockReport.prInfo,
          url: value
        }
      }
      ElMessage.success('已加载 Mock Review 报告')
      return
    }

    const result = await analyzeReview(value)
    report.value = result || null
    ElMessage.success('分析完成')
  } catch (error) {
    const message = error?.response?.data?.message || error?.message || 'AI 分析失败，请稍后重试'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

function handleClear() {
  prUrl.value = ''
  report.value = null
}
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  padding-bottom: 48px;
}

.page-shell {
  width: min(1100px, calc(100% - 32px));
  margin: 0 auto;
}

.empty-state {
  min-height: 260px;
  margin-top: 18px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px dashed #cfd8e6;
  border-radius: 8px;
}

.report-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 18px;
}

.risk-list-card,
.report-grid > :deep(.wide-card) {
  grid-column: 1 / -1;
}

.risk-list-title {
  margin-bottom: 0;
}

.risk-list {
  display: grid;
  gap: 14px;
}

@media (max-width: 820px) {
  .page-shell {
    width: min(100% - 24px, 1100px);
  }

  .report-grid {
    grid-template-columns: 1fr;
  }
}
</style>
