import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ThemeMode = 'light' | 'dark'

const THEME_STORAGE_KEY = 'theme-mode-v2'

export const useThemeStore = defineStore('theme', () => {
  const currentTheme = ref<ThemeMode>(
    (localStorage.getItem(THEME_STORAGE_KEY) as ThemeMode) || 'light'
  )

  const toggleTheme = () => {
    currentTheme.value = currentTheme.value === 'light' ? 'dark' : 'light'
    localStorage.setItem(THEME_STORAGE_KEY, currentTheme.value)
    updateBodyClass()
  }

  const setTheme = (theme: ThemeMode) => {
    currentTheme.value = theme
    localStorage.setItem(THEME_STORAGE_KEY, theme)
    updateBodyClass()
  }

  const updateBodyClass = () => {
    const root = document.documentElement
    if (currentTheme.value === 'dark') {
      root.classList.add('dark-theme')
      root.classList.remove('light-theme')
    } else {
      root.classList.add('light-theme')
      root.classList.remove('dark-theme')
    }
  }

  // Initialize theme class on store creation
  if (typeof window !== 'undefined') {
    updateBodyClass()
  }

  return {
    currentTheme,
    toggleTheme,
    setTheme
  }
})
