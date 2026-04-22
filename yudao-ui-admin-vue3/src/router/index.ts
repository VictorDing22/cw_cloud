import type { App } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { createRouter, createWebHistory } from 'vue-router'
import remainingRouter from './modules/remaining'
import filterRouter from './modules/filter'
import iotRouters from './modules/iot'
import realtimeRouter from './modules/realtime'
import monitorRouter from './modules/monitor'
import detectionRouter from './modules/detection'

// 创建路由实例
// 注意：remaining 内含 /:pathMatch(.*)* 兜底 404，必须排在自定义业务路由之后，
// 否则 /detection 等路径会先命中 404，导致无法进入真实页面、也无法按预期走登录前逻辑。
const router = createRouter({
  history: createWebHistory(import.meta.env.VITE_BASE_PATH), // createWebHashHistory URL带#，createWebHistory URL不带#
  strict: true,
  routes: [detectionRouter, ...remainingRouter, filterRouter, ...iotRouters, realtimeRouter, monitorRouter] as RouteRecordRaw[],
  scrollBehavior: () => ({ left: 0, top: 0 })
})

export const resetRouter = (): void => {
  const resetWhiteNameList = ['Redirect', 'Login', 'NoFound', 'Home', 'Detection', 'DetectionIndex']
  router.getRoutes().forEach((route) => {
    const { name } = route
    if (name && !resetWhiteNameList.includes(name as string)) {
      router.hasRoute(name) && router.removeRoute(name)
    }
  })
}

export const setupRouter = (app: App<Element>) => {
  app.use(router)
}

export default router
