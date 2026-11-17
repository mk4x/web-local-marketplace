import { gql } from './gql'

export async function getListing(id: string) {
  const data = await gql<{ listing: { id: string; title: string } | null }>(
    `query ($id: ID!) { listing(id: $id) { id title } }`,
    { id },
  )
  return data.listing
}

export type Listing = {
  id: string
  title: string
  price: string | null
  distanceM: number
}

export type Filters = {
  kind: string
  category: string
  maxPrice: string
}

export async function searchListings(text: string, filters: Filters) {
  const data = await gql<{ searchListings: Listing[] }>(
    `query ($filter: SearchFilterInput!) {
      searchListings(filter: $filter) { id title price distanceM }
    }`,
    {
      filter: {
        lon: 10.39,
        lat: 55.4,
        text: text || null,
        limit: 50,
        kind: filters.kind || null,
        category: filters.category || null,
        maxPrice: filters.maxPrice || null,
      },
    },
  )
  return data.searchListings
}
