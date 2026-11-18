import { request } from 'graphql-request'
import { ME } from '../me'

// gql-request needs a full url, a relative one throws
const url = window.location.origin + '/graphql'

export async function getListing(id: string) {
  const data = await request<{ listing: ListingDetail | null }>(
    url,
    `query ($id: ID!) {
    listing(id: $id) {
      id sellerId sellerName version title description
      category kind status price
    }
  }`,
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

// used when going into a listing
export type ListingDetail = {
  id: string
  sellerId: string
  sellerName: string
  version: number
  title: string
  description: string
  category: string
  kind: string
  status: string
  price: string | null
}

export type NewListing = {
  title: string
  description: string
  category: string
  kind: string
  price: string
}

export type Filters = {
  kind: string
  category: string
  maxPrice: string
}

export async function searchListings(text: string, filters: Filters) {
  const data = await request<{ searchListings: Listing[] }>(
    url,
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

export async function createListing(input: NewListing) {
  const data = await request<{ createListing: { id: string } }>(
    url,
    `mutation ($input: NewListingInput!) {
      createListing(input: $input) { id }
    }`,
    {
      input: {
        ...input,
        price: input.price || null,
        sellerId: ME,
        lon: 10.39,
        lat: 55.4,
        city: 'Odense',
      },
    },
  )
  return data.createListing.id
}
