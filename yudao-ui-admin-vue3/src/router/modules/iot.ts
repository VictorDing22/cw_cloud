import { Layout } from '@/utils/routerHelper'
import type { AppRouteRecordRaw } from '@/router/types'

const iotRouter: AppRouteRecordRaw = {
  path: '/iot-product',
  component: Layout,
  name: 'IotProduct',
  meta: {
    title: '物联网产品',
    icon: 'ep:box',
    alwaysShow: true
  },
  children: [
    {
      path: 'device-group',
      component: () => import('@/views/iot/deviceGroup/index.vue'),
      name: 'IotDeviceGroup',
      meta: {
        title: '设备分组',
        icon: 'ep:collection-tag'
      }
    },
    {
      path: 'device-manage',
      component: () => import('@/views/iot/device/index.vue'),
      name: 'IotDevice',
      meta: {
        title: '设备管理',
        icon: 'ep:monitor'
      }
    }
  ]
}

const iotDataRouter: AppRouteRecordRaw = {
  path: '/iot-data',
  component: Layout,
  name: 'IotData',
  meta: {
    title: '物联网数据',
    icon: 'ep:data-board',
    alwaysShow: true
  },
  children: [
    {
      path: 'sound-emission',
      component: () => import('@/views/filter/IndustrialMonitor.vue'),
      name: 'IndustrialMonitor',
      meta: {
        title: '声发射数据',
        icon: 'ep:pie-chart'
      }
    },
    {
      path: 'sound-rating',
      component: () => import('@/views/iot/soundRating/index.vue'),
      name: 'IotSoundRating',
      meta: {
        title: '声发射评级',
        icon: 'ep:star'
      }
    },
    {
      path: 'vibration',
      component: () => import('@/views/iot/vibration/index.vue'),
      name: 'IotVibration',
      meta: {
        title: '振动数据',
        icon: 'ep:odometer'
      }
    },
    {
      path: 'correlation',
      component: () => import('@/views/iot/correlation/index.vue'),
      name: 'IotCorrelation',
      meta: {
        title: '相关图',
        icon: 'ep:connection'
      }
    },
    {
      path: 'pattern',
      component: () => import('@/views/iot/pattern/index.vue'),
      name: 'IotPattern',
      meta: {
        title: '模式识别',
        icon: 'ep:grid'
      }
    },
    {
      path: 'm2v',
      component: () => import('@/views/iot/m2v/index.vue'),
      name: 'IotM2V',
      meta: {
        title: 'M2V数据',
        icon: 'ep:cpu'
      }
    },
    {
      path: 'compare',
      component: () => import('@/views/iot/data/compare.vue'),
      name: 'IotDataCompare',
      meta: {
        title: '参数数据对比',
        icon: 'ep:data-analysis'
      }
    }
  ]
}

const iotAlertRouter: AppRouteRecordRaw = {
  path: '/iot-alert',
  component: Layout,
  name: 'IotAlertManage',
  meta: {
    title: '告警管理',
    icon: 'ep:bell',
    alwaysShow: true
  },
  children: [
    {
      path: 'list',
      component: () => import('@/views/iot/alert/index.vue'),
      name: 'IotAlert',
      meta: {
        title: '告警管理',
        icon: 'ep:bell'
      }
    },
    {
      path: 'user',
      component: () => import('@/views/iot/alert/user.vue'),
      name: 'IotAlertUser',
      meta: {
        title: '告警用户',
        icon: 'ep:user'
      }
    },
    {
      path: 'scene',
      component: () => import('@/views/iot/alert/scene.vue'),
      name: 'IotAlertScene',
      meta: {
        title: '告警场景',
        icon: 'ep:setting'
      }
    },
    {
      path: 'message',
      component: () => import('@/views/iot/alert/message.vue'),
      name: 'IotAlertMessage',
      meta: {
        title: '用户消息',
        icon: 'ep:message'
      }
    },
    {
      path: 'log',
      component: () => import('@/views/iot/alert/log.vue'),
      name: 'IotAlertLog',
      meta: {
        title: '告警日志',
        icon: 'ep:document'
      }
    }
  ]
}

const iotToolsRouter: AppRouteRecordRaw = {
  path: '/iot-tools',
  component: Layout,
  name: 'IotToolsManage',
  meta: {
    title: '物联网工具',
    icon: 'ep:tools'
  },
  children: [
    {
      path: '',
      component: () => import('@/views/iot/tools/index.vue'),
      name: 'IotTools',
      meta: {
        title: '物联网工具',
        icon: 'ep:tools'
      }
    }
  ]
}

const iotApplicationRouter: AppRouteRecordRaw = {
  path: '/iot-application',
  component: Layout,
  name: 'IotApplicationManage',
  meta: {
    title: '物联网应用',
    icon: 'ep:histogram'
  },
  children: [
    {
      path: '',
      component: () => import('@/views/iot/application/index.vue'),
      name: 'IotApplication',
      meta: {
        title: '物联网应用',
        icon: 'ep:histogram'
      }
    }
  ]
}

export default [iotRouter, iotDataRouter, iotAlertRouter, iotToolsRouter, iotApplicationRouter]
