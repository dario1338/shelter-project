import { Field, NativeSelect } from '@chakra-ui/react'
import type { ApplicationStatus } from '../api/sponsorships'
import { APPLICATION_STATUSES, applicationStatusLabel, isApplicationStatus } from './format'

interface ApplicationStatusFilterProps {
  value: ApplicationStatus | undefined
  onChange: (status: ApplicationStatus | undefined) => void
}

export function ApplicationStatusFilter({ value, onChange }: Readonly<ApplicationStatusFilterProps>) {
  return (
    <Field.Root width="auto" minW="48">
      <Field.Label>Status</Field.Label>
      <NativeSelect.Root>
        <NativeSelect.Field
          value={value ?? ''}
          onChange={(event) => {
            const chosen = event.target.value
            onChange(isApplicationStatus(chosen) ? chosen : undefined)
          }}
        >
          <option value="">All</option>
          {APPLICATION_STATUSES.map((status) => (
            <option key={status} value={status}>
              {applicationStatusLabel(status)}
            </option>
          ))}
        </NativeSelect.Field>
        <NativeSelect.Indicator />
      </NativeSelect.Root>
    </Field.Root>
  )
}
