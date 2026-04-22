<template>
  <el-card shadow="hover" class="status-card" :body-style="{ padding: '15px' }">
    <div class="card-content">
      <div class="label">{{ label }}</div>
      <div class="value" :style="{ color: color }">
        {{ value }} <span class="unit">{{ unit }}</span>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
defineProps({
  label: String,
  value: [String, Number],
  unit: String,
  color: { type: String, default: '#fff' }
})
</script>

<style scoped>
.status-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #fff;
}
.label {
  font-size: 14px;
  color: #aaa;
  margin-bottom: 8px;
}
.value {
  font-size: 24px;
  font-weight: bold;
}
.unit {
  font-size: 12px;
  margin-left: 4px;
}
</style>
