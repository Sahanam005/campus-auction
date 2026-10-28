import { useEffect, useState } from 'react'

export default function useClock() {
  const [now, setNow] = useState(0)

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  return now
}