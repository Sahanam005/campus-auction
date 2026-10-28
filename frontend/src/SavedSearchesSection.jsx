import { useEffect, useState } from 'react'
import { Bookmark, Search, X } from 'lucide-react'
import { createSavedSearch, deleteSavedSearch, getSavedSearches } from './api.js'

export default function SavedSearchesSection({ currentUser, currentFilter, onApplySearch }) {
  const [savedSearches, setSavedSearches] = useState([])
  const [saving, setSaving] = useState(false)
  const [savedToast, setSavedToast] = useState(false)

  const load = async () => {
    if (!currentUser) return
    try {
      const data = await getSavedSearches()
      setSavedSearches(data)
    } catch (e) {
      console.error(e)
    }
  }

  useEffect(() => {
    load()
  }, [currentUser])

  if (!currentUser) return null

  const handleSaveCurrent = async () => {
    setSaving(true)
    try {
      const payload = {
        query: currentFilter.query?.trim() || null,
        category: currentFilter.category !== 'All categories' ? currentFilter.category : null,
        maxPrice: currentFilter.maxPrice ? Number(currentFilter.maxPrice) : null,
      }
      await createSavedSearch(payload)
      setSavedToast(true)
      setTimeout(() => setSavedToast(false), 3000)
      await load()
    } catch (e) {
      alert(e.response?.data?.message || e.message || 'Failed to save search.')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (id, e) => {
    e.stopPropagation()
    try {
      await deleteSavedSearch(id)
      setSavedSearches((prev) => prev.filter((s) => s.id !== id))
    } catch (err) {
      console.error(err)
    }
  }

  const hasFilterActive = Boolean(
    currentFilter.query?.trim() ||
    (currentFilter.category && currentFilter.category !== 'All categories') ||
    currentFilter.maxPrice
  )

  return (
    <div className="saved-searches-card">
      <div className="saved-searches-header">
        <span className="saved-search-title">
          <Bookmark size={14} className="text-gold" />
          <span>Saved Search Alerts</span>
        </span>
        {hasFilterActive && (
          <button
            className="save-current-search-btn"
            type="button"
            disabled={saving}
            onClick={handleSaveCurrent}
          >
            {saving ? 'Saving…' : savedToast ? 'Saved!' : 'Save current filter'}
          </button>
        )}
      </div>

      {savedSearches.length > 0 ? (
        <div className="saved-searches-chips">
          {savedSearches.map((item) => {
            const labelParts = []
            if (item.query) labelParts.push(`"${item.query}"`)
            if (item.category) labelParts.push(item.category)
            if (item.maxPrice) labelParts.push(`≤ ₹${item.maxPrice}`)
            const label = labelParts.join(' · ') || 'All Listings'

            return (
              <div
                key={item.id}
                className="saved-search-chip"
                onClick={() => onApplySearch?.(item)}
                title="Click to apply search filters"
              >
                <Search size={12} className="text-orange" />
                <span>{label}</span>
                <button
                  type="button"
                  className="chip-delete-btn"
                  onClick={(e) => handleDelete(item.id, e)}
                  aria-label={`Delete saved search ${label}`}
                >
                  <X size={12} />
                </button>
              </div>
            )
          })}
        </div>
      ) : (
        <p className="saved-searches-hint">
          Save your favourite searches to get notified when new campus items match.
        </p>
      )}
    </div>
  )
}
