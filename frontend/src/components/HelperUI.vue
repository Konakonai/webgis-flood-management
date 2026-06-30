<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useTheme } from '../composables/useTheme'

// 获取全局主题状态与切换函数
const { isDark, toggleTheme } = useTheme()

// 全屏状态控制
const isFullscreen = ref(false)

const toggleFullscreen = () => {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
      .catch((err) => {
        console.error(`[Fullscreen] 开启全屏失败: ${err.message}`)
      })
  } else {
    document.exitFullscreen()
  }
}

const handleFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
}

// 新手引导步骤定义
export interface GuideStep {
  target: string
  title: string
  content: string
  placement: 'center' | 'left' | 'right' | 'top' | 'bottom'
}

const guideSteps: GuideStep[] = [
  {
    target: '#map-container',
    title: '地图基础浏览',
    content: '通过双击或按住鼠标拖拽进行地图缩放与平移。底图默认使用日间道路图，便于在徐州市区全景和积水点细节间快速切换观察。',
    placement: 'center'
  },
  {
    target: '#spatial-query-panel',
    title: '空间查询检索',
    content: '提供多维度的空间查询工具。输入地址可快速进行本地搜索，或滑动滑块调整缓冲区半径，检索周边特定的排涝泵站与抢险物资储备点。',
    placement: 'right'
  },
  {
    target: '#emergency-dispatch-panel',
    title: '应急调度管理',
    content: '实时监测内涝警情及排涝工单。您可以在此指派抢险分队前往低洼易涝区，实时调度水泵和挡水板等防汛资源，协同高效作业。',
    placement: 'left'
  }
]

const guideActive = ref(false)
const currentStep = ref(0)

// 镂空裁剪区域配置 (x, y, 宽度, 高度)
const cutout = ref({ x: 0, y: 0, w: 0, h: 0 })
// 引导气泡的定位样式
const tooltipStyle = ref<Record<string, string>>({})

const activeStepData = computed(() => guideSteps[currentStep.value])

// 更新引导框位置和气泡样式
const updateGuidePosition = () => {
  if (!guideActive.value) return
  const step = guideSteps[currentStep.value]
  const el = document.querySelector(step.target) as HTMLElement

  if (!el) {
    // 找不到目标元素时，默认居中显示气泡，无镂空裁剪
    cutout.value = { x: 0, y: 0, w: 0, h: 0 }
    tooltipStyle.value = {
      position: 'fixed',
      left: '50%',
      top: '50%',
      transform: 'translate(-50%, -50%)',
      width: '340px',
      zIndex: '9999'
    }
    return
  }

  const rect = el.getBoundingClientRect()
  
  // 给高亮区域边缘加 8px 的内边距，提升视觉呼吸感
  cutout.value = {
    x: rect.left - 8,
    y: rect.top - 8,
    w: rect.width + 16,
    h: rect.height + 16
  }

  const tooltipWidth = 340
  const margin = 20 // 气泡距离高亮区域的边距

  if (step.placement === 'center') {
    tooltipStyle.value = {
      position: 'fixed',
      left: '50%',
      top: '55%',
      transform: 'translate(-50%, -50%)',
      width: `${tooltipWidth}px`,
      zIndex: '9999'
    }
  } else if (step.placement === 'right') {
    tooltipStyle.value = {
      position: 'fixed',
      left: `${rect.right + margin}px`,
      top: `${rect.top + rect.height / 2}px`,
      transform: 'translateY(-50%)',
      width: `${tooltipWidth}px`,
      zIndex: '9999'
    }
  } else if (step.placement === 'left') {
    tooltipStyle.value = {
      position: 'fixed',
      left: `${rect.left - tooltipWidth - margin}px`,
      top: `${rect.top + rect.height / 2}px`,
      transform: 'translateY(-50%)',
      width: `${tooltipWidth}px`,
      zIndex: '9999'
    }
  } else if (step.placement === 'top') {
    tooltipStyle.value = {
      position: 'fixed',
      left: `${rect.left + rect.width / 2}px`,
      top: `${rect.top - margin}px`,
      transform: 'translate(-50%, -100%)',
      width: `${tooltipWidth}px`,
      zIndex: '9999'
    }
  } else if (step.placement === 'bottom') {
    tooltipStyle.value = {
      position: 'fixed',
      left: `${rect.left + rect.width / 2}px`,
      top: `${rect.bottom + margin}px`,
      transform: 'translate(-50%, 0)',
      width: `${tooltipWidth}px`,
      zIndex: '9999'
    }
  }
}

