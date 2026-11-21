import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { createListing, getCategories } from '../api/listings'

const inputClass = 'rounded border border-gray-300 px-3 py-2'
const formClass = 'flex max-w-md flex-col gap-3'

export default function CreateListingPage() {
  const [form, setForm] = useState({
    title: '',
    description: '',
    category: 'home',
    kind: 'sale',
    price: '',
  })
  const [error, setError] = useState('')
  const navigate = useNavigate()
  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  return (
    <>
      <form
        className={formClass}
        onSubmit={async (event) => {
          event.preventDefault()
          try {
            const id = await createListing(form)
            navigate(`/listings/${id}`)
          } catch (e) {
            setError((e as Error).message)
          }
        }}
      >
        <input
          type="text"
          placeholder="Title"
          className={inputClass}
          value={form.title}
          onChange={(event) => setForm({ ...form, title: event.target.value })}
        />

        <textarea
          placeholder="Description"
          className={inputClass + ' h-32 resize-y'}
          value={form.description}
          onChange={(event) =>
            setForm({ ...form, description: event.target.value })
          }
        />

        <select
          className={inputClass}
          value={form.category}
          onChange={(event) =>
            setForm({ ...form, category: event.target.value })
          }
        >
          {categories?.map((c) => (
            <option key={c.path} value={c.path}>
              {c.label}
            </option>
          ))}
        </select>

        <select
          className={inputClass}
          value={form.kind}
          onChange={(event) => setForm({ ...form, kind: event.target.value })}
        >
          <option value="sale">For sale</option>
          <option value="wanted">Wanted</option>
          <option value="trade">Trade</option>
          <option value="free">Free</option>
        </select>

        <input
          type="number"
          placeholder="Price"
          className={inputClass}
          value={form.price}
          onChange={(event) => setForm({ ...form, price: event.target.value })}
        />

        <button type="submit" className="rounded bg-blue-300 px-3 py-2">
          Create
        </button>
      </form>
      {error && <p className="text-red-600">{error}</p>}
    </>
  )
}
