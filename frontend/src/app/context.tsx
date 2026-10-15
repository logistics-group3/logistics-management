import { useState, useCallback } from 'react'
import { AuthContext } from './auth'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { api, configureAuth } from '../shared/api'
import type { User } from '../shared/types'
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, staleTime: 30000, refetchOnWindowFocus: false },
    mutations: { retry: false },
  },
})
export function Providers({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [expired, setExpired] = useState(false)
  const [notice, setNotice] = useState('')
  const endSession = useCallback((sessionExpired = false) => {
    configureAuth(null)
    queryClient.clear()
    setUser(null)
    setExpired(sessionExpired)
    setNotice('')
  }, [])
  const login = async (username: string, password: string) => {
    const result = await api<{ accessToken: string }>('/auth/login', {
      method: 'POST',
      body: { username, password },
    })
    configureAuth(result.accessToken, () => endSession(true))
    try {
      const me = await api<User>('/users/me')
      queryClient.clear()
      setUser(me)
      setExpired(false)
    } catch (error) {
      endSession()
      throw error
    }
  }
  const refreshUser = async () => {
    setUser(await api<User>('/users/me'))
  }
  return (
    <QueryClientProvider client={queryClient}>
      <AuthContext.Provider
        value={{ user, expired, login, refreshUser, logout: () => endSession(), notify: setNotice }}
      >
        {children}
        {createPortal(
          <div aria-live="polite" aria-atomic="true" role="status">
            {notice && user && (
              <div className="toast">
                <span>{notice}</span>
                <button onClick={() => setNotice('')} aria-label="알림 닫기">
                  ×
                </button>
              </div>
            )}
          </div>,
          document.body,
        )}
      </AuthContext.Provider>
    </QueryClientProvider>
  )
}
