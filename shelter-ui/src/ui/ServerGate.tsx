import { Button, Center, Spinner, Stack, Text } from '@chakra-ui/react'
import { useEffect, useState, type ReactNode } from 'react'
import { waitForApi } from '../api/waitForApi'
import { ErrorAlert } from './ErrorAlert'

// When the server is awake the check takes a fraction of a second, and nothing is shown for it.
// The waiting screen appears only if the server needs longer, i.e. while it is waking up.
const SHOW_WAITING_AFTER_MS = 600

type GateState = 'checking' | 'ready' | 'failed'

// Holds the application back until the API answers, so the first calls do not hit a sleeping server.
export function ServerGate({ children }: Readonly<{ children: ReactNode }>) {
  const [state, setState] = useState<GateState>('checking')
  const [showWaiting, setShowWaiting] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    const timer = setTimeout(() => setShowWaiting(true), SHOW_WAITING_AFTER_MS)
    void waitForApi(controller.signal).then((healthy) => {
      if (!controller.signal.aborted) {
        setState(healthy ? 'ready' : 'failed')
      }
    })
    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [attempt])

  function tryAgain() {
    setState('checking')
    setShowWaiting(false)
    setAttempt((n) => n + 1)
  }

  if (state === 'ready') {
    return <>{children}</>
  }
  if (state === 'failed') {
    return (
      <Center minH="100vh" p="6">
        <Stack gap="4" maxW="md">
          <ErrorAlert message="The server did not respond. Please try again in a moment." />
          <Button onClick={tryAgain}>Try again</Button>
        </Stack>
      </Center>
    )
  }
  if (!showWaiting) {
    return null
  }
  return (
    <Center minH="100vh" p="6">
      <Stack align="center" gap="4" textAlign="center" role="status">
        <Spinner size="xl" />
        <Text fontWeight="semibold">Waking up the server…</Text>
        <Text color="fg.muted" fontSize="sm">
          The server sleeps when it is not used. This can take up to two minutes.
        </Text>
      </Stack>
    </Center>
  )
}
