import { useQuery } from '@tanstack/react-query'
import { searchListings } from '../api/listings'
import ListingCard from '../components/ListingCard'

type Props = { search: string }

export default function BrowsePage({ search }: Props) {
  const { data, isPending, error } = useQuery({
    queryKey: ['listings', search],
    queryFn: () => searchListings(search),
  })

  if (isPending) return <p>Loading...</p>
  if (error) return <p>{error.message}</p>

  return (
    <div>
      <h1>Browse</h1>
      <div id="filter-section"></div>
      <div id="items-section">
        {data.length === 0 && <p>No listings found.</p>}
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
      </div>
    </div>
  )
}
