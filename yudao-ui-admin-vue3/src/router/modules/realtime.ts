import { Layout } from '@/utils/routerHelper'

const realtime: AppRouteRecordRaw = {
  path: '/realtime',
  component: Layout,
  name: 'Realtime',
  meta: {
    title: '实时监控',
    icon: 'ep:monitor'
  },
  children: [
    {
      path: 'monitor',
      component: () => import('@/views/realtime/FilterMonitor.vue'),
      name: 'FilterMonitor',
      meta: { title: '实时监控', noCache: true, icon: 'ep:data-line' }
    },
    {
      path: 'distributed',
      component: () => import('@/views/realtime/DistributedFilterMonitor.vue'),
      name: 'DistributedFilterMonitor',
      meta: { title: '分布式监控', noCache: true, icon: 'ep:connection' }
    },
    {
      path: 'history',
      component: () => import('@/views/realtime/TDMSSignalViewer.vue'),
      name: 'TDMSSignalViewer',
      meta: { title: '历史分析', noCache: true, icon: 'ep:document' }
    },
    {
      path: 'backend',
      component: () => import('@/views/realtime/BackendFilter.vue'),
      name: 'BackendFilter',
      meta: { title: 'Backend服务', noCache: true, icon: 'ep:cpu' }
    },
    {
      path: 'status',
      component: () => import('@/views/realtime/SystemStatus.vue'),
      name: 'SystemStatus',
      meta: { title: '系统状态', noCache: true, icon: 'ep:circle-check' }
    }
  ]
}

export default realtime
