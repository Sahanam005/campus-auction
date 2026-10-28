import { useCallback, useDeferredValue, useEffect, useRef, useState } from 'react'
import {
  ArrowDownWideNarrow, ArrowLeft, ArrowRight, Bike, BookOpen, Calculator,
  Check, ChevronDown, Clock3, Laptop, LogOut, Menu, PackageOpen, Search,
  ShieldCheck, Sparkles, X, Zap,
} from 'lucide-react'
import {
  BrowserRouter, Link, NavLink, Route, Routes, useNavigate, useParams,
  useSearchParams,
} from 'react-router-dom'
import AuctionCard from './AuctionCard.jsx'
import AuctionImage from './AuctionImage.jsx'
import Countdown from './Countdown.jsx'
import { formatPrice } from './formatters.js'
import useClock from './useClock.js'
import {
  apiErrorMessage, createAuction, getAuction, getAuctions, getBids,
  getCurrentUser, getMyAuctions, loginAccount, logoutAccount, placeBid,
  registerAccount,
} from './api.js'
import './App.css'

const categories = ['Cycles', 'Books', 'Calculators', 'Electronics', 'Other']
const conditions = ['Like New', 'Excellent', 'Good', 'Fair']
const categoryIcons = { Cycles: Bike, Books: BookOpen, Calculators: Calculator, Electronics: Laptop, Other: PackageOpen }
const matchesCategory = (auctionCategory, selectedCategory) => auctionCategory.trim().toLowerCase().replace(/s$/, '') === selectedCategory.trim().toLowerCase().replace(/s$/, '')

function useAuctionFeed() {
  const [auctions, setAuctions] = useState([])
  const [bidsByAuction, setBidsByAuction] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    let active = true
    let pending = false
    const load = async () => {
      if (pending) return
      pending = true
      try {
        const items = await getAuctions()
        const entries = await Promise.all(items.map(async (item) => [item.id, await getBids(item.id)]))
        if (active) {
          setAuctions(items)
          setBidsByAuction(Object.fromEntries(entries))
          setError('')
        }
      } catch (requestError) {
        if (active) setError(apiErrorMessage(requestError))
      } finally {
        pending = false
        if (active) setLoading(false)
      }
    }
    load()
    const timer = window.setInterval(load, 30000)
    return () => { active = false; window.clearInterval(timer) }
  }, [revision])

  return { auctions, bidsByAuction, loading, error, refresh: () => setRevision((value) => value + 1) }
}

function Header({ user, onLogout }) {
  const [open, setOpen] = useState(false)
  const close = () => setOpen(false)
  return (
    <header className="site-header"><div className="header-inner">
      <Link className="brand" to="/" onClick={close} aria-label="CampusAuction home"><span className="brand-mark"><span /></span><span className="brand-name">campus<span>auction</span></span></Link>
      <button className="icon-button menu-toggle" type="button" aria-label={open ? 'Close navigation' : 'Open navigation'} aria-expanded={open} onClick={() => setOpen(!open)}>{open ? <X size={20} /> : <Menu size={20} />}</button>
      <nav className={`main-nav${open ? ' is-open' : ''}`} aria-label="Main navigation">
        <NavLink to="/marketplace" onClick={close}>Explore</NavLink><a href="/#how-it-works" onClick={close}>How it works</a><NavLink to="/my-auctions" onClick={close}>My auctions</NavLink>
        {user ? <><span className="nav-account">{user.displayName}</span><button className="nav-account-action" type="button" onClick={() => { close(); onLogout() }}><LogOut size={14} /> Sign out</button></> : <Link className="nav-account-action" to="/account" onClick={close}>Sign in</Link>}
        <Link className="nav-sell" to="/sell" onClick={close}>Sell an item <ArrowRight size={16} /></Link>
      </nav>
    </div></header>
  )
}

function Footer() {
  return <footer className="page-footer"><Link className="brand footer-brand" to="/"><span className="brand-mark"><span /></span><span className="brand-name">campus<span>auction</span></span></Link><p>Good finds. Better campus stories.</p><span className="footer-note">A marketplace made for campus life</span></footer>
}