// 开启新手引导
const startGuide = () => {
  guideActive.value = true
  currentStep.value = 0
  nextTick(() => {
    updateGuidePosition()
  })
}

// 下一步
const nextStep = () => {
  if (currentStep.value < guideSteps.length - 1) {
    currentStep.value++
    nextTick(() => {
      updateGuidePosition()
    })
  } else {
    skipGuide()
  }
}

// 上一步
const prevStep = () => {
  if (currentStep.value > 0) {
    currentStep.value--
    nextTick(() => {
      updateGuidePosition()
    })
  }
}

// 跳过或结束引导
const skipGuide = () => {
  guideActive.value = false
  cutout.value = { x: 0, y: 0, w: 0, h: 0 }
}

// 监听窗口缩放和滚动事件，动态刷新高亮位置
watch(guideActive, (active) => {
  if (active) {
    window.addEventListener('resize', updateGuidePosition)
    window.addEventListener('scroll', updateGuidePosition)
  } else {
    window.removeEventListener('resize', updateGuidePosition)
    window.removeEventListener('scroll', updateGuidePosition)
  }
})

onMounted(() => {
  document.addEventListener('fullscreenchange', handleFullscreenChange)
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  window.removeEventListener('resize', updateGuidePosition)
  window.removeEventListener('scroll', updateGuidePosition)
})
</script>

<template>
  <!-- 现代高质感系统头栏 -->
  <header class="system-header">
    <!-- 左侧标题与徽章 -->
    <div class="header-left">
      <div class="logo-wrapper">
        <svg class="logo-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
        </svg>
      </div>
      <div class="title-container">
        <h1 class="header-title">徐州市 WebGIS 内涝监测与应急管理系统</h1>
        <span class="header-subtitle">XUZHOU WEBGIS FLOOD MONITORING & EMERGENCY MANAGEMENT SYSTEM</span>
      </div>
    </div>

    <!-- 右侧功能控制区 -->
    <div class="header-right">
      <!-- 动态闪烁响应等级徽章 -->
      <div class="alarm-level-tag level-orange">
        <span class="tag-pulse-dot"></span>
        <span class="tag-text">防汛 II 级应急响应</span>
      </div>

      <!-- 新手引导 -->
      <button class="action-btn" @click="startGuide" title="系统操作导览">
        <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10"/>
          <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
          <line x1="12" y1="17" x2="12.01" y2="17"/>
        </svg>
        <span class="btn-text">新手引导</span>
      </button>

      <!-- 全屏控制 -->
      <button class="action-btn" @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '全屏显示'">
        <svg v-if="!isFullscreen" class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/>
        </svg>
        <svg v-else class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M4 14h6v6M20 10h-6V4M14 10l7-7M10 14l-7 7"/>
        </svg>
        <span class="btn-text">{{ isFullscreen ? '退出全屏' : '全屏显示' }}</span>
      </button>

      <!-- 主题明暗切换 -->
      <button class="action-btn theme-toggle-btn" @click="toggleTheme" :title="isDark ? '切换到日间模式' : '切换到夜间模式'">
        <!-- 太阳图标 -->
        <svg v-if="isDark" class="btn-icon sun-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="4"/>
          <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/>
        </svg>
        <!-- 月亮图标 -->
        <svg v-else class="btn-icon moon-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/>
        </svg>
        <span class="btn-text">{{ isDark ? '日间模式' : '夜间模式' }}</span>
      </button>

    </div>
  </header>

  <!-- 引导遮罩层与气泡弹窗 -->
  <div v-if="guideActive" class="guide-overlay-container">
    <svg class="guide-mask-svg" width="100%" height="100%">
      <defs>
        <mask id="guide-mask">
          <!-- 覆盖整个屏幕的白底 (遮蔽区域) -->
          <rect x="0" y="0" width="100%" height="100%" fill="white" />
          <!-- 黑色矩形 (镂空高亮裁剪区)，支持 CSS transitions 平滑平移缩放 -->
          <rect
            :x="cutout.x"
            :y="cutout.y"
            :width="cutout.w"
            :height="cutout.h"
            rx="8"
            ry="8"
            fill="black"
            class="guide-mask-rect"
          />
        </mask>
      </defs>
      <!-- 应用蒙版的暗色半透明遮罩背景 -->
      <rect x="0" y="0" width="100%" height="100%" fill="rgba(0, 0, 0, 0.72)" mask="url(#guide-mask)" />
      
      <!-- 外接圆角发光脉冲框 -->
      <rect
        v-if="cutout.w > 0"
        :x="cutout.x"
        :y="cutout.y"
        :width="cutout.w"
        :height="cutout.h"
        rx="8"
        ry="8"
        fill="none"
        stroke="var(--primary-color)"
        stroke-width="2.5"
        class="guide-pulse-rect"
      />
    </svg>

    <!-- 气泡指引卡片 -->
    <div class="guide-card" :style="tooltipStyle">
      <!-- 气泡小箭头装饰 -->
      <div class="guide-card-arrow" :class="`arrow-${activeStepData.placement}`"></div>
      
      <div class="guide-card-header">
        <h3 class="guide-step-title">{{ activeStepData.title }}</h3>
        <span class="guide-progress-badge">第 {{ currentStep + 1 }} / {{ guideSteps.length }} 步</span>
      </div>
      <div class="guide-card-body">
        <p class="guide-step-content">{{ activeStepData.content }}</p>
      </div>
      <div class="guide-card-footer">
        <button class="guide-btn btn-skip" @click="skipGuide">跳过</button>
        <div class="guide-nav-buttons">
          <button class="guide-btn btn-prev" :disabled="currentStep === 0" @click="prevStep">上一步</button>
          <button class="guide-btn btn-next" @click="nextStep">
            {{ currentStep === guideSteps.length - 1 ? '我知道了' : '下一步' }}
          </button>
        </div>
      </div>
    </div>
  </div>

