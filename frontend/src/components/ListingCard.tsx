import { Link } from 'react-router'

type Props = {
  id: string
  title: string
  price: string | null
  distanceM: number
}

export default function ListingCard({ id, title, price, distanceM }: Props) {
  return (
    <Link
      to={`/listings/${id}`}
      className="flex flex-col gap-1 rounded border border-gray-300 p-3 hover:shadow"
    >
      {/* todo thumbnail here when seed has images */}
      <div className="h-32 rounded bg-gray-200" />
      <p className="font-semibold">{title}</p>
      <p>{price ? price + ' kr' : 'Free'}</p>
      <p className="text-sm text-gray-600">
        {(distanceM / 1000).toFixed(1)} km away
      </p>
    </Link>
  )
}
