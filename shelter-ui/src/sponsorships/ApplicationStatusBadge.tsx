import { Badge } from '@chakra-ui/react'
import type { ApplicationStatus } from '../api/sponsorships'
import { applicationStatusLabel } from './format'

const PALETTES: Record<ApplicationStatus, string> = {
  PENDING: 'orange',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'gray',
}

export function ApplicationStatusBadge({ status }: Readonly<{ status: ApplicationStatus }>) {
  return (
    <Badge colorPalette={PALETTES[status]} variant="subtle">
      {applicationStatusLabel(status)}
    </Badge>
  )
}
