<template>
  <el-tag :type="tagType" effect="light" round>{{ label }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: {
    type: String,
    default: ''
  }
})

const normalized = computed(() => String(props.status || 'UNKNOWN').toUpperCase())

const label = computed(() => {
  const labels = {
    PENDING: '等待中',
    FETCHING_PR: '获取 PR',
    PARSING_DIFF: '解析 Diff',
    REVIEWING: 'AI 评审',
    SUMMARIZING: '生成总结',
    SCORING: '计算评分',
    SUCCESS: '成功',
    FAILED: '失败',
    CANCELLED: '已取消',
    UNKNOWN: '未知'
  }
  return labels[normalized.value] || normalized.value
})

const tagType = computed(() => {
  if (normalized.value === 'SUCCESS') return 'success'
  if (normalized.value === 'FAILED' || normalized.value === 'CANCELLED') return 'danger'
  if (normalized.value === 'PENDING') return 'info'
  return 'warning'
})
</script>
