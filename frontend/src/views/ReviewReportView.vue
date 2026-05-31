<template>
  <div class="report-view">
    <section class="report-header">
      <div>
        <el-button text :icon="ArrowLeft" @click="emit('back')">返回</el-button>
        <h2>{{ report?.prInfo?.title || task?.prTitle || '报告详情' }}</h2>
        <div class="header-meta">
          <StatusTag :status="task?.status || 'UNKNOWN'" />
          <RiskLevelTag v-if="report?.riskLevel || task?.riskLevel" :risk-level="report?.riskLevel || task?.riskLevel" />
          <span>任务 #{{ taskId || '-' }}</span>
          <span v-if="task?.createdAt">创建于 {{ task.createdAt }}</span>
        </div>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadReport">刷新</el-button>
        <el-button :icon="CopyDocument" type="primary" plain :disabled="!report" @click="copyMarkdown">
          复制 Review
        </el-button>
        <el-button :icon="Link" plain :disabled="!prUrl" @click="openExternal(prUrl)">GitHub</el-button>
      </div>
    </section>

    <el-alert
      v-if="task?.status === 'FAILED'"
      type="error"
      show-icon
      :closable="false"
      :title="task.errorMessage || '任务执行失败'"
    />

    <!-- P0 缓存命中提示 -->
    <el-alert
      v-if="task?.cached"
      type="success"
      show-icon
      :closable="false"
      title="已命中历史报告，无需重新 AI 分析。"
    />

    <!-- P0 进度横幅：非终态时展示轮询进度 -->
    <div v-if="isPollingStatus(task?.status)" class="progress-banner">
      <div class="progress-top">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span class="progress-stage">{{ task?.currentStep || '处理中...' }}</span>
        <span class="progress-text">
          已分析 {{ task?.analyzedFileCount ?? 0 }} / {{ (task?.totalFileCount ?? 0) - (task?.skippedFileCount ?? 0) }} 个文件
          <template v-if="task?.skippedFileCount">（跳过 {{ task.skippedFileCount }} 个）</template>
          <template v-if="task?.failedFileCount">（失败 {{ task.failedFileCount }} 个）</template>
        </span>
      </div>
      <el-progress
        :percentage="task?.progressPercent ?? 0"
        :stroke-width="6"
        :show-text="true"
      />
      <p class="progress-hint">
        当前 PR 文件较多，AI Review 仍在执行。可以离开页面，稍后从任务中心查看结果。
      </p>
    </div>

    <section v-loading="loading" class="report-body">
      <el-empty v-if="!taskId" description="请选择一个任务查看报告" />
      <el-tabs v-else v-model="activeTab">
        <el-tab-pane label="总览" name="overview">
          <div v-if="report" class="overview-grid">
            <PrInfoCard :pr-info="report.prInfo" />
            <RiskScoreCard :risk-score="report.riskScore" :risk-level="report.riskLevel" />
            <SummaryCard :summary="report.summary" :main-changes="report.mainChanges" />
            <TestSuggestionCard :test-suggestions="report.testSuggestions" />
            <FinalReviewCard :final-review="report.finalReview" :risk-level="report.riskLevel" />
          </div>
          <el-empty v-else description="当前任务还没有可展示报告，稍后刷新查看" />
        </el-tab-pane>

        <el-tab-pane label="文件视图" name="files">
          <el-empty v-if="files.length === 0" description="暂无文件数据" />
          <div v-else class="file-list">
            <article v-for="file in files" :key="file.fileId || file.filePath" class="file-card">
              <div class="file-head">
                <div>
                  <div class="file-path mono">{{ file.filePath || '未返回文件路径' }}</div>
                  <div class="file-meta">
                    <el-tag effect="plain">{{ file.fileStatus || '-' }}</el-tag>
                    <el-tag v-if="file.language" type="info" effect="plain">{{ file.language }}</el-tag>
                    <span>+{{ file.additions || 0 }} / -{{ file.deletions || 0 }}</span>
                  </div>
                </div>
                <RiskLevelTag v-if="highestRiskByFile[file.filePath]" :risk-level="highestRiskByFile[file.filePath]" />
              </div>

              <p class="file-summary">{{ file.aiSummary || file.skipReason || '暂无文件级总结。' }}</p>

              <div v-if="commentsByFile[file.filePath]?.length" class="file-comments">
                <RiskItemCard
                  v-for="item in commentsByFile[file.filePath]"
                  :key="item.id || item.title"
                  :item="item"
                />
              </div>
            </article>
          </div>
        </el-tab-pane>

        <el-tab-pane label="Review 建议" name="comments">
          <div class="comment-toolbar">
            <el-radio-group v-model="riskFilter">
              <el-radio-button label="">全部</el-radio-button>
              <el-radio-button label="HIGH">HIGH</el-radio-button>
              <el-radio-button label="MEDIUM">MEDIUM</el-radio-button>
              <el-radio-button label="LOW">LOW</el-radio-button>
              <el-radio-button label="INFO">INFO</el-radio-button>
            </el-radio-group>
          </div>
          <el-empty v-if="filteredComments.length === 0" description="暂无匹配风险建议" />
          <div v-else class="comment-list">
            <RiskItemCard v-for="item in filteredComments" :key="item.id || item.title" :item="item" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="执行轨迹" name="trace">
          <el-steps direction="vertical" :active="traceActiveIndex" finish-status="success">
            <el-step v-for="step in traceSteps" :key="step.status" :title="step.title" :description="step.description" />
          </el-steps>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, CopyDocument, Link, Loading, Refresh } from '@element-plus/icons-vue'
