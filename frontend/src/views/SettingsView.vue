<template>
  <div class="settings-view">
    <section class="status-panel">
      <div class="panel-header">
        <div>
          <h2>联调状态检查</h2>
          <p>只展示配置是否可用，不展示 token、apiKey 或数据库密码。</p>
        </div>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadStatus">重新检查</el-button>
      </div>

      <div class="status-grid">
        <div class="status-item">
          <span>后端服务</span>
          <el-tag :type="health?.status === 'UP' ? 'success' : 'danger'" effect="light">
            {{ health?.status || 'UNKNOWN' }}
          </el-tag>
        </div>
        <div class="status-item">
          <span>服务名称</span>
          <strong>{{ health?.service || '-' }}</strong>
        </div>
        <div class="status-item">
          <span>GitHub Token</span>
          <el-tag :type="config?.githubTokenConfigured ? 'success' : 'info'" effect="plain">
            {{ config ? yesNo(config.githubTokenConfigured) : '未接入' }}
          </el-tag>
        </div>
        <div class="status-item">
          <span>GitHub API</span>
          <el-tag :type="config?.githubApiReachable ? 'success' : 'info'" effect="plain">
            {{ config ? yesNo(config.githubApiReachable) : '未接入' }}
          </el-tag>
        </div>
        <div class="status-item">
          <span>AI Key</span>
          <el-tag :type="config?.aiKeyConfigured ? 'success' : 'info'" effect="plain">
            {{ config ? yesNo(config.aiKeyConfigured) : '未接入' }}
          </el-tag>
        </div>
        <div class="status-item">
          <span>模型</span>
          <strong>{{ config?.aiModelName || '-' }}</strong>
        </div>
        <div class="status-item">
          <span>Prompt 版本</span>
          <strong>{{ config?.promptVersion || '-' }}</strong>
        </div>
        <div class="status-item">
          <span>数据库</span>
          <el-tag :type="config?.databaseConnected ? 'success' : 'info'" effect="plain">
            {{ config ? yesNo(config.databaseConnected) : '未接入' }}
          </el-tag>
        </div>
      </div>

      <el-alert
        v-if="notice"
        class="notice"
        type="info"
        show-icon
        :closable="false"
        :title="notice"
      />
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getConfigStatus, getHealth } from '../api/config'

const loading = ref(false)
const health = ref(null)
const config = ref(null)
const notice = ref('')

onMounted(loadStatus)

async function loadStatus() {
  loading.value = true
  notice.value = ''
  try {
    health.value = await getHealth()
  } catch (error) {
    health.value = null
    ElMessage.error('后端健康检查失败')
  }

  try {
    config.value = await getConfigStatus()
  } catch {
    config.value = null
    notice.value = '配置状态接口暂未实现，当前只显示后端健康检查结果。'
  } finally {
    loading.value = false
  }
}

function yesNo(value) {
  return value ? '已配置' : '未配置'
}
</script>

<style scoped>
.settings-view {
  display: grid;
  gap: 16px;
}

.status-panel {
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

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.status-item {
  min-height: 86px;
  padding: 14px;
  background: #fbfcfe;
  border: 1px solid #e5ebf4;
  border-radius: 8px;
}

.status-item span {
  display: block;
  margin-bottom: 10px;
  color: #69788d;
  font-size: 13px;
}

.status-item strong {
  color: #172033;
}

.notice {
  margin-top: 16px;
}

@media (max-width: 960px) {
  .status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .panel-header {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 580px) {
  .status-grid {
    grid-template-columns: 1fr;
  }
}
</style>
