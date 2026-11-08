import { Box, type BoxProps, Image } from '@chakra-ui/react'
import { useState } from 'react'

interface DogImageProps extends BoxProps {
  name: string
  // The address of the photo. Without one, or when it cannot be loaded, a placeholder takes its place.
  src?: string | null
  alt?: string
}

export function DogImage({ name, src, alt, ...box }: Readonly<DogImageProps>) {
  // The address that failed to load, so a different photo given later is tried again.
  const [failedSrc, setFailedSrc] = useState<string | null>(null)

  if (src && src !== failedSrc) {
    return (
      <Box overflow="hidden" bg="bg.muted" {...box}>
        <Image src={src} alt={alt ?? `Photo of ${name}`} loading="lazy" w="full" h="full" objectFit="cover" onError={() => setFailedSrc(src)} />
      </Box>
    )
  }

  const label = src ? `Photo of ${name} could not be loaded` : `No photo of ${name} yet`
  return (
    <Box
      role="img"
      aria-label={label}
      bg="bg.muted"
      color="fg.muted"
      display="flex"
      alignItems="center"
      justifyContent="center"
      {...box}
    >
      <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <ellipse cx="6.5" cy="10" rx="2" ry="2.6" />
        <ellipse cx="10.5" cy="6.2" rx="2" ry="2.6" />
        <ellipse cx="15.5" cy="6.2" rx="2" ry="2.6" />
        <ellipse cx="19" cy="10" rx="2" ry="2.6" />
        <path d="M12.9 11.2c-2.7 0-5.9 3.3-5.9 6 0 1.7 1.3 2.5 2.9 2.5 1 0 1.8-.5 3-.5s2 .5 3 .5c1.6 0 2.9-.8 2.9-2.5 0-2.7-3.2-6-5.9-6z" />
      </svg>
    </Box>
  )
}
