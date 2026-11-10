import { Field, IconButton, Input, InputGroup, type InputProps } from '@chakra-ui/react'
import { useState } from 'react'

interface PasswordFieldProps extends Omit<InputProps, 'onChange' | 'value' | 'type'> {
  label: string
  value: string
  onChange: (value: string) => void
  error?: string
}

function EyeIcon({ crossed }: Readonly<{ crossed: boolean }>) {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z" />
      <circle cx="12" cy="12" r="3" />
      {crossed && <path d="M4 4l16 16" />}
    </svg>
  )
}

// A password input with a button that shows the password while the user checks what was typed. The
// password is hidden by default, and each field decides on its own, so a page with two of them cannot
// show one by accident. The button is in the tab order after the input and says its state in words.
export function PasswordField({ label, value, onChange, error, ...input }: Readonly<PasswordFieldProps>) {
  const [visible, setVisible] = useState(false)
  const action = visible ? 'Hide password' : 'Show password'
  return (
    <Field.Root invalid={Boolean(error)}>
      <Field.Label>{label}</Field.Label>
      <InputGroup
        endElement={
          <IconButton
            variant="ghost"
            size="xs"
            aria-label={action}
            aria-pressed={visible}
            title={action}
            onClick={() => setVisible((current) => !current)}
          >
            <EyeIcon crossed={visible} />
          </IconButton>
        }
      >
        <Input {...input} type={visible ? 'text' : 'password'} value={value} onChange={(event) => onChange(event.target.value)} />
      </InputGroup>
      <Field.ErrorText>{error}</Field.ErrorText>
    </Field.Root>
  )
}
