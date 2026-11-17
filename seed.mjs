// fills the db with test data, only if theres under 5 listings
// run from repo root. to start over run clear.mjs first
import { execSync } from 'node:child_process'

// run sql in the db container, returns the output as text
function sql(query) {
  return execSync(`docker compose exec -T db psql -U app -d app -t -A -c "${query}"`)
    .toString()
    .trim()
}

// send a gql request to the backend
async function gql(query, variables) {
  const res = await fetch('http://localhost:8080/graphql', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query, variables }),
  })
  const json = await res.json()
  if (json.errors) throw new Error(json.errors[0].message)
  return json.data
}

const count = Number(sql('select count(*) from current_listings'))
if (count >= 5) {
  console.log(`db has ${count} listings, no seed`)
  process.exit(0)
}

// categories. one line, windows cant do line breaks in the cmd (sorry mikkel)
sql(`insert into categories (path, label) values ('home', 'Home'), ('furniture', 'Furniture'), ('electronics', 'Electronics'), ('clothing', 'Clothing'), ('books', 'Books'), ('vehicles', 'Vehicles'), ('vehicles.bikes', 'Bikes') on conflict do nothing`)

// users. fails if the emails already exist, then run clear.mjs first
const users = [
  { email: 'anna@test.dk', displayName: 'Anna Jensen' },
  { email: 'mads@test.dk', displayName: 'Mads Nielsen' },
]

const userIds = []
for (const user of users) {
  const data = await gql(
    'mutation ($input: NewUserInput!) { createUser(input: $input) { id } }',
    { input: user },
  )
  userIds.push(data.createUser.id)
}

// listings. seller 0 = anna, 1 = mads
// first half all in odense centre, second half spread out (0.01 is about 1 km)
const items = [
  { seller: 0, title: 'Desk lamp', category: 'home', price: '150', kind: 'sale', lon: 10.39, lat: 55.40 },
  { seller: 0, title: 'Three-seat sofa', category: 'furniture', kind: 'free', lon: 10.39, lat: 55.40 },
  { seller: 0, title: 'City bike', category: 'vehicles.bikes', price: '900', kind: 'sale', lon: 10.39, lat: 55.40 },
  { seller: 0, title: 'Phone, 128 GB', category: 'electronics', price: '2200', kind: 'sale', lon: 10.39, lat: 55.40 },
  { seller: 0, title: 'Programming books', category: 'books', price: '300', kind: 'sale', lon: 10.39, lat: 55.40 },
  { seller: 1, title: 'Winter jacket, size M', category: 'clothing', price: '250', kind: 'sale', lon: 10.36, lat: 55.38 },
  { seller: 1, title: 'Looking for a desk chair', category: 'furniture', price: '300', kind: 'wanted', lon: 10.42, lat: 55.37 },
  { seller: 1, title: 'Gaming monitor 27 inch', category: 'electronics', price: '1500', kind: 'sale', lon: 10.33, lat: 55.42 },
  { seller: 1, title: 'Acoustic guitar for a keyboard', category: 'home', kind: 'trade', lon: 10.30, lat: 55.36 },
  { seller: 1, title: 'Car, 2012 hatchback', category: 'vehicles', price: '45000', kind: 'sale', lon: 10.50, lat: 55.45 },
]

for (const item of items) {
  await gql(
    'mutation ($input: NewListingInput!) { createListing(input: $input) { id } }',
    {
      input: {
        sellerId: userIds[item.seller],
        title: item.title,
        category: item.category,
        price: item.price,
        kind: item.kind,
        lon: item.lon,
        lat: item.lat,
        city: 'Odense',
      },
    },
  )
}

console.log('seed done, user ids: ' + userIds.join(', '))
