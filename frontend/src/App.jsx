import { Link, Route, Routes } from 'react-router'
import HelloPage from './pages/HelloPage'

let buttonClass = "px-3 py-3 bg-blue-300 rounded cursor-pointer"

function App() {

  return (
    <>
      <header className="flex items-center gap-3 border-b border-gray-300 px-6 py-3">
        <Link to="/" className="font-bold">
          Local Marketplace
        </Link>
        <input
          placeholder="Search listings..."
        />
        <button className={buttonClass}>Sell</button>
        <button className={buttonClass}>Me</button>
      </header>
      <main className="p-6">
        <Routes>
          {/* <Route path="/" element={<BrowsePage />} /> */ }
          {/* <Route path="/listings/:id" element={<ListingPage />} /> */ }
          <Route path="/hello" element={<HelloPage />} />
        </Routes>
      </main>
    </>
  )
}

export default App
