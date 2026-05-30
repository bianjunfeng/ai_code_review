<template>
  <el-tag :type="tagType" effect="dark" round>{{ normalizedLevel }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  riskLevel: {
    type: String,
    default: ''
  }
})

const normalizedLevel = computed(() => String(props.riskLevel || 'UNKNOWN').toUpperCase())

const tagType = computed(() => {
  if (normalizedLevel.value === 'CRITICAL' || normalizedLevel.value === 'HIGH') return 'danger'
  if (normalizedLevel.value === 'MEDIUM') return 'warning'
  if (normalizedLevel.value === 'LOW') return 'success'
  return 'info'
})
</script>
