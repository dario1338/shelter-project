import { Box, Button, Container, Flex, HStack, Link, Text } from '@chakra-ui/react'
import { Link as RouterLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { useColorMode } from './useColorMode'

function ColorModeToggle() {
  const { mode, toggle } = useColorMode()
  const target = mode === 'dark' ? 'light' : 'dark'
  return (
    <Button variant="ghost" size="sm" onClick={toggle} aria-label={`Switch to ${target} theme`}>
      {mode === 'dark' ? 'Light' : 'Dark'}
    </Button>
  )
}

function AccountLinks() {
  const { state, signOut } = useAuth()

  if (state.status === 'loading') {
    return null
  }
  if (state.status === 'anonymous') {
    return (
      <>
        <Button asChild variant="ghost" size="sm">
          <RouterLink to="/login">Sign in</RouterLink>
        </Button>
        <Button asChild size="sm">
          <RouterLink to="/register">Create account</RouterLink>
        </Button>
      </>
    )
  }
  return (
    <>
      {state.user.role === 'USER' && (
        <Button asChild variant="ghost" size="sm">
          <RouterLink to="/applications">My applications</RouterLink>
        </Button>
      )}
      {state.user.role === 'ADMIN' && (
        <Button asChild variant="ghost" size="sm">
          <RouterLink to="/admin/applications?status=PENDING">Applications</RouterLink>
        </Button>
      )}
      <Text fontSize="sm">{state.user.firstName}</Text>
      <Button variant="outline" size="sm" onClick={() => void signOut().catch(() => undefined)}>
        Sign out
      </Button>
    </>
  )
}

export default function AppLayout() {
  return (
    <Box minH="100vh">
      <Box as="header" borderBottomWidth="1px">
        <Container maxW="5xl">
          <Flex align="center" justify="space-between" py="3" columnGap="4" rowGap="2" wrap="wrap">
            <HStack gap="6">
              <Link asChild fontWeight="bold" fontSize="lg">
                <RouterLink to="/">Shelter</RouterLink>
              </Link>
              <Link asChild>
                <RouterLink to="/dogs">Dogs</RouterLink>
              </Link>
            </HStack>
            <HStack gap="2">
              <ColorModeToggle />
              <AccountLinks />
            </HStack>
          </Flex>
        </Container>
      </Box>
      <Container as="main" maxW="5xl" py="8">
        <Outlet />
      </Container>
    </Box>
  )
}
