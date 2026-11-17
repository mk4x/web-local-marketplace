import { useQuery } from "@tanstack/react-query"
import { useParams } from "react-router"
import { searchListings } from "../api/listings"
import ListingCard from "../components/ListingCard"

export default function BrowsePage() {
  const { searchName='' } = useParams()
  const { data, isPending, error } = useQuery({
    queryKey: ['listing', searchName],
    queryFn: () => searchListings(searchName),
  })

  if (isPending) return <p>Loading...</p>
  if (error) return <p>{error.message}</p>
  if (!data) return <p>Not found</p>

  return <div>
    <h1>Browse</h1>
    <div id="filter-section">

    </div>
    <div id="items-section">
      {data.length === 0 && <p>No listings found.</p>}
      <div>
          {data.map((listing) => (                  // runs once per listing
            <ListingCard                            // what to draw for each one
              key={listing.id}                      // unique id, React needs it for lists
              id={listing.id}
              title={listing.title}
            />
          ))}
        </div>
    </div>
  </div>
}
