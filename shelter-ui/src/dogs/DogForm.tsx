import { Button, chakra, Field, NativeSelect, Stack, Text, Textarea } from '@chakra-ui/react'
import { useState, type SubmitEvent } from 'react'
import type { DogInput, DogStatus } from '../api/dogs'
import { ErrorAlert } from '../ui/ErrorAlert'
import { FormField } from '../ui/FormField'
import {
  DESCRIPTION_MAX,
  hasErrors,
  toDogInput,
  validateDogForm,
  type DogFormErrors,
  type DogFormValues,
} from './dogFormValues'
import { statusLabel } from './format'
import { isDogStatus } from './listSearch'

const STATUSES: readonly DogStatus[] = ['AVAILABLE', 'SPONSORED', 'ADOPTED']

interface DogFormProps {
  initial: DogFormValues
  submitLabel: string
  pending: boolean
  // The message of the failed request, if the last one failed.
  error?: string
  onSubmit: (input: DogInput) => void
}

export function DogForm({ initial, submitLabel, pending, error, onSubmit }: Readonly<DogFormProps>) {
  const [values, setValues] = useState(initial)
  const [errors, setErrors] = useState<DogFormErrors>({})

  const set = (change: Partial<DogFormValues>) => setValues((current) => ({ ...current, ...change }))

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateDogForm(values)
    setErrors(found)
    if (!hasErrors(found)) {
      onSubmit(toDogInput(values))
    }
  }

  return (
    <chakra.form noValidate onSubmit={handleSubmit} w="full" maxW="lg">
      <Stack gap="4">
        {error && <ErrorAlert message={error} />}
        <FormField label="Name" value={values.name} onChange={(name) => set({ name })} error={errors.name} />
        <FormField label="Breed" value={values.breed} onChange={(breed) => set({ breed })} error={errors.breed} />
        <FormField
          label="Year of birth"
          inputMode="numeric"
          value={values.birthYear}
          onChange={(birthYear) => set({ birthYear })}
          error={errors.birthYear}
        />
        <Field.Root invalid={Boolean(errors.description)}>
          <Field.Label>Description</Field.Label>
          <Textarea value={values.description} onChange={(event) => set({ description: event.target.value })} rows={5} />
          <Field.HelperText>
            {values.description.length} / {DESCRIPTION_MAX}
          </Field.HelperText>
          <Field.ErrorText>{errors.description}</Field.ErrorText>
        </Field.Root>
        <Field.Root>
          <Field.Label>Status</Field.Label>
          <NativeSelect.Root>
            <NativeSelect.Field
              value={values.status}
              onChange={(event) => {
                const chosen = event.target.value
                if (isDogStatus(chosen)) {
                  set({ status: chosen })
                }
              }}
            >
              {STATUSES.map((status) => (
                <option key={status} value={status}>
                  {statusLabel(status)}
                </option>
              ))}
            </NativeSelect.Field>
            <NativeSelect.Indicator />
          </NativeSelect.Root>
          <Field.HelperText>
            Approving an application sets Sponsored by itself. Changing it here does not create or remove an application.
          </Field.HelperText>
        </Field.Root>
        <Button type="submit" alignSelf="start" loading={pending}>
          {submitLabel}
        </Button>
        <Text srOnly role="status" aria-live="polite">
          {pending ? 'Saving…' : ''}
        </Text>
      </Stack>
    </chakra.form>
  )
}
