## Quick start (Linux or Windows)

Needs Docker, Node and JDK 21 (via `JAVA_HOME`).

```sh
node dev.mjs
```

Then open http://localhost:5173/hello. Ctrl+C stops React, and
`docker compose down` stops the backend.


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


### Windows (PS)
Needs Docker Desktop running and JDK 21.

Start pointing JAVA_HOME env to jdk-21
```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
```

Then to start docker
```powershell
docker compose up db -d --wait         # start database
$env:DB_URL = "jdbc:postgresql://localhost:5432/app"
$env:DB_USER = "app"
$env:DB_PASSWORD = "app"
.\gradlew.bat --no-daemon buildFatJar  # migrations
docker compose up -d --build --wait    # start app
```

Then test the hello endpoint
```powershell
Invoke-RestMethod http://localhost:8080/api/hello
```

