<template>
  <div class="dashboard-view">
    <section class="stats-grid">
      <StatTile label="本地最近任务" :value="stats.total" hint="来自当前浏览器缓存">
        <template #icon><Tickets /></template>
      </StatTile>
      <StatTile label="成功报告" :value="stats.success" hint="可直接进入报告详情">
        <template #icon><CircleCheck /></template>
      </StatTile>
      <StatTile label="高风险 PR" :value="stats.highRisk" hint="HIGH / CRITICAL">
        <template #icon><Warning /></template>
      </StatTile>
      <StatTile label="缓存命中" :value="stats.cached" hint="复用历史报告">
        <template #icon><Coin /></template>
      </StatTile>
    </section>

    <section class="main-grid">
      <div class="workbench-panel">
        <div class="panel-header">
          <div>
            <h2>PR 工作台</h2>
            <p>输入仓库后获取 GitHub PR 列表，或直接使用 PR 链接开始评审。</p>
          </div>
          <el-tag effect="plain">P1 接口降级可用</el-tag>
        </div>

        <el-form class="repo-form" :model="repoForm" label-position="top">
          <el-form-item label="Owner">
            <el-input v-model="repoForm.owner" placeholder="crash73" clearable />
          </el-form-item>
          <el-form-item label="Repository">
            <el-input v-model="repoForm.repo" placeholder="test-for-PRreview" clearable />
          </el-form-item>
          <el-form-item label="状态">
            <el-segmented v-model="repoForm.state" :options="stateOptions" />
          </el-form-item>
          <el-form-item label="操作">
            <el-button type="primary" :icon="Search" :loading="pullsLoading" @click="loadPulls">获取 PR</el-button>
          </el-form-item>
        </el-form>

        <el-alert
          v-if="pullsError"
          class="inline-alert"
          type="info"
          :closable="false"
          show-icon
          :title="pullsError"
        />

        <div class="pull-list">
          <el-empty v-if="!pullsLoading && pullRequests.length === 0" description="暂无 PR 列表，后端接口完成后这里会展示仓库 PR" />
          <article v-for="item in pullRequests" :key="item.pullNumber" class="pull-card">
            <div class="pull-main">
              <div class="pull-title">#{{ item.pullNumber }} {{ item.title }}</div>
              <div class="pull-meta">
                <span>{{ item.author || '-' }}</span>
                <span>{{ item.sourceBranch || '-' }} → {{ item.targetBranch || '-' }}</span>
                <StatusTag :status="item.latestTaskStatus || item.state" />
              </div>
            </div>
            <div class="pull-actions">
              <RiskLevelTag v-if="item.latestRiskLevel" :risk-level="item.latestRiskLevel" />
              <el-button size="small" type="primary" :loading="runningPrUrl === item.htmlUrl" @click="startFromPull(item)">
                开始评审
              </el-button>
              <el-button size="small" plain @click="openExternal(item.htmlUrl)">GitHub</el-button>
            </div>
          </article>
        </div>

        <div class="manual-box">
          <h3>手动 PR 链接</h3>
          <div class="manual-row">
            <el-input
              v-model="manualPrUrl"
              clearable
              placeholder="https://github.com/owner/repo/pull/12"
              :prefix-icon="Link"
              @keyup.enter="startManualReview(false)"
            />
            <el-button type="primary" :icon="DataAnalysis" :loading="manualLoading" @click="startManualReview(false)">
              开始评审
            </el-button>
            <el-button :icon="Refresh" :loading="manualLoading" @click="startManualReview(true)">
              重新分析
            </el-button>
          </div>
        </div>
      </div>

      <aside class="recent-panel">
        <div class="panel-header compact">
          <div>
            <h2>最近任务</h2>
            <p>任务中心接口未完成时，先展示本地最近任务。</p>
          </div>
          <el-button text type="primary" @click="emit('navigate', 'tasks')">全部</el-button>
        </div>

        <el-empty v-if="recentTasks.length === 0" description="暂无最近任务" />
        <div v-else class="recent-list">
          <article v-for="task in recentTasks.slice(0, 6)" :key="task.taskId" class="recent-item">
            <div>
              <div class="recent-title">#{{ task.pullNumber || '-' }} {{ task.prTitle }}</div>
              <div class="recent-meta">
                <span>{{ task.ownerName || '-' }}/{{ task.repoName || '-' }}</span>
                <span>{{ task.createdAt }}</span>
              </div>
            </div>
            <div class="recent-actions">
              <StatusTag :status="task.status" />
              <RiskLevelTag v-if="task.riskLevel" :risk-level="task.riskLevel" />
              <el-button size="small" plain @click="emit('open-report', task.taskId)">查看</el-button>
            </div>
          </article>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, Coin, DataAnalysis, Link, Refresh, Search, Tickets, Warning } from '@element-plus/icons-vue'
import { listPullRequests } from '../api/github'
import RiskLevelTag from '../components/common/RiskLevelTag.vue'
import StatTile from '../components/common/StatTile.vue'
import StatusTag from '../components/common/StatusTag.vue'
import { buildPrUrl, isValidPrUrl } from '../utils/prUrl'
import { loadRecentTasks, saveRecentTask } from '../utils/recentTasks'
import { startReviewTask } from '../utils/reviewTaskFlow'

