import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { AuthContext, type AuthContextValue, type AuthState } from './AuthContext'
import { fetchCurrentUser } from './currentUser'
import { login, logout, refreshSession } from './session'

// On page load the access token is gone (it only lives in memory), so the session is restored
// through the refresh cookie.
async function loadSession(): Promise<AuthState> {
  try {
    if (!(await refreshSession())) {
      return { status: 'anonymous' }
    }
    return { status: 'authenticated', user: await fetchCurrentUser() }
  } catch {
    return { status: 'anonymous' }
  }
}

export function AuthProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [state, setState] = useState<AuthState>({ status: 'loading' })

  useEffect(() => {
    let cancelled = false
    void loadSession().then((next) => {
      if (!cancelled) {
        setState(next)
      }
    })
    return () => {
      cancelled = true
    }
  }, [])

  const signIn = useCallback(async (email: string, password: string) => {
    await login(email, password)
    setState({ status: 'authenticated', user: await fetchCurrentUser() })
  }, [])

  const signOut = useCallback(async () => {
    try {
      await logout()
    } finally {
      setState({ status: 'anonymous' })
    }
  }, [])

  const value = useMemo<AuthContextValue>(() => ({ state, signIn, signOut }), [state, signIn, signOut])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}