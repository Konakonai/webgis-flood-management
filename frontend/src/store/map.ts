import { defineStore } from 'pinia'
import { ref, shallowRef } from 'vue'
import type { Map } from 'maplibre-gl'

export interface MapLayer {
  id: string
  name: string
  visible: boolean
  opacity: number
  type: 'geojson' | 'raster'
  sourceId: string
  layerIds: string[] // One source can have multiple style layers (e.g. line, fill, circle)
}

export const useMapStore = defineStore('map', () => {
  const mapInstance = shallowRef<Map | null>(null)
  const isMapLoaded = ref(false)
  const activeLayers = ref<MapLayer[]>([])

  const setMapInstance = (map: Map) => {
    mapInstance.value = map
  }

  const setMapLoaded = (loaded: boolean) => {
    isMapLoaded.value = loaded
  }

  const addLayerToStore = (layer: MapLayer) => {
    // Check if layer already exists in store
    const exists = activeLayers.value.some(l => l.id === layer.id)
    if (!exists) {
      activeLayers.value.push(layer)
    }
  }

  const removeLayerFromStore = (id: string) => {
    activeLayers.value = activeLayers.value.filter(l => l.id !== id)
  }

  const updateLayerVisibility = (id: string, visible: boolean) => {
    const layer = activeLayers.value.find(l => l.id === id)
    if (layer && mapInstance.value) {
      layer.visible = visible
      const visibilityValue = visible ? 'visible' : 'none'
      layer.layerIds.forEach(layerId => {
        if (mapInstance.value!.getLayer(layerId)) {
          mapInstance.value!.setLayoutProperty(layerId, 'visibility', visibilityValue)
        }
      })
    }
  }

  const updateLayerOpacity = (id: string, opacity: number) => {
    const layer = activeLayers.value.find(l => l.id === id)
    if (layer && mapInstance.value) {
      layer.opacity = opacity
      layer.layerIds.forEach(layerId => {
        const map = mapInstance.value!
        const lyr = map.getLayer(layerId)
        if (lyr) {
          const type = lyr.type
          if (type === 'fill') {
            map.setPaintProperty(layerId, 'fill-opacity', opacity)
          } else if (type === 'line') {
            map.setPaintProperty(layerId, 'line-opacity', opacity)
          } else if (type === 'circle') {
            map.setPaintProperty(layerId, 'circle-opacity', opacity)
          } else if (type === 'raster') {
            map.setPaintProperty(layerId, 'raster-opacity', opacity)
          } else if (type === 'symbol') {
            map.setPaintProperty(layerId, 'icon-opacity', opacity)
            map.setPaintProperty(layerId, 'text-opacity', opacity)
          }
        }
      })
    }
  }

  return {
    mapInstance,
    isMapLoaded,
    activeLayers,
    setMapInstance,
    setMapLoaded,
    addLayerToStore,
    removeLayerFromStore,
    updateLayerVisibility,
    updateLayerOpacity
  }
})
