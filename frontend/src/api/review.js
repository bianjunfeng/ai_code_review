import axios from 'axios'

const http = axios.create({
  baseURL: '',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

export const mockReport = {
  taskId: 1,
  prInfo: {
    title: 'fix login token validation and refresh flow',
    author: 'demo-user',
    url: 'https://github.com/owner/repo/pull/12',
    changedFiles: 6,
    additions: 186,
    deletions: 48
  },
  summary:
    '本次 PR 主要调整登录认证链路，新增 JWT 工具类和刷新 token 逻辑，同时修改用户接口返回结构。整体改动集中在认证模块，存在密钥管理、异常分支覆盖和回归测试不足等风险。',
  riskScore: 78,
  riskLevel: 'HIGH',
  mainChanges: [
    '新增 JwtUtil 工具类，负责 token 签发、解析和过期校验',
    '修改 LoginService 登录逻辑，引入 refresh token 分支',
    '调整 UserController 返回结构，统一登录成功响应字段',
    '补充部分认证失败提示，但测试覆盖仍不完整'
  ],
  riskItems: [
    {
      filePath: 'src/main/java/com/demo/auth/JwtUtil.java',
      riskLevel: 'HIGH',
      riskType: 'SECURITY',
      description: 'JWT 密钥存在硬编码风险。',
      reason: '密钥直接写在源码中，公开仓库或日志泄露时会导致 token 可被伪造。',
      suggestion: '建议改为从环境变量或安全配置中心读取，并区分本地、测试和生产环境。',
      confidence: 'HIGH',
      comment: '建议不要在源码中硬编码 JWT 密钥，可以改为从环境变量或安全配置中心读取。'
    },
    {
      filePath: 'src/main/java/com/demo/service/LoginService.java',
      riskLevel: 'MEDIUM',
      riskType: 'BUG_RISK',
      description: 'refresh token 过期后仍可能继续执行用户信息查询。',
      reason: '当前异常分支只记录错误，没有及时中断后续流程。',
      suggestion: '建议在 token 校验失败时直接返回明确业务异常，避免继续访问用户上下文。',
      confidence: 'MEDIUM',
      comment: 'refresh token 校验失败后应立即中断流程，避免继续查询用户上下文导致错误状态。'
    },
    {
      filePath: 'src/test/java/com/demo/service/LoginServiceTest.java',
      riskLevel: 'LOW',
      riskType: 'TEST_RISK',
      description: '测试只覆盖登录成功路径。',
      suggestion: '建议补充 token 过期、签名错误、用户不存在和密码错误等分支测试。',
      confidence: 'MEDIUM',
      comment: '建议补充认证失败和 token 过期场景的单元测试，降低后续回归风险。'
    }
  ],
  testSuggestions: [
    '建议补充 token 过期场景测试，验证系统返回明确错误信息。',
    '建议补充 refresh token 签名错误场景测试。',
    '建议补充登录失败、用户不存在、密码错误等异常分支测试。',
    '建议补充 UserController 返回结构的接口回归测试。'
  ],
  finalReview: '建议修改高风险问题后再合并。'
}

export async function analyzeReview(prUrl) {
  const response = await http.post('/api/reviews/analyze', { prUrl })
  return unwrapReviewReport(response.data)
}

export function unwrapReviewReport(payload) {
  if (!payload) {
    return null
  }

  if (payload.data && (Object.hasOwn(payload, 'success') || Object.hasOwn(payload, 'code'))) {
    return payload.data
  }

  if (payload.data && payload.message && !payload.prInfo) {
    return payload.data
  }

  return payload
}
