/** Runs the real bundled Electron main/preloads against a local unauthenticated API fixture. */
const { app, BrowserWindow, Menu } = require('electron')
const assert = require('node:assert/strict')
const { createServer } = require('node:http')
const { mkdtempSync, writeFileSync, rmSync } = require('node:fs')
const { tmpdir } = require('node:os')
const { join } = require('node:path')
const directory = mkdtempSync(join(tmpdir(), 'messenger-notification-test-'))
app.setPath('userData', directory)
const api = createServer((_request, response) => {
  response.writeHead(401, { 'Content-Type': 'application/json' })
  response.end(JSON.stringify({ success: false, code: 'AUTH_001', message: 'Test fixture: no session' }))
})

/** Waits for observable Electron state and fails with a useful label. */
async function until(label, predicate, timeout = 5000) {
  const start = Date.now()
  while (Date.now() - start < timeout) {
    if (await predicate()) return
    await new Promise(resolve => setTimeout(resolve, 50))
  }
  throw new Error(`Timed out: ${label}`)
}

/** Returns only local topmost notification cards, excluding the main window. */
function popups(main) { return BrowserWindow.getAllWindows().filter(window => window !== main) }

api.listen(0, '127.0.0.1', async () => {
  let exitCode = 0
  try {
    const properties = join(directory, 'messenger.properties')
    writeFileSync(properties, `api.origin=http://127.0.0.1:${api.address().port}\n`)
    process.env.MESSENGER_DESKTOP_CONFIG = properties
    delete process.env.ELECTRON_RENDERER_URL
    require('../dist-electron/main.js')
    await app.whenReady()
    await until('main renderer', () => BrowserWindow.getAllWindows().some(window => !window.webContents.isLoading() && window.webContents.getURL().startsWith('file:')))
    const main = BrowserWindow.getAllWindows()[0]
    await until('preload bridge', () => main.webContents.executeJavaScript('Boolean(window.messengerDesktop)'))
    main.show()
    main.focus()
    await main.webContents.executeJavaScript('window.reloadTestMarker = "retained"')
    const menuRoles = []
    const collectRoles = menu => menu?.items.forEach(item => { menuRoles.push(item.role); collectRoles(item.submenu) })
    collectRoles(Menu.getApplicationMenu())
    assert.equal(menuRoles.some(role => ['reload', 'forceReload', 'forcereload'].includes(role)), false, 'Application menu must not expose Reload')
    for (const event of [
      { keyCode: 'F5' }, { keyCode: 'F5', modifiers: ['control'] },
      { keyCode: 'R', modifiers: ['control'] }, { keyCode: 'R', modifiers: ['control', 'shift'] },
      { keyCode: 'R', modifiers: ['meta'] }, { keyCode: 'R', modifiers: ['meta', 'shift'] }
    ]) {
      main.webContents.sendInputEvent({ type: 'keyDown', ...event })
      main.webContents.sendInputEvent({ type: 'keyUp', ...event })
      await new Promise(resolve => setTimeout(resolve, 100))
      assert.equal(await main.webContents.executeJavaScript('window.reloadTestMarker'), 'retained', `Reload blocked: ${JSON.stringify(event)}`)
    }
    console.log('PASS: F5 and Ctrl/Cmd(+Shift)+R keep the existing renderer; no reload menu')

    await main.webContents.executeJavaScript(`window.messengerDesktop.showNotification('Silent', 'History only', {displayMode:'SILENT'})`)
    assert.equal(popups(main).length, 0)
    await main.webContents.executeJavaScript(`window.messengerDesktop.showNotification('Approval <img>', '<script>bad()</script>', {displayMode:'ALWAYS_ON_TOP', notificationType:'APPROVAL'})`)
    await until('visible topmost notification', () => popups(main).length === 1 && popups(main)[0].isVisible())
    const first = popups(main)[0]
    assert.equal(first.isAlwaysOnTop(), true)
    assert.equal(first.isFocused(), false, 'Notification must not steal keyboard focus')
    assert.equal(await first.webContents.executeJavaScript('typeof window.messengerDesktop'), 'undefined')
    await until('safe literal content', () => first.webContents.executeJavaScript(`document.getElementById('body').textContent === '<script>bad()</script>'`))
    assert.equal(await first.webContents.executeJavaScript('document.querySelectorAll("img").length'), 0)
    console.log('PASS: SILENT creates no window; ALWAYS_ON_TOP shows a safe isolated card without stealing focus')

    for (let index = 0; index < 3; index++) {
      await main.webContents.executeJavaScript(`window.messengerDesktop.showNotification('Alert ${index}', 'Body', {displayMode:'ALWAYS_ON_TOP'})`)
    }
    await until('bounded stack', () => popups(main).length === 3 && popups(main).every(window => window.isVisible()))
    assert.equal(first.isDestroyed(), true, 'Oldest card is replaced; history remains on the server')
    const dismissed = popups(main)[0]
    await dismissed.webContents.executeJavaScript('document.getElementById("dismiss").click()')
    await until('dismissal', () => popups(main).length === 2)
    await main.webContents.executeJavaScript('window.notificationOpened = false; void window.messengerDesktop.onNotificationOpen(() => { window.notificationOpened = true })')
    await popups(main)[0].webContents.executeJavaScript('document.getElementById("open").click()')
    await until('open notification history', () => main.webContents.executeJavaScript('window.notificationOpened'))
    await until('automatic dismissal', () => popups(main).length === 0, 17000)
    console.log('PASS: cards are bounded to three, can be dismissed/opened, and expire after 15 seconds')

    main.minimize()
    await main.webContents.executeJavaScript(`window.scheduleChannel = null; window.messengerDesktop.onNotificationOpen(id => { window.scheduleChannel = id }); window.messengerDesktop.showNotification('회의 일정', '7층 중 회의실', {displayMode:'ALWAYS_ON_TOP',notificationType:'SCHEDULE',channelId:42})`)
    await until('schedule popup while minimized', () => popups(main).length === 1 && popups(main)[0].isVisible())
    await until('schedule action label', () => popups(main)[0].webContents.executeJavaScript(`document.querySelector('#open small').textContent.includes('대화방 열기')`))
    await popups(main)[0].webContents.executeJavaScript('document.getElementById("open").click()')
    await until('schedule routes to its channel', () => main.webContents.executeJavaScript('window.scheduleChannel === 42'))
    console.log('PASS: minimized schedule popup forwards its channel when opened')

    await main.webContents.executeJavaScript(`window.messengerDesktop.showNotification('Private', 'Clear on logout', {displayMode:'ALWAYS_ON_TOP'})`)
    await until('logout card', () => popups(main).length === 1)
    await main.webContents.executeJavaScript(`window.messengerDesktop.authRequest({ action: 'logout' })`)
    await until('logout clears cards', () => popups(main).length === 0)
    console.log('PASS: logout clears previous-session notifications')
  } catch (error) {
    exitCode = 1
    console.error(error)
  } finally {
    api.close()
    for (const window of BrowserWindow.getAllWindows()) window.destroy()
    try { rmSync(directory, { recursive: true, force: true }) } catch { /* The OS may release a cookie file shortly after exit. */ }
    app.exit(exitCode)
  }
})
