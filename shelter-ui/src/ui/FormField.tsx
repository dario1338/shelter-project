import { Field, Input, type InputProps } from '@chakra-ui/react'

interface FormFieldProps extends Omit<InputProps, 'onChange' | 'value'> {
  label: string
  value: string
  onChange: (value: string) => void
  error?: string
}

// A labelled text input with its validation message. The label is tied to the input by Chakra,
// so screen readers announce the label and the message together.
export function FormField({ label, value, onChange, error, ...input }: Readonly<FormFieldProps>) {
  return (
    <Field.Root invalid={Boolean(error)}>
      <Field.Label>{label}</Field.Label>
      <Input {...input} value={value} onChange={(event) => onChange(event.target.value)} />
      <Field.ErrorText>{error}</Field.ErrorText>
    </Field.Root>
  )
}
