import { createContext, useContext } from 'react'
import type { User } from '../shared/types'
interface Auth {
  user: User | null
  expired: boolean
  login: (username: string, password: string) => Promise<void>
  refreshUser: () => Promise<void>
  logout: () => void
  notify: (message: string) => void
}
export const AuthContext = createContext<Auth | null>(null)
export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('인증 상태를 찾을 수 없습니다.')
  return value
}
