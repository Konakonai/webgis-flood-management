import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  define: {
    global: 'globalThis'
  },
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/uploads': 'http://127.0.0.1:8080',
      '/ws': { target: 'http://127.0.0.1:8080', ws: true },
      '/osrm': { target: 'http://127.0.0.1:5000', rewrite: (path) => path.replace(/^\/osrm/, '') }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/maplibre-gl') || id.includes('node_modules/@turf')) return 'map'
          if (id.includes('node_modules/naive-ui') || id.includes('node_modules/lucide-vue-next')) return 'ui'
          if (id.includes('node_modules/@stomp') || id.includes('node_modules/sockjs-client')) return 'realtime'
        }
      }
    }
  }
})
