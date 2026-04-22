import { Layout } from '@/utils/routerHelper'
import type { AppRouteRecordRaw } from '@/router/types'

const filterRouter: AppRouteRecordRaw = {
  path: '/filter',
  component: Layout,
  name: 'Filter',
  meta: {
    title: '自适应滤波器',
    icon: 'ep:cpu',
    alwaysShow: true
  },
  children: [
    {
      path: 'adaptive',
      component: () => import('@/views/filter/AdaptiveFilter.vue'),
      name: 'AdaptiveFilter',
      meta: {
        title: '滤波器实验',
        icon: 'ep:data-analysis',
        noCache: false
      }
    },
    {
      path: 'comparison',
      component: () => import('@/views/filter/FilterComparison.vue'),
      name: 'FilterComparison',
      meta: {
        title: '算法对比',
        icon: 'ep:trend-charts',
        noCache: false
      }
    }
  ]
}

export default filterRouter
