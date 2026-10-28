import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 12000,
  withCredentials: true,
})

let csrfToken = ''
const ensureCsrfToken = async () => {
  const response = await api.get('/auth/csrf')
  csrfToken = response.data.csrfToken
}
const postWithCsrf = async (path, body) => (await api.post(path, body, {
  headers: { 'X-CSRF-TOKEN': csrfToken },
})).data

export const getCurrentUser = async () => {
  try {
    return (await api.get('/auth/me')).data
  } catch (error) {
    if (error.response?.status === 401) return null
    throw error
  }
}
export const registerAccount = async (account) => {
  await ensureCsrfToken()
  return postWithCsrf('/auth/register', account)
}
export const loginAccount = async (credentials) => {
  await ensureCsrfToken()
  return postWithCsrf('/auth/login', credentials)
}
export const logoutAccount = async () => {
  await ensureCsrfToken()
  return postWithCsrf('/auth/logout')
}
export const getAuctions = async () => (await api.get('/auctions')).data
export const getAuction = async (id) => (await api.get(`/auctions/${id}`)).data
export const getBids = async (id) => (await api.get(`/auctions/${id}/bids`)).data
export const getMyAuctions = async () => (await api.get('/auctions/mine')).data
export const createAuction = async (auction, imageFile) => {
  await ensureCsrfToken()
  if (!imageFile) return postWithCsrf('/auctions', auction)

  const formData = new FormData()
  formData.append('auction', new Blob([JSON.stringify(auction)], { type: 'application/json' }), 'auction.json')
  formData.append('image', imageFile)
  return postWithCsrf('/auctions', formData)
}
export const placeBid = async (id, amount) => {
  await ensureCsrfToken()
  return postWithCsrf(`/auctions/${id}/bids`, { amount })
}
export const updateAuction = async (id, auction) => {
  await ensureCsrfToken()
  return (await api.put(`/auctions/${id}`, auction, {
    headers: { 'X-CSRF-TOKEN': csrfToken },
  })).data
}
export const cancelAuction = async (id, reason) => {
  await ensureCsrfToken()
  return postWithCsrf(`/auctions/${id}/cancel`, { reason })
}
export const approveWinner = async (id, pickupDetails) => {
  await ensureCsrfToken()
  return postWithCsrf(`/auctions/${id}/approve`, pickupDetails)
}
export const rejectWinner = async (id, reason) => {
  await ensureCsrfToken()
  return postWithCsrf(`/auctions/${id}/reject`, { reason })
}
export const schedulePickup = async (id, pickupDetails) => {
  await ensureCsrfToken()
  return postWithCsrf(`/auctions/${id}/pickup`, pickupDetails)
}
export const confirmHandover = async (id) => {
  await ensureCsrfToken()
  return postWithCsrf(`/auctions/${id}/handover`)
}
export const recordFailedPickup = async (id, reason) => {
  await ensureCsrfToken()
  return postWithCsrf(`/auctions/${id}/pickup-failed`, { reason })
}

export const getNotifications = async () => (await api.get('/notifications')).data
export const getUnreadNotificationCount = async () => (await api.get('/notifications/unread-count')).data?.unreadCount || 0
export const markNotificationRead = async (id) => {
  await ensureCsrfToken()
  return (await api.patch(`/notifications/${id}/read`, {}, { headers: { 'X-CSRF-TOKEN': csrfToken } })).data
}
export const markAllNotificationsRead = async () => {
  await ensureCsrfToken()
  return postWithCsrf('/notifications/mark-all-read')
}

export const getSavedSearches = async () => (await api.get('/saved-searches')).data
export const createSavedSearch = async (search) => {
  await ensureCsrfToken()
  return postWithCsrf('/saved-searches', search)
}
export const deleteSavedSearch = async (id) => {
  await ensureCsrfToken()
  return (await api.delete(`/saved-searches/${id}`, { headers: { 'X-CSRF-TOKEN': csrfToken } })).data
}

export const getAuctionQuestions = async (id) => (await api.get(`/auctions/${id}/questions`)).data
export const askAuctionQuestion = async (id, questionText) => {
  await ensureCsrfToken()
  return postWithCsrf(`/auctions/${id}/questions`, { questionText })
}
export const answerAuctionQuestion = async (id, questionId, answerText) => {
  await ensureCsrfToken()
  return postWithCsrf(`/auctions/${id}/questions/${questionId}/answer`, { answerText })
}

export const chatWithAssistant = async (message, statedBudget, contextAuctionId) => {
  return (await api.post('/assistant/chat', { message, statedBudget, contextAuctionId })).data
}

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
  return error.response?.data?.message || error.response?.data?.detail || error.response?.data?.error || error.message || 'Something went wrong. Please try again.'
}