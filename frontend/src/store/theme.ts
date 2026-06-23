import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ThemeMode = 'light' | 'dark'

export const useThemeStore = defineStore('theme', () => {
  const currentTheme = ref<ThemeMode>(
    (localStorage.getItem('theme-mode') as ThemeMode) || 'dark'
  )

  const toggleTheme = () => {
    currentTheme.value = currentTheme.value === 'light' ? 'dark' : 'light'
    localStorage.setItem('theme-mode', currentTheme.value)
    updateBodyClass()
  }

  const setTheme = (theme: ThemeMode) => {
    currentTheme.value = theme
    localStorage.setItem('theme-mode', theme)
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
