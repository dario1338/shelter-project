import { ChakraProvider, defaultSystem } from '@chakra-ui/react'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { dog } from '../test/dogs'
import { DogGallery } from './DogGallery'

function renderGallery(images: { id: number; url: string }[]) {
  return render(
    <ChakraProvider value={defaultSystem}>
      <DogGallery dog={dog({ name: 'Rex', images, primaryImageUrl: images[0]?.url ?? null })} />
    </ChakraProvider>,
  )
}

describe('DogGallery', () => {
  it('shows the placeholder for a dog without photos', () => {
    renderGallery([])

    expect(screen.getByRole('img', { name: 'No photo of Rex yet' })).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('shows a single photo without a row to choose from', () => {
    renderGallery([{ id: 1, url: 'https://files/a.jpg' }])

    expect(screen.getByRole('img', { name: 'Photo of Rex' })).toHaveAttribute('src', 'https://files/a.jpg')
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('shows the first of several photos and lets the visitor choose another', () => {
    renderGallery([
      { id: 1, url: 'https://files/a.jpg' },
      { id: 2, url: 'https://files/b.jpg' },
    ])
    expect(screen.getByRole('img', { name: 'Photo of Rex' })).toHaveAttribute('src', 'https://files/a.jpg')
    expect(screen.getByRole('button', { name: 'Show photo 1 of 2' })).toHaveAttribute('aria-pressed', 'true')

    fireEvent.click(screen.getByRole('button', { name: 'Show photo 2 of 2' }))

    expect(screen.getByRole('img', { name: 'Photo of Rex' })).toHaveAttribute('src', 'https://files/b.jpg')
    expect(screen.getByRole('button', { name: 'Show photo 2 of 2' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Show photo 1 of 2' })).toHaveAttribute('aria-pressed', 'false')
  })
})
