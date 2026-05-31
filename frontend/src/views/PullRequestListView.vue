<template>
  <div class="pull-request-list-view">
    <section class="toolbar-panel">
      <div class="toolbar-title">
        <h2>选择 PR 后启动评审</h2>
        <p>选择仓库后获取 PR 列表，直接点击启动评审。</p>
      </div>

      <div class="toolbar-grid">
        <el-input v-model="filters.owner" placeholder="owner" clearable />
        <el-input v-model="filters.repo" placeholder="repository" clearable />
        <el-radio-group v-model="filters.state">
          <el-radio-button label="open">Open</el-radio-button>
          <el-radio-button label="closed">Closed</el-radio-button>
          <el-radio-button label="all">All</el-radio-button>
        </el-radio-group>
        <el-button type="primary" :icon="Search" :loading="loading" @click="loadPulls">查询</el-button>
      </div>
    </section>

    <el-alert
      v-if="errorMessage"
      type="info"
      show-icon
      :closable="false"
      :title="errorMessage"
    />

    <section class="table-panel">
      <el-table v-loading="loading" :data="pullRequests" empty-text="暂无 PR 数据">
        <el-table-column label="PR" min-width="300">
          <template #default="{ row }">
            <div class="pr-title">#{{ row.pullNumber }} {{ row.title || '未返回标题' }}</div>
            <div class="pr-sub">
              {{ row.author || '-' }} · {{ row.sourceBranch || '-' }} → {{ row.targetBranch || '-' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.state" />
          </template>
        </el-table-column>
        <el-table-column label="历史评审" min-width="180">
          <template #default="{ row }">
            <div class="history-cell">
              <RiskLevelTag v-if="row.latestRiskLevel" :risk-level="row.latestRiskLevel" />
              <StatusTag v-if="row.latestTaskStatus" :status="row.latestTaskStatus" />
              <span v-if="!row.latestTaskStatus" class="muted">暂无</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" prop="updatedAt" min-width="160" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button size="small" type="primary" :loading="runningPrUrl === getPrUrl(row)" @click="startReview(row, false)">
                评审
              </el-button>
              <el-button size="small" :loading="runningPrUrl === getPrUrl(row)" @click="startReview(row, true)">
                重跑
              </el-button>
              <el-button size="small" plain @click="openExternal(row.htmlUrl)">GitHub</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="manual-panel">
      <div>
        <h3>没有列表接口时的兜底入口</h3>
        <p>直接粘贴 PR 链接也能进入同一套 Review 任务流程。</p>
      </div>
      <div class="manual-row">
        <el-input v-model="manualPrUrl" placeholder="https://github.com/owner/repo/pull/12" clearable :prefix-icon="Link" />
        <el-button type="primary" :loading="manualLoading" @click="startManual(false)">开始评审</el-button>
        <el-button :loading="manualLoading" @click="startManual(true)">强制重跑</el-button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Link, Search } from '@element-plus/icons-vue'
import { listPullRequests } from '../api/github'
import RiskLevelTag from '../components/common/RiskLevelTag.vue'
import StatusTag from '../components/common/StatusTag.vue'
import { createReviewTask } from '../api/review'
import { buildPrUrl, isValidPrUrl, parsePrUrl } from '../utils/prUrl'
import { saveRecentTask } from '../utils/recentTasks'

const emit = defineEmits(['open-report'])

const filters = ref({
  owner: 'crash73',
  repo: 'test-for-PRreview',
  state: 'open'
})
const pullRequests = ref([])
const loading = ref(false)
const errorMessage = ref('')
const runningPrUrl = ref('')
const manualPrUrl = ref('')
const manualLoading = ref(false)

async function loadPulls() {
  if (!filters.value.owner || !filters.value.repo) {
    ElMessage.warning('请输入 owner 和 repository')
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const page = await listPullRequests({
      ...filters.value,
      page: 1,
      pageSize: 20
    })
    pullRequests.value = page.records || []
    if (pullRequests.value.length === 0) {
      errorMessage.value = '当前仓库没有返回 PR 数据。'
    }
  } catch {
    pullRequests.value = []
    errorMessage.value = '后端暂未提供 PR 列表接口，请先使用下方 PR 链接入口。'
  } finally {
    loading.value = false
  }
}

async function startReview(row, forceRefresh) {
  const prUrl = getPrUrl(row)
  if (!prUrl) {
    ElMessage.warning('当前 PR 缺少链接信息')
    return
  }
  runningPrUrl.value = prUrl
  try {
    const created = await createReviewTask(prUrl, Boolean(forceRefresh))
    if (!created?.taskId) {
      throw new Error('创建任务失败，未返回 taskId')
    }
    const parsed = parsePrUrl(prUrl)
    saveRecentTask({
      taskId: created.taskId,
      prUrl,
      ownerName: parsed?.owner,
      repoName: parsed?.repo,
      pullNumber: parsed?.pullNumber,
      status: created.status || 'PENDING',
      cached: created.cached,
      cachedFromTaskId: created.cachedFromTaskId
    })
    ElMessage.success(created.cached ? '已命中历史报告' : '评审任务已创建')
    emit('open-report', created.taskId)
  } catch (error) {
    ElMessage.error(error.message || '创建评审任务失败')
  } finally {
    runningPrUrl.value = ''
  }
}

async function startManual(forceRefresh) {
  const value = manualPrUrl.value.trim()
  if (!isValidPrUrl(value)) {
    ElMessage.warning('请输入正确的 GitHub PR 链接')
    return
  }
  manualLoading.value = true
  try {
    const created = await createReviewTask(value, Boolean(forceRefresh))
    if (!created?.taskId) {
      throw new Error('创建任务失败，未返回 taskId')
    }
    const parsed = parsePrUrl(value)
    saveRecentTask({
      taskId: created.taskId,
      prUrl: value,
      ownerName: parsed?.owner,
      repoName: parsed?.repo,
      pullNumber: parsed?.pullNumber,
      status: created.status || 'PENDING',
      cached: created.cached,
      cachedFromTaskId: created.cachedFromTaskId
    })
    ElMessage.success(created.cached ? '已命中历史报告' : '评审任务已创建')
    emit('open-report', created.taskId)
  } catch (error) {
    ElMessage.error(error.message || '创建评审任务失败')
  } finally {
    manualLoading.value = false
  }
}

function getPrUrl(row) {
  return row.htmlUrl || buildPrUrl(row.owner || filters.value.owner, row.repo || filters.value.repo, row.pullNumber)
}

function openExternal(url) {
  if (url) {
    window.open(url, '_blank', 'noreferrer')
  }
}
</script>

<style scoped>
.pull-request-list-view {
  display: grid;
  gap: 16px;
}

.toolbar-panel,
.table-panel,
.manual-panel {
  padding: 18px;
  background: #ffffff;
  border: 1px solid #e1e7f0;
  border-radius: 8px;
}

.toolbar-panel {
  display: grid;
  grid-template-columns: minmax(260px, 0.8fr) minmax(0, 1.2fr);
  gap: 18px;
  align-items: end;
}

h2,
h3,
p {
  margin: 0;
}

h2 {
  color: #172033;
  font-size: 18px;
}

h3 {
  color: #172033;
  font-size: 15px;
}

p {
  margin-top: 5px;
  color: #68778c;
  font-size: 13px;
}

.toolbar-grid {
  display: grid;
  grid-template-columns: 1fr 1fr auto auto;
  gap: 10px;
  align-items: center;
}

.pr-title {
  color: #172033;
  font-weight: 700;
}

.pr-sub {
  margin-top: 5px;
  color: #69788d;
  font-size: 12px;
}

.history-cell,
.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.manual-panel {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
  align-items: end;
}

.manual-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 10px;
}

@media (max-width: 1050px) {
  .toolbar-panel,
  .manual-panel {
    grid-template-columns: 1fr;
  }

  .toolbar-grid,
  .manual-row {
    grid-template-columns: 1fr;
  }
}
</style>
