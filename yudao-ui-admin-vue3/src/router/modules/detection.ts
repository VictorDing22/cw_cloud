import { Layout } from '@/utils/routerHelper'

const detection: AppRouteRecordRaw = {
  path: '/detection',
  component: Layout,
  name: 'Detection',
  meta: {
    title: '数据源检测',
    icon: 'ep:opportunity'
  },
  children: [
    {
      path: '',
      component: () => import('@/views/detection/index.vue'),
      name: 'DetectionIndex',
      meta: { title: '数据源检测', noCache: true, icon: 'ep:cpu' }
    }
  ]
}

export default detection
