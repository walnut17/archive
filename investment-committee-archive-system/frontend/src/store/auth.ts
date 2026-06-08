import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, type LoginResult } from '@/api/auth'

const TOKEN_KEY = 'archive-token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<{ id: number; username: string; role: string } | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  async function login(username: string, password: string) {
    const result: LoginResult = await authApi.login(username, password)
    token.value = result.token
    localStorage.setItem(TOKEN_KEY, result.token)
    user.value = {
      id: result.userId,
      username: result.username,
      role: result.role,
    }
    return result
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  async function fetchMe() {
    if (!token.value) return null
    try {
      const me = await authApi.me()
      user.value = me
      return me
    } catch {
      logout()
      return null
    }
  }

  return {
    token,
    user,
    isLoggedIn,
    login,
    logout,
    fetchMe,
  }
})