function LoadingGrid() {
  return <div className="auction-grid" aria-label="Loading auctions">{Array.from({ length: 3 }, (_, index) => <div className="auction-skeleton" key={index} />)}</div>
}

function EmptyState({ title, text, action = true }) {
  return <div className="state-panel state-empty"><div className="state-icon"><PackageOpen size={20} /></div><div><h3>{title}</h3><p>{text}</p></div>{action && <Link className="text-link" to="/sell">List an item <ArrowRight size={15} /></Link>}</div>
}

function FeedError({ error, retry }) {
  return <div className="state-panel state-error" role="alert"><div className="state-icon"><Zap size={19} /></div><div><h3>Couldn’t load the marketplace</h3><p>{error}</p></div><button className="button button-secondary" type="button" onClick={retry}>Try again</button></div>
}

function AccountPage({ user, onAuthenticated }) {
  const navigate = useNavigate()
  const [registering, setRegistering] = useState(false)
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event) => {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      if (registering) await registerAccount({ email, displayName, password })
      else await loginAccount({ email, password })
      await onAuthenticated()
      navigate('/my-auctions')
    } catch (requestError) {
      setError(apiErrorMessage(requestError))
    } finally {
      setSubmitting(false)
    }
  }

  if (user) return <main className="page-main page-width"><div className="state-panel"><div className="state-icon"><Check size={19} /></div><div><h3>You’re signed in as {user.displayName}</h3><p>{user.email}</p></div><Link className="button button-secondary" to="/my-auctions">My auctions</Link></div></main>

  return <main className="page-main page-width auth-page"><div className="form-heading"><span className="eyebrow eyebrow-muted">YOUR CAMPUS EXCHANGE</span><h1>{registering ? 'Join the ' : 'Welcome '}<span>{registering ? 'community.' : 'exchange.'}</span></h1><p>{registering ? 'Create an account to list items and place bids.' : 'Sign in to manage your listings and take part in auctions.'}</p></div>
    <form className="auth-form" onSubmit={submit}>
      {registering && <label>Display name<input autoComplete="name" required minLength="2" maxLength="100" value={displayName} onChange={(event) => setDisplayName(event.target.value)} /></label>}
      <label>Email<input type="email" autoComplete="email" required maxLength="254" value={email} onChange={(event) => setEmail(event.target.value)} /></label>
      <label>Password<input type="password" autoComplete={registering ? 'new-password' : 'current-password'} required minLength={registering ? 12 : 1} maxLength="72" value={password} onChange={(event) => setPassword(event.target.value)} />{registering && <span className="auth-hint">Use at least 12 characters.</span>}</label>
      {error && <p className="form-message" role="alert">{error}</p>}
      <button className="button button-primary auth-submit" type="submit" disabled={submitting}>{submitting ? 'Please wait…' : registering ? 'Create account' : 'Sign in'} <ArrowRight size={16} /></button>
      <p className="auth-switch">{registering ? 'Already have an account?' : 'New to CampusAuction?'} <button type="button" onClick={() => { setRegistering(!registering); setError('') }}>{registering ? 'Sign in' : 'Create an account'}</button></p>
    </form>
  </main>
}

function AuctionGrid({ auctions, bids, loading, error, retry, emptyTitle, emptyText }) {
  if (loading) return <LoadingGrid />
  if (error) return <FeedError error={error} retry={retry} />
  if (!auctions.length) return <EmptyState title={emptyTitle || 'Nothing in this corner yet'} text={emptyText || 'New campus finds will show up here.'} />
  return <div className="auction-grid">{auctions.map((auction, index) => <AuctionCard key={auction.id} auction={auction} bids={bids[auction.id] || []} index={index} />)}</div>
}

