import { http, unwrapResult } from './request'

export async function getHealth() {
  const response = await http.get('/api/health')
  return unwrapResult(response.data)
}

export async function getConfigStatus() {
  const response = await http.get('/api/config/status')
  return unwrapResult(response.data)
}
