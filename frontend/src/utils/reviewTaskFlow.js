import { createReviewTask } from '../api/review'
import { parsePrUrl } from './prUrl'
import { saveRecentTask } from './recentTasks'

/**
 * P0 优化：创建任务后立即返回 taskId，不再阻塞等待任务完成。
 * 轮询逻辑已移入 ReviewReportView，页面自行处理状态更新与报告加载。
 */
export async function startReviewTask(prUrl, options = {}) {
  const parsed = parsePrUrl(prUrl)
  const created = await createReviewTask(prUrl, Boolean(options.forceRefresh))
  const taskId = created?.taskId
  if (!taskId) {
    throw new Error('创建任务失败，未返回 taskId')
  }

  saveRecentTask({
    taskId,
    prUrl,
    ownerName: parsed?.owner,
    repoName: parsed?.repo,
    pullNumber: parsed?.pullNumber,
    status: created.status || 'PENDING',
    cached: created.cached,
    cachedFromTaskId: created.cachedFromTaskId
  })

  return {
    created,
    taskId
  }
}
