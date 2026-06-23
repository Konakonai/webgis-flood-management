// src/mock/api.ts
import * as turf from '@turf/turf'
import type { FeatureCollection } from 'geojson'

export interface ResourcePoint {
  id: string
  name: string
  type: 'resource'
  lng: number
  lat: number
  address: string
  status: '充足' | '紧张'
  details: string
}

export interface PumpStation {
  id: string
  name: string
  type: 'pump'
  lng: number
  lat: number
  address: string
  vehicle: string
  contact: string
  status: '空闲' | '已派发'
}

export const mockResources: ResourcePoint[] = [
  {
    id: 'R001',
    name: '徐州市鼓楼防汛物资储备库',
    type: 'resource',
    lng: 117.185,
    lat: 34.282,
    address: '鼓楼区中山北路大庆路口',
    status: '充足',
    details: '编织沙袋 5000个, 防汛吸水膨胀袋 1000个, 便携式抽水泵 8台, 应急救生衣 300件'
  },
  {
    id: 'R002',
    name: '云龙区防汛物资保障中心',
    type: 'resource',
    lng: 117.225,
    lat: 34.245,
    address: '云龙区青年路235号',
    status: '充足',
    details: '发电机组 4台, 应急照明灯 50套, 冲锋舟 3艘, 编织沙袋 3000个'
  },
  {
    id: 'R003',
    name: '泉山区防汛抢险应急物资储备点',
    type: 'resource',
    lng: 117.145,
    lat: 34.242,
    address: '泉山区金山路云龙湖南岸',
    status: '紧张',
    details: '沙袋 800个, 救生圈 150个, 柴油发电机 1台, 汽油抽水泵 2台'
  }
]

export const mockStations: PumpStation[] = [
  {
    id: 'P001',
    name: '金山桥防汛大功率泵车驻地',
    type: 'pump',
    lng: 117.252,
    lat: 34.285,
    address: '经济技术开发区杨山路12号',
    vehicle: '徐州防汛01号大型移动泵车 (3000 m³/h)',
    contact: '张铁柱 队长 (13812345678)',
    status: '空闲'
  },
  {
    id: 'P002',
    name: '矿大南湖防汛泵车驻车场',
    type: 'pump',
    lng: 117.152,
    lat: 34.185,
    address: '泉山区大学路中国矿业大学南湖校区西门',
    vehicle: '徐州防汛02号中型移动泵车 (1500 m³/h)',
    contact: '李建国 队长 (13987654321)',
    status: '空闲'
  }
]

