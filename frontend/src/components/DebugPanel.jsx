import { useState } from 'react'
import { ApiError, reserveEvent, resetDemo as resetDemoApi } from '../lib/api'

const EMPTY_RESULTS = { 202: 0, 409: 0, 429: 0, other: 0 }

function statusTone(status) {
  if (status === 202 || status === 200 || status === 201) return 'text-emerald-300'
  if (status === 429) return 'text-amber-300'
  if (status >= 400 || status === 0) return 'text-red-300'
  return 'text-zinc-300'
}

export default function DebugPanel({ userId, logs, onChangeUser, onAfterReset, onRefreshEvent, onRequest }) {
  const [open, setOpen] = useState(false)
  const [resetting, setResetting] = useState(false)
  const [spamming, setSpamming] = useState(false)
  const [crowding, setCrowding] = useState(false)
  const [crowdInfo, setCrowdInfo] = useState(null)
  const [results, setResults] = useState(null)
  const [error, setError] = useState(null)

  const handleReset = async () => {
    if (!window.confirm('Reset toàn bộ vé và đơn hàng của bản demo?')) return

    setResetting(true)
    setError(null)
    try {
      await resetDemoApi({ onRequest })
      setResults(null)
      await onAfterReset()
    } catch (requestError) {
      setError(requestError.message || 'Không thể reset demo.')
    } finally {
      setResetting(false)
    }
  }

  const handleSpam = async () => {
    setSpamming(true)
    setError(null)
    setResults(null)

    const requests = Array.from({ length: 10 }, () =>
      reserveEvent(1, userId, { onRequest }),
    )
    const settled = await Promise.allSettled(requests)
    const nextResults = { ...EMPTY_RESULTS }

    settled.forEach((result) => {
      const status =
        result.status === 'fulfilled'
          ? result.value.status
          : result.reason instanceof ApiError
            ? result.reason.status
            : 0

      if (status === 202 || status === 409 || status === 429) {
        nextResults[status] += 1
      } else {
        nextResults.other += 1
      }
    })

    setResults(nextResults)
    setSpamming(false)
  }

  const handleCrowd = async () => {
    setCrowding(true)
    setError(null)
    setResults(null)
    setCrowdInfo(null)

    const runId = Date.now().toString(36)
    const startedAt = performance.now()
    // 200 NGƯỜI KHÁC NHAU tranh vé cùng lúc (khác nút Spam: 1 user, 10 request)
    const requests = Array.from({ length: 200 }, (_, i) =>
      reserveEvent(1, `crowd-${runId}-${i + 1}`, { onRequest }),
    )
    const settled = await Promise.allSettled(requests)
    const elapsedMs = Math.round(performance.now() - startedAt)

    const nextResults = { ...EMPTY_RESULTS }
    settled.forEach((result) => {
      const status =
        result.status === 'fulfilled'
          ? result.value.status
          : result.reason instanceof ApiError
            ? result.reason.status
            : 0
      if (status === 202 || status === 409 || status === 429) {
        nextResults[status] += 1
      } else {
        nextResults.other += 1
      }
    })

    setResults(nextResults)
    setCrowdInfo({ elapsedMs })
    setCrowding(false)
    // Cập nhật stock bar ngay, khỏi chờ chu kỳ poll kế tiếp
    try {
      await onRefreshEvent?.()
    } catch {
      /* ignore */
    }
  }

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="fixed bottom-4 right-4 z-50 flex items-center gap-2 rounded-full border border-zinc-700 bg-zinc-900/95 px-4 py-2.5 text-xs font-bold text-zinc-300 shadow-xl backdrop-blur transition hover:border-zinc-500 hover:text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-orange-400"
        aria-label="Mở bảng điều khiển demo"
      >
        <span aria-hidden="true">⌘</span> Debug
      </button>
    )
  }

  return (
    <aside className="fixed bottom-3 right-3 z-50 flex max-h-[min(680px,calc(100vh-1.5rem))] w-[calc(100%-1.5rem)] max-w-[390px] flex-col overflow-hidden rounded-2xl border border-zinc-700/80 bg-zinc-950/95 shadow-2xl backdrop-blur-xl">
      <header className="flex items-center justify-between border-b border-white/10 px-4 py-3">
        <div>
          <p className="text-xs font-black uppercase tracking-[0.18em] text-zinc-200">Debug panel</p>
          <p className="mt-0.5 text-[10px] text-zinc-600">Công cụ mô phỏng concurrency</p>
        </div>
        <button
          type="button"
          onClick={() => setOpen(false)}
          className="grid h-8 w-8 place-items-center rounded-lg text-lg text-zinc-500 transition hover:bg-white/5 hover:text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-orange-400"
          aria-label="Thu gọn bảng điều khiển demo"
        >
          ×
        </button>
      </header>

      <div className="overflow-y-auto p-4">
        <section className="rounded-xl border border-white/[0.06] bg-white/[0.025] p-3">
          <div className="flex items-center justify-between gap-3">
            <div className="min-w-0">
              <p className="debug-label">User hiện tại</p>
              <p className="mt-1 truncate font-mono text-sm font-semibold text-orange-300" title={userId}>
                {userId}
              </p>
            </div>
            <button type="button" onClick={onChangeUser} className="debug-secondary-button shrink-0">
              Đổi user
            </button>
          </div>
        </section>

        <div className="mt-3 grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={handleReset}
            disabled={resetting || spamming || crowding}
            className="debug-danger-button"
          >
            {resetting ? 'Đang reset...' : 'Reset demo'}
          </button>
          <button
            type="button"
            onClick={handleSpam}
            disabled={spamming || resetting || crowding}
            className="debug-primary-button"
          >
            {spamming ? 'Đang gửi...' : 'Spam 10 request'}
          </button>
        </div>

        <button
          type="button"
          onClick={handleCrowd}
          disabled={crowding || spamming || resetting}
          className="mt-2 w-full rounded-xl bg-gradient-to-r from-red-500 to-orange-500 px-4 py-3 text-sm font-black uppercase tracking-wide text-white shadow-lg transition hover:brightness-110 disabled:opacity-50"
        >
          {crowding ? '🔥 200 người đang tranh vé...' : '🔥 Giả lập 200 người mua'}
        </button>

        {results && (
          <section className="mt-4">
            <p className="debug-label">
              Kết quả lần chạy gần nhất{crowdInfo ? ` — 200 user / ${crowdInfo.elapsedMs}ms` : ''}
            </p>
            <div className="mt-2 grid grid-cols-3 gap-2">
              {[
                ['202', results[202], 'text-emerald-300'],
                ['409', results[409], 'text-red-300'],
                ['429', results[429], 'text-amber-300'],
              ].map(([status, count, tone]) => (
                <div key={status} className="rounded-xl border border-white/[0.06] bg-white/[0.03] p-2 text-center">
                  <p className={`tabular-nums text-xl font-black ${tone}`}>{count}</p>
                  <p className="mt-0.5 text-[10px] font-bold text-zinc-600">HTTP {status}</p>
                </div>
              ))}
            </div>
            {results.other > 0 && (
              <p className="mt-2 text-center text-[11px] text-zinc-500">Khác: {results.other}</p>
            )}
          </section>
        )}

        {error && (
          <p className="mt-3 rounded-lg border border-red-400/20 bg-red-500/10 p-2 text-xs text-red-200">
            {error}
          </p>
        )}

        <section className="mt-4">
          <div className="flex items-center justify-between">
            <p className="debug-label">20 request gần nhất</p>
            <span className="text-[10px] text-zinc-700">{logs.length}/20</span>
          </div>

          <div className="mt-2 overflow-hidden rounded-xl border border-white/[0.06]">
            {logs.length === 0 ? (
              <p className="px-3 py-5 text-center text-xs text-zinc-600">Chưa có request nào.</p>
            ) : (
              <ul className="max-h-56 divide-y divide-white/[0.05] overflow-y-auto">
                {logs.map((log) => (
                  <li key={log.id} className="grid grid-cols-[38px_1fr_34px_42px] items-center gap-2 px-3 py-2 text-[10px]">
                    <span className="font-black text-zinc-500">{log.method}</span>
                    <span className="truncate font-mono text-zinc-400" title={log.path}>
                      {log.path}
                    </span>
                    <span className={`text-right font-black ${statusTone(log.status)}`}>
                      {log.status || 'ERR'}
                    </span>
                    <span className="tabular-nums text-right text-zinc-600">{log.duration}ms</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>
      </div>
    </aside>
  )
}
