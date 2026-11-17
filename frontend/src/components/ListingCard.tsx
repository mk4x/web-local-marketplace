import { Link } from "react-router"

type Props = {
    id: string
    title: string
}

export default function ListingCard({ id, title}: Props) {
    return (
        <Link to={`/listings/${id}`} className="block rounded border p-3">
            {title}
        </Link>
    )
}