const emit = defineEmits(['open-report', 'navigate'])

const stateOptions = [
  { label: 'Open', value: 'open' },
  { label: 'Closed', value: 'closed' },
  { label: 'All', value: 'all' }
]

const repoForm = ref({
  owner: 'crash73',
  repo: 'test-for-PRreview',
  state: 'open'
})
const pullRequests = ref([])
const pullsLoading = ref(false)
const pullsError = ref('')
const recentTasks = ref([])
const manualPrUrl = ref('')
const manualLoading = ref(false)
const runningPrUrl = ref('')

const stats = computed(() => {
  const tasks = recentTasks.value
  return {
    total: tasks.length,
    success: tasks.filter((task) => task.status === 'SUCCESS').length,
    highRisk: tasks.filter((task) => ['HIGH', 'CRITICAL'].includes(String(task.riskLevel || '').toUpperCase())).length,
    cached: tasks.filter((task) => task.cached).length
  }
})

onMounted(() => {
  recentTasks.value = loadRecentTasks()
})

async function loadPulls() {
  if (!repoForm.value.owner || !repoForm.value.repo) {
    ElMessage.warning('请输入 owner 和 repository')
    return
  }

  pullsLoading.value = true
  pullsError.value = ''
  try {
    const page = await listPullRequests({
      owner: repoForm.value.owner.trim(),
      repo: repoForm.value.repo.trim(),
      state: repoForm.value.state,
      page: 1,
      pageSize: 10
    })
    pullRequests.value = page.records || []
    if (pullRequests.value.length === 0) {
      pullsError.value = '当前仓库没有返回 PR 数据。'
    }
  } catch (error) {
    pullRequests.value = []
    pullsError.value = 'PR 列表接口暂未可用，仍可通过下方 PR 链接入口创建评审任务。'
  } finally {
    pullsLoading.value = false
  }
}

async function startFromPull(item) {
  const prUrl = item.htmlUrl || buildPrUrl(item.owner || repoForm.value.owner, item.repo || repoForm.value.repo, item.pullNumber)
  if (!prUrl) {
    ElMessage.warning('当前 PR 缺少链接信息')
    return
  }
  runningPrUrl.value = prUrl
  try {
    const result = await startReviewTask(prUrl)
    afterTaskStarted(result.created.taskId)
  } catch (error) {
    ElMessage.error(error.message || '创建评审任务失败')
  } finally {
    runningPrUrl.value = ''
    recentTasks.value = loadRecentTasks()
  }
}

async function startManualReview(forceRefresh) {
  const value = manualPrUrl.value.trim()
  if (!isValidPrUrl(value)) {
    ElMessage.warning('请输入正确的 GitHub PR 链接')
    return
  }

  manualLoading.value = true
  try {
    const result = await startReviewTask(value, { forceRefresh })
    saveRecentTask({
      ...result.task,
      taskId: result.created.taskId,
      prUrl: value,
      status: result.finalStatus || result.created.status,
      cached: result.created.cached,
      cachedFromTaskId: result.created.cachedFromTaskId
    })
    afterTaskStarted(result.created.taskId, Boolean(result.created.cached))
  } catch (error) {
    ElMessage.error(error.message || '创建评审任务失败')
  } finally {
    manualLoading.value = false
    recentTasks.value = loadRecentTasks()
  }
}

function afterTaskStarted(taskId, cached = false) {
  ElMessage.success(cached ? '已命中历史报告' : '评审任务已创建')
  emit('open-report', taskId)
}

function openExternal(url) {
  if (url) {
    window.open(url, '_blank', 'noreferrer')
  }
}
</script>

<style scoped>
.dashboard-view {
  display: grid;
  gap: 18px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 390px;
  gap: 18px;
  align-items: start;
}

.workbench-panel,
.recent-panel {
  padding: 20px;
  background: #ffffff;
  border: 1px solid #e1e7f0;
  border-radius: 8px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.panel-header.compact {
  align-items: flex-start;
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

.repo-form {
  display: grid;
  grid-template-columns: 1fr 1fr 180px auto;
  gap: 12px;
  align-items: end;
}

.repo-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.inline-alert {
  margin-top: 14px;
}

.pull-list,
.recent-list {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}

.pull-card,
.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px;
  background: #fbfcfe;
  border: 1px solid #e5ebf4;
  border-radius: 8px;
}

.pull-title,
.recent-title {
  color: #172033;
  font-weight: 700;
}

.pull-meta,
.recent-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  color: #69788d;
  font-size: 12px;
}

.pull-actions,
.recent-actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.manual-box {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid #e7edf5;
}

.manual-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 10px;
  margin-top: 10px;
}

@media (max-width: 1120px) {
  .main-grid {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .stats-grid,
  .repo-form,
  .manual-row {
    grid-template-columns: 1fr;
  }

  .pull-card,
  .recent-item,
  .panel-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .pull-actions,
  .recent-actions,
  .manual-row :deep(.el-button) {
    width: 100%;
  }
}
</style>
