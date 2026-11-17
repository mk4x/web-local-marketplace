// helper to call graphql and get json back

export async function gql<T>(
  query: string,
  variables: Record<string, unknown> = {},
): Promise<T> {
  const response = await fetch('/graphql', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query, variables }),
  })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)

  const json = await response.json()
  if (json.errors) throw new Error(json.errors[0].message)
  return json.data
}
