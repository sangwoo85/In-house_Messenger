// Exercises file:// or MESSENGER_SMOKE_RENDERER_URL with real preload/main IPC and synthetic HTTP data.
const { app, BrowserWindow, session } = require('electron')
// Fail tests immediately instead of opening Electron's blocking JavaScript error dialog.
process.on('uncaughtException', error => { console.error(error); app.exit(1) })
const { createServer } = require('node:http')
const { mkdtempSync, writeFileSync } = require('node:fs')
const { tmpdir } = require('node:os')
const { join } = require('node:path')
const assert = require('node:assert/strict')
const directory = mkdtempSync(join(tmpdir(), 'messenger-desktop-smoke-'))
app.setPath('userData', directory)
const profile = { id: 1, userId: 'reviewer', nickname: '테스트 사용자', status: 'ONLINE', department: '개발팀', departmentId: 'dev', userGroup: '사용자', profileImageUrl: null }
const typingBenchmark = process.argv.includes('--typing') || process.env.MESSENGER_SMOKE_TYPING === '1'
let cookieRefreshes = 0
let failNextSend = false
let sendAttempts = []
let fileUploads = 0
let failNextUpload = false
let uploadResponseGate
let fileMessageResponseGate
let slowUploadReads = false
const uploadedFile = { id: 700, originalName: '답장 자료.pdf', mimeType: 'application/pdf', fileSize: 16 * 1024 * 1024, downloadUrl: '/api/v1/files/700', image: false }
let channels = []
let messages = Array.from({ length: typingBenchmark ? 1000 : 31 }, (_, n) => ({ id: n + 1, channelId: 1, senderUserId: 'bob', content: `이전 메시지 ${n + 1}`, type: 'TEXT', attachment: null, deleted: false, createdAt: '2026-09-13T12:00:00', updatedAt: '2026-09-13T12:00:00', clientRequestId: null, readUserIds: [], unreadCount: 1, reactions: [], deletable: true }))
messages.at(-1).replyTo = { id: 1, senderUserId: 'bob', content: messages[0].content, type: 'TEXT', deleted: false }
const server = createServer(async (request, response) => {
  const chunks = []
  for await (const chunk of request) {
    chunks.push(chunk)
    // Real socket backpressure also exercises upload events when Chromium skips loopback emulation.
    if (slowUploadReads && request.url === '/api/v1/files/upload') await new Promise(resolve => setTimeout(resolve, 20))
  }
  const multipart = request.headers['content-type']?.startsWith('multipart/form-data')
  const input = chunks.length && !multipart ? JSON.parse(Buffer.concat(chunks).toString()) : {}
  const url = new URL(request.url, 'http://localhost')
  response.setHeader('Access-Control-Allow-Origin', request.headers.origin || 'null')
  response.setHeader('Access-Control-Allow-Headers', 'Authorization,Content-Type')
  response.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,PATCH,DELETE,OPTIONS')
  if (request.method === 'OPTIONS') { response.writeHead(204); response.end(); return }
  response.setHeader('Content-Type', 'application/json')
  const ok = data => response.end(JSON.stringify({ success: true, data }))
  const failed = () => { response.statusCode = 401; response.end(JSON.stringify({ code: 'AUTH_004' })) }
  if (url.pathname.endsWith('/auth/login')) {
    if (input.emprId !== 'reviewer' || input.password !== 'synthetic-only') return failed()
    response.setHeader('Set-Cookie', 'refreshToken=synthetic-only; Path=/; HttpOnly; SameSite=Strict; Max-Age=604800')
    return ok({ accessToken: 'synthetic-access', expiresIn: 900, user: profile })
  }
  if (url.pathname.endsWith('/auth/refresh')) {
    if (!request.headers.cookie?.includes('refreshToken=synthetic-only')) return failed()
    cookieRefreshes++
    return ok({ accessToken: 'synthetic-access', expiresIn: 900, user: profile })
  }
  if (url.pathname.endsWith('/auth/logout')) {
    response.setHeader('Set-Cookie', 'refreshToken=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0')
    return ok(null)
  }
  if (url.pathname === '/api/v1/organizations') return ok({ departments: [{ id: 'company', parentId: null, name: '테스트 회사' }, { id: 'tech', parentId: 'company', name: '기술본부' }, { id: 'dev', parentId: 'tech', name: '개발팀' }], users: [{ ...profile, id: 2, userId: 'bob', nickname: '동료' }], syncedAt: '2026-09-20T09:00:00', stale: false })
  if (url.pathname === '/api/v1/users/me/profile-image') {
    profile.profileImageUrl = request.method === 'DELETE' ? null : '/api/v1/users/reviewer/profile-image?v=1'
    return ok(profile)
  }
  if (url.pathname === '/api/v1/users/reviewer/profile-image') {
    assert.equal(request.headers.authorization, 'Bearer synthetic-access', 'Profile image must require bearer auth')
    response.setHeader('Content-Type', 'image/png')
    return response.end(Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j3ioAAAAASUVORK5CYII=', 'base64'))
  }
  if (url.pathname.endsWith('/schedules') || url.pathname.endsWith('/schedule-reminders')) return ok([])
  if (url.pathname.endsWith('/users')) return ok([{ ...profile, id: 2, userId: 'bob', nickname: '동료' }])
  if (url.pathname.endsWith('/presence/heartbeat')) return ok(null)
  if (url.pathname.endsWith('/notifications')) return ok({ items: [], page: 0, size: 20, totalElements: 0, unreadCount: 0 })
  if (url.pathname.endsWith('/notices')) return ok({ items: [{ id: 1, title: '공지 이력 확인', content: '보관된 공지입니다.', sender: '운영팀', createdAt: '2026-09-13T12:00:00' }], page: 0, size: 20, totalElements: 1 })
  if (url.pathname === '/api/v1/files/upload') {
    fileUploads++
    if (uploadResponseGate) await uploadResponseGate
    if (failNextUpload) { failNextUpload = false; response.statusCode = 503; return response.end('{}') }
    return ok(uploadedFile)
  }
  if (url.pathname === '/api/v1/channels') {
    if (request.method === 'POST') channels = [{ id: 1, name: input.name, type: input.type, members: ['reviewer', ...input.memberUserIds], unreadCount: 31, ownerUserId: 'reviewer', lastMessageAt: messages.at(-1).createdAt }]
    return ok(request.method === 'POST' ? channels[0] : channels)
  }
  if (url.pathname === '/api/v1/channels/1/messages') {
    if (request.method === 'POST') {
      sendAttempts.push(input)
      if (failNextSend) { failNextSend = false; response.statusCode = 503; return response.end('{}') }
      if (input.fileId && fileMessageResponseGate) await fileMessageResponseGate
      const now = new Date().toISOString()
      const original = messages.find(message => message.id === input.replyToMessageId)
      const replyTo = original ? { id: original.id, senderUserId: original.senderUserId, content: original.content, type: original.type, deleted: original.deleted } : null
      const message = { ...messages[0], ...input, replyTo, attachment: input.fileId ? uploadedFile : null, id: messages.length + 1, senderUserId: 'reviewer', createdAt: now, updatedAt: now }
      messages.push(message); channels[0].lastMessageAt = message.createdAt; return ok(message)
    }
    const cursor = Number(url.searchParams.get('cursor') || Number.MAX_SAFE_INTEGER)
    const eligible = messages.filter(message => message.id < cursor)
    const pageSize = typingBenchmark ? 1000 : 30
    const items = eligible.slice(-pageSize)
    return ok({ items, hasNext: eligible.length > pageSize, nextCursor: eligible.length > pageSize ? items[0].id : null })
  }
  if (url.pathname === '/api/v1/channels/2/messages') return ok({ items: [], hasNext: false, nextCursor: null })
  if (url.pathname.endsWith('/read')) return ok(0)
  if (url.pathname.endsWith('/reaction')) {
    const message = messages.find(message => message.id === Number(url.pathname.split('/')[4]))
    message.reactions = request.method === 'DELETE' ? [] : [{ emoji: input.emoji, userIds: ['reviewer'] }]
    message.updatedAt = new Date().toISOString()
    return ok(message)
  }
  if (url.pathname.startsWith('/api/v1/messages/')) {
    const message = messages.find(message => message.id === Number(url.pathname.split('/').pop()))
    if (request.method === 'DELETE') { message.deleted = true; message.content = '' }
    else message.content = input.content
    message.updatedAt = new Date().toISOString()
    return ok(message)
  }
  response.statusCode = 404; response.end('{}')
})
async function waitFor(window, expression) {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (await window.webContents.executeJavaScript(expression)) return
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  throw new Error('Timed out: ' + expression)
}
async function click(window, label) {
  await waitFor(window, `Array.from(document.querySelectorAll('button')).some(button => button.textContent.trim() === ${JSON.stringify(label)})`)
  await window.webContents.executeJavaScript(`Array.from(document.querySelectorAll('button')).find(button => button.textContent.trim() === ${JSON.stringify(label)})?.click()`)
}
async function input(window, selector, value) {
  await window.webContents.executeJavaScript(`(() => { const input = document.querySelector(${JSON.stringify(selector)}); Object.getOwnPropertyDescriptor(input.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype, 'value').set.call(input, ${JSON.stringify(value)}); input.dispatchEvent(new Event('input', { bubbles: true })); })()`)
}
server.listen(0, '127.0.0.1', async () => {
  const origin = `http://localhost:${server.address().port}`
  const config = join(directory, 'messenger.properties')
  writeFileSync(config, `api.origin=${origin}\n`)
  process.env.MESSENGER_DESKTOP_CONFIG = config
  if (process.env.MESSENGER_SMOKE_RENDERER_URL) process.env.ELECTRON_RENDERER_URL = process.env.MESSENGER_SMOKE_RENDERER_URL
  else delete process.env.ELECTRON_RENDERER_URL
  try {
    require('../dist-electron/main.js')
    await app.whenReady()
    while (!BrowserWindow.getAllWindows().length) await new Promise(resolve => setTimeout(resolve, 20))
    const window = BrowserWindow.getAllWindows()[0]
    await waitFor(window, "!!document.querySelector('form')")
    await input(window, 'form input:not([type=password])', 'reviewer')
    await input(window, 'input[type=password]', 'synthetic-only')
    await click(window, '로그인')
    await waitFor(window, "document.body.textContent.includes('새 그룹 대화')")
    assert.equal((await session.defaultSession.cookies.get({ url: origin, name: 'refreshToken' })).length, 1)
    window.webContents.reload()
    await waitFor(window, "document.body.textContent.includes('새 그룹 대화')")
    assert.ok(cookieRefreshes > 0, 'file:// restart must restore via main-process cookie')
    await waitFor(window, "document.body.textContent.includes('기술본부')")
    await window.webContents.executeJavaScript("document.querySelector('button[aria-label=\"내 프로필 사진 설정\"]').click()")
    await waitFor(window, "!!document.querySelector('[aria-labelledby=profile-title]')")
    await window.webContents.executeJavaScript(`(() => {
      const bytes = Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j3ioAAAAASUVORK5CYII='), char => char.charCodeAt(0))
      const transfer = new DataTransfer(); transfer.items.add(new File([bytes], 'profile.png', { type: 'image/png' }))
      const input = document.querySelector('[aria-labelledby=profile-title] input[type=file]'); input.files = transfer.files; input.dispatchEvent(new Event('change', { bubbles: true }))
    })()`)
    await waitFor(window, "!!document.querySelector('[aria-labelledby=profile-title] img')")
    await click(window, '사진 삭제')
    await waitFor(window, "!document.querySelector('[aria-labelledby=profile-title] img')")
    await click(window, '닫기')
    await click(window, '새 그룹 대화')
    await waitFor(window, "!!document.querySelector('[aria-label=\"그룹 이름\"]')")
    await input(window, '[aria-label="그룹 이름"]', '검증 그룹')
    await window.webContents.executeJavaScript("document.querySelector('input[type=checkbox]').click()")
    await click(window, '그룹 만들기')
    await waitFor(window, "!!document.querySelector('textarea[aria-label=메시지]')")
    if (typingBenchmark) {
      await waitFor(window, "document.querySelectorAll('[data-message-id]').length === 1000")
      const result = await window.webContents.executeJavaScript(`(async () => {
        const textarea = document.querySelector('textarea[aria-label=메시지]')
        textarea.focus()
        const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set
        const timings = []
        const phrase = '빠르게 입력해도 메시지와 커서가 자연스럽게 표시되어야 합니다. typing 1234567890'
        for (const character of phrase) {
          await new Promise(resolve => requestAnimationFrame(resolve))
          const started = performance.now()
          setter.call(textarea, textarea.value + character)
          textarea.dispatchEvent(new Event('input', { bubbles: true }))
          await new Promise(resolve => requestAnimationFrame(resolve))
          timings.push(performance.now() - started)
        }
        timings.sort((a, b) => a - b)
        return { messages: document.querySelectorAll('[data-message-id]').length, inputs: timings.length,
          medianMs: timings[Math.floor(timings.length / 2)], p95Ms: timings[Math.floor(timings.length * .95)],
          maxMs: timings.at(-1), value: textarea.value, expected: phrase,
          caret: textarea.selectionStart, focused: document.activeElement === textarea }
      })()`)
      console.log('TYPING_BENCHMARK:', JSON.stringify(result))
      assert.equal(result.value, result.expected, 'Fast typing must retain every character')
      assert.equal(result.caret, result.value.length, 'Typing must preserve the caret')
      assert.equal(result.focused, true, 'Typing must retain focus')
      assert.ok(result.p95Ms < 50, `Input-to-frame p95 must stay below 50ms with 1000 messages; observed ${result.p95Ms.toFixed(1)}ms`)
      console.log('PASS: typing responsiveness with 1000 loaded messages')
      return
    }
    await waitFor(window, "document.querySelector('.messenger-room time')?.dateTime === '2026-09-13T12:00:00'")
    await waitFor(window, "document.body.textContent.includes('이전 메시지 더 보기')")
    assert.equal(await window.webContents.executeJavaScript("!!document.querySelector('[data-message-id=\"1\"]')"), false)
    await window.webContents.executeJavaScript("document.querySelector('[data-message-id=\"31\"] .messenger-reply-quote').click()")
    await waitFor(window, "Array.from(document.querySelectorAll('article p')).some(node => node.textContent === '이전 메시지 1')")
    await waitFor(window, "!!document.querySelector('[data-message-id=\"1\"].is-highlighted')")
    assert.equal(await window.webContents.executeJavaScript("document.activeElement.dataset.messageId"), '1', 'Following a quote must focus the original after loading older history')
    assert.equal(await window.webContents.executeJavaScript("document.querySelector('.messenger-room time').dateTime"), '2026-09-13T12:00:00', 'Older history must not move the last send date backwards')
    await input(window, 'textarea[aria-label=메시지]', '전송 저장 확인')
    await click(window, '전송')
    await waitFor(window, "document.body.textContent.includes('전송 저장 확인') && document.querySelector('textarea[aria-label=메시지]').value === ''")
    const lastSentAt = messages.at(-1).createdAt
    await waitFor(window, `document.querySelector('.messenger-room time')?.dateTime === ${JSON.stringify(lastSentAt)}`)
    assert.match(await window.webContents.executeJavaScript("document.querySelector('.messenger-room time').textContent"), /\d+:\d{2}/, 'Today\'s message must show its send time')
    assert.equal(await window.webContents.executeJavaScript("Array.from(document.querySelectorAll('button')).some(button => button.textContent.trim() === '수정')"), false)
    await window.webContents.executeJavaScript("document.querySelector('button[aria-label=\"메시지 32 반응 선택\"]').click()")
    await window.webContents.executeJavaScript("document.querySelector('button[aria-label=\"👍 선택\"]').click()")
    await waitFor(window, "!!document.querySelector('button[aria-label=\"👍 반응 1명\"][aria-pressed=true]')")
    await window.webContents.executeJavaScript("document.querySelector('button[aria-label=\"메시지 32 반응 선택\"]').click()")
    await window.webContents.executeJavaScript("document.querySelector('button[aria-label=\"❤️ 선택\"]').click()")
    await waitFor(window, "!!document.querySelector('button[aria-label=\"❤️ 반응 1명\"][aria-pressed=true]') && !document.querySelector('button[aria-label=\"👍 반응 1명\"]')")
    writeFileSync(join(directory, 'chat.png'), (await window.webContents.capturePage()).toPNG())
    await window.webContents.executeJavaScript("document.querySelector('button[aria-label=\"❤️ 반응 1명\"]').click()")
    await waitFor(window, "!document.querySelector('button[aria-label=\"❤️ 반응 1명\"]')")
    await click(window, '삭제')
    await waitFor(window, "document.body.textContent.includes('삭제된 메시지입니다.')")
    assert.equal(await window.webContents.executeJavaScript("document.querySelector('.messenger-room time').dateTime"), lastSentAt, 'Reactions and deletion must preserve the original send time')

    // Reload with a second room to exercise draft isolation and persisted quotes.
    channels.push({ ...channels[0], id: 2, name: '별도 대화', lastMessageAt: null, unreadCount: 0 })
    window.webContents.reload()
    await waitFor(window, "!!document.querySelector('.messenger-nav button')")
    await window.webContents.executeJavaScript("document.querySelector('.messenger-nav button').click()")
    await waitFor(window, "!!document.querySelector('[data-message-id=\"31\"] .messenger-reply-quote')")
    await window.webContents.executeJavaScript("document.querySelector('button[aria-label=\"메시지 31 답장\"]').click()")
    await waitFor(window, "!!document.querySelector('.messenger-reply-composer')")
    assert.equal(await window.webContents.executeJavaScript("document.activeElement.tagName"), 'TEXTAREA')
    await input(window, 'textarea[aria-label=메시지]', '원문을 인용한 답장')
    await window.webContents.executeJavaScript("Array.from(document.querySelectorAll('.messenger-room')).find(button => button.textContent.includes('별도 대화')).click()")
    await waitFor(window, "document.querySelector('.messenger-page-title')?.textContent === '별도 대화'")
    assert.equal(await window.webContents.executeJavaScript("!!document.querySelector('.messenger-reply-composer')"), false)
    assert.equal(await window.webContents.executeJavaScript("document.querySelector('textarea').value"), '')
    await window.webContents.executeJavaScript("Array.from(document.querySelectorAll('.messenger-room')).find(button => button.textContent.includes('검증 그룹')).click()")
    await waitFor(window, "document.querySelector('textarea')?.value === '원문을 인용한 답장' && !!document.querySelector('.messenger-reply-composer')")
    failNextSend = true
    await click(window, '전송')
    await waitFor(window, "document.querySelector('[role=alert]')?.textContent.includes('입력을 유지')")
    assert.equal(await window.webContents.executeJavaScript("document.querySelector('textarea').value"), '원문을 인용한 답장')
    assert.equal(await window.webContents.executeJavaScript("!!document.querySelector('.messenger-reply-composer')"), true)
    await click(window, '전송')
    await waitFor(window, "!!document.querySelector('[data-message-id=\"33\"] .messenger-reply-quote') && !document.querySelector('.messenger-reply-composer')")
    assert.equal(sendAttempts.at(-1).replyToMessageId, 31)
    assert.equal(sendAttempts.at(-1).clientRequestId, sendAttempts.at(-2).clientRequestId, 'Failed reply retries must reuse the same idempotency key')
    await window.webContents.executeJavaScript("document.querySelector('[data-message-id=\"33\"] .messenger-reply-quote').click()")
    await waitFor(window, "!!document.querySelector('[data-message-id=\"31\"].is-highlighted')")
    await window.webContents.executeJavaScript("document.querySelector('button[aria-label=\"메시지 33 답장\"]').click()")
    await input(window, 'textarea[aria-label=메시지]', '답장 취소 후에도 유지되는 내용')
    await window.webContents.executeJavaScript("document.querySelector('textarea').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))")
    await waitFor(window, "!document.querySelector('.messenger-reply-composer')")
    assert.equal(await window.webContents.executeJavaScript("document.querySelector('textarea').value"), '답장 취소 후에도 유지되는 내용')
    await window.webContents.executeJavaScript("document.querySelector('button[aria-label=\"메시지 33 반응 선택\"]').click()")
    await waitFor(window, "!!document.querySelector('.messenger-reaction-picker')")
    await window.webContents.executeJavaScript("document.activeElement.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))")
    await waitFor(window, "!document.querySelector('.messenger-reaction-picker')")
    assert.equal(await window.webContents.executeJavaScript("document.activeElement.getAttribute('aria-label')"), '메시지 33 반응 선택')
    writeFileSync(join(directory, 'replies.png'), (await window.webContents.capturePage()).toPNG())

    // Self replies can outlive an unread original; its text must disappear from the quote immediately.
    await window.webContents.executeJavaScript("document.querySelector('button[aria-label=\"메시지 33 답장\"]').click()")
    await input(window, 'textarea[aria-label=메시지]', '자신의 메시지에 답장')
    await click(window, '전송')
    await waitFor(window, "!!document.querySelector('[data-message-id=\"34\"] .messenger-reply-quote')")
    await window.webContents.executeJavaScript("Array.from(document.querySelectorAll('[data-message-id=\"33\"] button')).find(button => button.textContent === '삭제').click()")
    await waitFor(window, "document.querySelector('[data-message-id=\"34\"] .messenger-reply-summary')?.textContent === '삭제된 메시지입니다.'")
    await window.webContents.executeJavaScript("document.querySelector('button[aria-label=\"메시지 34 답장\"]').click()")
    failNextSend = true
    let releaseUploadResponse
    uploadResponseGate = new Promise(resolve => { releaseUploadResponse = resolve })
    slowUploadReads = true
    await window.webContents.executeJavaScript(`(() => {
      const transfer = new DataTransfer(); transfer.items.add(new File([new Uint8Array(16 * 1024 * 1024)], '답장 자료.pdf', { type: 'application/pdf' }))
      const input = document.querySelector('.messenger-composer input[type=file]'); input.files = transfer.files; input.dispatchEvent(new Event('change', { bubbles: true }))
    })()`)
    await waitFor(window, "Number(document.querySelector('[role=progressbar]')?.getAttribute('aria-valuenow')) > 0 && Number(document.querySelector('[role=progressbar]')?.getAttribute('aria-valuenow')) < 100")
    assert.match(await window.webContents.executeJavaScript("document.querySelector('.messenger-upload-details').textContent"), /\/ 16\.0 MB/)
    writeFileSync(join(directory, 'upload-progress.png'), (await window.webContents.capturePage()).toPNG())
    await waitFor(window, "!!document.querySelector('[data-upload-state=processing] [role=progressbar][aria-valuenow=\"100\"]')")
    assert.equal(await window.webContents.executeJavaScript("document.querySelector('.messenger-upload [role=status]').textContent"), '파일 처리 중')
    assert.equal(await window.webContents.executeJavaScript("!!document.querySelector('[data-message-id=\"35\"]')"), false, '100% transferred must not imply the message has been saved')
    slowUploadReads = false
    releaseUploadResponse(); uploadResponseGate = undefined
    await waitFor(window, "Array.from(document.querySelectorAll('button')).some(button => button.textContent === '재시도')")
    assert.equal(await window.webContents.executeJavaScript("!!document.querySelector('.messenger-reply-composer')"), true)
    assert.equal(await window.webContents.executeJavaScript("document.querySelector('[data-upload-state=failed] [role=progressbar]').getAttribute('aria-valuenow')"), '100')
    let releaseFileMessageResponse
    fileMessageResponseGate = new Promise(resolve => { releaseFileMessageResponse = resolve })
    await click(window, '재시도')
    await waitFor(window, "!!document.querySelector('[data-upload-state=sending] [role=progressbar][aria-valuenow=\"100\"]')")
    assert.equal(await window.webContents.executeJavaScript("document.querySelector('.messenger-upload [role=status]').textContent"), '메시지 전송 중')
    releaseFileMessageResponse(); fileMessageResponseGate = undefined
    await waitFor(window, "!!document.querySelector('[data-message-id=\"35\"] .messenger-file') && !document.querySelector('.messenger-reply-composer')")
    assert.equal(await window.webContents.executeJavaScript("!!document.querySelector('[role=progressbar]')"), false)
    assert.equal(fileUploads, 1, 'Reply retry must reuse the already uploaded attachment')
    assert.equal(sendAttempts.at(-1).replyToMessageId, 34)
    assert.equal(sendAttempts.at(-1).clientRequestId, sendAttempts.at(-2).clientRequestId)
    await window.webContents.executeJavaScript("document.querySelector('button[aria-label=\"메시지 35 답장\"]').click()")
    await waitFor(window, "document.querySelector('.messenger-reply-composer')?.textContent.includes('파일 · 답장 자료.pdf')")
    await input(window, 'textarea[aria-label=메시지]', '자료 확인했습니다.')
    await window.webContents.executeJavaScript("document.querySelector('button[aria-label=\"메시지 35 반응 선택\"]').click()")
    await waitFor(window, "!!document.querySelector('.messenger-reaction-picker')")
    writeFileSync(join(directory, 'reply-composer.png'), (await window.webContents.capturePage()).toPNG())
    await window.webContents.executeJavaScript("document.querySelector('textarea').dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }))")
    await waitFor(window, "!document.querySelector('.messenger-reaction-picker')")
    await window.webContents.executeJavaScript("Array.from(document.querySelectorAll('[data-message-id=\"35\"] button')).find(button => button.textContent === '삭제').click()")
    await waitFor(window, "document.querySelector('.messenger-reply-composer')?.textContent.includes('삭제된 메시지입니다.') && document.querySelector('.messenger-send')?.disabled")
    await window.webContents.executeJavaScript("document.querySelector('button[aria-label=\"답장 취소\"]').click()")
    await waitFor(window, "!document.querySelector('.messenger-reply-composer') && !document.querySelector('.messenger-send')?.disabled")
    assert.equal(await window.webContents.executeJavaScript("document.querySelector('textarea').value"), '자료 확인했습니다.')

    // A failed upload starts from zero on retry, unlike a failed message whose upload is reusable.
    failNextUpload = true
    await window.webContents.executeJavaScript(`(() => {
      const transfer = new DataTransfer(); transfer.items.add(new File([new Uint8Array(16 * 1024 * 1024)], '재시도 자료.pdf', { type: 'application/pdf' }))
      const input = document.querySelector('.messenger-composer input[type=file]'); input.files = transfer.files; input.dispatchEvent(new Event('change', { bubbles: true }))
    })()`)
    await waitFor(window, "document.querySelector('[data-upload-state=failed] [role=alert]')?.textContent.includes('파일 업로드에 실패')")
    slowUploadReads = true
    await click(window, '재시도')
    await waitFor(window, "Number(document.querySelector('[data-upload-state=uploading] [role=progressbar]')?.getAttribute('aria-valuenow')) > 0 && Number(document.querySelector('[data-upload-state=uploading] [role=progressbar]')?.getAttribute('aria-valuenow')) < 100")
    await waitFor(window, "!document.querySelector('.messenger-upload')")
    slowUploadReads = false
    assert.equal(fileUploads, 3, 'Failed file transfer must upload again; failed message transfer must reuse the upload')
    await click(window, '알림')
    await click(window, '전체 공지')
    await waitFor(window, "document.body.textContent.includes('공지 이력 확인')")
    await click(window, '로그아웃')
    await waitFor(window, "!!document.querySelector('form')")
    assert.equal((await session.defaultSession.cookies.get({ url: origin, name: 'refreshToken' })).length, 0)
    console.log(`PASS: ${process.env.MESSENGER_SMOKE_RENDERER_URL ? 'development URL' : 'file'} renderer, auth IPC, persistent refresh, organization, profile, groups, history, message send/delete, emoji add/replace/remove, reply persistence, paginated original navigation, channel draft isolation, retry-safe text/file replies, measured upload progress/volume, server-processing/message-send phases, upload retry reset, Escape/outside cancellation, deleted quotes/drafts, notice history; screenshots=${join(directory, 'chat.png')}, ${join(directory, 'replies.png')}, ${join(directory, 'reply-composer.png')}, ${join(directory, 'upload-progress.png')}`)
  } catch (error) {
    console.error(error)
    const window = BrowserWindow.getAllWindows()[0]
    if (window && !window.isDestroyed()) {
      console.error('Upload state:', await window.webContents.executeJavaScript("document.querySelector('.messenger-upload-list')?.textContent"))
      writeFileSync(join(directory, 'failure.png'), (await window.webContents.capturePage()).toPNG())
      console.error('Screenshot:', join(directory, 'failure.png'))
    }
    process.exitCode = 1
  }
  finally { server.close(); app.exit(process.exitCode || 0) }
})
