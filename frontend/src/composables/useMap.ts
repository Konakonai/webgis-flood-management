import { computed } from 'vue'
import { useMapStore } from '../store/map'
import type { Map } from 'maplibre-gl'

export interface LayerStyleOpts {
  id: string
  type: 'fill' | 'line' | 'circle' | 'raster' | 'symbol'
  paint?: any
  layout?: any
  filter?: any
  beforeId?: string
}

export interface RegisterLayerOpts {
  name: string
  type: 'geojson' | 'raster'
  source: any
  layers: LayerStyleOpts[]
}

export function useMap() {
  const mapStore = useMapStore()

  const map = computed(() => mapStore.mapInstance as Map)
  const isLoaded = computed(() => mapStore.isMapLoaded)
  const activeLayers = computed(() => mapStore.activeLayers)

  /**
   * 动态注册图层并同步到 Pinia 状态管理器
   * @param id 图层唯一标识
   * @param opts 图层配置（包含数据源、渲染风格层等）
   */
  const registerLayer = (id: string, opts: RegisterLayerOpts) => {
    const mapInst = mapStore.mapInstance
    if (!mapInst) {
      console.warn(`[MapManager] 注册图层失败: 地图实例尚未初始化。`)
      return
    }

    try {
      // 1. 注册数据源（若已存在则不重复注册）
      if (!mapInst.getSource(id)) {
        mapInst.addSource(id, opts.source)
      }

      // 2. 依次添加具体的渲染风格层
      const layerIds: string[] = []
      opts.layers.forEach(styleLayer => {
        if (!mapInst.getLayer(styleLayer.id)) {
          mapInst.addLayer({
            id: styleLayer.id,
            source: id,
            type: styleLayer.type,
            paint: styleLayer.paint || {},
            layout: styleLayer.layout || {},
            filter: styleLayer.filter
          } as any, styleLayer.beforeId)
          layerIds.push(styleLayer.id)
        } else {
          layerIds.push(styleLayer.id)
        }
      })

      // 3. 同步到 Pinia store，以便控制面板调用
      mapStore.addLayerToStore({
        id,
        name: opts.name,
        visible: true,
        opacity: 1.0,
        type: opts.type,
        sourceId: id,
        layerIds
      })

      console.log(`[MapManager] 成功注册图层: ${opts.name} (${id})`)
    } catch (e) {
      console.error(`[MapManager] 注册图层发生错误:`, e)
    }
  }

  /**
   * 动态移除图层并从 Pinia 状态中卸载
   * @param id 图层唯一标识
   */
  const removeLayer = (id: string) => {
    const mapInst = mapStore.mapInstance
    if (!mapInst) return

    try {
      const layer = mapStore.activeLayers.find(l => l.id === id)
      if (layer) {
        // 1. 移除相关的具体渲染风格层
        layer.layerIds.forEach(layerId => {
          if (mapInst.getLayer(layerId)) {
            mapInst.removeLayer(layerId)
          }
        })

        // 2. 移除数据源
        if (mapInst.getSource(layer.sourceId)) {
          mapInst.removeSource(layer.sourceId)
        }

        // 3. 从 Pinia 状态管理器中清除
        mapStore.removeLayerFromStore(id)
        console.log(`[MapManager] 成功卸载图层: ${layer.name} (${id})`)
      }
    } catch (e) {
      console.error(`[MapManager] 卸载图层发生错误:`, e)
    }
  }

  /**
   * 切换图层显隐状态
   */
  const toggleVisibility = (id: string, visible: boolean) => {
    mapStore.updateLayerVisibility(id, visible)
  }

  /**
   * 更新图层透明度
   */
  const updateOpacity = (id: string, opacity: number) => {
    mapStore.updateLayerOpacity(id, opacity)
  }

  return {
    map,
    isLoaded,
    activeLayers,
    registerLayer,
    removeLayer,
    toggleVisibility,
    updateOpacity
  }
}
