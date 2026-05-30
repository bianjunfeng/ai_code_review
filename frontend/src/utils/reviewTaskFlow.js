import { createReviewTask, getReviewTask } from '../api/review'
import { parsePrUrl } from './prUrl'
import { saveRecentTask } from './recentTasks'

const POLL_INTERVAL_MS = 2000
const MAX_POLL_COUNT = 90
const POLLING_STATUSES = ['PENDING', 'FETCHING_PR', 'PARSING_DIFF', 'REVIEWING', 'SUMMARIZING', 'SCORING']

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

  if (created.cached || created.status === 'SUCCESS') {
    return {
      created,
      finalStatus: created.status || 'SUCCESS',
      task: null
    }
  }

  const task = await pollReviewTaskStatus(taskId, options.onStatus)
  return {
    created,
    finalStatus: task?.status,
    task
  }
}

export async function pollReviewTaskStatus(taskId, onStatus) {
  let latestTask = null
  for (let i = 0; i < MAX_POLL_COUNT; i++) {
    await new Promise((resolve) => window.setTimeout(resolve, POLL_INTERVAL_MS))
    latestTask = await getReviewTask(taskId).catch(() => null)
    if (!latestTask) {
      continue
    }

    onStatus?.(latestTask)
    saveRecentTask(latestTask)

    if (!POLLING_STATUSES.includes(latestTask.status)) {
      return latestTask
    }
  }

  return latestTask
}
