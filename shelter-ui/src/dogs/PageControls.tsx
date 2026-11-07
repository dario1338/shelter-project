import { Box, Button, HStack, Text } from '@chakra-ui/react'

interface PageControlsProps {
  // One-based, as shown to the user.
  page: number
  totalPages: number
  onChange: (page: number) => void
}

// Shown only when there is more than one page.
export function PageControls({ page, totalPages, onChange }: Readonly<PageControlsProps>) {
  if (totalPages <= 1) {
    return null
  }
  return (
    <Box as="nav" aria-label="Pages">
      <HStack gap="4" justify="center">
        <Button variant="outline" disabled={page <= 1} onClick={() => onChange(page - 1)}>
          Previous
        </Button>
        <Text aria-live="polite">
          Page {page} of {totalPages}
        </Text>
        <Button variant="outline" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>
          Next
        </Button>
      </HStack>
    </Box>
  )
}
