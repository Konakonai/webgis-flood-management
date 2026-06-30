import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'maplibre-gl/dist/maplibre-gl.css'
import './style.css'
import './mock/api'
import App from './App.vue'
import MobileReportApp from './components/MobileReportApp.vue'

const isMobileReportPage =
  window.location.pathname === '/report' ||
  window.location.pathname.includes('/mobile-report') ||
  window.location.search.includes('mobile=report')

const app = createApp(isMobileReportPage ? MobileReportApp : App)
const pinia = createPinia()

app.use(pinia)
app.mount('#app')
