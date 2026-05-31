import { http, unwrapResult } from './request'

export async function getModelUsageSummary(startDate, endDate) {
  const params = {}
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  const response = await http.get('/api/model-usage/summary', { params })
  return unwrapResult(response.data)
}

export async function getModelUsageLogs(page, pageSize, taskId, success) {
  const params = { page, pageSize }
  if (taskId) params.taskId = taskId
  if (success !== undefined && success !== null) params.success = success
  const response = await http.get('/api/model-usage/logs', { params })
  return unwrapResult(response.data)
}

export async function getTaskModelUsage(taskId) {
  const response = await http.get(`/api/model-usage/tasks/${taskId}`)
  return unwrapResult(response.data)
}

export async function getConfigStatus() {
  const response = await http.get('/api/config/status')
  return unwrapResult(response.data)
}
