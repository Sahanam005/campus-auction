import { useState } from 'react'
import { AlertCircle, ArrowRight, Ban, Check, Edit3, X } from 'lucide-react'
import { cancelAuction, updateAuction } from './api.js'

const categories = ['Cycles', 'Books', 'Calculators', 'Electronics', 'Other']
const conditions = ['Like New', 'Excellent', 'Good', 'Fair']

export default function ListingEditModal({ auction, bidsCount, mode = 'edit', isOpen, onClose, onUpdated }) {
  const [form, setForm] = useState({
    title: auction?.title || '',
    category: auction?.category || 'Electronics',
    conditionLabel: auction?.conditionLabel || 'Good',
    description: auction?.description || '',
    startingPrice: auction?.startingPrice || '',
    minIncrement: auction?.minIncrement || '',
    closingTime: auction?.closingTime ? auction.closingTime.slice(0, 16) : '',
  })
  const [cancelReason, setCancelReason] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  if (!isOpen || !auction) return null

  const hasBids = bidsCount > 0

  const handleEditSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const payload = {
        title: form.title.trim(),
        category: form.category,
        conditionLabel: form.conditionLabel,
        description: form.description.trim(),
        closingTime: form.closingTime ? (form.closingTime.length === 16 ? `${form.closingTime}:00` : form.closingTime) : null,
      }
      if (!hasBids) {
        if (form.startingPrice) payload.startingPrice = Number(form.startingPrice)
        if (form.minIncrement) payload.minIncrement = Number(form.minIncrement)
      }
      const updated = await updateAuction(auction.id, payload)
      onUpdated?.(updated)
      onClose()
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to update listing.')
    } finally {
      setLoading(false)
    }
  }

  const handleCancelSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const updated = await cancelAuction(auction.id, cancelReason.trim())
      onUpdated?.(updated)
      onClose()
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to cancel listing.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose} role="dialog" aria-modal="true" aria-label={mode === 'edit' ? 'Edit Listing' : 'Cancel Listing'}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-card-header">
          <div className="drawer-title">
            {mode === 'edit' ? <Edit3 size={18} className="accent-icon" /> : <Ban size={18} className="danger-icon" />}
            <h3>{mode === 'edit' ? 'Edit Listing' : 'Cancel Listing'}</h3>
          </div>
          <button className="icon-close-btn" type="button" onClick={onClose} aria-label="Close modal">
            <X size={18} />
          </button>
        </div>

        {mode === 'edit' ? (
          <form className="modal-form" onSubmit={handleEditSubmit}>
            <label>
              Title
              <input
                required
                maxLength={150}
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
              />
            </label>

            <div className="modal-form-row">
              <label>
                Category
                <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
                  {categories.map((c) => (
                    <option key={c}>{c}</option>
                  ))}
                </select>
              </label>
              <label>
                Condition
                <select value={form.conditionLabel} onChange={(e) => setForm({ ...form, conditionLabel: e.target.value })}>
                  {conditions.map((c) => (
                    <option key={c}>{c}</option>
                  ))}
                </select>
              </label>
            </div>

            <label>
              Description
              <textarea
                required
                rows={4}
                maxLength={2000}
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </label>

            <div className="modal-form-row">
              <label>
                Starting Price (₹)
                <input
                  type="number"
                  min="0.01"
                  step="0.01"
                  disabled={hasBids}
                  value={form.startingPrice}
                  onChange={(e) => setForm({ ...form, startingPrice: e.target.value })}
                />
              </label>
              <label>
                Min Increment (₹)
                <input
                  type="number"
                  min="0.01"
                  step="0.01"
                  disabled={hasBids}
                  value={form.minIncrement}
                  onChange={(e) => setForm({ ...form, minIncrement: e.target.value })}
                />
              </label>
            </div>
            {hasBids && (
              <p className="field-hint">
                <AlertCircle size={13} /> Pricing is locked because bids have already been placed.
              </p>
            )}

            <label>
              Closing Date & Time
              <input
                type="datetime-local"
                value={form.closingTime}
                onChange={(e) => setForm({ ...form, closingTime: e.target.value })}
              />
            </label>

            {error && <p className="form-message" role="alert">{error}</p>}

            <div className="modal-card-actions">
              <button className="button button-secondary" type="button" onClick={onClose}>
                Cancel
              </button>
              <button className="button button-primary" type="submit" disabled={loading}>
                {loading ? 'Saving…' : 'Save Changes'} <Check size={16} />
              </button>
            </div>
          </form>
        ) : (
          <form className="modal-form" onSubmit={handleCancelSubmit}>
            <p className="danger-warning-text">
              Are you sure you want to cancel this listing? Once cancelled, no further bids can be accepted.
            </p>

            <label>
              Cancellation Reason (Optional)
              <textarea
                rows={3}
                placeholder="e.g. Item damaged or no longer available for campus sale"
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
              />
            </label>

            {error && <p className="form-message" role="alert">{error}</p>}

            <div className="modal-card-actions">
              <button className="button button-secondary" type="button" onClick={onClose}>
                Keep Listing
              </button>
              <button className="button button-danger" type="submit" disabled={loading}>
                {loading ? 'Cancelling…' : 'Confirm Cancellation'} <ArrowRight size={16} />
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
