import { ChakraProvider } from '@chakra-ui/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from '../App'
import { AuthProvider } from '../auth/AuthProvider'
import { ColorModeProvider } from '../ui/ColorModeProvider'
import { system } from '../ui/system'

// Renders the whole application at a path, the way main.tsx does (without the browser router).
export function renderApp(path = '/') {
  // A new client for every test, so that nothing cached by one test is seen by the next. Retrying is
  // off, because a failed call should show its error at once.
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <ChakraProvider value={system}>
      <ColorModeProvider>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={[path]}>
            <AuthProvider>
              <App />
            </AuthProvider>
          </MemoryRouter>
        </QueryClientProvider>
      </ColorModeProvider>
    </ChakraProvider>,
  )
}
