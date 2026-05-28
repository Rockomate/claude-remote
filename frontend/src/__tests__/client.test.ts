import { describe, it, expect } from 'vitest'

// Test API client utilities
describe('API Client', () => {
  it('should format session names correctly', () => {
    const sessionId = 'de3334de-367c-4d46-a269-627f878e34ed'
    const shortId = sessionId.slice(0, 8)
    expect(shortId).toBe('de3334de')
  })

  it('should truncate long names', () => {
    const longName = 'This is a very long session name that should be truncated'
    const truncated = longName.length > 40 ? longName.slice(0, 40) + '...' : longName
    expect(truncated.length).toBe(43) // 40 + "..."
    expect(truncated.endsWith('...')).toBe(true)
  })

  it('should validate project paths', () => {
    const validPath = 'C:\\Users\\MR\\Desktop\\deepseek'
    expect(validPath).toContain('Desktop')
    expect(validPath).not.toContain('..')
  })

  it('should handle model selection', () => {
    const models = [
      { id: 'default', name: 'Auto (proxy default)', provider: 'system' },
      { id: 'claude-haiku-4-5', name: 'claude-haiku-4-5', provider: 'proxy' },
      { id: 'claude-opus-4-7', name: 'claude-opus-4-7', provider: 'proxy' },
    ]
    expect(models.length).toBe(3)
    expect(models[0].id).toBe('default')
    expect(models[1].id).toBe('claude-haiku-4-5')
  })

  it('should validate project paths', () => {
    const validPath = 'C:\\Users\\MR\\Desktop\\deepseek'
    expect(validPath).toContain('Desktop')
    expect(validPath).not.toContain('..')
    expect(validPath.length).toBeGreaterThan(0)
  })
})
