import type { FeatureCollection } from 'geojson'

// 离线交付所需的徐州市业务底图数据；这些要素用于地图基础展示，不替代后端业务数据。
export const xuzhouBoundary: FeatureCollection = {
  type: 'FeatureCollection',
  features: [
    {
      type: 'Feature',
      properties: {
        name: '徐州市核心城区',
        code: '320300',
        area: '约150平方公里',
        population: '约120万人',
        manager: '徐州市防汛指挥部'
      },
      geometry: {
        type: 'Polygon',
        coordinates: [
          [
            [117.08, 34.20],
            [117.16, 34.18],
            [117.28, 34.18],
            [117.32, 34.22],
            [117.32, 34.30],
            [117.24, 34.34],
            [117.12, 34.34],
            [117.06, 34.28],
            [117.08, 34.20]
          ]
        ]
      }
    }
  ]
}

// 模拟积水点和水位监测站
export const waterStations: FeatureCollection = {
  type: 'FeatureCollection',
  features: [
    {
      type: 'Feature',
      properties: {
        id: 'S001',
        name: '云龙湖水位监测站',
        type: '河湖监测点',
        waterLevel: '31.25 m',
        warningLevel: '32.00 m',
        flowRate: '5.2 m³/s',
        status: '正常',
        time: '2026-06-23 15:30',
        address: '云龙湖东岸大坝'
      },
      geometry: {
        type: 'Point',
        coordinates: [117.142, 34.235]
      }
    },
    {
      type: 'Feature',
      properties: {
        id: 'S002',
        name: '奎河排水枢纽监测点',
        type: '河湖监测点',
        waterLevel: '28.85 m',
        warningLevel: '28.50 m',
        flowRate: '18.4 m³/s',
        status: '超警戒',
        time: '2026-06-23 15:30',
        address: '解放南路与奎河交叉口'
      },
      geometry: {
        type: 'Point',
        coordinates: [117.182, 34.248]
      }
    },
    {
      type: 'Feature',
      properties: {
        id: 'S003',
        name: '徐州东站地下立交积水点',
        type: '道路积水点',
        waterLevel: '0.12 m',
        warningLevel: '0.15 m',
        flowRate: '0.00',
        status: '预警',
        time: '2026-06-23 15:30',
        address: '徐州东站落客平台下方匝道'
      },
      geometry: {
        type: 'Point',
        coordinates: [117.291, 34.262]
      }
    },
    {
      type: 'Feature',
      properties: {
        id: 'S004',
        name: '彭城广场商圈排水监测站',
        type: '道路积水点',
        waterLevel: '0.02 m',
        warningLevel: '0.10 m',
        flowRate: '0.00',
        status: '正常',
        time: '2026-06-23 15:30',
        address: '彭城广场地铁3号出口旁'
      },
      geometry: {
        type: 'Point',
        coordinates: [117.186, 34.263]
      }
    },
    {
      type: 'Feature',
      properties: {
        id: 'S005',
        name: '京杭运河徐州段港口水位站',
        type: '河湖监测点',
        waterLevel: '22.40 m',
        warningLevel: '23.00 m',
        flowRate: '125.0 m³/s',
        status: '正常',
        time: '2026-06-23 15:30',
        address: '金山桥开发区大运河码头'
      },
      geometry: {
        type: 'Point',
        coordinates: [117.288, 34.312]
      }
    }
  ]
}

// 常用徐州地标（本地地理编码搜索备用池）
export interface SearchLocation {
  name: string
  address: string
  lng: number
  lat: number
  type: string
}

export const xuzhouLandmarks: SearchLocation[] = [
  { name: '云龙湖景区', address: '徐州市泉山区金山路', lng: 117.142, lat: 34.235, type: '景区' },
  { name: '彭城广场', address: '徐州市鼓楼区中山北路', lng: 117.186, lat: 34.263, type: '商圈' },
  { name: '徐州东站', address: '徐州市鼓楼区站东路', lng: 117.291, lat: 34.262, type: '交通枢纽' },
  { name: '徐州火车站', address: '徐州市鼓楼区大马路', lng: 117.202, lat: 34.269, type: '交通枢纽' },
  { name: '中国矿业大学(南湖校区)', address: '徐州市铜山区大学路1号', lng: 117.142, lat: 34.168, type: '高校' },
  { name: '江苏师范大学(泉山校区)', address: '徐州市泉山区上海路101号', lng: 117.188, lat: 34.218, type: '高校' },
  { name: '徐州市人民政府', address: '徐州市云龙区昆仑大道1号', lng: 117.266, lat: 34.219, type: '政府机构' },
  { name: '徐州市防汛指挥部', address: '徐州市泉山区建国西路80号', lng: 117.172, lat: 34.258, type: '政府机构' },
  { name: '云龙山', address: '徐州市泉山区和平路', lng: 117.171, lat: 34.249, type: '景区' },
  { name: '快哉亭公园', address: '徐州市云龙区解放路', lng: 117.195, lat: 34.259, type: '公园' },
  { name: '金山桥开发区管委会', address: '徐州市鼓楼区金源路', lng: 117.258, lat: 34.298, type: '政府机构' }
]
