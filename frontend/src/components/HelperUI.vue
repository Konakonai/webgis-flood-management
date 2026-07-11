<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { CircleHelp, Maximize2, Minimize2, Moon, Sun, Waves } from 'lucide-vue-next'
import { useTheme } from '../composables/useTheme'
import { useAuthStore } from '../store/auth'

// 获取全局主题状态与切换函数
const { isDark, toggleTheme } = useTheme()
const auth = useAuthStore()
const emit = defineEmits<{
  guidePanel: [panel: 'spatial' | 'dispatch']
}>()

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
    title: '空间分析',
    content: '绘制框选或多边形范围，结合缓冲区半径检索周边排涝泵站和抢险物资点。',
    placement: 'right'
  },
  {
    target: '#emergency-dispatch-card',
    title: '应急指挥',
    content: '查看内涝工单、派遣泵车并定位抢险资源，持续跟踪现场处置状态。',
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

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max)

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
  
  // 高亮框始终约束在视口内，避免全屏地图的边框被裁掉。
  const highlightPadding = 8
  const viewportPadding = 4
  const cutoutLeft = clamp(rect.left - highlightPadding, viewportPadding, window.innerWidth - viewportPadding)
  const cutoutTop = clamp(rect.top - highlightPadding, viewportPadding, window.innerHeight - viewportPadding)
  const cutoutRight = clamp(rect.right + highlightPadding, viewportPadding, window.innerWidth - viewportPadding)
  const cutoutBottom = clamp(rect.bottom + highlightPadding, viewportPadding, window.innerHeight - viewportPadding)
  cutout.value = {
    x: cutoutLeft,
    y: cutoutTop,
    w: Math.max(0, cutoutRight - cutoutLeft),
    h: Math.max(0, cutoutBottom - cutoutTop)
  }

  const tooltipWidth = Math.min(360, window.innerWidth - 32)
  const tooltipHalfHeight = 110
  const margin = 20 // 气泡距离高亮区域的边距
  const centeredTop = clamp(
    rect.top + rect.height / 2,
    tooltipHalfHeight + 16,
    window.innerHeight - tooltipHalfHeight - 16
  )

  const sideStyle = (preferredSide: 'left' | 'right') => {
    const rightLeft = rect.right + margin
    const leftLeft = rect.left - tooltipWidth - margin
    const canUseRight = rightLeft + tooltipWidth <= window.innerWidth - 16
    const canUseLeft = leftLeft >= 16
    const side = preferredSide === 'right'
      ? (canUseRight ? 'right' : canUseLeft ? 'left' : 'center')
      : (canUseLeft ? 'left' : canUseRight ? 'right' : 'center')

    if (side === 'center') {
      return {
        left: '50%',
        top: `${centeredTop}px`,
        transform: 'translate(-50%, -50%)'
      }
    }

    return {
      left: `${side === 'right' ? rightLeft : leftLeft}px`,
      top: `${centeredTop}px`,
      transform: 'translateY(-50%)'
    }
  }

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
      ...sideStyle('right'),
      width: `${tooltipWidth}px`,
      zIndex: '9999'
    }
  } else if (step.placement === 'left') {
    tooltipStyle.value = {
      position: 'fixed',
      ...sideStyle('left'),
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
    emit('guidePanel', currentStep.value === 2 ? 'dispatch' : 'spatial')
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
    if (currentStep.value === 1) emit('guidePanel', 'spatial')
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
      <div class="logo-wrapper" aria-hidden="true">
        <Waves class="logo-icon" />
      </div>
      <div class="title-container">
        <h1 class="header-title">徐州市城市内涝应急指挥平台</h1>
        <span class="header-subtitle">XUZHOU FLOOD RESPONSE · WEBGIS</span>
      </div>
    </div>

    <!-- 右侧功能控制区 -->
    <div class="header-right">
      <!-- 动态闪烁响应等级徽章 -->
      <div class="alarm-level-tag level-orange">
        <span class="tag-pulse-dot"></span>
        <span class="tag-text">防汛 II 级响应</span>
      </div>

      <!-- 新手引导 -->
      <button class="action-btn" @click="startGuide" title="系统操作导览">
        <CircleHelp class="btn-icon" aria-hidden="true" />
        <span class="btn-text">新手引导</span>
      </button>

      <!-- 当前会话：作为头栏正常布局的一部分，避免覆盖相邻功能按钮 -->
      <div class="session-control" :title="`当前用户：${auth.user?.realName || auth.user?.username}`">
        <span class="session-user">
          {{ auth.user?.realName || auth.user?.username }}{{ auth.canManageWorkOrders ? '' : ' · 只读' }}
        </span>
        <button class="logout-btn" type="button" @click="auth.logout">退出</button>
      </div>

      <!-- 全屏控制 -->
      <button class="action-btn" @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '全屏显示'">
        <component :is="isFullscreen ? Minimize2 : Maximize2" class="btn-icon" aria-hidden="true" />
        <span class="btn-text">{{ isFullscreen ? '退出全屏' : '全屏显示' }}</span>
      </button>

      <!-- 主题明暗切换 -->
      <button class="action-btn theme-toggle-btn" @click="toggleTheme" :title="isDark ? '切换到日间模式' : '切换到夜间模式'">
        <Sun v-if="isDark" class="btn-icon sun-icon" aria-hidden="true" />
        <Moon v-else class="btn-icon moon-icon" aria-hidden="true" />
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
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  border-radius: 10px;
  background: linear-gradient(145deg, #0f766e 0%, #059669 100%);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 16px rgba(5, 150, 105, 0.24);
}

.logo-icon {
  width: 23px;
  height: 23px;
  stroke-width: 2.2;
}

.title-container {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.header-title {
  font-size: 19px;
  font-weight: 700;
  margin: 0;
  padding: 0;
  color: var(--text-primary);
  letter-spacing: 0.2px;
  white-space: nowrap;
}

.header-subtitle {
  font-size: 9px;
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 1.5px;
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

.session-control {
  height: 34px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 7px 0 12px;
  box-sizing: border-box;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: #f8fafc;
  color: var(--text-primary);
  font-size: 13px;
  white-space: nowrap;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.dark-theme .session-control,
.dark-theme .action-btn {
  background: var(--bg-card);
}

.session-user {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.logout-btn {
  height: 24px;
  padding: 0 8px;
  border: 0;
  border-left: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: color 0.25s cubic-bezier(0.4, 0, 0.2, 1),
              border-color 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.logout-btn:hover {
  color: var(--primary-color);
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
              width 0.3s cubic-bezier(0.4, 0, 0.2, 1),
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
  box-sizing: border-box;
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

  .session-control {
    padding-left: 8px;
  }

  .session-user {
    display: none;
  }
}

@media (max-width: 760px) {
  .system-header {
    min-height: 106px;
    height: auto;
    padding: 10px 14px;
    flex-wrap: wrap;
    align-content: center;
    gap: 8px;
  }

  .header-left,
  .header-right {
    width: 100%;
  }

  .header-left {
    gap: 10px;
  }

  .header-title {
    font-size: 16px;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .logo-wrapper {
    width: 34px;
    height: 34px;
    flex-basis: 34px;
    border-radius: 9px;
  }

  .logo-icon {
    width: 20px;
    height: 20px;
  }

  .header-right {
    flex-wrap: nowrap;
    gap: 8px;
  }

  .session-control {
    margin-left: auto;
  }

  .session-user {
    display: inline;
    max-width: 110px;
  }
}

@media (max-width: 460px) {
  .session-user {
    display: none;
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
