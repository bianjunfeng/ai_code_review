const STORAGE_KEY = 'ai-pr-review.recentTasks'
const MAX_RECENT_TASKS = 30

export function loadRecentTasks() {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function saveRecentTask(task) {
  if (!task?.taskId) {
    return loadRecentTasks()
  }

  const current = loadRecentTasks()
  const normalized = normalizeTask(task)
  const merged = [normalized, ...current.filter((item) => item.taskId !== normalized.taskId)]
  const next = merged.slice(0, MAX_RECENT_TASKS)
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  return next
}

export function removeRecentTask(taskId) {
  const next = loadRecentTasks().filter((item) => item.taskId !== taskId)
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  return next
}

export function clearRecentTasks() {
  window.localStorage.removeItem(STORAGE_KEY)
  return []
}

export function normalizeTask(task) {
  return {
    taskId: task.taskId,
    prUrl: task.prUrl || task.prInfo?.url || '',
    ownerName: task.ownerName || task.owner || '',
    repoName: task.repoName || task.repo || '',
    pullNumber: task.pullNumber || task.prNumber || task.prInfo?.pullNumber || '',
    prTitle: task.prTitle || task.title || task.prInfo?.title || '未返回 PR 标题',
    author: task.author || task.prAuthor || task.prInfo?.author || '',
    sourceBranch: task.sourceBranch || task.prInfo?.sourceBranch || '',
    targetBranch: task.targetBranch || task.prInfo?.targetBranch || '',
    status: task.status || 'PENDING',
    riskScore: task.riskScore ?? null,
    riskLevel: task.riskLevel || '',
    modelName: task.modelName || '',
    promptVersion: task.promptVersion || '',
    cached: Boolean(task.cached),
    cachedFromTaskId: task.cachedFromTaskId || null,
    errorMessage: task.errorMessage || '',
    createdAt: task.createdAt || new Date().toLocaleString(),
    updatedAt: task.updatedAt || new Date().toLocaleString()
  }
}
