<template>
  <AppLayout :active-view="activeView" @refresh="refreshActiveView">
    <router-view v-slot="{ Component, route }">
      <component
        :is="Component"
        :key="`${route.fullPath}-${viewRefreshKey}`"
        v-bind="routeComponentProps"
        @navigate="handleNavigate"
        @open-report="openReport"
        @back="goTaskList"
      />
    </router-view>
  </AppLayout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from './components/layout/AppLayout.vue'

const route = useRoute()
const router = useRouter()
const viewRefreshKey = ref(0)

const routeMap = {
  dashboard: '/dashboard',
  pulls: '/pulls',
  tasks: '/tasks',
  settings: '/settings',
  'model-usage': '/model-usage'
}

const activeView = computed(() => route.meta.activeView || 'dashboard')

const routeComponentProps = computed(() => {
  if (route.name !== 'report') {
    return {}
  }
  return {
    taskId: route.params.taskId
  }
})

function handleNavigate(viewName) {
  router.push(routeMap[viewName] || '/dashboard')
}

function openReport(taskId) {
  router.push(`/reports/${taskId}`)
}

function goTaskList() {
  router.push('/tasks')
}

function refreshActiveView() {
  viewRefreshKey.value += 1
}
</script>
