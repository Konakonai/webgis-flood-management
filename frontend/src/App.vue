<script setup lang="ts">
import {
  NConfigProvider,
  NNotificationProvider,
  NMessageProvider,
  NDialogProvider
} from 'naive-ui'

import HelperUI from './components/HelperUI.vue'
import MapContainer from './components/MapContainer.vue'
import SpatialQueryPanel from './components/SpatialQueryPanel.vue'
import EmergencyDispatchPanel from './components/EmergencyDispatchPanel.vue'
import { useTheme } from './composables/useTheme'

const { isDark, naiveTheme } = useTheme()
</script>

<template>
  <n-config-provider :theme="naiveTheme">
    <n-notification-provider>
      <n-message-provider>
        <n-dialog-provider>
        <!-- 主布局容器，根据全局主题动态绑定类名 -->
        <div class="app-layout" :class="{ 'dark-theme': isDark, 'light-theme': !isDark }">
          
          <!-- 系统顶栏 -->
          <HelperUI />

          <!-- 地图区域 + 悬浮控制面板 -->
          <main class="main-content">
            <!-- 底层 WebGIS 地图画布 -->
            <div id="map-container" class="map-viewport">
              <MapContainer />
            </div>

            <!-- 左侧面板：空间查询检索 -->
            <SpatialQueryPanel />

            <!-- 右侧面板：应急调度管理（模块五核心组件） -->
            <EmergencyDispatchPanel />
          </main>


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

.map-viewport {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
}
</style>
