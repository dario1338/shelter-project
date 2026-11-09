import { Tabs } from '@chakra-ui/react'

// The tabs of the dog edit page: the dog itself, its photos and its care details.
export function DogTabList() {
  return (
    <Tabs.List>
      <Tabs.Trigger value="dog">Dog</Tabs.Trigger>
      <Tabs.Trigger value="photos">Photos</Tabs.Trigger>
      <Tabs.Trigger value="care">Care details</Tabs.Trigger>
    </Tabs.List>
  )
}
