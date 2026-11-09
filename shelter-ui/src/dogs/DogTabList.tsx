import { Tabs } from '@chakra-ui/react'

// The tabs of the dog pages. While a dog is being added it has no photos and no care details yet,
// and nothing to attach them to, so those tabs are shown but cannot be opened.
export function DogTabList({ disabled = false }: Readonly<{ disabled?: boolean }>) {
  return (
    <Tabs.List>
      <Tabs.Trigger value="dog">Dog</Tabs.Trigger>
      <Tabs.Trigger value="photos" disabled={disabled}>
        Photos
      </Tabs.Trigger>
      <Tabs.Trigger value="care" disabled={disabled}>
        Care details
      </Tabs.Trigger>
    </Tabs.List>
  )
}
