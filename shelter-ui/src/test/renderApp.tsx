import { ChakraProvider, defaultSystem } from '@chakra-ui/react'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from '../App'
import { AuthProvider } from '../auth/AuthProvider'
import { ColorModeProvider } from '../ui/ColorModeProvider'

// Renders the whole application at a path, the way main.tsx does (without the browser router).
export function renderApp(path = '/') {
  return render(
    <ChakraProvider value={defaultSystem}>
      <ColorModeProvider>
        <MemoryRouter initialEntries={[path]}>
          <AuthProvider>
            <App />
          </AuthProvider>
        </MemoryRouter>
      </ColorModeProvider>
    </ChakraProvider>,
  )
}