import { getReviewComments, getReviewFiles, getReviewMarkdown, getReviewReport, getReviewTask } from '../api/review'
import FinalReviewCard from '../components/FinalReviewCard.vue'
import PrInfoCard from '../components/PrInfoCard.vue'
import RiskItemCard from '../components/RiskItemCard.vue'
import RiskScoreCard from '../components/RiskScoreCard.vue'
import SummaryCard from '../components/SummaryCard.vue'
import TestSuggestionCard from '../components/TestSuggestionCard.vue'
import RiskLevelTag from '../components/common/RiskLevelTag.vue'
import StatusTag from '../components/common/StatusTag.vue'
import { buildReviewMarkdown } from '../utils/reviewMarkdown'
import { saveRecentTask } from '../utils/recentTasks'

const props = defineProps({
  taskId: {
    type: [Number, String],
    default: null
  }
})

const emit = defineEmits(['back'])

const loading = ref(false)
const task = ref(null)
const report = ref(null)
const files = ref([])
const comments = ref([])
const activeTab = ref('overview')
const riskFilter = ref('')

// ── P0 轮询：每隔 2s 查询任务进度，非终态自动更新 ──
const POLL_INTERVAL_MS = 2000
const POLLING_STATUSES = ['PENDING', 'FETCHING_PR', 'PARSING_DIFF', 'REVIEWING', 'SUMMARIZING', 'SCORING']
let pollTimer = null

function isPollingStatus(status) {
  if (!status) return false
  return POLLING_STATUSES.includes(String(status).toUpperCase())
}

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(async () => {
    if (!props.taskId) {
      stopPolling()
      return
    }
    try {
      const t = await getReviewTask(props.taskId)
      if (!t) return
      task.value = { ...task.value, ...t }
      if (!isPollingStatus(t.status)) {
        stopPolling()
        if (String(t.status).toUpperCase() === 'SUCCESS') {
          await loadReportData()
        }
      }
    } catch {
      // 轮询期间忽略网络错误
    }
  }, POLL_INTERVAL_MS)
}

