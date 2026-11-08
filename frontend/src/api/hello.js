export async function getHello() {
  const response = await fetch('/api/hello')
  if (!response.ok) throw new Error(`API error: ${response.status}`)
  return response.json()
}
