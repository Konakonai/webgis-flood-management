import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { apiRequest, AUTH_TOKEN_KEY } from '../services/api'

export interface AuthUser {
  userId: number
  username: string
  realName: string
  roles: string[]
}

interface LoginResponse extends AuthUser {
  token: string
}

interface CurrentUser {
  id: number
  username: string
  realName: string
  roles?: Array<{ code?: string; name?: string }> | string[]
}

const USER_KEY = 'flood-gis-auth-user'

const readStoredUser = (): AuthUser | null => {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(readStoredUser())
  const ready = ref(false)
  const loading = ref(false)
  const isAuthenticated = computed(() => Boolean(user.value && localStorage.getItem(AUTH_TOKEN_KEY)))
  const canManageWorkOrders = computed(() => user.value?.roles.some((role) =>
    role === 'ROLE_ADMIN' || role === 'ROLE_OPERATOR') ?? false)

  const persist = (token: string, nextUser: AuthUser) => {
    localStorage.setItem(AUTH_TOKEN_KEY, token)
    localStorage.setItem(USER_KEY, JSON.stringify(nextUser))
    user.value = nextUser
  }

  const logout = () => {
    localStorage.removeItem(AUTH_TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    user.value = null
  }

  const login = async (username: string, password: string) => {
    loading.value = true
    try {
      const result = await apiRequest<LoginResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username: username.trim(), password })
      })
      persist(result.token, {
        userId: result.userId,
        username: result.username,
        realName: result.realName,
        roles: result.roles
      })
    } finally {
      loading.value = false
    }
  }

  const initialize = async () => {
    if (!localStorage.getItem(AUTH_TOKEN_KEY)) {
      logout()
      ready.value = true
      return
    }
    try {
      const current = await apiRequest<CurrentUser>('/api/auth/me')
      const stored = readStoredUser()
      user.value = {
        userId: current.id,
        username: current.username,
        realName: current.realName,
        roles: stored?.roles || []
      }
      localStorage.setItem(USER_KEY, JSON.stringify(user.value))
    } catch {
      logout()
    } finally {
      ready.value = true
    }
  }

  return { user, ready, loading, isAuthenticated, canManageWorkOrders, login, logout, initialize }
})
