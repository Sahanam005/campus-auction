import { useEffect, useState } from 'react'
import { MessageSquare, Send, ShieldCheck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { answerAuctionQuestion, askAuctionQuestion, getAuctionQuestions } from './api.js'

export default function QuestionsSection({ auctionId, sellerUserId, currentUser }) {
  const [questions, setQuestions] = useState([])
  const [newQuestion, setNewQuestion] = useState('')
  const [answerDrafts, setAnswerDrafts] = useState({})
  const [submittingQ, setSubmittingQ] = useState(false)
  const [submittingA, setSubmittingA] = useState({})
  const [error, setError] = useState('')

  const loadQuestions = async () => {
    try {
      const data = await getAuctionQuestions(auctionId)
      setQuestions(data)
    } catch (e) {
      console.error(e)
    }
  }

  useEffect(() => {
    loadQuestions()
  }, [auctionId])

  const handleAsk = async (e) => {
    e.preventDefault()
    if (!newQuestion.trim()) return
    setError('')
    setSubmittingQ(true)
    try {
      await askAuctionQuestion(auctionId, newQuestion.trim())
      setNewQuestion('')
      await loadQuestions()
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to submit question.')
    } finally {
      setSubmittingQ(false)
    }
  }

  const handleAnswer = async (questionId) => {
    const text = answerDrafts[questionId]?.trim()
    if (!text) return
    setSubmittingA((prev) => ({ ...prev, [questionId]: true }))
    try {
      await answerAuctionQuestion(auctionId, questionId, text)
      setAnswerDrafts((prev) => ({ ...prev, [questionId]: '' }))
      await loadQuestions()
    } catch (err) {
      alert(err.response?.data?.message || err.message || 'Failed to submit answer.')
    } finally {
      setSubmittingA((prev) => ({ ...prev, [questionId]: false }))
    }
  }

  const isSeller = currentUser && sellerUserId && currentUser.id === sellerUserId

  return (
    <section className="qa-section" aria-label="Questions and answers">
      <div className="section-heading">
        <div>
          <span className="eyebrow eyebrow-muted">CAMPUS COMMUNITY INQUIRIES</span>
          <h2>Questions & Answers</h2>
        </div>
        <span className="history-count">
          {questions.length} {questions.length === 1 ? 'question' : 'questions'}
        </span>
      </div>

      <div className="qa-thread-list">
        {questions.length === 0 ? (
          <p className="qa-empty">No questions yet. Ask the seller about item condition, accessories, or pickup.</p>
        ) : (
          questions.map((q) => (
            <article key={q.id} className="qa-item">
              <div className="qa-question-block">
                <span className="qa-avatar">{q.questionerName?.slice(0, 1).toUpperCase()}</span>
                <div className="qa-text-wrap">
                  <div className="qa-author-row">
                    <strong>{q.questionerName}</strong>
                    <time>{new Date(q.createdAt).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' })}</time>
                  </div>
                  <p className="qa-body">{q.questionText}</p>
                </div>
              </div>

              {q.answerText ? (
                <div className="qa-answer-block">
                  <div className="qa-seller-badge">
                    <ShieldCheck size={14} /> Seller Response
                  </div>
                  <p className="qa-answer-body">{q.answerText}</p>
                  <time className="qa-answer-time">
                    {new Date(q.answeredAt).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' })}
                  </time>
                </div>
              ) : isSeller ? (
                <div className="qa-reply-box">
                  <textarea
                    rows={2}
                    placeholder="Type your reply to this student…"
                    value={answerDrafts[q.id] || ''}
                    onChange={(e) => setAnswerDrafts({ ...answerDrafts, [q.id]: e.target.value })}
                  />
                  <button
                    className="button button-primary qa-reply-submit"
                    type="button"
                    disabled={submittingA[q.id] || !answerDrafts[q.id]?.trim()}
                    onClick={() => handleAnswer(q.id)}
                  >
                    {submittingA[q.id] ? 'Posting…' : 'Post Reply'} <Send size={13} />
                  </button>
                </div>
              ) : (
                <p className="qa-pending-answer">Awaiting seller response…</p>
              )}
            </article>
          ))
        )}
      </div>

      {currentUser ? (
        !isSeller && (
          <form className="ask-question-form" onSubmit={handleAsk}>
            <h4>Ask a question about this listing</h4>
            <div className="qa-input-wrap">
              <input
                required
                maxLength={1000}
                placeholder="e.g. Does this include the original charging cable?"
                value={newQuestion}
                onChange={(e) => setNewQuestion(e.target.value)}
              />
              <button className="button button-primary" type="submit" disabled={submittingQ || !newQuestion.trim()}>
                {submittingQ ? 'Posting…' : 'Ask Question'} <MessageSquare size={15} />
              </button>
            </div>
            {error && <p className="form-message" role="alert">{error}</p>}
          </form>
        )
      ) : (
        <div className="qa-signin-prompt">
          <p>
            <Link to="/account" className="text-link">Sign in</Link> to ask questions or discuss campus pickup details with the seller.
          </p>
        </div>
      )}
    </section>
  )
}
