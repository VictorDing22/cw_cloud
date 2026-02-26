import { Layout } from '@/utils/routerHelper'

const detection: AppRouteRecordRaw = {
  path: '/detection',
  component: Layout,
  name: 'Detection',
  meta: {
    title: '故障检测',
    icon: 'ep:opportunity'
  },
  children: [
    {
      path: '',
      component: () => import('@/views/detection/index.vue'),
      name: 'DetectionIndex',
      meta: { title: '声发射检测', noCache: true, icon: 'ep:cpu' }
    }
  ]
}

export default detection
