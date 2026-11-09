import { Box, Button, Container, Flex, HStack, IconButton, Link, Separator, Stack, Text } from '@chakra-ui/react'
import { useState, type KeyboardEvent, type ReactNode } from 'react'
import { Link as RouterLink, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { PawIcon } from './PawIcon'
import { COMPACT, useMediaQuery } from './screens'
import { useColorMode } from './useColorMode'

// The content is up to 1280 px wide, and up to 1600 px on a very large monitor, where there is room for a
// fifth dog in a row. The header uses the same width, so the two line up.
const CONTENT_WIDTH = { base: '7xl', '3xl': '100rem' }

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

function MenuIcon({ open }: Readonly<{ open: boolean }>) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
      {open ? <path d="M6 6l12 12M18 6L6 18" /> : <path d="M4 6h16M4 12h16M4 18h16" />}
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

function Brand() {
  return (
    <Link asChild fontWeight="bold" fontSize="lg" color="purple.fg">
      <RouterLink to="/">
        <PawIcon size={22} />
        Shelter
      </RouterLink>
    </Link>
  )
}

interface NavTarget {
  to: string
  label: string
}

// What a visitor can open, by role: the links of the signed-in roles come after "Dogs".
function useNavTargets(): NavTarget[] {
  const { state } = useAuth()
  const role = state.status === 'authenticated' ? state.user.role : null
  const targets: NavTarget[] = [{ to: '/dogs', label: 'Dogs' }]
  if (role === 'USER') {
    targets.push({ to: '/my-dogs', label: 'My dogs' }, { to: '/applications', label: 'My applications' })
  }
  if (role === 'ADMIN') {
    targets.push({ to: '/admin/applications?status=PENDING', label: 'Applications' })
  }
  return targets
}

// The page that is open is marked by the router (aria-current), and shown in a heavier weight.
function NavItem({ to, children, onClick }: Readonly<{ to: string; children: ReactNode; onClick?: () => void }>) {
  return (
    <Link asChild _currentPage={{ fontWeight: 'semibold' }}>
      <NavLink to={to} onClick={onClick}>
        {children}
      </NavLink>
    </Link>
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

// On a screen wide enough for it: the links on the left, the account on the right.
function WideHeader() {
  const targets = useNavTargets()
  return (
    <Flex align="center" justify="space-between" py="3" columnGap="4" rowGap="2" wrap="wrap">
      <HStack as="nav" aria-label="Main" gap="5" wrap="wrap">
        <Brand />
        {targets.map((target) => (
          <NavItem key={target.to} to={target.to}>
            {target.label}
          </NavItem>
        ))}
      </HStack>
      <HStack gap="2">
        <AccountLinks />
        <ColorModeToggle />
      </HStack>
    </Flex>
  )
}

// On a phone: a button that opens the menu on the left, and who is signed in on the right. The menu holds the
// links and the account actions (sign in, create account, sign out), and closes when one of them is used.
function CompactHeader() {
  const { state, signOut } = useAuth()
  const targets = useNavTargets()
  const [open, setOpen] = useState(false)
  const close = () => setOpen(false)

  function closeOnEscape(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      close()
    }
  }

  return (
    <Box onKeyDown={closeOnEscape}>
      <Flex align="center" justify="space-between" py="3" columnGap="3">
        <HStack gap="2">
          <IconButton
            variant="ghost"
            size="sm"
            aria-label={open ? 'Close the menu' : 'Open the menu'}
            aria-expanded={open}
            aria-controls="main-menu"
            onClick={() => setOpen((current) => !current)}
          >
            <MenuIcon open={open} />
          </IconButton>
          <Brand />
        </HStack>
        <HStack gap="2">
          {state.status === 'authenticated' && <Text fontSize="sm">{state.user.firstName}</Text>}
          <ColorModeToggle />
        </HStack>
      </Flex>
      {open && (
        <Box as="nav" id="main-menu" aria-label="Main" pb="4">
          <Stack gap="3" align="start">
            {targets.map((target) => (
              <NavItem key={target.to} to={target.to} onClick={close}>
                {target.label}
              </NavItem>
            ))}
            {state.status !== 'loading' && <Separator w="full" />}
            {state.status === 'anonymous' && (
              <>
                <NavItem to="/login" onClick={close}>
                  Sign in
                </NavItem>
                <NavItem to="/register" onClick={close}>
                  Create account
                </NavItem>
              </>
            )}
            {state.status === 'authenticated' && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  close()
                  void signOut().catch(() => undefined)
                }}
              >
                Sign out
              </Button>
            )}
          </Stack>
        </Box>
      )}
    </Box>
  )
}

export default function AppLayout() {
  const compact = useMediaQuery(COMPACT)
  return (
    <Box minH="100vh">
      <Box as="header" borderBottomWidth="1px">
        <Container maxW={CONTENT_WIDTH}>{compact ? <CompactHeader /> : <WideHeader />}</Container>
      </Box>
      <Container as="main" maxW={CONTENT_WIDTH} py="8">
        <Outlet />
      </Container>
    </Box>
  )
}