function HomePage({ feed }) {
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const active = feed.auctions.filter((auction) => auction.status === 'ACTIVE')
  const closingSoon = [...active].sort((a, b) => new Date(a.closingTime) - new Date(b.closingTime)).slice(0, 3)
  const submit = (event) => { event.preventDefault(); navigate(`/marketplace?q=${encodeURIComponent(search.trim())}`) }

  return <main>
    <section className="hero-section">
      <video className="hero-video" autoPlay={!prefersReducedMotion} muted loop playsInline preload="metadata" poster="https://images.pexels.com/videos/13638410/pexels-photo-13638410.jpeg" aria-hidden="true" tabIndex="-1">
        <source src="https://videos.pexels.com/video-files/13638410/13638410-uhd_2560_1440_60fps.mp4" type="video/mp4" />
      </video>
      <div className="hero-scrim" /><div className="hero-light hero-light-one" /><div className="hero-light hero-light-two" />
      <div className="hero-content page-width">
        <div className="hero-copy"><div className="eyebrow"><span className="live-dot" /> THE CAMPUS EXCHANGE</div><h1>Pass it on.<br /><span>Bid it up.</span></h1><p className="hero-description">The good stuff between semesters. Find your next favorite thing, right here on campus.</p>
          <form className="hero-search" onSubmit={submit} role="search"><Search size={19} aria-hidden="true" /><label className="visually-hidden" htmlFor="hero-search">Search campus auctions</label><input id="hero-search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Try “a calculator for class”" /><button className="search-submit" type="submit" aria-label="Search auctions"><ArrowRight size={19} /></button></form>
          <div className="hero-actions"><Link className="button button-primary" to="/marketplace">Browse auctions <ArrowRight size={16} /></Link><Link className="hero-secondary-link" to="/sell">Have something to pass on? <span>Sell an item</span></Link></div>
        </div>
        <div className="hero-side-note"><span className="hero-side-line" /><p>One campus.<br />A thousand second chapters.</p></div>
        <div className="hero-bottomline"><span><span className="live-dot" /> {feed.loading ? 'Checking live listings' : `${active.length} live auction${active.length === 1 ? '' : 's'}`}</span><a href="#featured">Scroll to discover <ChevronDown size={15} /></a></div>
      </div>
    </section>

    <section className="category-strip page-width" aria-label="Browse categories"><div className="category-intro"><span className="eyebrow eyebrow-muted">GOOD FINDS, BY CATEGORY</span><h2>What are you after?</h2></div><div className="category-links">{categories.map((category) => { const Icon = categoryIcons[category]; return <Link className="category-link" to={`/marketplace?category=${encodeURIComponent(category)}`} key={category}><span className="category-icon"><Icon size={19} strokeWidth={1.7} /></span><span>{category}</span><ArrowRight className="category-arrow" size={14} /></Link> })}</div></section>

    <section className="section-block page-width" id="featured"><div className="section-heading"><div><span className="eyebrow eyebrow-muted">STRAIGHT FROM YOUR CAMPUS</span><h2>On the block <span className="accent-dot">.</span></h2></div><Link className="text-link" to="/marketplace">See all auctions <ArrowRight size={16} /></Link></div>
      <AuctionGrid auctions={active.slice(0, 4)} bids={feed.bidsByAuction} loading={feed.loading} error={feed.error} retry={feed.refresh} emptyTitle="The first bid is yours to start" emptyText="There aren’t any active listings just yet. Check back soon or list something of your own." />
    </section>

    <section className="closing-band"><div className="page-width closing-inner"><div><span className="eyebrow"><Clock3 size={14} /> LAST CALL</span><h2>Almost gone.<br /><span>Almost yours.</span></h2></div>{closingSoon.length ? <div className="closing-list">{closingSoon.map((auction) => <Link className="closing-item" to={`/auctions/${auction.id}`} key={auction.id}><span className="closing-item-title">{auction.title}</span><span className="closing-item-price">{formatPrice(feed.bidsByAuction[auction.id]?.[0]?.amount || auction.startingPrice)}</span><Countdown closingTime={auction.closingTime} status={auction.status} compact /><ArrowRight size={17} /></Link>)}</div> : <p className="closing-empty">The next last call is still a little way off. Browse everything live now.</p>}</div></section>

    <section className="how-section page-width" id="how-it-works"><div className="how-heading"><span className="eyebrow eyebrow-muted">A BETTER WAY TO PASS IT ON</span><h2>Good deals.<br /><span>Good neighbors.</span></h2><p>Keep useful things in circulation, and keep the handover close to home.</p></div><div className="how-steps"><article className="how-step"><span className="step-number">01</span><div className="step-icon"><Search size={19} /></div><h3>Find your next thing</h3><p>Browse real listings from people around your campus.</p></article><article className="how-step"><span className="step-number">02</span><div className="step-icon"><ArrowDownWideNarrow size={19} /></div><h3>Make a fair bid</h3><p>Every auction shows its current price and minimum next bid.</p></article><article className="how-step"><span className="step-number">03</span><div className="step-icon"><ShieldCheck size={19} /></div><h3>Meet. Swap. Repeat.</h3><p>Arrange a simple campus handover with your seller.</p></article></div></section>

    <section className="assistant-note page-width"><span className="assistant-icon"><Sparkles size={18} /></span><div><strong>Looking for something specific?</strong><p>AI-powered search is planned but isn’t connected yet. For now, try marketplace search and filters.</p></div><Link className="text-link" to="/marketplace">Search items <ArrowRight size={15} /></Link></section>
  </main>
}

