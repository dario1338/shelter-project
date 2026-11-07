import { Field, NativeSelect } from '@chakra-ui/react'
import type { DogStatus } from '../api/dogs'
import { statusLabel } from './format'
import { isDogStatus } from './listSearch'

const STATUS_OPTIONS: readonly DogStatus[] = ['AVAILABLE', 'SPONSORED', 'ADOPTED']

interface StatusFilterProps {
  value: DogStatus | undefined
  onChange: (status: DogStatus | undefined) => void
}

export function StatusFilter({ value, onChange }: Readonly<StatusFilterProps>) {
  return (
    <Field.Root width="auto" minW="48">
      <Field.Label>Status</Field.Label>
      <NativeSelect.Root>
        <NativeSelect.Field
          value={value ?? ''}
          onChange={(event) => {
            const chosen = event.target.value
            onChange(isDogStatus(chosen) ? chosen : undefined)
          }}
        >
          <option value="">All</option>
          {STATUS_OPTIONS.map((status) => (
            <option key={status} value={status}>
              {statusLabel(status)}
            </option>
          ))}
        </NativeSelect.Field>
        <NativeSelect.Indicator />
      </NativeSelect.Root>
    </Field.Root>
  )
}
