import { Button, Heading, Link, Skeleton, Stack, Text } from '@chakra-ui/react'
import { Link as RouterLink } from 'react-router-dom'
import { errorMessage } from '../api/ApiError'
import { useAuth } from '../auth/useAuth'
import { useMyApplications } from '../sponsorships/useSponsorships'
import { editPath } from '../dogs/editTab'
import { ErrorAlert } from '../ui/ErrorAlert'
import { CareView } from './CareView'
import { useCare } from './useCare'

function LoadedCare({ dogId, admin }: Readonly<{ dogId: number; admin: boolean }>) {
  const care = useCare(dogId)

  return (
    <Stack gap="3" as="section" aria-labelledby="care-heading">
      <Heading as="h2" size="lg" id="care-heading">
        Care details
      </Heading>
      {!admin && (
        <Text color="fg.muted">You sponsor this dog. Only you and the shelter can see these details.</Text>
      )}
      {care.isPending && <Skeleton h="24" />}
      {care.isError && (
        <Stack gap="3" align="start">
          <ErrorAlert message={errorMessage(care.error)} />
          <Button variant="outline" onClick={() => void care.refetch()}>
            Try again
          </Button>
        </Stack>
      )}
      {care.data && <CareView care={care.data} admin={admin} />}
      {admin && (
        <Link asChild variant="underline" alignSelf="start">
          <RouterLink to={editPath(dogId, 'care')}>Edit care details</RouterLink>
        </Link>
      )}
    </Stack>
  )
}

// A user is the sponsor of a dog when the application for it was approved. Asking for that first
// avoids a request that the API would refuse for everybody else.
function SponsorCare({ dogId }: Readonly<{ dogId: number }>) {
  const approved = useMyApplications({ page: 0, size: 1, dogId, status: 'APPROVED' })

  if (approved.data && approved.data.content.length > 0) {
    return <LoadedCare dogId={dogId} admin={false} />
  }
  return null
}

// Care details are shown to administrators and to the sponsor of the dog. Nobody else sees even a heading.
export function CareSection({ dogId }: Readonly<{ dogId: number }>) {
  const { state } = useAuth()

  if (state.status !== 'authenticated') {
    return null
  }
  if (state.user.role === 'ADMIN') {
    return <LoadedCare dogId={dogId} admin />
  }
  return <SponsorCare dogId={dogId} />
}
