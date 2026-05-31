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
        v-if="!report && !loadingStatus"
        class="empty-state"
        description="输入 GitHub Pull Request 链接后开始生成 Review 报告"
      />

      <div v-if="loadingStatus" class="loading-status">
        <el-icon class="loading-icon"><Loading /></el-icon>
        <span>{{ loadingStatus }}</span>
      </div>

      <div v-if="report" class="report-grid">
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

          <el-empty v-if="riskItems.length === 0" description="暂无 Review 建议，可能该 PR 风险较低" />
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
import { Loading, WarningFilled } from '@element-plus/icons-vue'
import { createReviewTask, getReviewReport, getReviewTask, mockReport } from '../api/review'
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
const useMock = ref(false)
const loadingStatus = ref('')

const riskItems = computed(() => {
  return Array.isArray(report.value?.riskItems) ? report.value.riskItems : []
})

function isValidPrUrl(value) {
  if (!value) return false
  const pattern = /^https:\/\/github\.com\/[a-zA-Z0-9_.-]+\/[a-zA-Z0-9_.-]+\/pull\/\d+\/?$/i
  if (!pattern.test(value)) return false

  const match = value.match(/\/pull\/(\d+)/)
  if (match) {
    const pullNumber = parseInt(match[1], 10)
    if (pullNumber < 1 || pullNumber > 99999) return false
  }
  return true
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
  loadingStatus.value = '正在创建 Review 任务...'
  report.value = null

  try {
    if (useMock.value) {
      loadingStatus.value = ''
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

    const created = await createReviewTask(value)
    const taskId = created?.taskId
    if (!taskId) {
      throw new Error('创建任务失败，未返回 taskId')
    }

    loadingStatus.value = '正在获取 PR 信息...'
    const taskStatus = await pollReviewTaskStatus(taskId)

    if (taskStatus === 'SUCCESS' || taskStatus === 'PARTIAL_SUCCESS') {
      loadingStatus.value = '正在生成评审报告...'
      report.value = await getReviewReport(taskId)
      loadingStatus.value = ''
      ElMessage.success(taskStatus === 'PARTIAL_SUCCESS' ? '评审报告已生成，部分文件需人工补充检查' : '评审报告已生成')
    } else if (taskStatus === 'FAILED') {
      loadingStatus.value = ''
      const task = await getReviewTask(taskId).catch(() => null)
      const errorMsg = task?.errorMessage || 'AI 分析失败，请稍后重试'
      throw new Error(errorMsg)
    } else if (taskStatus === 'CANCELLED') {
      loadingStatus.value = ''
      throw new Error('任务已取消')
    } else {
      loadingStatus.value = ''
      throw new Error('AI 分析耗时较长，请稍后刷新报告')
    }
  } catch (error) {
    loadingStatus.value = ''
    let message = 'AI 分析失败，请稍后重试'
    if (error?.response?.data?.code === 0 && error?.response?.data?.message) {
      message = error.response.data.message
    } else if (error?.response?.data?.message) {
      message = error.response.data.message
    } else if (error?.message && error.message.indexOf('.') === -1) {
      message = error.message
    }
    ElMessage.error(message)
  } finally {
    loading.value = false
    loadingStatus.value = ''
  }
}

const POLL_INTERVAL_MS = 2000
const MAX_POLL_COUNT = 90
const POLLING_STATUSES = ['PENDING', 'FETCHING_PR', 'PARSING_DIFF', 'REVIEWING', 'SUMMARIZING', 'SCORING']

async function pollReviewTaskStatus(taskId) {
  for (let i = 0; i < MAX_POLL_COUNT; i++) {
    await new Promise((resolve) => window.setTimeout(resolve, POLL_INTERVAL_MS))

    const task = await getReviewTask(taskId).catch(() => null)
    if (!task) continue

    const status = task.status
    if (!POLLING_STATUSES.includes(status)) {
      return status
    }
  }
  return null
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

.loading-status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 120px;
  margin-top: 18px;
  padding: 24px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px dashed #cfd8e6;
  border-radius: 8px;
  color: #334158;
  font-size: 15px;
}

.loading-icon {
  font-size: 20px;
  color: #1f63d8;
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
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
