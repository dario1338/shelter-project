import { Alert } from '@chakra-ui/react'

export function ErrorAlert({ message }: Readonly<{ message: string }>) {
  return (
    <Alert.Root status="error">
      <Alert.Indicator />
      <Alert.Content>
        <Alert.Description>{message}</Alert.Description>
      </Alert.Content>
    </Alert.Root>
  )
}
