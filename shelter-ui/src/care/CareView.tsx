import { Box, Heading, Stack, Table, Text } from '@chakra-ui/react'
import type { Care } from '../api/care'
import { formatDay, isCareEmpty, sterilizedLabel } from './format'

interface CareViewProps {
  care: Care
  // Administrators also see the internal note.
  admin: boolean
}

export function CareView({ care, admin }: Readonly<CareViewProps>) {
  if (isCareEmpty(care)) {
    return <Text color="fg.muted">No care details have been added yet.</Text>
  }
  return (
    <Stack gap="4">
      <Box as="dl" display="grid" gridTemplateColumns="auto 1fr" columnGap="4" rowGap="1">
        <Text as="dt" color="fg.muted">
          Sterilized
        </Text>
        <Text as="dd">{sterilizedLabel(care.sterilized)}</Text>
        <Text as="dt" color="fg.muted">
          Microchip
        </Text>
        <Text as="dd">{care.microchipNumber ?? 'Not known'}</Text>
      </Box>
      {care.healthNotes && <Text whiteSpace="pre-wrap">{care.healthNotes}</Text>}

      <Heading as="h3" size="md">
        Vaccinations
      </Heading>
      {care.vaccinations.length === 0 ? (
        <Text color="fg.muted">No vaccinations recorded.</Text>
      ) : (
        <Table.Root size="sm" aria-label="Vaccinations">
          <Table.Header>
            <Table.Row>
              <Table.ColumnHeader>Vaccine</Table.ColumnHeader>
              <Table.ColumnHeader>Given</Table.ColumnHeader>
              <Table.ColumnHeader>Next dose</Table.ColumnHeader>
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {care.vaccinations.map((vaccination) => (
              <Table.Row key={vaccination.id}>
                <Table.Cell>{vaccination.name}</Table.Cell>
                <Table.Cell>{formatDay(vaccination.vaccinatedOn)}</Table.Cell>
                <Table.Cell>{vaccination.nextDueOn ? formatDay(vaccination.nextDueOn) : '—'}</Table.Cell>
              </Table.Row>
            ))}
          </Table.Body>
        </Table.Root>
      )}

      {admin && care.internalNote && (
        <Box borderWidth="1px" borderRadius="md" p="3">
          <Text fontWeight="semibold">Internal note (only administrators see this)</Text>
          <Text whiteSpace="pre-wrap">{care.internalNote}</Text>
        </Box>
      )}
    </Stack>
  )
}
