import { useParams } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { getListing } from "../api/listings";

export default function ListingPage() {
  const { id } = useParams()

  const { data, isPending, error } = useQuery({
    queryKey: ['listings', id],
    queryFn: () => getListing(id!),
  })

  if (isPending) return <p>Loading...</p>
  if (error) return <p>{error.message}</p>
  if (!data) return <p>Listing not found</p>

  return (
    <div>
      <h1>{data.title}</h1>
      <p>{data.price ? data.price + ' kr' : 'Free'}</p>
      <p>{data.sellerName}</p>
      <p>{data.description}</p>
      <p>{data.category}</p>
      <p>{data.kind}</p>
      <p>{data.status}</p>
    </div>
  )
}
