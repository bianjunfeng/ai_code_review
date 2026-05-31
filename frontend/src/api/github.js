import { http, normalizePage, unwrapResult } from './request'

export async function listPullRequests(params = {}) {
  const response = await http.get('/api/github/pulls', { params })
  const data = unwrapResult(response.data)
  return normalizePage(data, params.page, params.pageSize)
}

export async function getPullRequestReviewState(owner, repo, pullNumber) {
  const response = await http.get(`/api/github/pulls/${owner}/${repo}/${pullNumber}/review-state`)
  return unwrapResult(response.data)
}
