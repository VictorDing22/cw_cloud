import { Layout } from '@/utils/routerHelper'

const monitor: AppRouteRecordRaw = {
  path: '/monitor',
  component: Layout,
  name: 'Monitor',
  meta: {
    title: '实时监控',
    icon: 'ep:monitor'
  },
  children: [
    {
      path: 'realtime-detection',
      component: () => import('@/views/monitor/RealtimeDetection.vue'),
      name: 'RealtimeDetection',
      meta: { title: '实时检测', noCache: true, icon: 'ep:data-line' }
    },
    {
      path: 'history-analysis',
      component: () => import('@/views/monitor/HistoryAnalysis.vue'),
      name: 'HistoryAnalysis',
      meta: { title: '历史分析', noCache: true, icon: 'ep:histogram' }
    }
  ]
}

export default monitor
