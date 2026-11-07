import { describe, expect, it } from 'vitest'
import { returnPathFrom } from './returnTo'

describe('returnPathFrom', () => {
  it('returns the remembered path', () => {
    expect(returnPathFrom({ from: '/dogs/7?x=1' })).toBe('/dogs/7?x=1')
  })

  it.each([undefined, null, {}, { from: 5 }, { from: 'https://evil.example' }, { from: '//evil.example' }, { from: '/\\evil' }, { from: 'dogs' }])(
    'falls back to the home page for %j',
    (state) => {
      expect(returnPathFrom(state)).toBe('/')
    },
  )
})
