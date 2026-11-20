// deletes all listings and users in the db, categories stay. asks first
// run from repo root
import { execSync } from 'node:child_process'
import readline from 'node:readline/promises'

const rl = readline.createInterface({ input: process.stdin, output: process.stdout })
const answer = await rl.question('delete everything in the db? (y/n) ')
rl.close()

if (answer === 'y') {
  execSync(
    'docker compose exec -T db psql -U app -d app -c "truncate listings_current, listing_versions, listings, user_versions, users cascade"',
    { stdio: 'inherit' },
  )
  console.log('db cleared')
} else {
  console.log('nothing deleted')
}