function stopPolling() {
  if (pollTimer != null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

async function loadReportData() {
  if (!props.taskId) return
  try {
    files.value = await getReviewFiles(props.taskId).catch(() => [])
    comments.value = await getReviewComments(props.taskId).catch(() => [])
    report.value = await getReviewReport(props.taskId).catch(() => null)
  } catch {
    // loadReport 统一处理错误
  }
}

onBeforeUnmount(() => {
  stopPolling()
})

const prUrl = computed(() => report.value?.prInfo?.url || task.value?.prUrl || '')

const filteredComments = computed(() => {
  if (!riskFilter.value) return comments.value
  return comments.value.filter((item) => String(item.riskLevel || '').toUpperCase() === riskFilter.value)
})

const commentsByFile = computed(() => {
  return comments.value.reduce((map, item) => {
    const key = item.filePath || '未返回文件路径'
    if (!map[key]) map[key] = []
    map[key].push(item)
    return map
  }, {})
})

const highestRiskByFile = computed(() => {
  const weight = {
    CRITICAL: 4,
    HIGH: 3,
    MEDIUM: 2,
    LOW: 1,
    INFO: 0
  }
  return comments.value.reduce((map, item) => {
    const key = item.filePath || '未返回文件路径'
    const current = map[key]
    const next = String(item.riskLevel || 'INFO').toUpperCase()
    if (!current || (weight[next] ?? 0) > (weight[current] ?? 0)) {
      map[key] = next
    }
    return map
  }, {})
})

const traceSteps = computed(() => [
  {
    status: 'PENDING',
    title: '创建任务',
    description: '记录 PR 链接并初始化 Review 任务'
  },
  {
    status: 'FETCHING_PR',
    title: '获取 PR 信息',
    description: '读取标题、作者、分支和基础元数据'
  },
  {
    status: 'PARSING_DIFF',
    title: '解析 Diff',
    description: '获取变更文件并过滤不适合分析的内容'
  },
  {
    status: 'REVIEWING',
    title: '执行 AI Review',
    description: '按文件生成风险建议和文件级总结'
  },
  {
    status: 'SUMMARIZING',
    title: '生成报告',
    description: '聚合 Review 建议、测试建议和最终结论'
  },
  {
    status: 'SUCCESS',
    title: '完成',
    description: '报告已保存，可进入详情查看'
  }
])

const traceActiveIndex = computed(() => {
  const status = String(task.value?.status || '').toUpperCase()
  if (status === 'FAILED') return Math.max(0, traceSteps.value.findIndex((item) => item.status === 'REVIEWING'))
  const index = traceSteps.value.findIndex((item) => item.status === status)
  if (index >= 0) return index
  return status === 'SUCCESS' ? traceSteps.value.length : 0
})

onMounted(loadReport)

watch(() => props.taskId, () => {
  activeTab.value = 'overview'
  loadReport()
})

async function loadReport() {
  if (!props.taskId) {
    return
  }

  loading.value = true
  try {
    task.value = await getReviewTask(props.taskId)

    // ── P0：非终态则启动轮询，不阻塞页面 ──
    if (isPollingStatus(task.value?.status)) {
      startPolling()
      loading.value = false
      return
    }

    stopPolling()
    await loadReportData()

    saveRecentTask({
      ...task.value,
      prUrl: prUrl.value,
      prTitle: report.value?.prInfo?.title || task.value?.prTitle,
      author: report.value?.prInfo?.author || task.value?.author,
      riskScore: report.value?.riskScore ?? task.value?.riskScore,
      riskLevel: report.value?.riskLevel || task.value?.riskLevel
    })
  } catch (error) {
    ElMessage.error(error.message || '加载报告失败')
  } finally {
    loading.value = false
  }
}

async function copyMarkdown() {
  if (!report.value) {
    ElMessage.warning('暂无可复制报告')
    return
  }

  let markdown = ''
  try {
    const data = await getReviewMarkdown(props.taskId)
    markdown = data?.markdown || ''
  } catch {
    markdown = buildReviewMarkdown(report.value, comments.value)
  }

  try {
    await navigator.clipboard.writeText(markdown)
    ElMessage.success('Review Markdown 已复制')
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = markdown
    textarea.setAttribute('readonly', 'readonly')
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('Review Markdown 已复制')
  }
}

function openExternal(url) {
  if (url) {
    window.open(url, '_blank', 'noreferrer')
  }
}
</script>

<style scoped>
.report-view {
  display: grid;
  gap: 16px;
}

.progress-banner {
  padding: 18px;
  background: #f0f5ff;
  border: 1px solid #c9d9f2;
  border-radius: 8px;
}

.progress-top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.progress-stage {
  color: #1f63d8;
  font-weight: 700;
  font-size: 14px;
}

.progress-text {
  color: #68778c;
  font-size: 13px;
  margin-left: auto;
}

.progress-hint {
  margin: 10px 0 0;
  color: #8492a6;
  font-size: 12px;
}

.report-header,
.report-body {
  padding: 18px;
  background: #ffffff;
  border: 1px solid #e1e7f0;
  border-radius: 8px;
}

.report-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

h2 {
  margin: 4px 0 0;
  color: #172033;
  font-size: 22px;
}

.header-meta,
.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-top: 10px;
}

.header-meta {
  color: #69788d;
  font-size: 13px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.overview-grid > :deep(.wide-card) {
  grid-column: 1 / -1;
}

.file-list,
.comment-list {
  display: grid;
  gap: 14px;
}

.file-card {
  padding: 16px;
  background: #fbfcfe;
  border: 1px solid #e5ebf4;
  border-radius: 8px;
}

.file-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.file-path {
  color: #172033;
  font-weight: 700;
  word-break: break-all;
}

.file-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-top: 8px;
  color: #69788d;
  font-size: 12px;
}

.file-summary {
  margin: 12px 0 0;
  color: #42526a;
  line-height: 1.7;
}

.file-comments {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.comment-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 14px;
}

@media (max-width: 920px) {
  .report-header,
  .file-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

  .header-actions :deep(.el-button) {
    width: 100%;
  }
}
</style>
