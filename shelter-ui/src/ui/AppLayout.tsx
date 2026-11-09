import { Box, Button, Container, Flex, HStack, IconButton, Link, Text } from '@chakra-ui/react'
import type { ReactNode } from 'react'
import { Link as RouterLink, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { PawIcon } from './PawIcon'
import { useColorMode } from './useColorMode'

// The content is up to 1280 px wide, and up to 1600 px on screens of 1536 px and more, where there is room
// for a fifth dog in a row. The header uses the same width, so the two line up.
const CONTENT_WIDTH = { base: '7xl', '2xl': '100rem' }

function SunIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </svg>
  )
}

function MoonIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
    </svg>
  )
}

// The icon shows the theme that a click switches to, and the label says it in words.
function ColorModeToggle() {
  const { mode, toggle } = useColorMode()
  const target = mode === 'dark' ? 'light' : 'dark'
  const label = `Switch to ${target} theme`
  return (
    <IconButton variant="ghost" size="sm" onClick={toggle} aria-label={label} title={label}>
      {mode === 'dark' ? <SunIcon /> : <MoonIcon />}
    </IconButton>
  )
}

// The page that is open is marked by the router (aria-current), and shown in a heavier weight.
function NavItem({ to, children }: Readonly<{ to: string; children: ReactNode }>) {
  return (
    <Link asChild fontSize={{ base: 'sm', sm: 'md' }} _currentPage={{ fontWeight: 'semibold' }}>
      <NavLink to={to}>{children}</NavLink>
    </Link>
  )
}

// What a visitor can open, by role. The links for the signed-in roles sit next to "Dogs",
// and the account (name, sign out) is on the right.
function MainLinks() {
  const { state } = useAuth()
  const role = state.status === 'authenticated' ? state.user.role : null

  return (
    <>
      <NavItem to="/dogs">Dogs</NavItem>
      {role === 'USER' && (
        <>
          <NavItem to="/my-dogs">My dogs</NavItem>
          <NavItem to="/applications">My applications</NavItem>
        </>
      )}
      {role === 'ADMIN' && <NavItem to="/admin/applications?status=PENDING">Applications</NavItem>}
    </>
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
        <Container maxW={CONTENT_WIDTH}>
          <Flex align="center" justify="space-between" py="3" columnGap="4" rowGap="2" wrap="wrap">
            <HStack as="nav" aria-label="Main" gap={{ base: '3', sm: '5' }} wrap="wrap">
              <Link asChild fontWeight="bold" fontSize="lg" color="purple.fg">
                <RouterLink to="/">
                  <PawIcon size={22} />
                  Shelter
                </RouterLink>
              </Link>
              <MainLinks />
            </HStack>
            <HStack gap="2">
              <AccountLinks />
              <ColorModeToggle />
            </HStack>
          </Flex>
        </Container>
      </Box>
      <Container as="main" maxW={CONTENT_WIDTH} py="8">
        <Outlet />
      </Container>
    </Box>
  )
}
