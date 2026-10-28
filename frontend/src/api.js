import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 12000,
})

export const getAuctions = async () => (await api.get('/auctions')).data
export const getAuction = async (id) => (await api.get(`/auctions/${id}`)).data
export const getBids = async (id) => (await api.get(`/auctions/${id}/bids`)).data
export const createAuction = async (auction) => (await api.post('/auctions', auction)).data
export const placeBid = async (id, bid) => (await api.post(`/auctions/${id}/bids`, bid)).data

export function apiErrorMessage(error) {
  return error.response?.data?.message || error.response?.data?.error || error.message || 'Something went wrong. Please try again.'
}