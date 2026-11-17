import { useState } from 'react'
import { Link, useNavigate } from 'react-router'

type Props = { onSearch: (text: string) => void }

const buttonClass = 'rounded bg-blue-300 px-3 py-2'

export default function Header({ onSearch }: Props) {
  // whats typed right now, app only gets it on submit
  const [text, setText] = useState('')
  const navigate = useNavigate()

  return (
    <header className="flex items-center gap-3 border-b border-gray-300 px-6 py-3">
      <Link to="/" className="font-bold">
        Local Marketplace
      </Link>

      <form
        onSubmit={(event) => {
          event.preventDefault()
          onSearch(text)
          navigate('/')
        }}
      >
        <input
          placeholder="Search listings..."
          value={text}
          onChange={(event) => setText(event.target.value)}
          className="rounded border border-gray-300 px-3 py-2"
        />
      </form>

      <Link to="/sell" className={buttonClass}>
        Sell
      </Link>
      <Link to="/profile" className={buttonClass}>
        Me
      </Link>
    </header>
  )
}
