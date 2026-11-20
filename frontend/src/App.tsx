import { useState } from 'react'
import { Route, Routes } from 'react-router'
import Header from './components/Header'
import BrowsePage from './pages/BrowsePage'
import CreateListingPage from './pages/CreateListingPage'
import ListingPage from './pages/ListingPage'
import NotFoundPage from './pages/NotFoundPage'
import ProfilePage from './pages/ProfilePage'

function App() {
  // lives here so header can set it and browse page can read it
  const [search, setSearch] = useState('')

  return (
    <>
      <Header onSearch={setSearch} />
      <main className="p-6">
        <Routes>
          <Route path="/" element={<BrowsePage search={search} />} />
          <Route path="/listings/:id" element={<ListingPage />} />
          <Route path="/sell" element={<CreateListingPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
    </>
  )
}

export default App
