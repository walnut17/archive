import http, { getData } from './http'

export interface LoginResult {
  success: boolean
  message?: string
  token: string
  userId: number
  username: string
  displayName: string
  role: string
}

export interface MeInfo {
  id: number
  username: string
  role: string
}

export const authApi = {
  login(username: string, password: string) {
    return http.post('/auth/login', { username, password }).then(getData<LoginResult>)
  },
  me() {
    return http.get('/auth/me').then(getData<MeInfo>)
  },
}
