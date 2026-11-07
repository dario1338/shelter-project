import { Heading, Link, Spinner, Stack, Text } from '@chakra-ui/react'
import { Link as RouterLink, Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Role } from './currentUser'
import type { ReturnState } from './returnTo'
import { useAuth } from './useAuth'

interface RequireAuthProps {
  // When given, only this role may see the pages inside.
  role?: Role
}

// A layout route: the pages inside are shown only to a signed-in user. This is a convenience of the
// interface only. The API checks the token and the role on every call, and that is the real protection.
export default function RequireAuth({ role }: Readonly<RequireAuthProps>) {
  const { state } = useAuth()
  const location = useLocation()

  // The session is restored on every page load. Until that is known, redirecting would send a
  // signed-in user to the sign-in page for a moment.
  if (state.status === 'loading') {
    return (
      <Stack role="status" align="center" py="10">
        <Spinner />
        <Text>Checking your session…</Text>
      </Stack>
    )
  }
  if (state.status === 'anonymous') {
    const returnState: ReturnState = { from: `${location.pathname}${location.search}` }
    return <Navigate to="/login" replace state={returnState} />
  }
  if (role !== undefined && state.user.role !== role) {
    return (
      <Stack gap="4" align="start">
        <Heading as="h1" size="2xl">
          No access
        </Heading>
        <Text>Your account cannot open this page.</Text>
        <Link asChild variant="underline">
          <RouterLink to="/">Back to the home page</RouterLink>
        </Link>
      </Stack>
    )
  }
  return <Outlet />
}
