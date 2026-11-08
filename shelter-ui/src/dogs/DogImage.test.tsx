import { ChakraProvider, defaultSystem } from '@chakra-ui/react'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DogImage } from './DogImage'

function renderImage(props: Parameters<typeof DogImage>[0]) {
  return render(
    <ChakraProvider value={defaultSystem}>
      <DogImage {...props} />
    </ChakraProvider>,
  )
}

describe('DogImage', () => {
  it('shows a placeholder when the dog has no photo', () => {
    renderImage({ name: 'Rex' })

    expect(screen.getByRole('img', { name: 'No photo of Rex yet' })).toBeInTheDocument()
  })

  it('shows the photo when there is one', () => {
    renderImage({ name: 'Rex', src: 'https://files/a.jpg' })

    expect(screen.getByRole('img', { name: 'Photo of Rex' })).toHaveAttribute('src', 'https://files/a.jpg')
  })

  it('takes the description of the photo from the caller', () => {
    renderImage({ name: 'Rex', src: 'https://files/a.jpg', alt: 'Photo 2 of Rex' })

    expect(screen.getByRole('img', { name: 'Photo 2 of Rex' })).toBeInTheDocument()
  })

  it('falls back to the placeholder when the photo cannot be loaded', () => {
    renderImage({ name: 'Rex', src: 'https://files/gone.jpg' })

    fireEvent.error(screen.getByRole('img', { name: 'Photo of Rex' }))

    expect(screen.getByRole('img', { name: 'Photo of Rex could not be loaded' })).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: 'Photo of Rex' })).not.toBeInTheDocument()
  })

  it('tries a different photo again after one has failed', () => {
    const view = renderImage({ name: 'Rex', src: 'https://files/gone.jpg' })
    fireEvent.error(screen.getByRole('img', { name: 'Photo of Rex' }))

    view.rerender(
      <ChakraProvider value={defaultSystem}>
        <DogImage name="Rex" src="https://files/b.jpg" />
      </ChakraProvider>,
    )

    expect(screen.getByRole('img', { name: 'Photo of Rex' })).toHaveAttribute('src', 'https://files/b.jpg')
  })
})
