import { createSystem, defaultConfig, defineConfig } from '@chakra-ui/react'

// One more breakpoint than the default theme has: a very large monitor (from 2200 px), where the dogs
// are shown five in a row. The laptops and the screens below it keep four.
const config = defineConfig({
  theme: {
    breakpoints: {
      '3xl': '137.5em',
    },
  },
})

export const system = createSystem(defaultConfig, config)