// 空间查询模拟数据集
export const mockFacilities: FeatureCollection = {
  type: 'FeatureCollection',
  features: [
    {
      type: 'Feature',
      properties: {
        id: 'F001',
        name: '彭城广场地下通道积水点',
        type: 'waterlogging',
        typeName: '道路积水点',
        waterDepth: '0.25m',
        status: 'critical',
        statusName: '积水严重',
        manager: '鼓楼区排水处',
        phone: '13888889901',
        address: '徐州市鼓楼区中山北路与淮海路交叉口地下通道'
      },
      geometry: {
        type: 'Point',
        coordinates: [117.186, 34.263]
      }
    },
    {
      type: 'Feature',
      properties: {
        id: 'F002',
        name: '云龙湖东路防汛雨水泵站',
        type: 'pump',
        typeName: '雨水泵站',
        capacity: '5000 m³/h',
        status: 'running',
        statusName: '正在运行',
        manager: '泉山区水务局',
        phone: '13888889902',
        address: '徐州市泉山区金山路云龙湖东岸'
      },
      geometry: {
        type: 'Point',
        coordinates: [117.142, 34.235]
      }
    },
    {
      type: 'Feature',
      properties: {
        id: 'F003',
        name: '徐州东站落客平台下积水点',
        type: 'waterlogging',
        typeName: '道路积水点',
        waterDepth: '0.12m',
        status: 'warning',
        statusName: '轻度积水',
        manager: '开发区住建局',
        phone: '13888889903',
        address: '徐州市鼓楼区徐州东站东广场地下匝道'
      },
      geometry: {
        type: 'Point',
        coordinates: [117.291, 34.262]
      }
    },
    {
      type: 'Feature',
      properties: {
        id: 'F004',
        name: '矿大南湖校区东门雨水排涝站',
        type: 'pump',
        typeName: '雨水泵站',
        capacity: '3200 m³/h',
        status: 'running',
        statusName: '正在运行',
        manager: '铜山区水务局',
        phone: '13888889904',
        address: '徐州市铜山区大学路中国矿大南湖校区东门'
      },
      geometry: {
        type: 'Point',
        coordinates: [117.146, 34.169]
      }
    },
    {
      type: 'Feature',
      properties: {
        id: 'F005',
        name: '淮海西路立交桥下积水点',
        type: 'waterlogging',
        typeName: '道路积水点',
        waterDepth: '0.45m',
        status: 'critical',
        statusName: '严重积水',
        manager: '泉山区住建局',
        phone: '13888889905',
        address: '徐州市泉山区淮海西路立交桥下'
      },
      geometry: {
        type: 'Point',
        coordinates: [117.158, 34.268]
      }
    },
    {
      type: 'Feature',
      properties: {
        id: 'F006',
        name: '奎河解放路排水排涝泵站',
        type: 'pump',
        typeName: '雨水泵站',
        capacity: '8000 m³/h',
        status: 'standby',
        statusName: '设备待机',
        manager: '云龙区水务局',
        phone: '13888889906',
        address: '徐州市云龙区解放南路奎河大桥旁'
      },
      geometry: {
        type: 'Point',
        coordinates: [117.182, 34.248]
      }
    },
    {
      type: 'Feature',
      properties: {
        id: 'F007',
        name: '金山桥高铁桥下积水点',
        type: 'waterlogging',
        typeName: '道路积水点',
        waterDepth: '0.05m',
        status: 'normal',
        statusName: '无明显积水',
        manager: '开发区排水处',
        phone: '13888889907',
        address: '徐州市金山桥开发区高铁高架桥下方路段'
      },
      geometry: {
        type: 'Point',
        coordinates: [117.258, 34.298]
      }
    },
    {
      type: 'Feature',
      properties: {
        id: 'F008',
        name: '和平路立交桥雨水收集泵站',
        type: 'pump',
        typeName: '雨水泵站',
        capacity: '4500 m³/h',
        status: 'running',
        statusName: '正在运行',
        manager: '云龙区排水处',
        phone: '13888889908',
        address: '徐州市云龙区和平大道立交桥下'
      },
      geometry: {
        type: 'Point',
        coordinates: [117.228, 34.258]
      }
    }
  ]
}

// 拦截 window.fetch 实现透明的 Mock API
if (typeof window !== 'undefined') {
  const originalFetch = window.fetch
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString()
    
    if (url.includes('/api/resources')) {
      return new Response(JSON.stringify(mockResources), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    }
    
    if (url.includes('/api/stations')) {
      return new Response(JSON.stringify(mockStations), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    }

    if (url.includes('/api/spatial-query')) {
      if (init && init.method === 'POST' && init.body) {
        try {
          const body = JSON.parse(init.body as string)
          const bufferGeoJSON = body.bufferGeoJSON
          const filterName = body.name || ''
          const filterType = body.type || 'all'

          const filteredFeatures = mockFacilities.features.filter((feature: any) => {
            // 1. 按名称过滤
            if (filterName.trim() && !feature.properties.name.includes(filterName.trim())) {
              return false
            }
            // 2. 按类型过滤
            if (filterType !== 'all' && feature.properties.type !== filterType) {
              return false
            }
            // 3. 空间范围过滤 (包含缓冲区)
            if (bufferGeoJSON) {
              try {
                const pt = turf.point(feature.geometry.coordinates)
                const isInside = turf.booleanPointInPolygon(pt, bufferGeoJSON)
                if (!isInside) return false
              } catch (err) {
                console.error('[Mock API] 空间判断出错:', err)
                return false
              }
            }
            return true
          })

          const responseGeoJSON: FeatureCollection = {
            type: 'FeatureCollection',
            features: filteredFeatures
          }

          return new Response(JSON.stringify(responseGeoJSON), {
            status: 200,
            headers: { 'Content-Type': 'application/json' }
          })
        } catch (err) {
          return new Response(JSON.stringify({ error: 'Failed to parse request body or execute spatial query' }), {
            status: 400,
            headers: { 'Content-Type': 'application/json' }
          })
        }
      }
    }
    
    return originalFetch(input, init)
  }
}
