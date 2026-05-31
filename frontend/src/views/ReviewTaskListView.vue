<template>
  <div class="task-list-view">
    <section class="filter-panel">
      <div>
        <h2>任务中心</h2>
        <p>集中查看历史 Review 任务，后端列表接口不可用时会读取本地最近任务。</p>
      </div>
      <div class="filters">
        <el-input v-model="filters.keyword" clearable placeholder="搜索 PR 标题 / 仓库 / 作者" :prefix-icon="Search" />
        <el-select v-model="filters.status" clearable placeholder="状态">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="部分成功" value="PARTIAL_SUCCESS" />
          <el-option label="评审中" value="REVIEWING" />
          <el-option label="等待中" value="PENDING" />
          <el-option label="失败" value="FAILED" />
        </el-select>
        <el-select v-model="filters.riskLevel" clearable placeholder="风险">
          <el-option label="LOW" value="LOW" />
          <el-option label="MEDIUM" value="MEDIUM" />
          <el-option label="HIGH" value="HIGH" />
          <el-option label="CRITICAL" value="CRITICAL" />
        </el-select>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadTasks">刷新</el-button>
      </div>
    </section>

    <el-alert v-if="sourceNotice" type="info" show-icon :closable="false" :title="sourceNotice" />

    <section class="table-panel">
      <el-table v-loading="loading" :data="filteredTasks" empty-text="暂无任务">
        <el-table-column label="任务" width="90">
          <template #default="{ row }">#{{ row.taskId }}</template>
        </el-table-column>
        <el-table-column label="PR" min-width="280">
          <template #default="{ row }">
            <div class="task-title">{{ row.prTitle || '未返回 PR 标题' }}</div>
            <div class="task-sub">
              {{ row.ownerName || '-' }}/{{ row.repoName || '-' }} · PR #{{ row.pullNumber || '-' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="风险" width="150">
          <template #default="{ row }">
            <div class="risk-cell">
              <RiskLevelTag v-if="row.riskLevel" :risk-level="row.riskLevel" />
              <span v-else class="muted">-</span>
              <span v-if="row.riskScore != null">{{ row.riskScore }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="缓存" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.cached" type="success" effect="plain">命中</el-tag>
            <span v-else class="muted">否</span>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="150">
          <template #default="{ row }">
            <span v-if="['FAILED', 'PARTIAL_SUCCESS'].includes(row.status) && row.errorMessage" class="error-text" :title="row.errorMessage">
              {{ truncateError(row.errorMessage) }}
            </span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="模型" min-width="150">
          <template #default="{ row }">
            <span>{{ row.modelName || '-' }}</span>
            <span v-if="row.promptVersion" class="prompt-version"> {{ row.promptVersion }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createdAt" min-width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button size="small" type="primary" plain @click="emit('open-report', row.taskId)">报告</el-button>
              <el-button size="small" plain @click="openExternal(row.prUrl)">GitHub</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { listReviewTasks } from '../api/review'
import RiskLevelTag from '../components/common/RiskLevelTag.vue'
import StatusTag from '../components/common/StatusTag.vue'
import { loadRecentTasks, normalizeTask } from '../utils/recentTasks'

const emit = defineEmits(['open-report'])

const loading = ref(false)
const tasks = ref([])
const sourceNotice = ref('')
const filters = ref({
  keyword: '',
  status: '',
  riskLevel: ''
})

const filteredTasks = computed(() => {
  const keyword = filters.value.keyword.trim().toLowerCase()
  return tasks.value.filter((task) => {
    if (filters.value.status && task.status !== filters.value.status) return false
    if (filters.value.riskLevel && task.riskLevel !== filters.value.riskLevel) return false
    if (!keyword) return true
    const haystack = [
      task.prTitle,
      task.ownerName,
      task.repoName,
      task.author,
      task.prUrl
    ].join(' ').toLowerCase()
    return haystack.includes(keyword)
  })
})

onMounted(() => {
  loadTasks()
})

async function loadTasks() {
  loading.value = true
  sourceNotice.value = ''
  try {
    const page = await listReviewTasks({
      page: 1,
      pageSize: 50,
      keyword: filters.value.keyword || undefined,
      status: filters.value.status || undefined,
      riskLevel: filters.value.riskLevel || undefined
    })
    tasks.value = (page.records || []).map(normalizeTask)
    sourceNotice.value = '当前数据来自后端任务列表接口。'
  } catch {
    tasks.value = loadRecentTasks()
    sourceNotice.value = '后端任务列表接口暂未可用，当前展示浏览器本地最近任务。'
  } finally {
    loading.value = false
  }
}

function openExternal(url) {
  if (!url) {
    ElMessage.warning('当前任务缺少 PR 链接')
    return
  }
  window.open(url, '_blank', 'noreferrer')
}

function truncateError(message) {
  if (!message) return '-'
  return message.length > 50 ? message.substring(0, 50) + '...' : message
}
</script>

<style scoped>
.task-list-view {
  display: grid;
  gap: 16px;
}

.filter-panel,
.table-panel {
  padding: 18px;
  background: #ffffff;
  border: 1px solid #e1e7f0;
  border-radius: 8px;
}

.filter-panel {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 18px;
  align-items: end;
}

h2,
p {
  margin: 0;
}

h2 {
  color: #172033;
  font-size: 18px;
}

p {
  margin-top: 5px;
  color: #68778c;
  font-size: 13px;
}

.filters {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px 140px auto;
  gap: 10px;
}

.task-title {
  color: #172033;
  font-weight: 700;
}

.task-sub {
  margin-top: 5px;
  color: #69788d;
  font-size: 12px;
}

.risk-cell,
.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.prompt-version {
  color: #69788d;
  font-size: 12px;
}

.error-text {
  color: #f56c6c;
  font-size: 12px;
}

.muted {
  color: #c0c4cc;
}

@media (max-width: 980px) {
  .filter-panel,
  .filters {
    grid-template-columns: 1fr;
  }
}
</style>
