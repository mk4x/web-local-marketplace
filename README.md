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
docker compose up -d --build              # Start and clean build application
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
docker compose up -d --build    # Start and clean build app
```

Then test the hello endpoint
```powershell
Invoke-RestMethod http://localhost:8080/api/hello
```

