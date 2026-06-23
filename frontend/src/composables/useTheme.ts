import { computed } from 'vue'
import { useThemeStore } from '../store/theme'
import { darkTheme } from 'naive-ui'

export function useTheme() {
  const themeStore = useThemeStore()

  const isDark = computed(() => themeStore.currentTheme === 'dark')

  const naiveTheme = computed(() => {
    return isDark.value ? darkTheme : null
  })

  const toggleTheme = () => {
    themeStore.toggleTheme()
  }

  const setTheme = (theme: 'light' | 'dark') => {
    themeStore.setTheme(theme)
  }

  return {
    currentTheme: computed(() => themeStore.currentTheme),
    isDark,
    naiveTheme,
    toggleTheme,
    setTheme
  }
}