function MarketplacePage({ feed }) {
  const [params, setParams] = useSearchParams()
  const [query, setQuery] = useState(params.get('q') || '')
  const [category, setCategory] = useState(params.get('category') || 'All categories')
  const [condition, setCondition] = useState('All conditions')
  const [minPrice, setMinPrice] = useState('')
  const [maxPrice, setMaxPrice] = useState('')
  const [closingSoon, setClosingSoon] = useState(false)
  const [sort, setSort] = useState('ending')
  const deferredQuery = useDeferredValue(query)
  const now = useClock()

  const changeCategory = (value) => {
    setCategory(value)
    const next = new URLSearchParams(params)
    if (value === 'All categories') next.delete('category')
    else next.set('category', value)
    setParams(next, { replace: true })
  }
  const reset = () => { setQuery(''); changeCategory('All categories'); setCondition('All conditions'); setMinPrice(''); setMaxPrice(''); setClosingSoon(false) }
  const priceFor = (auction) => Number(feed.bidsByAuction[auction.id]?.[0]?.amount || auction.startingPrice)
  const filtered = feed.auctions.filter((auction) => {
    const currentPrice = priceFor(auction)
    const text = `${auction.title} ${auction.description} ${auction.category}`.toLowerCase()
    const remaining = new Date(auction.closingTime).getTime() - now
    return (!deferredQuery.trim() || text.includes(deferredQuery.trim().toLowerCase()))
      && (category === 'All categories' || matchesCategory(auction.category, category))
      && (condition === 'All conditions' || auction.conditionLabel.toLowerCase() === condition.toLowerCase())
      && (!minPrice || currentPrice >= Number(minPrice)) && (!maxPrice || currentPrice <= Number(maxPrice))
      && (!closingSoon || (auction.status === 'ACTIVE' && remaining > 0 && remaining <= 86400000))
  }).sort((a, b) => sort === 'ending' ? new Date(a.closingTime) - new Date(b.closingTime)
    : sort === 'price-low' ? priceFor(a) - priceFor(b)
      : sort === 'price-high' ? priceFor(b) - priceFor(a) : b.id - a.id)

  return <main className="page-main page-width"><div className="market-heading"><div><span className="eyebrow eyebrow-muted">THE CAMPUS EXCHANGE</span><h1>Find your <span>next thing.</span></h1><p>Good stuff, going to good homes nearby.</p></div><Link className="button button-primary" to="/sell">List an item <ArrowRight size={16} /></Link></div>
    <form className="market-search" role="search" onSubmit={(event) => event.preventDefault()}><Search size={19} aria-hidden="true" /><label className="visually-hidden" htmlFor="market-search">Search listings</label><input id="market-search" placeholder="Search by item or description" value={query} onChange={(event) => setQuery(event.target.value)} />{query && <button className="clear-search" type="button" onClick={() => setQuery('')} aria-label="Clear search"><X size={16} /></button>}</form>
    <div className="market-layout"><aside className="filter-panel" aria-label="Auction filters"><div className="filter-title"><span>Refine your search</span><button type="button" onClick={reset}>Reset</button></div>
      <label className="filter-label" htmlFor="category-filter">Category</label><select id="category-filter" value={category} onChange={(event) => changeCategory(event.target.value)}><option>All categories</option>{[...new Set([...categories, ...feed.auctions.map((item) => item.category)])].map((item) => <option key={item}>{item}</option>)}</select>
      <label className="filter-label" htmlFor="condition-filter">Condition</label><select id="condition-filter" value={condition} onChange={(event) => setCondition(event.target.value)}><option>All conditions</option>{[...new Set([...conditions, ...feed.auctions.map((item) => item.conditionLabel)])].map((item) => <option key={item}>{item}</option>)}</select>
      <span className="filter-label">Current price · ₹</span><div className="price-range"><label><span className="visually-hidden">Minimum price</span><input type="number" min="0" inputMode="decimal" placeholder="Min" value={minPrice} onChange={(event) => setMinPrice(event.target.value)} /></label><span>to</span><label><span className="visually-hidden">Maximum price</span><input type="number" min="0" inputMode="decimal" placeholder="Max" value={maxPrice} onChange={(event) => setMaxPrice(event.target.value)} /></label></div>
      <label className="toggle-row"><span><Clock3 size={16} /> Closing in 24 hours</span><input type="checkbox" checked={closingSoon} onChange={(event) => setClosingSoon(event.target.checked)} /></label><div className="filter-footnote">Prices update from public bid history.</div>
    </aside><section className="market-results" aria-live="polite"><div className="results-toolbar"><p><strong>{feed.loading ? '—' : filtered.length}</strong> {filtered.length === 1 ? 'auction' : 'auctions'} <span>on your campus</span></p><label className="sort-control"><ArrowDownWideNarrow size={16} /><span className="visually-hidden">Sort auctions</span><select value={sort} onChange={(event) => setSort(event.target.value)}><option value="ending">Ending soon</option><option value="recent">Recently listed</option><option value="price-low">Price: low to high</option><option value="price-high">Price: high to low</option></select></label></div>
      <AuctionGrid auctions={filtered} bids={feed.bidsByAuction} loading={feed.loading} error={feed.error} retry={feed.refresh} emptyTitle="No matches this time" emptyText="Try a broader search or clear a filter to see more listings." />
    </section></div></main>
}