</template>

<style scoped>
/* ==========================================================================
   系统头栏 (System Header) 样式
   ========================================================================== */
.system-header {
  min-height: 64px;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-sizing: border-box;
  background: #ffffff;
  border-bottom: 1px solid var(--border-color);
  box-shadow: var(--shadow);
  z-index: 100;
  position: relative;
  transition: background-color 0.3s, border-color 0.3s, box-shadow 0.3s;
}

.dark-theme .system-header {
  background: var(--bg-panel);
  backdrop-filter: blur(12px) saturate(130%);
  -webkit-backdrop-filter: blur(12px) saturate(130%);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.logo-wrapper {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--primary-color) 0%, var(--accent-color) 100%);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 3px 10px rgba(24, 144, 255, 0.2);
}

.logo-svg {
  width: 20px;
  height: 20px;
}

.title-container {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.header-title {
  font-size: 19px;
  font-weight: 700;
  margin: 0;
  padding: 0;
  background: linear-gradient(135deg, var(--text-primary) 30%, var(--primary-color) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: 0.5px;
}

.header-subtitle {
  font-size: 9px;
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 1.2px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

/* 报警等级闪烁徽章 */
.alarm-level-tag {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  background: rgba(250, 173, 20, 0.12);
  border: 1px solid rgba(250, 173, 20, 0.3);
  color: #faad14;
  margin-right: 12px;
}

/* 暗色主题时调高亮 */
.dark-theme .alarm-level-tag {
  background: rgba(250, 173, 20, 0.18);
  border: 1px solid rgba(250, 173, 20, 0.4);
}

.tag-pulse-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background-color: #faad14;
  margin-right: 8px;
  box-shadow: 0 0 0 0 rgba(250, 173, 20, 0.7);
  animation: level-pulse 1.8s infinite ease-in-out;
}

@keyframes level-pulse {
  0% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(250, 173, 20, 0.7);
  }
  70% {
    transform: scale(1);
    box-shadow: 0 0 0 6px rgba(250, 173, 20, 0);
  }
  100% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(250, 173, 20, 0);
  }
}

