const PR_URL_PATTERN = /^https:\/\/github\.com\/([a-zA-Z0-9_.-]+)\/([a-zA-Z0-9_.-]+)\/pull\/(\d+)\/?$/i

export function parsePrUrl(value) {
  const match = String(value || '').trim().match(PR_URL_PATTERN)
  if (!match) {
    return null
  }

  const pullNumber = Number(match[3])
  if (!Number.isInteger(pullNumber) || pullNumber < 1 || pullNumber > 999999) {
    return null
  }

  return {
    owner: match[1],
    repo: match[2],
    pullNumber,
    prUrl: buildPrUrl(match[1], match[2], pullNumber)
  }
}

export function isValidPrUrl(value) {
  return Boolean(parsePrUrl(value))
}

export function buildPrUrl(owner, repo, pullNumber) {
  if (!owner || !repo || !pullNumber) {
    return ''
  }
  return `https://github.com/${owner}/${repo}/pull/${pullNumber}`
}
