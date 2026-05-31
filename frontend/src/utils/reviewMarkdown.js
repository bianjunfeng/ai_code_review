export function buildReviewMarkdown(report, comments = []) {
  const safeReport = report || {}
  const prInfo = safeReport.prInfo || {}
  const riskItems = Array.isArray(comments) && comments.length > 0
    ? comments
    : Array.isArray(safeReport.riskItems)
      ? safeReport.riskItems
      : []
  const testSuggestions = Array.isArray(safeReport.testSuggestions) ? safeReport.testSuggestions : []
  const mainChanges = Array.isArray(safeReport.mainChanges) ? safeReport.mainChanges : []

  const lines = [
    '## AI Review 总结',
    '',
    `风险等级：${safeReport.riskLevel || '-'}`,
    `风险分数：${safeReport.riskScore ?? '-'}`,
    `文件统计：总数 ${safeReport.totalFileCount ?? 0}，已分析 ${safeReport.analyzedFileCount ?? 0}，跳过 ${safeReport.skippedFileCount ?? 0}，截断 ${safeReport.truncatedFileCount ?? 0}，失败 ${safeReport.failedFileCount ?? 0}`,
    '',
    `PR：${prInfo.title || '未返回标题'}`,
    `作者：${prInfo.author || '-'}`,
    ''
  ]

  if (safeReport.summary) {
    lines.push('### 总结', '', safeReport.summary, '')
  }

  if (mainChanges.length > 0) {
    lines.push('### 主要变更', '')
    mainChanges.forEach((item) => lines.push(`- ${item}`))
    lines.push('')
  }

  if (riskItems.length > 0) {
    lines.push('### Review 建议', '')
    riskItems.forEach((item, index) => {
      lines.push(`#### ${index + 1}. [${item.riskLevel || 'INFO'}] ${item.title || '未命名建议'}`)
      if (item.filePath) {
        lines.push(`文件：\`${item.filePath}\``)
      }
      if (item.line || item.lineNumber) {
        lines.push(`行号：${item.line || item.lineNumber}`)
      }
      if (item.description) {
        lines.push('', item.description)
      }
      if (item.reason) {
        lines.push('', `依据：${item.reason}`)
      }
      if (item.evidence) {
        lines.push('', `证据：${item.evidence}`)
      }
      if (item.actionLevel) {
        lines.push('', `处理级别：${item.actionLevel}`)
      }
      if (item.suggestion) {
        lines.push('', `建议：${item.suggestion}`)
      }
      lines.push('')
    })
  } else {
    lines.push('### Review 建议', '', '暂无明确风险建议。', '')
  }

  if (testSuggestions.length > 0) {
    lines.push('### 测试建议', '')
    testSuggestions.forEach((item) => lines.push(`- ${item}`))
    lines.push('')
  }

  if (safeReport.finalReview) {
    lines.push('### 最终结论', '', safeReport.finalReview)
  }

  return lines.join('\n').trim()
}
