import { spawnSync } from 'node:child_process'
import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

process.chdir(dirname(fileURLToPath(import.meta.url)))

// my postgres on my pc (mark) runs on 5432, so this would clash
// this seems to fix it for both of us
const dbPort = process.env.DB_PORT ?? '5432'
const env = {
  ...process.env,
  DB_PORT: dbPort,
  DB_URL: `jdbc:postgresql://localhost:${dbPort}/app`,
  DB_USER: 'app',
  DB_PASSWORD: 'app',
}

function run(command) {
  const { status } = spawnSync(command, { shell: true, stdio: 'inherit', env })
  if (status !== 0) process.exit(status ?? 1)
}

const gradle = process.platform === 'win32' ? '.\\gradlew.bat' : './gradlew'

run('docker compose up db -d --wait')
run(`${gradle} --no-daemon buildFatJar`)
// this would run react on 3000 without app as it would start
// all sservices. just run the "app" that is the backend
run('docker compose up app -d --build --wait')
run('npm --prefix frontend install')
run('npm --prefix frontend run dev')