function AuctionDetailPage({ feed, user }) {
  const { id } = useParams()
  const navigate = useNavigate()
  const [auction, setAuction] = useState(null)
  const [bids, setBids] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [amount, setAmount] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState('')

  const refresh = useCallback(async () => {
    try { const [item, history] = await Promise.all([getAuction(id), getBids(id)]); setAuction(item); setBids(history); setError('') }
    catch (requestError) { setError(apiErrorMessage(requestError)) }
    finally { setLoading(false) }
  }, [id])
  useEffect(() => {
    const initialRequest = window.setTimeout(refresh, 0)
    const refreshTimer = window.setInterval(refresh, 30000)
    return () => { window.clearTimeout(initialRequest); window.clearInterval(refreshTimer) }
  }, [refresh])
  const now = useClock()
  const currentBid = bids[0]?.amount || auction?.startingPrice || 0
  const nextBid = bids.length ? Number(bids[0].amount) + Number(auction?.minIncrement || 0) : Number(auction?.startingPrice || 0)
  const active = auction?.status === 'ACTIVE' && new Date(auction.closingTime).getTime() > now
  const submitBid = async (event) => {
    event.preventDefault(); setMessage('')
    if (Number(amount) < nextBid) { setMessage(`The next valid bid is ${formatPrice(nextBid)} or more.`); return }
    setSubmitting(true)
    try { await placeBid(id, Number(amount)); setMessage('Your bid is in. Good luck!'); setAmount(''); await refresh(); feed.refresh() }
    catch (requestError) { setMessage(apiErrorMessage(requestError)) }
    finally { setSubmitting(false) }
  }

  if (loading || (auction && String(auction.id) !== id)) return <main className="page-main page-width"><div className="detail-skeleton" /></main>
  if (error || !auction) return <main className="page-main page-width"><div className="state-panel state-error"><div className="state-icon"><Zap size={19} /></div><div><h3>Listing unavailable</h3><p>{error || 'This auction could not be found.'}</p></div><Link className="button button-secondary" to="/marketplace">Back to auctions</Link></div></main>

  return <main className="page-main page-width detail-page"><button className="back-link" type="button" onClick={() => navigate(-1)}><ArrowLeft size={16} /> Back to the exchange</button><div className="detail-layout">
    <div className="detail-image-wrap"><AuctionImage className="detail-image" src={auction.imageUrl} alt={auction.title} loading="eager" /><span className={`status-pill status-${auction.status.toLowerCase()}`}>{auction.status.replaceAll('_', ' ')}</span></div>
    <div className="detail-copy"><div className="eyebrow eyebrow-muted">{auction.category} <span className="eyebrow-separator">/</span> {auction.conditionLabel}</div><h1>{auction.title}</h1><p className="detail-description">{auction.description}</p><div className="seller-line"><span className="seller-avatar">{auction.sellerName?.slice(0, 1).toUpperCase()}</span><span>Listed by <strong>{auction.sellerName}</strong></span></div>
      <section className="bid-panel" aria-label="Place a bid"><div className="bid-panel-top"><span>{bids.length ? 'CURRENT LEADING BID' : 'STARTING PRICE'}</span><span>{bids.length} {bids.length === 1 ? 'bid' : 'bids'}</span></div><div className="bid-price">{formatPrice(currentBid)}</div><div className="next-bid-line">Next valid bid <strong>{formatPrice(nextBid)}</strong></div><div className="detail-countdown"><Clock3 size={16} /><span>{active ? 'Time left' : 'Auction status'}</span><Countdown closingTime={auction.closingTime} status={active ? auction.status : 'ENDED'} /></div>
        {active && user ? <form className="bid-form" onSubmit={submitBid}><p className="authenticated-bidder">Bidding as <strong>{user.displayName}</strong></p><label htmlFor="bid-amount">Your bid <span>Minimum {formatPrice(nextBid)}</span></label><div className="bid-input-wrap"><span>₹</span><input id="bid-amount" type="number" inputMode="decimal" min={nextBid} step="0.01" required value={amount} onChange={(event) => setAmount(event.target.value)} placeholder={String(nextBid)} /></div>{message && <p className={`form-message${message.includes('in.') ? ' is-success' : ''}`} role="status">{message}</p>}<button className="button button-primary bid-submit" type="submit" disabled={submitting}>{submitting ? 'Placing bid…' : 'Place bid'} <ArrowRight size={16} /></button><p className="bid-footnote"><ShieldCheck size={14} /> Your bid is public and binding.</p></form> : active ? <div className="sign-in-prompt"><p>Sign in to place a bid. Your account name will be shown in public bid history.</p><Link className="button button-primary" to="/account">Sign in to bid <ArrowRight size={16} /></Link></div> : <p className="ended-note">Bidding has ended for this listing.</p>}
      </section>
    </div></div>
    <section className="bid-history-section"><div className="section-heading"><div><span className="eyebrow eyebrow-muted">OPEN TO EVERYONE</span><h2>Bid history</h2></div><span className="history-count">{bids.length} {bids.length === 1 ? 'bid' : 'bids'}</span></div>{bids.length ? <div className="bid-history-list">{bids.map((bid) => <article className="bid-history-row" key={bid.id}><span className="bid-history-avatar">{bid.bidderName?.slice(0, 1).toUpperCase()}</span><strong>{bid.bidderName}</strong><time dateTime={bid.createdAt}>{new Date(bid.createdAt).toLocaleString()}</time><b>{formatPrice(bid.amount)}</b></article>)}</div> : <EmptyState title="Make the first move" text={`No bids yet. The opening bid starts at ${formatPrice(auction.startingPrice)}.`} action={false} />}</section>
  </main>
}

