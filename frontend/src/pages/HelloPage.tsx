import { useQuery } from '@tanstack/react-query'
import { getHello } from '../api/hello'

export default function HelloPage() {
  const { data, error, isPending } = useQuery({
    queryKey: ['hello'],
    queryFn: getHello,
    retry: false,
  })

  if (isPending) return <p>Loading...</p>
  if (error) return <p className="text-red-600">{error.message}</p>

  return <p className="text-green-700">{data.message}</p>
}
