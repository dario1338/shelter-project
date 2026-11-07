import { Badge } from '@chakra-ui/react'
import type { DogStatus } from '../api/dogs'
import { statusLabel } from './format'

const PALETTES: Record<DogStatus, string> = {
  AVAILABLE: 'green',
  SPONSORED: 'blue',
  ADOPTED: 'gray',
}

// The text carries the meaning, the colour only helps to find it.
export function StatusBadge({ status }: Readonly<{ status: DogStatus }>) {
  return (
    <Badge colorPalette={PALETTES[status]} variant="subtle">
      {statusLabel(status)}
    </Badge>
  )
}
