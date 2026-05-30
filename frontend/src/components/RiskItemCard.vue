<template>
  <article class="risk-item" :class="levelClass">
    <div class="risk-head">
      <div class="risk-tags">
        <el-tag :type="tagType" effect="dark">{{ level }}</el-tag>
        <el-tag effect="plain">{{ item.riskType || 'UNKNOWN' }}</el-tag>
        <el-tag v-if="item.confidence != null" type="info" effect="plain">
          置信度 {{ formatConfidence(item.confidence) }}
        </el-tag>
      </div>
      <el-button :icon="CopyDocument" plain @click="copyComment">复制评论</el-button>
    </div>

    <div class="file-path mono">{{ item.filePath || '未返回文件路径' }}</div>

    <div class="risk-content">
      <section>
        <h4>问题描述</h4>
        <p>{{ item.description || '暂无描述' }}</p>
      </section>

      <section v-if="item.reason">
        <h4>原因分析</h4>
        <p>{{ item.reason }}</p>
      </section>

      <section>
        <h4>修改建议</h4>
        <p>{{ item.suggestion || '暂无建议' }}</p>
      </section>

      <section v-if="item.comment" class="comment-box">
        <h4>可复制 Review 评论</h4>
        <p>{{ item.comment }}</p>
      </section>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'

const props = defineProps({
  item: {
    type: Object,
    default: () => ({})
  }
})

const level = computed(() => String(props.item.riskLevel || 'LOW').toUpperCase())

const tagType = computed(() => {
  if (level.value === 'CRITICAL' || level.value === 'HIGH') {
    return 'danger'
  }
  if (level.value === 'MEDIUM') {
    return 'warning'
  }
  if (level.value === 'LOW') {
    return 'success'
  }
  return 'info'
})

const levelClass = computed(() => {
  return `risk-item--${level.value.toLowerCase()}`
})

function formatConfidence(value) {
  const num = Number(value)
  if (Number.isNaN(num)) {
    return 'N/A'
  }
  return Math.round(num * 100) + '%'
}

async function copyComment() {
  let text = props.item.comment
  if (!text) {
    const parts = [props.item.title, props.item.description, props.item.suggestion].filter(Boolean)
    text = parts.join('\n\n')
  }

  if (!text) {
    ElMessage.warning('暂无可复制评论')
    return
  }

  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('复制成功')
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.setAttribute('readonly', 'readonly')
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('复制成功')
  }
}
</script>

<style scoped>
.risk-item {
  padding: 16px;
  background: #ffffff;
  border: 1px solid #e2e8f2;
  border-left-width: 5px;
  border-radius: 8px;
}

.risk-item--high {
  border-left-color: #d93025;
}

.risk-item--critical {
  border-left-color: #8f1d18;
}

.risk-item--medium {
  border-left-color: #e68a00;
}

.risk-item--low {
  border-left-color: #138a56;
}

.risk-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.risk-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.file-path {
  margin-top: 14px;
  padding: 10px 12px;
  overflow: hidden;
  color: #24456f;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: #f5f8fc;
  border: 1px solid #dce5f1;
  border-radius: 8px;
  font-size: 13px;
}

.risk-content {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

section {
  padding: 12px;
  background: #fbfcfe;
  border: 1px solid #edf1f7;
  border-radius: 8px;
}

h4 {
  margin: 0 0 6px;
  color: #172033;
  font-size: 14px;
}

p {
  margin: 0;
  color: #42526a;
  line-height: 1.7;
}

.comment-box {
  background: #f8fbff;
  border-color: #dbe9ff;
}

@media (max-width: 640px) {
  .risk-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .risk-head :deep(.el-button) {
    width: 100%;
  }
}
</style>
