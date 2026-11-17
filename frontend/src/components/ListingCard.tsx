import { Link } from 'react-router'

type Props = {
  id: string
  title: string
  price: string | null
  distanceM: number
}

export default function ListingCard({ id, title, price, distanceM }: Props) {
  return (
    <Link to={`/listings/${id}`} className="block rounded border p-3">
      {title} - {price ? price + ' kr' : 'Free'} -{' '}
      {(distanceM / 1000).toFixed(1)} km away
    </Link>
  )
}