function SellPage({ feed, user }) {
  const navigate = useNavigate()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [imageFile, setImageFile] = useState(null)
  const [imagePreview, setImagePreview] = useState('')
  const imagePreviewRef = useRef('')
  const now = useClock()
  const [form, setForm] = useState({ title: '', category: 'Electronics', conditionLabel: 'Good', description: '', startingPrice: '', minIncrement: '25', closingTime: '' })
  const update = (key) => (event) => setForm((current) => ({ ...current, [key]: event.target.value }))
  useEffect(() => {
    return () => {
      if (imagePreviewRef.current) URL.revokeObjectURL(imagePreviewRef.current)
    }
  }, [])
  const selectImage = (event) => {
    const selectedFile = event.target.files?.[0] || null
    if (imagePreviewRef.current) URL.revokeObjectURL(imagePreviewRef.current)
    imagePreviewRef.current = selectedFile ? URL.createObjectURL(selectedFile) : ''
    setImagePreview(imagePreviewRef.current)
    setImageFile(selectedFile)
  }
  const submit = async (event) => {
    event.preventDefault(); setError(''); setSubmitting(true)
    try {
      if (imageFile && imageFile.size > 5 * 1024 * 1024) throw new Error('Choose an image smaller than 5 MB.')
      const created = await createAuction({ ...form, startingPrice: Number(form.startingPrice), minIncrement: Number(form.minIncrement), closingTime: form.closingTime.length === 16 ? `${form.closingTime}:00` : form.closingTime }, imageFile)
      feed.refresh(); navigate(`/auctions/${created.id}`)
    } catch (requestError) { setError(apiErrorMessage(requestError)) }
    finally { setSubmitting(false) }
  }

  if (!user) return <main className="page-main page-width"><div className="state-panel"><div className="state-icon"><ShieldCheck size={19} /></div><div><h3>Sign in to list an item</h3><p>Your seller identity is securely linked to your account.</p></div><Link className="button button-primary" to="/account">Sign in or create an account <ArrowRight size={16} /></Link></div></main>

  return <main className="page-main page-width form-page"><Link className="back-link" to="/marketplace"><ArrowLeft size={16} /> Back to the exchange</Link><div className="form-heading"><span className="eyebrow eyebrow-muted">GIVE A GOOD THING A SECOND CHAPTER</span><h1>List your <span>find.</span></h1><p>Set a fair start, choose when the auction closes, and let campus take it from here.</p></div>
    <form className="listing-form" onSubmit={submit}><div className="form-section-title"><span>01</span><div><h2>The item</h2><p>A clear title and honest details help the right person find it.</p></div></div><div className="form-grid">
      <label className="field-full">Item title<input required maxLength="150" value={form.title} onChange={update('title')} placeholder="e.g. Scientific calculator, barely used" /></label>
      <label>Category<select value={form.category} onChange={update('category')}>{categories.map((item) => <option key={item}>{item}</option>)}</select></label><label>Condition<select value={form.conditionLabel} onChange={update('conditionLabel')}>{conditions.map((item) => <option key={item}>{item}</option>)}</select></label>
      <label className="field-full">Description<textarea required maxLength="2000" rows="4" value={form.description} onChange={update('description')} placeholder="What should a new owner know? Mention wear, included parts, or useful details." /></label>
      <label className="field-full">Item photo <span className="field-optional">Optional · JPEG or PNG, up to 5 MB</span><input type="file" accept="image/jpeg,image/png" onChange={selectImage} /></label>
      {imagePreview && <div className="image-preview"><AuctionImage className="image-preview-thumbnail" src={imagePreview} alt="Listing photo preview" loading="eager" /><span>{imageFile?.name}</span></div>}
    </div><div className="form-section-title"><span>02</span><div><h2>The auction</h2><p>Choose your opening price and the minimum step between bids.</p></div></div><div className="form-grid">
      <label>Starting price<input required type="number" min="0.01" step="0.01" inputMode="decimal" value={form.startingPrice} onChange={update('startingPrice')} placeholder="₹ 250" /></label><label>Minimum increment<input required type="number" min="0.01" step="0.01" inputMode="decimal" value={form.minIncrement} onChange={update('minIncrement')} /></label>
      <label>Closing date & time<input required type="datetime-local" min={now ? new Date(now + 60000).toISOString().slice(0, 16) : undefined} value={form.closingTime} onChange={update('closingTime')} /></label><div className="account-seller-field"><span>Seller identity</span><strong>{user.displayName}</strong><small>Linked to your signed-in account</small></div>
    </div>{error && <p className="form-message" role="alert">{error}</p>}<div className="form-actions"><p><ShieldCheck size={15} /> Your listing is public to campus.</p><button className="button button-primary" type="submit" disabled={submitting}>{submitting ? 'Publishing…' : 'Publish listing'} <ArrowRight size={16} /></button></div></form>
  </main>
}

