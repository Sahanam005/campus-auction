import { useEffect, useState } from 'react'
import { Bell, CheckCheck, ExternalLink, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import { getNotifications, markAllNotificationsRead, markNotificationRead } from './api.js'

export default function NotificationsModal({ isOpen, onClose, onCountChange }) {
  const [notifications, setNotifications] = useState([])
  const [loading, setLoading] = useState(true)

  const load = async () => {
    try {
      const data = await getNotifications()
      setNotifications(data)
      const unread = data.filter((n) => !n.read).length
      onCountChange?.(unread)
    } catch (e) {
      console.error('Failed to load notifications', e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (isOpen) {
      load()
    }
  }, [isOpen])

  if (!isOpen) return null

  const handleRead = async (n) => {
    if (!n.read) {
      try {
        await markNotificationRead(n.id)
        setNotifications((prev) => prev.map((item) => (item.id === n.id ? { ...item, read: true } : item)))
        onCountChange?.((prev) => Math.max(0, prev - 1))
      } catch (e) {
        console.error(e)
      }
    }
    onClose()
  }

  const handleMarkAll = async () => {
    try {
      await markAllNotificationsRead()
      setNotifications((prev) => prev.map((item) => ({ ...item, read: true })))
      onCountChange?.(0)
    } catch (e) {
      console.error(e)
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose} role="dialog" aria-modal="true" aria-label="Notifications">
      <div className="modal-drawer notifications-drawer" onClick={(e) => e.stopPropagation()}>
        <div className="drawer-header">
          <div className="drawer-title">
            <Bell size={18} className="accent-icon" />
            <h3>Activity & Alerts</h3>
          </div>
          <div className="drawer-actions">
            {notifications.some((n) => !n.read) && (
              <button className="text-action-button" type="button" onClick={handleMarkAll}>
                <CheckCheck size={14} /> Mark all read
              </button>
            )}
            <button className="icon-close-btn" type="button" onClick={onClose} aria-label="Close notifications">
              <X size={18} />
            </button>
          </div>
        </div>

        <div className="drawer-body">
          {loading ? (
            <div className="drawer-skeleton-list">
              <div className="drawer-skeleton-item" />
              <div className="drawer-skeleton-item" />
              <div className="drawer-skeleton-item" />
            </div>
          ) : notifications.length === 0 ? (
            <div className="drawer-empty-state">
              <Bell size={32} opacity={0.3} />
              <p>No new notifications</p>
              <small>You will get notified about bids, outbid events, and pickup handovers here.</small>
            </div>
          ) : (
            <div className="notifications-list">
              {notifications.map((n) => (
                <div key={n.id} className={`notification-item ${n.read ? 'is-read' : 'is-unread'}`}>
                  {!n.read && <span className="unread-dot" />}
                  <div className="notification-content">
                    <div className="notification-top">
                      <strong className="notification-title">{n.title}</strong>
                      <time className="notification-time">{new Date(n.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</time>
                    </div>
                    <p className="notification-message">{n.message}</p>
                    {n.linkUrl && (
                      <Link to={n.linkUrl} className="notification-link" onClick={() => handleRead(n)}>
                        View listing <ExternalLink size={12} />
                      </Link>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
