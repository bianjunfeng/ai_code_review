import { http, unwrapResult } from './request'

export function getModelUsageSummary(startDate, endDate) {
  const params = {}
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  return http.get('/api/model-usage/summary', { params }).then(unwrapResult)
}

export function getModelUsageLogs(page, pageSize, taskId, success) {
  const params = { page, pageSize }
  if (taskId) params.taskId = taskId
  if (success !== undefined && success !== null) params.success = success
  return http.get('/api/model-usage/logs', { params }).then(unwrapResult)
}

export function getTaskModelUsage(taskId) {
  return http.get(`/api/model-usage/tasks/${taskId}`).then(unwrapResult)
}

export function getConfigStatus() {
  return http.get('/api/config/status').then(unwrapResult)
}