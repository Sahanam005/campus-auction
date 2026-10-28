import { useEffect, useState } from 'react'

function getRemaining(closingTime) {
  const difference = new Date(closingTime).getTime() - Date.now()
  return Number.isFinite(difference) ? Math.max(0, Math.floor(difference / 1000)) : 0
}

export default function Countdown({ closingTime, status, compact = false }) {
  const [seconds, setSeconds] = useState(() => getRemaining(closingTime))

  useEffect(() => {
    const timer = window.setInterval(() => setSeconds(getRemaining(closingTime)), 1000)
    return () => window.clearInterval(timer)
  }, [closingTime])

  if (status !== 'ACTIVE' || seconds <= 0) return <span className="countdown-ended">Ended</span>

  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remainder = seconds % 60
  const label = days ? `${days}d ${hours}h` : `${hours}h ${minutes}m ${remainder}s`
  if (compact) return <span className="countdown-compact" aria-label={`${label} remaining`}>{days ? `${days}d ${hours}h` : `${hours}h ${minutes}m`}</span>

  return <span className="countdown-units" aria-label={`${label} remaining`}>
    {days > 0 && <span><b>{String(days).padStart(2, '0')}</b><small>d</small></span>}
    <span><b>{String(hours).padStart(2, '0')}</b><small>h</small></span><span><b>{String(minutes).padStart(2, '0')}</b><small>m</small></span>
    {!days && <span><b>{String(remainder).padStart(2, '0')}</b><small>s</small></span>}
  </span>
}