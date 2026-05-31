import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import ModelUsageView from '../views/ModelUsageView.vue'
import PullRequestListView from '../views/PullRequestListView.vue'
import ReviewReportView from '../views/ReviewReportView.vue'
import ReviewTaskListView from '../views/ReviewTaskListView.vue'
import SettingsView from '../views/SettingsView.vue'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: DashboardView,
    meta: {
      activeView: 'dashboard'
    }
  },
  {
    path: '/pulls',
    name: 'pulls',
    component: PullRequestListView,
    meta: {
      activeView: 'pulls'
    }
  },
  {
    path: '/tasks',
    name: 'tasks',
    component: ReviewTaskListView,
    meta: {
      activeView: 'tasks'
    }
  },
  {
    path: '/model-usage',
    name: 'modelUsage',
    component: ModelUsageView,
    meta: {
      activeView: 'model-usage'
    }
  },
  {
    path: '/settings',
    name: 'settings',
    component: SettingsView,
    meta: {
      activeView: 'settings'
    }
  },
  {
    path: '/reports/:taskId',
    name: 'report',
    component: ReviewReportView,
    props: (route) => ({
      taskId: route.params.taskId
    }),
    meta: {
      activeView: 'report'
    }
  },
  {
    path: '/report/:taskId',
    redirect: (to) => `/reports/${to.params.taskId}`
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
