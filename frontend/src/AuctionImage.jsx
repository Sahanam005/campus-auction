import { useState } from 'react'
import { resolveImageUrl } from './api.js'

export default function AuctionImage({ src, alt, className, loading = 'lazy' }) {
  const imageUrl = resolveImageUrl(src)
  const [failedUrl, setFailedUrl] = useState('')
  const unavailable = !imageUrl || failedUrl === imageUrl

  return <img
    className={className}
    src={unavailable ? '/item-fallback.svg' : imageUrl}
    alt={alt}
    loading={loading}
    onError={unavailable ? undefined : () => setFailedUrl(imageUrl)}
  />
}