/* 导航按钮通用样式 */
.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  background: #f8fafc;
  border: 1px solid var(--border-color);
  cursor: pointer;
  outline: none;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.action-btn:hover {
  background: #eef6ff;
  border-color: var(--primary-color);
  color: var(--primary-color);
  transform: translateY(-1px);
}

.action-btn:active {
  transform: translateY(0);
}

.btn-icon {
  width: 15px;
  height: 15px;
}

.theme-toggle-btn:hover .moon-icon {
  transform: rotate(-15deg);
}

.theme-toggle-btn:hover .sun-icon {
  transform: rotate(30deg);
}

.btn-icon {
  transition: transform 0.3s ease;
}

/* ==========================================================================
   新手引导 (User Guide) 样式
   ========================================================================== */
.guide-overlay-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 9998;
  pointer-events: auto;
}

.guide-mask-svg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

/* 镂空框过渡 */
.guide-mask-rect {
  transition: x 0.3s cubic-bezier(0.4, 0, 0.2, 1),
              y 0.3s cubic-bezier(0.4, 0, 0.2, 1),
              width 0.3 cubic-bezier(0.4, 0, 0.2, 1),
              height 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 脉冲高亮边框样式与闪烁特效 */
.guide-pulse-rect {
  transition: x 0.3s cubic-bezier(0.4, 0, 0.2, 1),
              y 0.3s cubic-bezier(0.4, 0, 0.2, 1),
              width 0.3s cubic-bezier(0.4, 0, 0.2, 1),
              height 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  animation: border-pulse 2s infinite ease-in-out;
}

@keyframes border-pulse {
  0% {
    stroke-opacity: 0.5;
    stroke-width: 2px;
  }
  50% {
    stroke-opacity: 1;
    stroke-width: 3.5px;
  }
  100% {
    stroke-opacity: 0.5;
    stroke-width: 2px;
  }
}

/* 气泡信息面板 */
.guide-card {
  position: fixed;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: var(--shadow);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  transition: left 0.3s cubic-bezier(0.4, 0, 0.2, 1),
              top 0.3s cubic-bezier(0.4, 0, 0.2, 1),
              transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.guide-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 8px;
}

.guide-step-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--primary-color);
  margin: 0;
}

.guide-progress-badge {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
  background: rgba(120, 120, 120, 0.08);
  padding: 2px 8px;
  border-radius: 20px;
}

.guide-step-content {
  font-size: 13.5px;
  color: var(--text-primary);
  line-height: 1.6;
  margin: 0;
}

.guide-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
}

.guide-nav-buttons {
  display: flex;
  gap: 8px;
}

.guide-btn {
  height: 28px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  outline: none;
}

.btn-skip {
  background: transparent;
  border: 1px solid transparent;
  color: var(--text-secondary);
}

.btn-skip:hover {
  color: #ff4d4f;
}

.btn-prev {
  background: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-primary);
}

.btn-prev:hover:not(:disabled) {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.btn-prev:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn-next {
  background: var(--primary-color);
  border: none;
  color: #ffffff;
  box-shadow: 0 2px 6px rgba(24, 144, 255, 0.15);
}

.btn-next:hover {
  opacity: 0.9;
  transform: translateY(-0.5px);
}

/* 气泡小箭头装饰物（可选样式以增强指向性） */
.guide-card-arrow {
  position: absolute;
  width: 0;
  height: 0;
  border-style: solid;
  pointer-events: none;
  display: none; /* 为简化排版可默认隐藏，视高亮布局可用 */
}

@media (max-width: 1180px) {
  .system-header {
    padding: 0 16px;
  }

  .header-subtitle,
  .alarm-level-tag {
    display: none;
  }

  .btn-text {
    display: none;
  }

  .action-btn {
    width: 36px;
    padding: 0;
    justify-content: center;
  }
}

@media (min-width: 1800px) {
  .system-header {
    min-height: 68px;
    padding: 0 30px;
  }

  .header-title {
    font-size: 21px;
  }
}
</style>
