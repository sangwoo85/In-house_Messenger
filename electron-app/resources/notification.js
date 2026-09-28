/** Display untrusted business text as text nodes, never as HTML. */
window.messengerNotification.get().then(payload => {
  document.getElementById('title').textContent = payload.title
  document.getElementById('body').textContent = payload.body
  document.getElementById('type').textContent = payload.notificationType
  if (payload.channelId) document.querySelector('#open small').textContent = '대화방 열기 · 15초 뒤 자동 닫힘'
}).catch(() => window.messengerNotification.dismiss())
document.getElementById('dismiss').addEventListener('click', () => window.messengerNotification.dismiss())
document.getElementById('open').addEventListener('click', () => window.messengerNotification.open())