function MyAuctionsPage({ feed, user }) {
  const [result, setResult] = useState(null)
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    let active = true
    if (!user) return () => { active = false }
    getMyAuctions()
      .then((items) => { if (active) setResult({ userId: user.id, items, error: '' }) })
      .catch((requestError) => { if (active) setResult({ userId: user.id, items: [], error: apiErrorMessage(requestError) }) })
    return () => { active = false }
  }, [user, revision])

  if (!user) return <main className="page-main page-width"><div className="state-panel"><div className="state-icon"><ShieldCheck size={19} /></div><div><h3>Sign in to see your auctions</h3><p>Your dashboard is private to your account.</p></div><Link className="button button-primary" to="/account">Sign in <ArrowRight size={16} /></Link></div></main>

  const responseIsCurrent = result?.userId === user.id
  return <main className="page-main page-width"><div className="market-heading"><div><span className="eyebrow eyebrow-muted">YOUR CORNER OF THE EXCHANGE</span><h1>My <span>auctions.</span></h1><p>Listings owned by {user.displayName}.</p></div><Link className="button button-primary" to="/sell">List an item <ArrowRight size={16} /></Link></div>
    <AuctionGrid auctions={responseIsCurrent ? result.items : []} bids={feed.bidsByAuction} loading={!responseIsCurrent} error={responseIsCurrent ? result.error : ''} retry={() => setRevision((value) => value + 1)} emptyTitle="You haven’t listed anything yet" emptyText="Your items will appear here after you publish a listing." />
    <div className="capability-note"><span className="step-icon"><Check size={17} /></span><p>Seller identity and listing access are account-based. Editing and cancellation will be added in the next milestone.</p></div>
  </main>
}

