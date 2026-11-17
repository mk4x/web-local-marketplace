import { useQuery } from '@tanstack/react-query'
import { searchListings } from '../api/listings'
import ListingCard from '../components/ListingCard'
import { useState } from 'react'

type Props = { search: string }

const inputClass = 'rounded border border-gray-300 px-3 py-2'

export default function BrowsePage({ search }: Props) {
  const [filters, setFilters] = useState({
    kind: '',
    category: '',
    maxPrice: '',
  })

  const { data, isPending, error } = useQuery({
    queryKey: ['listings', search, filters],
    queryFn: () => searchListings(search, filters),
  })

  return (
    <div>
      <h1 className="mb-4 text-2xl font-bold">Browse</h1>
      <div className="flex gap-6">
        <div id="filter-section" className="flex w-1/4 flex-col gap-3">
          <select
            className={inputClass}
            value={filters.kind}
            onChange={(event) =>
              setFilters({ ...filters, kind: event.target.value })
            }
          >
            <option value="">Any type</option>
            <option value="sale">For sale</option>
            <option value="free">Free</option>
            <option value="wanted">Wanted</option>
            <option value="trade">Trade</option>
          </select>
          <select
            className={inputClass}
            value={filters.category}
            onChange={(event) =>
              setFilters({ ...filters, category: event.target.value })
            }
          >
            <option value="">Any category</option>
            <option value="home">Home</option>
            <option value="furniture">Furniture</option>
            <option value="electronics">Electronics</option>
            <option value="clothing">Clothing</option>
            <option value="books">Books</option>
            <option value="vehicles">Vehicles</option>
            <option value="vehicles.bikes">Bikes</option>
          </select>
          <input
            type="number"
            placeholder="Max price"
            className={inputClass}
            value={filters.maxPrice}
            onChange={(event) =>
              setFilters({ ...filters, maxPrice: event.target.value })
            }
          />
        </div>
        <div id="items-section" className="flex-1">
          {isPending && <p>Loading...</p>}
          {error && <p>{error.message}</p>}
          {data && data.length === 0 && <p>No listings found.</p>}
          {data && (
            <div className="grid grid-cols-3 gap-4">
              {data.map((listing) => (
                <ListingCard
                  key={listing.id}
                  id={listing.id}
                  title={listing.title}
                  price={listing.price}
                  distanceM={listing.distanceM}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
