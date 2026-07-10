import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'maplibre-gl/dist/maplibre-gl.css'
import './style.css'

if (import.meta.env.DEV && import.meta.env.VITE_USE_MOCK === 'true') {
  await import('./mock/api')
}

const isMobileReportPage =
  window.location.pathname === '/report' ||
  window.location.pathname.includes('/mobile-report') ||
  window.location.search.includes('mobile=report')

const rootComponent = isMobileReportPage
  ? (await import('./components/MobileReportApp.vue')).default
  : (await import('./App.vue')).default

const app = createApp(rootComponent)
const pinia = createPinia()

app.use(pinia)
app.mount('#app')
