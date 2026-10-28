import { useState } from 'react'
import {
  AlertCircle, AlertTriangle, Calendar, Check, CheckCircle2,
  Clock, MapPin, ShieldAlert, ShieldCheck, XCircle,
} from 'lucide-react'
import {
  approveWinner, confirmHandover, recordFailedPickup, rejectWinner,
  schedulePickup,
} from './api.js'
import { formatPrice } from './formatters.js'

export default function SellerDecisionPanel({ auction, currentUser, onActionComplete }) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [showScheduleForm, setShowScheduleForm] = useState(false)
  const [showRejectForm, setShowRejectForm] = useState(false)
  const [rejectReason, setRejectReason] = useState('')
  const [pickupData, setPickupData] = useState({
    pickupLocation: auction.pickupLocation || 'Student Center Front Desk',
    pickupTime: auction.pickupTime ? auction.pickupTime.slice(0, 16) : '',
    pickupNotes: auction.pickupNotes || 'Look for red backpack near entrance',
  })

  const isSeller = currentUser && auction.sellerUserId && currentUser.id === auction.sellerUserId
  const isWinner = currentUser && auction.winningBidderUserId && currentUser.id === auction.winningBidderUserId
  const isParticipant = isSeller || isWinner

  const handleApprove = async () => {
    setError('')
    setLoading(true)
    try {
      const payload = {
        pickupLocation: pickupData.pickupLocation.trim(),
        pickupTime: pickupData.pickupTime ? `${pickupData.pickupTime}:00` : null,
        pickupNotes: pickupData.pickupNotes.trim(),
      }
      await approveWinner(auction.id, payload)
      onActionComplete?.()
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to approve winner.')
    } finally {
      setLoading(false)
    }
  }

  const handleReject = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await rejectWinner(auction.id, rejectReason.trim())
      setShowRejectForm(false)
      onActionComplete?.()
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to decline bid.')
    } finally {
      setLoading(false)
    }
  }

  const handleScheduleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const payload = {
        pickupLocation: pickupData.pickupLocation.trim(),
        pickupTime: pickupData.pickupTime ? (pickupData.pickupTime.length === 16 ? `${pickupData.pickupTime}:00` : pickupData.pickupTime) : null,
        pickupNotes: pickupData.pickupNotes.trim(),
      }
      await schedulePickup(auction.id, payload)
      setShowScheduleForm(false)
      onActionComplete?.()
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to schedule pickup.')
    } finally {
      setLoading(false)
    }
  }

  const handleConfirmHandover = async () => {
    if (!window.confirm('Confirm that you have completed the in-person campus handover?')) return
    setError('')
    setLoading(true)
    try {
      await confirmHandover(auction.id)
      onActionComplete?.()
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to confirm handover.')
    } finally {
      setLoading(false)
    }
  }

  const handleFailPickup = async () => {
    const reason = window.prompt('Please enter the reason for failed pickup (e.g. buyer did not show up):')
    if (reason === null) return
    setError('')
    setLoading(true)
    try {
      await recordFailedPickup(auction.id, reason)
      onActionComplete?.()
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to record failed pickup.')
    } finally {
      setLoading(false)
    }
  }

  if (auction.status === 'ACTIVE') return null

  return (
    <section className="decision-panel-wrapper" aria-label="Auction outcome and coordination">
      {/* 1. ENDED PENDING SELLER DECISION */}
      {auction.status === 'ENDED_PENDING_SELLER' && (
        <div className="outcome-banner banner-pending">
          <div className="outcome-banner-header">
            <span className="banner-icon"><Clock size={18} /></span>
            <div>
              <h3>Auction Ended · Awaiting Seller Approval</h3>
              <p>
                Provisional winning bidder: <strong>{auction.winningBidderName || 'Bidder'}</strong> at{' '}
                <strong className="text-gold">{formatPrice(auction.winningBidAmount || 0)}</strong>
              </p>
            </div>
          </div>

          {isSeller ? (
            <div className="seller-approval-actions">
              <p className="approval-instruction">
                As the seller, you can approve the provisional winning bidder to reserve this item and schedule campus pickup, or decline if unavailable.
              </p>
              {error && <p className="form-message" role="alert">{error}</p>}
              <div className="approval-btn-row">
                <button
                  className="button button-primary"
                  type="button"
                  disabled={loading}
                  onClick={handleApprove}
                >
                  <CheckCircle2 size={16} /> Approve Winning Bid & Reserve
                </button>
                <button
                  className="button button-secondary button-danger-outline"
                  type="button"
                  disabled={loading}
                  onClick={() => setShowRejectForm(!showRejectForm)}
                >
                  <XCircle size={16} /> Decline Bid
                </button>
              </div>

              {showRejectForm && (
                <form className="decision-subform" onSubmit={handleReject}>
                  <label>
                    Decline Reason (Optional):
                    <input
                      placeholder="e.g. Item broken or no longer available"
                      value={rejectReason}
                      onChange={(e) => setRejectReason(e.target.value)}
                    />
                  </label>
                  <button className="button button-danger" type="submit" disabled={loading}>
                    Confirm Decline
                  </button>
                </form>
              )}
            </div>
          ) : isWinner ? (
            <div className="winner-notice">
              <p>🎉 You have the leading provisional bid! We’ve notified the seller to approve the sale.</p>
            </div>
          ) : (
            <p className="public-ended-note">Bidding has concluded for this item. Awaiting seller confirmation.</p>
          )}
        </div>
      )}

      {/* 2. RESERVED PENDING PICKUP */}
      {auction.status === 'RESERVED_PENDING_PICKUP' && (
        <div className="outcome-banner banner-reserved">
          <div className="outcome-banner-header">
            <span className="banner-icon is-green"><ShieldCheck size={20} /></span>
            <div>
              <h3>Item Reserved · Campus Pickup Coordination</h3>
              <p>
                Reserved for <strong>{auction.winningBidderName || 'Winning Student'}</strong> at{' '}
                <strong className="text-gold">{formatPrice(auction.winningBidAmount || 0)}</strong>
              </p>
            </div>
          </div>

          <div className="pickup-info-card">
            <div className="pickup-info-grid">
              <div className="pickup-data-item">
                <MapPin size={16} className="text-orange" />
                <div>
                  <small>Pickup Spot</small>
                  <strong>{auction.pickupLocation || 'Student Center Front Desk'}</strong>
                </div>
              </div>
              <div className="pickup-data-item">
                <Calendar size={16} className="text-gold" />
                <div>
                  <small>Scheduled Time</small>
                  <strong>{auction.pickupTime ? new Date(auction.pickupTime).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }) : 'Flexible / TBD'}</strong>
                </div>
              </div>
            </div>

            {auction.pickupNotes && (
              <p className="pickup-notes-text">
                <strong>Notes:</strong> {auction.pickupNotes}
              </p>
            )}

            {isParticipant && (
              <div className="pickup-actions-row">
                <button
                  className="button button-secondary"
                  type="button"
                  onClick={() => setShowScheduleForm(!showScheduleForm)}
                >
                  Update Pickup Details
                </button>
                <button
                  className="button button-primary"
                  type="button"
                  disabled={loading}
                  onClick={handleConfirmHandover}
                >
                  <Check size={16} /> Confirm In-Person Handover
                </button>
                {isSeller && (
                  <button
                    className="button button-ghost-danger"
                    type="button"
                    disabled={loading}
                    onClick={handleFailPickup}
                  >
                    Report Failed Pickup
                  </button>
                )}
              </div>
            )}

            {showScheduleForm && (
              <form className="decision-subform" onSubmit={handleScheduleSubmit}>
                <div className="modal-form-row">
                  <label>
                    Pickup Location
                    <input
                      required
                      placeholder="e.g. Library Front Steps"
                      value={pickupData.pickupLocation}
                      onChange={(e) => setPickupData({ ...pickupData, pickupLocation: e.target.value })}
                    />
                  </label>
                  <label>
                    Pickup Date & Time
                    <input
                      type="datetime-local"
                      value={pickupData.pickupTime}
                      onChange={(e) => setPickupData({ ...pickupData, pickupTime: e.target.value })}
                    />
                  </label>
                </div>
                <label>
                  Instructions & Notes
                  <input
                    placeholder="e.g. Call my cell or look for blue hoodie"
                    value={pickupData.pickupNotes}
                    onChange={(e) => setPickupData({ ...pickupData, pickupNotes: e.target.value })}
                  />
                </label>
                <button className="button button-primary" type="submit" disabled={loading}>
                  Save Pickup Details
                </button>
              </form>
            )}
          </div>
          {error && <p className="form-message" role="alert">{error}</p>}
        </div>
      )}

      {/* 3. COMPLETED HANDOVER */}
      {auction.status === 'COMPLETED' && (
        <div className="outcome-banner banner-completed">
          <div className="outcome-banner-header">
            <span className="banner-icon is-gold"><CheckCircle2 size={20} /></span>
            <div>
              <h3>Campus Handover Confirmed</h3>
              <p>
                Successfully handed over to <strong>{auction.winningBidderName}</strong> for{' '}
                <strong className="text-gold">{formatPrice(auction.winningBidAmount || 0)}</strong>.
              </p>
              {auction.handoverConfirmedAt && (
                <small className="handover-time">
                  Confirmed: {new Date(auction.handoverConfirmedAt).toLocaleDateString([], { dateStyle: 'medium' })}
                </small>
              )}
            </div>
          </div>
        </div>
      )}

      {/* 4. UNSOLD */}
      {auction.status === 'UNSOLD' && (
        <div className="outcome-banner banner-neutral">
          <div className="outcome-banner-header">
            <span className="banner-icon"><AlertCircle size={20} /></span>
            <div>
              <h3>Auction Ended Unsold</h3>
              <p>No bids met the starting price before the auction closing time.</p>
            </div>
          </div>
        </div>
      )}

      {/* 5. CANCELLED */}
      {auction.status === 'CANCELLED' && (
        <div className="outcome-banner banner-danger">
          <div className="outcome-banner-header">
            <span className="banner-icon"><AlertTriangle size={20} /></span>
            <div>
              <h3>Listing Cancelled</h3>
              <p>This auction was cancelled by the seller.</p>
              {auction.pickupNotes && <small className="cancel-reason">Reason: {auction.pickupNotes}</small>}
            </div>
          </div>
        </div>
      )}

      {/* 6. REJECTED */}
      {auction.status === 'REJECTED' && (
        <div className="outcome-banner banner-danger">
          <div className="outcome-banner-header">
            <span className="banner-icon"><XCircle size={20} /></span>
            <div>
              <h3>Bid Declined</h3>
              <p>The seller did not approve the provisional winning bid for this listing.</p>
            </div>
          </div>
        </div>
      )}

      {/* 7. FAILED PICKUP */}
      {auction.status === 'FAILED_PICKUP' && (
        <div className="outcome-banner banner-neutral">
          <div className="outcome-banner-header">
            <span className="banner-icon"><ShieldAlert size={20} /></span>
            <div>
              <h3>Campus Pickup Failed</h3>
              <p>The scheduled in-person campus handover could not be completed.</p>
              {auction.pickupNotes && <small className="cancel-reason">Notes: {auction.pickupNotes}</small>}
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
