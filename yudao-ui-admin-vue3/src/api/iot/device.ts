import request from '@/config/axios'

// ==================== 设备管理相关接口 ====================

export interface DeviceQuery {
  pageNo: number
  pageSize: number
  deviceName?: string
  deviceKey?: string
  productId?: string
  status?: string
}

export interface DeviceVO {
  id: number
  deviceKey: string
  deviceName: string
  productId: string
  productName: string
  status: string // online, offline, warning
  version: string
  createTime: string
  updateTime: string
}

// 获取设备列表
export const getDeviceList = (params: DeviceQuery) => {
  return request.get<{ list: DeviceVO[]; total: number }>({
    url: '/iot/device/list',
    params
  })
}

// 获取设备详情
export const getDeviceDetail = (id: number) => {
  return request.get<DeviceVO>({
    url: `/iot/device/${id}`
  })
}

// 创建设备
export const createDevice = (data: any) => {
  return request.post({
    url: '/iot/device/create',
    data
  })
}

// 更新设备
export const updateDevice = (data: any) => {
  return request.put({
    url: '/iot/device/update',
    data
  })
}

// 删除设备
export const deleteDevice = (ids: number[]) => {
  return request.delete({
    url: '/iot/device/delete',
    data: { ids }
  })
}

