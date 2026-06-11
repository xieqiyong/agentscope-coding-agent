import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/WorkspaceSelectView.vue'),
    },
    {
      path: '/ws/:workspaceId',
      name: 'workspace',
      component: () => import('@/views/AgentWorkspaceView.vue'),
    },
    {
      path: '/ws/:workspaceId/s/:sessionId',
      name: 'workspace-session',
      component: () => import('@/views/AgentWorkspaceView.vue'),
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/SettingsView.vue'),
    },
  ],
})

export default router
