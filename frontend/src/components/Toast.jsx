import { useEffect } from 'react'

const toneClasses = {
  success: 'border-emerald-400/30 bg-emerald-950/95 text-emerald-100',
  warning: 'border-amber-400/30 bg-amber-950/95 text-amber-100',
  error: 'border-red-400/30 bg-red-950/95 text-red-100',
  info: 'border-zinc-600 bg-zinc-900/95 text-zinc-100',
}

export default function Toast({ toast, onClose }) {
  useEffect(() => {
    if (!toast) return undefined
    const timeoutId = window.setTimeout(onClose, toast.duration || 3_000)
    return () => window.clearTimeout(timeoutId)
  }, [onClose, toast])

  if (!toast) return null

  return (
    <div
      className={`fixed left-1/2 top-4 z-[70] flex w-[calc(100%-2rem)] max-w-sm -translate-x-1/2 animate-toast-in items-center gap-3 rounded-2xl border px-4 py-3 text-sm font-semibold shadow-2xl backdrop-blur ${
        toneClasses[toast.type] || toneClasses.info
      }`}
      role="status"
      aria-live="polite"
    >
      <span aria-hidden="true">{toast.type === 'success' ? '✓' : toast.type === 'warning' ? '!' : '•'}</span>
      <span className="flex-1">{toast.message}</span>
      <button
        type="button"
        onClick={onClose}
        className="rounded-md px-1 text-lg leading-none opacity-60 transition hover:opacity-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-current"
        aria-label="Đóng thông báo"
      >
        ×
      </button>
    </div>
  )
}
