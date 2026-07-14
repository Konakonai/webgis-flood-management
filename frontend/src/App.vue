<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  NConfigProvider,
  NNotificationProvider,
  NMessageProvider,
  NDialogProvider,
  NIcon
} from 'naive-ui'
import { ScanSearch, Siren } from 'lucide-vue-next'

import HelperUI from './components/HelperUI.vue'
import AuthGate from './components/AuthGate.vue'
import MapContainer from './components/MapContainer.vue'
import SpatialQueryPanel from './components/SpatialQueryPanel.vue'
import EmergencyDispatchPanel from './components/EmergencyDispatchPanel.vue'
import AdminCenter from './components/AdminCenter.vue'
import { useTheme } from './composables/useTheme'
import { useAuthStore } from './store/auth'

const { isDark, naiveTheme } = useTheme()
const auth = useAuthStore()
const isNarrow = ref(false)
const activePanel = ref<'spatial' | 'dispatch'>('spatial')
const activeWorkspace = ref<'map' | 'admin'>('map')
let narrowQuery: MediaQueryList | null = null

const syncNarrowLayout = () => {
  isNarrow.value = narrowQuery?.matches ?? false
}

const showGuidePanel = (panel: 'spatial' | 'dispatch') => {
  if (isNarrow.value && auth.canManageWorkOrders) activePanel.value = panel
}

const changeWorkspace = (workspace: 'map' | 'admin') => {
  activeWorkspace.value = auth.isAdmin ? workspace : 'map'
}

watch(() => auth.isAdmin, (isAdmin) => {
  if (!isAdmin) activeWorkspace.value = 'map'
})

onMounted(() => {
  auth.initialize()
  narrowQuery = window.matchMedia('(max-width: 980px)')
  syncNarrowLayout()
  narrowQuery.addEventListener('change', syncNarrowLayout)
})

onBeforeUnmount(() => narrowQuery?.removeEventListener('change', syncNarrowLayout))
</script>

<template>
  <n-config-provider :theme="naiveTheme">
    <n-notification-provider>
      <n-message-provider>
        <n-dialog-provider>
        <!-- 主布局容器，根据全局主题动态绑定类名 -->
        <div class="app-layout" :class="{ 'dark-theme': isDark, 'light-theme': !isDark }">
          <AuthGate />
          
          <!-- 系统顶栏 -->
          <HelperUI
            v-if="auth.isAuthenticated"
            :active-workspace="activeWorkspace"
            @guide-panel="showGuidePanel"
            @workspace-change="changeWorkspace"
          />

          <!-- 地图区域 + 悬浮控制面板 -->
          <main v-if="auth.isAuthenticated && activeWorkspace === 'map'" class="main-content">
            <!-- 底层 WebGIS 地图画布 -->
            <div id="map-container" class="map-viewport">
              <MapContainer />
            </div>

            <div
              v-if="isNarrow && auth.canManageWorkOrders"
              class="workspace-switcher"
              role="group"
              aria-label="工作区切换"
            >
              <button type="button" :class="{ active: activePanel === 'spatial' }" :aria-pressed="activePanel === 'spatial'" @click="activePanel = 'spatial'">
                <n-icon :component="ScanSearch" aria-hidden="true" />
                空间分析
              </button>
              <button type="button" :class="{ active: activePanel === 'dispatch' }" :aria-pressed="activePanel === 'dispatch'" @click="activePanel = 'dispatch'">
                <n-icon :component="Siren" aria-hidden="true" />
                应急指挥
              </button>
            </div>

            <!-- 窄屏一次只呈现一个任务面板，避免地图工作区被双层遮挡。 -->
            <div class="panel-host" v-show="!isNarrow || activePanel === 'spatial' || !auth.canManageWorkOrders">
              <SpatialQueryPanel />
            </div>

            <div v-if="auth.canManageWorkOrders" class="panel-host" v-show="!isNarrow || activePanel === 'dispatch'">
              <EmergencyDispatchPanel />
            </div>
          </main>

          <AdminCenter
            v-if="auth.isAuthenticated && auth.isAdmin && activeWorkspace === 'admin'"
            class="admin-workspace"
          />


        </div>
        </n-dialog-provider>
      </n-message-provider>
    </n-notification-provider>
  </n-config-provider>
</template>

<style scoped>
/* 全局页面布局 */
.app-layout {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-color);
  color: var(--text-primary);
  overflow: hidden;
  transition: background-color 0.3s, color 0.3s;
}

.main-content {
  flex: 1;
  width: 100%;
  position: relative;
  overflow: hidden;
}

.admin-workspace {
  flex: 1;
  min-height: 0;
}

.map-viewport {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
}

.panel-host {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
}

.workspace-switcher {
  position: absolute;
  top: 10px;
  left: 50%;
  z-index: 30;
  display: flex;
  min-height: 44px;
  padding: 4px;
  border: 1px solid var(--border-color, #d8dee9);
  border-radius: 10px;
  background: var(--bg-color);
  box-shadow: var(--panel-shadow);
  transform: translateX(-50%);
}

.workspace-switcher button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 118px;
  min-height: 36px;
  padding: 0 14px;
  border: 0;
  border-radius: 7px;
  color: var(--text-secondary);
  background: transparent;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
  transition: color 0.18s ease, background-color 0.18s ease;
}

.workspace-switcher button.active {
  color: var(--primary-color);
  background: var(--primary-bg, rgba(24, 144, 255, 0.1));
}

.workspace-switcher button:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}

@media (max-width: 560px) {
  .workspace-switcher {
    width: calc(100% - 30px);
  }

  .workspace-switcher button {
    flex: 1;
    min-width: 0;
  }
}
</style>
