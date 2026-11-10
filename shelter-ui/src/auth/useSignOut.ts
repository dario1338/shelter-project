import { useNavigate } from 'react-router-dom'
import { useAuth } from './useAuth'

// Signing out takes the user to the home page first. Otherwise the page that was open (a page of a role)
// would send the visitor to the sign-in page and remember itself, and whoever signed in next, with
// another role, would be sent back to a page that account cannot open. The two changes land together.
export function useSignOut(): () => void {
  const { signOut } = useAuth()
  const navigate = useNavigate()
  return () => {
    navigate('/')
    void signOut().catch(() => undefined)
  }
}
