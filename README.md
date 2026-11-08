Routes:

| URL              | What it does                            |
|------------------|-----------------------------------------|
| `GET /api/hello` | Returns `{"message":"Hello from Ktor"}` |

## Run locally

### Unix systems
Builds are a little complicated for now. Compilation of the app requires a
running database with migrations that it can introspect to generate types
for database entities.

Do this:
```sh
docker compose up db -d           # Start the the database
./gradlew --no-daemon buildFatJar # Run migrations
docker compose up -d              # Start application
```

Then test the hello endpoint
```sh
curl -s http://localhost:8080/api/hello | jq .
```


### Micro and soft
something powershell
