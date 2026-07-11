import { beforeEach, describe, expect, it } from 'vitest'
import { createUserId, getOrCreateUserId, saveUserId } from './user'

beforeEach(() => {
  localStorage.clear()
})

describe('demo user identity', () => {
  it('tạo user id đúng định dạng', () => {
    expect(createUserId()).toMatch(/^user-[a-z0-9]{6}$/)
  })

  it('lưu và dùng lại cùng user giữa các lần tải trang', () => {
    saveUserId('user-fixed1')
    expect(getOrCreateUserId()).toBe('user-fixed1')
  })
})
