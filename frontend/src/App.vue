<template>
  <Toast position="top-right" />
  <router-view />
</template>

<script setup lang="ts">
import Toast from 'primevue/toast'
</script>

<style>
@import '@/styles/variables.css';

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  width: 100%;
  overflow: hidden;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif;
  color: var(--text-primary);
  background: var(--bg-main);
}
</style>
