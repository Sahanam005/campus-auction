import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 12000,
})

export const getAuctions = async () => (await api.get('/auctions')).data
export const getAuction = async (id) => (await api.get(`/auctions/${id}`)).data
export const getBids = async (id) => (await api.get(`/auctions/${id}/bids`)).data
export const createAuction = async (auction, imageFile) => {
  if (!imageFile) return (await api.post('/auctions', auction)).data

  const formData = new FormData()
  formData.append('auction', new Blob([JSON.stringify(auction)], { type: 'application/json' }), 'auction.json')
  formData.append('image', imageFile)
  return (await api.post('/auctions', formData)).data
}
export const placeBid = async (id, bid) => (await api.post(`/auctions/${id}/bids`, bid)).data

export function resolveImageUrl(imageUrl) {
  if (!imageUrl || /(?:placehold\.co|via\.placeholder\.com)/i.test(imageUrl)) return ''
  try {
    const apiBase = new URL(api.defaults.baseURL, window.location.origin)
    return new URL(imageUrl, apiBase).toString()
  } catch {
    return imageUrl
  }
}

export function apiErrorMessage(error) {
  return error.response?.data?.message || error.response?.data?.error || error.message || 'Something went wrong. Please try again.'
}