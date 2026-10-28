import { ArrowUpRight, Clock3, PackageOpen } from 'lucide-react'
import { Link } from 'react-router-dom'
import Countdown from './Countdown.jsx'
import { formatPrice } from './formatters.js'

export default function AuctionCard({ auction, bids = [], index = 0 }) {
  const currentBid = bids[0]?.amount || auction.startingPrice
  const active = auction.status === 'ACTIVE'
  const imageUrl = /(?:placehold\.co|via\.placeholder\.com)/i.test(auction.imageUrl || '') ? '' : auction.imageUrl
  return <Link className="auction-card" to={`/auctions/${auction.id}`} style={{ '--card-index': index }}>
    <div className="auction-card-image"><div className="image-fallback"><PackageOpen size={32} strokeWidth={1.4} /></div>{imageUrl && <img src={imageUrl} alt={auction.title} loading="lazy" onError={(event) => { event.currentTarget.hidden = true }} />}
      <span className={`status-pill status-${auction.status.toLowerCase()}`}>{auction.status.replaceAll('_', ' ')}</span><span className="card-arrow"><ArrowUpRight size={16} /></span>
    </div>
    <div className="auction-card-body"><div className="card-eyebrow"><span>{auction.category}</span><span className="card-condition">{auction.conditionLabel}</span></div><h3>{auction.title}</h3>
      <div className="card-price-row"><div><small>{bids.length ? 'LEADING BID' : 'STARTING AT'}</small><strong>{formatPrice(currentBid)}</strong></div><span className="bid-count">{bids.length} {bids.length === 1 ? 'bid' : 'bids'}</span></div>
      <div className="card-countdown"><Clock3 size={14} /><Countdown closingTime={auction.closingTime} status={active ? auction.status : 'ENDED'} compact /></div>
    </div>
  </Link>
}