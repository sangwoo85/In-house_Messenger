const { spawnSync } = require('node:child_process')
const required = ['CSC_LINK', 'CSC_KEY_PASSWORD']
if (process.platform === 'darwin') required.push('APPLE_ID', 'APPLE_APP_SPECIFIC_PASSWORD', 'APPLE_TEAM_ID')
const missing = required.filter(key => !process.env[key])
if (missing.length) { console.error('Release signing configuration required: ' + missing.join(', ')); process.exit(1) }
for (const args of [['run', 'build'], ['exec', 'electron-builder', '--', '--publish', 'never', ...(process.platform === 'darwin' ? ['--config.mac.notarize=true'] : [])]]) {
  const result = spawnSync(process.platform === 'win32' ? 'npm.cmd' : 'npm', args, { stdio: 'inherit', shell: process.platform === 'win32' })
  if (result.status !== 0) process.exit(result.status || 1)
}
