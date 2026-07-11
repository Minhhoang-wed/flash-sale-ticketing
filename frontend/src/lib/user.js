const USER_STORAGE_KEY = 'flash-sale-user-id'
const ALPHABET = 'abcdefghijklmnopqrstuvwxyz0123456789'

export function createUserId() {
  const values = new Uint32Array(6)

  if (globalThis.crypto?.getRandomValues) {
    globalThis.crypto.getRandomValues(values)
  } else {
    for (let index = 0; index < values.length; index += 1) {
      values[index] = Math.floor(Math.random() * ALPHABET.length)
    }
  }

  const suffix = Array.from(values, (value) => ALPHABET[value % ALPHABET.length]).join('')
  return `user-${suffix}`
}

export function getOrCreateUserId() {
  try {
    const stored = localStorage.getItem(USER_STORAGE_KEY)
    if (stored) return stored

    const userId = createUserId()
    localStorage.setItem(USER_STORAGE_KEY, userId)
    return userId
  } catch {
    return createUserId()
  }
}

export function saveUserId(userId) {
  try {
    localStorage.setItem(USER_STORAGE_KEY, userId)
  } catch {
    // The demo still works when storage is unavailable (for example, private mode).
  }
}
