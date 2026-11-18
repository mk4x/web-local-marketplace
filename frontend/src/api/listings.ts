import { ME } from '../me'
import { gql } from './gql'

export async function getListing(id: string) {
  const data = await gql<{ listing: ListingDetail | null }>(
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

export async function createListing(input: NewListing) {
  const data = await gql<{ createListing: { id: string } }>(
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
