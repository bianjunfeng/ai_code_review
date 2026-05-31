<template>
  <div class="stat-tile">
    <div class="stat-icon">
      <slot name="icon" />
    </div>
    <div>
      <div class="stat-label">{{ label }}</div>
      <div class="stat-value">{{ value }}</div>
      <div v-if="hint" class="stat-hint">{{ hint }}</div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  label: {
    type: String,
    required: true
  },
  value: {
    type: [String, Number],
    required: true
  },
  hint: {
    type: String,
    default: ''
  }
})
</script>

<style scoped>
.stat-tile {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  min-height: 92px;
  padding: 16px;
  background: #ffffff;
  border: 1px solid #e1e7f0;
  border-radius: 8px;
}

.stat-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  color: #1f63d8;
  background: #e8f1ff;
  border-radius: 8px;
}

.stat-icon :deep(svg) {
  width: 22px;
  height: 22px;
}

.stat-label {
  color: #69788d;
  font-size: 13px;
}

.stat-value {
  margin-top: 2px;
  color: #172033;
  font-size: 24px;
  font-weight: 800;
  line-height: 1.1;
}

.stat-hint {
  margin-top: 4px;
  color: #7b8798;
  font-size: 12px;
}
</style>
