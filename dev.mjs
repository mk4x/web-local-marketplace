import { spawnSync } from 'node:child_process'
import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

process.chdir(dirname(fileURLToPath(import.meta.url)))

const dbPort = '15432'
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
run('docker compose up -d --build --wait')
run('npm --prefix frontend install')
run('npm --prefix frontend run dev')
