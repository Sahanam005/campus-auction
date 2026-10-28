import { useEffect, useRef, useState } from 'react'
import {
  AlertTriangle, ArrowRight, CheckCircle2, ChevronRight,
  Send, ShieldCheck, Sparkles, Wallet, X,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { chatWithAssistant, placeBid } from './api.js'
import { formatPrice } from './formatters.js'

const QUICK_PROMPTS = [
  'Find cycles under ₹2000',
  'Calculators for engineering class',
  'Explain anti-sniping and bidding rules',
  'How does campus handover work?',
]

export default function AiAssistantDrawer({ isOpen, onClose, user, onBidPlaced, initialAuction }) {
  const [messages, setMessages] = useState([
    {
      sender: 'assistant',
      text: 'Hi! I’m your Campus Exchange AI Assistant. I use real marketplace listings to help you find deals, explain bidding rules, and enforce your budget.',
      listings: [],
      proposal: null,
      rules: null,
    },
  ])
  const [input, setInput] = useState('')
  const [budget, setBudget] = useState('')
  const [loading, setLoading] = useState(false)
  const [bidInProgress, setBidInProgress] = useState(false)
  const [bidFeedback, setBidFeedback] = useState('')
  const chatBottomRef = useRef(null)

  useEffect(() => {
    chatBottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  if (!isOpen) return null

  const handleSend = async (textToSend) => {
    const message = (textToSend || input).trim()
    if (!message) return

    const statedBudgetNum = budget ? Number(budget) : null

    const userMsg = { sender: 'user', text: message }
    setMessages((prev) => [...prev, userMsg])
    setInput('')
    setLoading(true)
    setBidFeedback('')

    try {
      const res = await chatWithAssistant(message, statedBudgetNum, initialAuction?.id || null)

      if (res.statedBudget && !budget) {
        setBudget(String(res.statedBudget))
      }

      const assistantMsg = {
        sender: 'assistant',
        text: res.reply,
        listings: res.listings || [],
        proposal: res.proposedAction || null,
        rules: res.explanation || null,
      }
      setMessages((prev) => [...prev, assistantMsg])
    } catch {
      setMessages((prev) => [
        ...prev,
        {
          sender: 'assistant',
          text: 'Sorry, I couldn’t reach the campus marketplace right now. Please try again.',
        },
      ])
    } finally {
      setLoading(false)
    }
  }

  const handleConfirmBid = async (proposal) => {
    if (!user) {
      setBidFeedback('Please sign in to place a binding bid.')
      return
    }

    setBidInProgress(true)
    setBidFeedback('')
    try {
      await placeBid(proposal.auctionId, proposal.proposedAmount)
      setBidFeedback(`Success! Placed verified bid of ${formatPrice(proposal.proposedAmount)} on "${proposal.auctionTitle}".`)
      onBidPlaced?.()
      setMessages((prev) => [
        ...prev,
        {
          sender: 'assistant',
          text: `Confirmed! Your bid of ${formatPrice(proposal.proposedAmount)} has been placed on "${proposal.auctionTitle}". Good luck!`,
        },
      ])
    } catch (err) {
      setBidFeedback(err.response?.data?.message || err.message || 'Failed to place bid.')
    } finally {
      setBidInProgress(false)
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose} role="dialog" aria-modal="true" aria-label="AI Auction Assistant">
      <div className="modal-drawer assistant-drawer" onClick={(e) => e.stopPropagation()}>
        <div className="drawer-header assistant-header">
          <div className="drawer-title">
            <span className="sparkle-badge">
              <Sparkles size={16} />
            </span>
            <div>
              <h3>AI Auction Assistant</h3>
              <small>Real-data grounded & budget-guarded</small>
            </div>
          </div>
          <button className="icon-close-btn" type="button" onClick={onClose} aria-label="Close assistant">
            <X size={18} />
          </button>
        </div>

        <div className="budget-bar">
          <div className="budget-label">
            <Wallet size={15} />
            <span>My Target Budget:</span>
          </div>
          <div className="budget-input-wrap">
            <span>₹</span>
            <input
              type="number"
              min="0"
              placeholder="e.g. 1500"
              value={budget}
              onChange={(e) => setBudget(e.target.value)}
              aria-label="Target budget in rupees"
            />
          </div>
          {budget && (
            <button className="budget-clear-btn" type="button" onClick={() => setBudget('')} title="Clear budget">
              Clear
            </button>
          )}
        </div>

        <div className="drawer-body assistant-body">
          <div className="chat-messages">
            {messages.map((m, idx) => (
              <div key={idx} className={`chat-bubble-wrap ${m.sender === 'user' ? 'is-user' : 'is-assistant'}`}>
                {m.sender === 'assistant' && (
                  <span className="assistant-avatar">
                    <Sparkles size={14} />
                  </span>
                )}
                <div className="chat-bubble">
                  <p className="bubble-text">{m.text}</p>

                  {m.rules && (
                    <div className="rules-box">
                      <div className="rules-title">
                        <ShieldCheck size={16} />
                        <span>Marketplace Bidding Rules</span>
                      </div>
                      <pre className="rules-text">{m.rules}</pre>
                    </div>
                  )}

                  {m.listings && m.listings.length > 0 && (
                    <div className="assistant-listings-grid">
                      {m.listings.map((item) => (
                        <Link
                          key={item.id}
                          to={`/auctions/${item.id}`}
                          className="assistant-listing-card"
                          onClick={onClose}
                        >
                          <div className="assistant-listing-info">
                            <strong>{item.title}</strong>
                            <div className="assistant-listing-meta">
                              <span>Starting: {formatPrice(item.startingPrice)}</span>
                              <span className="category-tag">{item.category}</span>
                            </div>
                          </div>
                          <ChevronRight size={16} className="listing-arrow" />
                        </Link>
                      ))}
                    </div>
                  )}

                  {m.proposal && (
                    <div className={`bid-proposal-card ${m.proposal.withinBudget ? 'within-budget' : 'exceeds-budget'}`}>
                      <div className="proposal-header">
                        {m.proposal.withinBudget ? (
                          <span className="proposal-status is-safe">
                            <CheckCircle2 size={15} /> Budget Safe
                          </span>
                        ) : (
                          <span className="proposal-status is-warning">
                            <AlertTriangle size={15} /> Exceeds Stated Budget
                          </span>
                        )}
                        <span className="proposal-label">PROPOSED BID</span>
                      </div>

                      <div className="proposal-details">
                        <h4>{m.proposal.auctionTitle}</h4>
                        <div className="proposal-price-row">
                          <div>
                            <small>Current Price</small>
                            <span>{formatPrice(m.proposal.currentPrice)}</span>
                          </div>
                          <ArrowRight size={14} />
                          <div>
                            <small>Proposed Bid</small>
                            <strong className="proposed-val">{formatPrice(m.proposal.proposedAmount)}</strong>
                          </div>
                        </div>

                        {m.proposal.statedBudget && (
                          <div className="proposal-budget-comparison">
                            Your Stated Budget: <strong>{formatPrice(m.proposal.statedBudget)}</strong>
                          </div>
                        )}
                      </div>

                      {m.proposal.withinBudget ? (
                        <div className="proposal-actions">
                          <p className="proposal-note">
                            <ShieldCheck size={13} /> Requires your explicit confirmation before placement.
                          </p>
                          <button
                            className="button button-primary proposal-confirm-btn"
                            type="button"
                            disabled={bidInProgress}
                            onClick={() => handleConfirmBid(m.proposal)}
                          >
                            {bidInProgress ? 'Placing Bid…' : `Confirm & Place Bid (${formatPrice(m.proposal.proposedAmount)})`}
                          </button>
                        </div>
                      ) : (
                        <p className="budget-alert-text">
                          This bid exceeds your budget limit. Please increase your stated budget above if you wish to bid on this item.
                        </p>
                      )}
                    </div>
                  )}
                </div>
              </div>
            ))}

            {loading && (
              <div className="chat-bubble-wrap is-assistant">
                <span className="assistant-avatar">
                  <Sparkles size={14} />
                </span>
                <div className="chat-bubble typing-bubble">
                  <span className="typing-dot" />
                  <span className="typing-dot" />
                  <span className="typing-dot" />
                </div>
              </div>
            )}

            {bidFeedback && (
              <div className={`assistant-toast ${bidFeedback.includes('Success') ? 'is-success' : 'is-error'}`}>
                {bidFeedback}
              </div>
            )}

            <div ref={chatBottomRef} />
          </div>

          <div className="quick-prompts-tray">
            <span className="prompts-label">Try asking:</span>
            <div className="prompts-scroll">
              {QUICK_PROMPTS.map((prompt, i) => (
                <button
                  key={i}
                  type="button"
                  className="quick-prompt-chip"
                  onClick={() => handleSend(prompt)}
                >
                  {prompt}
                </button>
              ))}
            </div>
          </div>

          <form
            className="assistant-input-form"
            onSubmit={(e) => {
              e.preventDefault()
              handleSend()
            }}
          >
            <input
              type="text"
              placeholder="Ask about listings, budget, or bidding rules…"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              disabled={loading}
            />
            <button
              className="assistant-send-btn"
              type="submit"
              disabled={!input.trim() || loading}
              aria-label="Send message"
            >
              <Send size={16} />
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
