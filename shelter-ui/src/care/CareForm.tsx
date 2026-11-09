import { Button, chakra, Field, Heading, HStack, NativeSelect, Stack, Text, Textarea } from '@chakra-ui/react'
import { useState, type SubmitEvent } from 'react'
import type { Care, CareInput } from '../api/care'
import { ErrorAlert } from '../ui/ErrorAlert'
import { FormField } from '../ui/FormField'
import {
  careHasErrors,
  careToForm,
  newRow,
  NOTES_MAX,
  toCareInput,
  validateCareForm,
  VACCINATIONS_MAX,
  type CareFormErrors,
  type CareFormValues,
  type SterilizedChoice,
  type VaccinationRow,
} from './careFormValues'

interface CareFormProps {
  care: Care | undefined
  pending: boolean
  saved: boolean
  error?: string
  onSubmit: (input: CareInput) => void
}

const NO_ERRORS: CareFormErrors = { rows: {} }

function isChoice(value: string): value is SterilizedChoice {
  return value === 'unknown' || value === 'yes' || value === 'no'
}

export function CareForm({ care, pending, saved, error, onSubmit }: Readonly<CareFormProps>) {
  const [values, setValues] = useState<CareFormValues>(() => careToForm(care))
  const [errors, setErrors] = useState<CareFormErrors>(NO_ERRORS)

  const set = (change: Partial<CareFormValues>) => setValues((current) => ({ ...current, ...change }))
  const changeRow = (key: number, change: Partial<VaccinationRow>) =>
    set({ vaccinations: values.vaccinations.map((row) => (row.key === key ? { ...row, ...change } : row)) })

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateCareForm(values)
    setErrors(found)
    if (!careHasErrors(found)) {
      onSubmit(toCareInput(values))
    }
  }

  return (
    <chakra.form noValidate onSubmit={handleSubmit} w="full" maxW="2xl">
      <Stack gap="4">
        {error && <ErrorAlert message={error} />}

        <Field.Root>
          <Field.Label>Sterilized</Field.Label>
          <NativeSelect.Root>
            <NativeSelect.Field
              value={values.sterilized}
              onChange={(event) => {
                if (isChoice(event.target.value)) {
                  set({ sterilized: event.target.value })
                }
              }}
            >
              <option value="unknown">Not known</option>
              <option value="yes">Yes</option>
              <option value="no">No</option>
            </NativeSelect.Field>
            <NativeSelect.Indicator />
          </NativeSelect.Root>
        </Field.Root>

        <FormField
          label="Microchip number"
          value={values.microchipNumber}
          onChange={(microchipNumber) => set({ microchipNumber })}
          error={errors.microchipNumber}
        />

        <Field.Root invalid={Boolean(errors.healthNotes)}>
          <Field.Label>Health notes (the sponsor sees them)</Field.Label>
          <Textarea value={values.healthNotes} onChange={(event) => set({ healthNotes: event.target.value })} rows={4} />
          <Field.HelperText>
            {values.healthNotes.length} / {NOTES_MAX}
          </Field.HelperText>
          <Field.ErrorText>{errors.healthNotes}</Field.ErrorText>
        </Field.Root>

        <Field.Root invalid={Boolean(errors.internalNote)}>
          <Field.Label>Internal note (only administrators see it)</Field.Label>
          <Textarea value={values.internalNote} onChange={(event) => set({ internalNote: event.target.value })} rows={3} />
          <Field.HelperText>
            {values.internalNote.length} / {NOTES_MAX}
          </Field.HelperText>
          <Field.ErrorText>{errors.internalNote}</Field.ErrorText>
        </Field.Root>

        <Heading as="h3" size="md">
          Vaccinations
        </Heading>
        {errors.vaccinations && <ErrorAlert message={errors.vaccinations} />}
        {values.vaccinations.map((row, index) => {
          const rowErrors = errors.rows[row.key] ?? {}
          return (
            <Stack key={row.key} gap="2" borderWidth="1px" borderRadius="md" p="3" role="group" aria-label={`Vaccination ${index + 1}`}>
              <FormField
                label="Vaccine"
                value={row.name}
                onChange={(name) => changeRow(row.key, { name })}
                error={rowErrors.name}
              />
              <HStack gap="3" align="start" wrap="wrap">
                <FormField
                  label="Given on"
                  type="date"
                  value={row.vaccinatedOn}
                  onChange={(vaccinatedOn) => changeRow(row.key, { vaccinatedOn })}
                  error={rowErrors.vaccinatedOn}
                />
                <FormField
                  label="Next dose (optional)"
                  type="date"
                  value={row.nextDueOn}
                  onChange={(nextDueOn) => changeRow(row.key, { nextDueOn })}
                  error={rowErrors.nextDueOn}
                />
              </HStack>
              <Button
                size="sm"
                variant="ghost"
                alignSelf="start"
                onClick={() => set({ vaccinations: values.vaccinations.filter((other) => other.key !== row.key) })}
              >
                Remove vaccination {index + 1}
              </Button>
            </Stack>
          )
        })}
        <Button
          variant="outline"
          alignSelf="start"
          disabled={values.vaccinations.length >= VACCINATIONS_MAX}
          onClick={() => set({ vaccinations: [...values.vaccinations, newRow()] })}
        >
          Add vaccination
        </Button>

        <Button type="submit" alignSelf="start" loading={pending}>
          Save care details
        </Button>
        <Text role="status" aria-live="polite">
          {saved && !pending ? 'Care details saved.' : ''}
        </Text>
      </Stack>
    </chakra.form>
  )
}
