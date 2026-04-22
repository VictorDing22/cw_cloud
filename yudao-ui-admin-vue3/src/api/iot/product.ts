import request from '@/config/axios'

// ==================== 产品管理相关接口 ====================

export interface ProductQuery {
  pageNo: number
  pageSize: number
  name?: string
}

export interface ProductVO {
  id: string
  name: string
  description?: string
  createTime: string
}

// 获取产品列表
export const getProductList = (params: ProductQuery) => {
  return request.get<{ list: ProductVO[]; total: number }>({
    url: '/iot/product/list',
    params
  })
}

// 获取产品详情
export const getProductDetail = (id: string) => {
  return request.get<ProductVO>({
    url: `/iot/product/${id}`
  })
}

