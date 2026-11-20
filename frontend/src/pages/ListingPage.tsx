import { useParams, Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { getListing } from '../api/listings'

export default function ListingPage() {
  const { id } = useParams()

  const { data, isPending, error } = useQuery({
    queryKey: ['listing', id],
    queryFn: () => getListing(id!),
  })

  if (isPending) return <p>Loading...</p>
  if (error) return <p>{error.message}</p>
  if (!data) return <p>Listing not found</p>

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-4">
      <Link to="/" className="text-sm text-blue-600">
        Back
      </Link>

      {/* todo images*/}
      <div className="h-64 rounded bg-gray-200" />

      <h1 className="text-2xl font-bold">{data.title}</h1>
      <p className="text-xl">{data.price ? data.price + ' kr' : 'Free'}</p>

      <div className="flex gap-2 text-sm">
        <span className="rounded bg-gray-100 px-2 py-1">{data.kind}</span>
        <span className="rounded bg-gray-100 px-2 py-1">{data.status}</span>
        <span className="rounded bg-gray-100 px-2 py-1">{data.category}</span>
      </div>

      <p>{data.description || 'No description'}</p>
      <p className="text-sm text-gray-600">Sold by {data.sellerName}</p>
    </div>
  )
}
