<template>
  <div class="model-usage-view">
    <section class="summary-section">
      <div class="panel-header">
        <div>
          <h2>用量概览</h2>
          <p>统计周期内的 AI 模型调用情况</p>
        </div>
        <div class="date-range-picker">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            @change="loadSummary"
          />
          <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadSummary">刷新</el-button>
        </div>
      </div>

      <div class="stats-grid" v-if="summary">
        <div class="stat-card">
          <div class="stat-label">总调用次数</div>
          <div class="stat-value">{{ summary.totalCalls }}</div>
        </div>
        <div class="stat-card success">
          <div class="stat-label">成功次数</div>
          <div class="stat-value">{{ summary.successCalls }}</div>
        </div>
        <div class="stat-card danger">
          <div class="stat-label">失败次数</div>
          <div class="stat-value">{{ summary.failedCalls }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">成功率</div>
          <div class="stat-value">{{ summary.successRate }}%</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Prompt Tokens</div>
          <div class="stat-value">{{ summary.totalPromptTokens }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Completion Tokens</div>
          <div class="stat-value">{{ summary.totalCompletionTokens }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">总 Tokens</div>
          <div class="stat-value">{{ summary.totalTokens }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">预估费用</div>
          <div class="stat-value">${{ summary.estimatedCost }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">平均延迟</div>
          <div class="stat-value">{{ summary.avgLatencyMs }}ms</div>
        </div>
      </div>
    </section>

    <section class="logs-section">
      <div class="panel-header">
        <div>
          <h2>调用明细</h2>
          <p>查看每次 AI 模型调用的详细信息</p>
        </div>
        <div class="filter-bar">
          <el-input
            v-model="taskIdFilter"
            placeholder="任务ID"
            clearable
            style="width: 140px"
            @change="loadLogs"
          />
          <el-select
            v-model="successFilter"
            placeholder="调用结果"
            clearable
            style="width: 120px"
            @change="loadLogs"
          >
            <el-option label="成功" :value="true" />
            <el-option label="失败" :value="false" />
          </el-select>
        </div>
      </div>

      <el-table :data="logs" v-loading="logsLoading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="taskId" label="任务ID" width="100" />
        <el-table-column prop="skillCode" label="技能" width="120" />
        <el-table-column prop="provider" label="供应商" width="160" />
        <el-table-column prop="modelName" label="模型" width="140" />
        <el-table-column prop="promptTokens" label="Prompt" width="80" align="right" />
        <el-table-column prop="completionTokens" label="Completion" width="100" align="right" />
        <el-table-column prop="totalTokens" label="总计" width="80" align="right" />
        <el-table-column prop="latencyMs" label="延迟" width="100" align="right">
          <template #default="{ row }">
            {{ row.latencyMs }}ms
          </template>
        </el-table-column>
        <el-table-column prop="success" label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="调用时间" min-width="160" />
        <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip />
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @change="loadLogs"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getModelUsageSummary, getModelUsageLogs } from '../api/modelUsage'

const loading = ref(false)
const logsLoading = ref(false)
const summary = ref(null)
const logs = ref([])
const dateRange = ref([])
const taskIdFilter = ref('')
const successFilter = ref(null)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

onMounted(() => {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 7)
  dateRange.value = [
    start.toISOString().split('T')[0],
    end.toISOString().split('T')[0]
  ]
  loadSummary()
  loadLogs()
})

async function loadSummary() {
  loading.value = true
  try {
    const [startDate, endDate] = dateRange.value || []
    summary.value = await getModelUsageSummary(startDate, endDate)
  } catch (error) {
    ElMessage.error('加载用量概览失败')
  } finally {
    loading.value = false
  }
}

async function loadLogs() {
  logsLoading.value = true
  try {
    const taskId = taskIdFilter.value ? parseInt(taskIdFilter.value) : null
    const result = await getModelUsageLogs(page.value, pageSize.value, taskId, successFilter.value)
    logs.value = result.records || []
    total.value = result.total || 0
  } catch (error) {
    ElMessage.error('加载调用明细失败')
  } finally {
    logsLoading.value = false
  }
}
</script>

<style scoped>
.model-usage-view {
  display: grid;
  gap: 18px;
}

.summary-section,
.logs-section {
  padding: 20px;
  background: #ffffff;
  border: 1px solid #e1e7f0;
  border-radius: 8px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
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

.date-range-picker {
  display: flex;
  gap: 10px;
  align-items: center;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(9, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  padding: 14px;
  background: #fbfcfe;
  border: 1px solid #e5ebf4;
  border-radius: 8px;
  text-align: center;
}

.stat-card.success {
  background: #f0f9eb;
  border-color: #e1f3d8;
}

.stat-card.danger {
  background: #fef0f0;
  border-color: #fde2e2;
}

.stat-label {
  color: #69788d;
  font-size: 13px;
  margin-bottom: 8px;
}

.stat-value {
  color: #172033;
  font-size: 20px;
  font-weight: 700;
}

.filter-bar {
  display: flex;
  gap: 10px;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .panel-header {
    flex-direction: column;
  }

  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 580px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .date-range-picker,
  .filter-bar {
    flex-direction: column;
    width: 100%;
  }
}
</style>