function NotFoundPage() { return <main className="page-main page-width not-found"><span className="eyebrow eyebrow-muted">404 · OFF THE MAP</span><h1>That listing wandered off.</h1><Link className="button button-primary" to="/marketplace">Back to auctions <ArrowRight size={16} /></Link></main> }

function AppLayout({ feed, user, authLoading, refreshUser }) {
  const navigate = useNavigate()
  const [logoutError, setLogoutError] = useState('')
  const signOut = async () => {
    setLogoutError('')
    try {
      await logoutAccount()
      await refreshUser()
      navigate('/')
    } catch (requestError) {
      setLogoutError(apiErrorMessage(requestError))
    }
  }
  return <div className="app-shell"><Header user={user} onLogout={signOut} />{logoutError && <div className="auth-alert" role="alert">{logoutError}</div>}<Routes><Route path="/" element={<HomePage feed={feed} />} /><Route path="/marketplace" element={<MarketplacePage feed={feed} />} /><Route path="/auctions/:id" element={<AuctionDetailPage feed={feed} user={user} />} /><Route path="/account" element={<AccountPage user={user} onAuthenticated={refreshUser} />} /><Route path="/sell" element={authLoading ? <main className="page-main page-width"><div className="detail-skeleton" /></main> : <SellPage feed={feed} user={user} />} /><Route path="/my-auctions" element={authLoading ? <main className="page-main page-width"><div className="detail-skeleton" /></main> : <MyAuctionsPage feed={feed} user={user} />} /><Route path="*" element={<NotFoundPage />} /></Routes><Footer /></div>
}

function App() {
  const feed = useAuctionFeed()
  const [user, setUser] = useState(null)
  const [authLoading, setAuthLoading] = useState(true)
  const [authError, setAuthError] = useState('')
  const refreshUser = useCallback(async () => {
    const currentUser = await getCurrentUser()
    setUser(currentUser)
    return currentUser
  }, [])
  useEffect(() => {
    let active = true
    getCurrentUser()
      .then((currentUser) => { if (active) { setUser(currentUser); setAuthError('') } })
      .catch((requestError) => { if (active) setAuthError(apiErrorMessage(requestError)) })
      .finally(() => { if (active) setAuthLoading(false) })
    return () => { active = false }
  }, [])
  return <BrowserRouter><AppLayout feed={feed} user={user} authLoading={authLoading} refreshUser={refreshUser} />{authError && <div className="auth-alert" role="alert">{authError}</div>}</BrowserRouter>
}

export default App
