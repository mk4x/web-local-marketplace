import { Link, Route, Routes } from 'react-router'
import HelloPage from './pages/HelloPage'
import BrowsePage  from './pages/BrowsePage'
import NotFoundPage from './pages/NotFoundPage'
import CreateListingPage from './pages/CreateListingPage'
import ListingPage from './pages/ListingPage'
import ProfilePage from './pages/ProfilePage'

const buttonClass = 'px-3 py-3 bg-blue-300 rounded cursor-pointer'
const headerClass = 'flex items-center gap-3 border-b border-gray-300 px-6 py-3'

function App() {
  return (
    <>
      <header className={headerClass}>
        <Link to="/" className="font-bold">
          Local Marketplace
        </Link>
        <input placeholder="Search listings..." />
        <button className={buttonClass}><Link to="/sell">Sell</Link></button>
        <button className={buttonClass}><Link to="/profile">Me</Link></button>
      </header>
      <main className="p-6">
        <Routes>
          <Route path="/" element={<BrowsePage />} />
          <Route path="/listings/:id" element={<ListingPage />} />
          <Route path="/sell" element={<CreateListingPage />} />
          <Route path="*" element={<NotFoundPage />} />
          <Route path="/hello" element={<HelloPage />} />
          <Route path="/profile" element={<ProfilePage />} />
        </Routes>
      </main>
    </>
  )
}

export default App
