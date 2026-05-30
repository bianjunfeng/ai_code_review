import axios from 'axios'

export const http = axios.create({
  baseURL: '',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

export function unwrapResult(payload) {
  if (!payload) {
    return null
  }
  if (Object.hasOwn(payload, 'code')) {
    if (payload.code !== 0) {
      throw new Error(payload.message || '请求失败')
    }
    if (payload.data && typeof payload.data === 'object' && Object.hasOwn(payload.data, 'code')) {
      return unwrapResult(payload.data)
    }
    return payload.data
  }
  return payload
}

export function normalizePage(data, fallbackPage = 1, fallbackPageSize = 10) {
  if (!data) {
    return {
      records: [],
      page: fallbackPage,
      pageSize: fallbackPageSize,
      total: 0,
      pages: 0
    }
  }

  if (Array.isArray(data)) {
    return {
      records: data,
      page: fallbackPage,
      pageSize: fallbackPageSize,
      total: data.length,
      pages: data.length > 0 ? 1 : 0
    }
  }

  return {
    records: Array.isArray(data.records) ? data.records : [],
    page: data.page || fallbackPage,
    pageSize: data.pageSize || fallbackPageSize,
    total: data.total ?? 0,
    pages: data.pages ?? 0
  }
}
