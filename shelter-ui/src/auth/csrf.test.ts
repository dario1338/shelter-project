import { afterEach, describe, expect, it } from 'vitest'
import { csrfHeaders } from './csrf'

function setCookie(name: string, value: string) {
  document.cookie = `${name}=${value}; path=/`
}

function clearCookie(name: string) {
  document.cookie = `${name}=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT`
}

afterEach(() => {
  clearCookie('XSRF-TOKEN')
  clearCookie('theme')
})

describe('csrfHeaders', () => {
  it('sends the token from the cookie in the header', () => {
    setCookie('XSRF-TOKEN', 'abc-123')

    expect(csrfHeaders()).toEqual({ 'X-XSRF-TOKEN': 'abc-123' })
  })

  it('finds the cookie among others', () => {
    setCookie('theme', 'dark')
    setCookie('XSRF-TOKEN', 'abc-123')

    expect(csrfHeaders()).toEqual({ 'X-XSRF-TOKEN': 'abc-123' })
  })

  it('leaves the header out when there is no cookie', () => {
    setCookie('theme', 'dark')

    expect(csrfHeaders()).toEqual({})
  })

  it('does not take a cookie that only ends with the name', () => {
    setCookie('NOT-XSRF-TOKEN', 'other')

    expect(csrfHeaders()).toEqual({})
    clearCookie('NOT-XSRF-TOKEN')
  })
})
