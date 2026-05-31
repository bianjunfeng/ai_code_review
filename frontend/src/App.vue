<template>
  <AppLayout :active-view="activeView" @navigate="handleNavigate" @refresh="refreshActiveView">
    <DashboardView
      v-if="activeView === 'dashboard'"
      :key="viewRefreshKey"
      @navigate="handleNavigate"
      @open-report="openReport"
    />
    <PullRequestListView
      v-else-if="activeView === 'pulls'"
      :key="viewRefreshKey"
      @open-report="openReport"
    />
    <ReviewTaskListView
      v-else-if="activeView === 'tasks'"
      :key="viewRefreshKey"
      @open-report="openReport"
    />
    <ReviewReportView
      v-else-if="activeView === 'report'"
      :key="`${selectedTaskId}-${viewRefreshKey}`"
      :task-id="selectedTaskId"
      @back="handleNavigate('tasks')"
    />
    <SettingsView v-else-if="activeView === 'settings'" :key="viewRefreshKey" />
    <ModelUsageView v-else-if="activeView === 'model-usage'" :key="viewRefreshKey" />
  </AppLayout>
</template>

<script setup>
import { ref } from 'vue'
import AppLayout from './components/layout/AppLayout.vue'
import DashboardView from './views/DashboardView.vue'
import PullRequestListView from './views/PullRequestListView.vue'
import ReviewReportView from './views/ReviewReportView.vue'
import ReviewTaskListView from './views/ReviewTaskListView.vue'
import SettingsView from './views/SettingsView.vue'
import ModelUsageView from './views/ModelUsageView.vue'

const activeView = ref('dashboard')
const selectedTaskId = ref(null)
const viewRefreshKey = ref(0)

function handleNavigate(viewName) {
  activeView.value = viewName
  if (viewName !== 'report') {
    selectedTaskId.value = null
  }
}

function openReport(taskId) {
  selectedTaskId.value = taskId
  activeView.value = 'report'
}

function refreshActiveView() {
  viewRefreshKey.value += 1
}
</script>
