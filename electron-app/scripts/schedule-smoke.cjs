/** UI contract test against deterministic API fixtures; backend persistence is covered separately. */
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')
const { createServer } = require('node:http')
const { readFile } = require('node:fs/promises')
const { join } = require('node:path')
const assert = require('node:assert/strict')
const root = join(__dirname, '../out/web')
const server = createServer(async (req,res) => {
  try {
    const file = req.url.split('?')[0] === '/' ? '/index.html' : req.url.split('?')[0]
    res.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : 'text/html')
    res.end(await readFile(join(root,file)))
  } catch { res.writeHead(404); res.end() }
})
const people = [
  { id:1,userId:'alice',nickname:'김상우',department:'개발팀',userGroup:'매니저',status:'ONLINE',profileImageUrl:null },
  { id:2,userId:'bob',nickname:'박지은',department:'기획팀',userGroup:'팀원',status:'ONLINE',profileImageUrl:null },
  { id:3,userId:'carol',nickname:'이도현',department:'디자인팀',userGroup:'팀원',status:'AWAY',profileImageUrl:null }
]
const room = { id:1,name:'서비스 기획팀',type:'GROUP',members:['alice','bob','carol'],unreadCount:0,ownerUserId:'alice',lastMessageAt:new Date().toISOString() }
let schedules=[], reminders=[], lastInput, saved=0, acked=0, snoozed=0
async function run() {
 await new Promise(resolve => server.listen(0,'127.0.0.1',resolve))
 const browser = await chromium.launch({headless:true, ...(process.env.CHROME_PATH ? {executablePath:process.env.CHROME_PATH} : {})})
 try {
  const page=await browser.newPage({viewport:{width:1440,height:920}})
  const errors=[]; page.on('pageerror',error => errors.push(error.message))
  await page.route('**/api/v1/**',async route => {
   const req=route.request(), url=new URL(req.url()), path=url.pathname, method=req.method()
   let data=null
   if(path.endsWith('/auth/refresh')) data={accessToken:'test',expiresIn:900,user:people[0]}
   else if(path.endsWith('/users')) data=people
   else if(path.endsWith('/organizations')) data={departments:[],users:people,syncedAt:new Date().toISOString(),stale:false}
   else if(path.endsWith('/channels')) data=method==='POST'?{...room,id:2,name:null,type:'DM',members:['alice','bob']}:[room]
   else if(path.endsWith('/notifications')) data={items:[],page:0,size:20,totalElements:0,unreadCount:0}
   else if(path.endsWith('/notices')) data={items:[],page:0,size:20,totalElements:0}
   else if(path.endsWith('/messages')) data={items:[{id:1,channelId:1,senderUserId:'bob',content:'안녕하세요! 다음 회의 일정과 장소를 확인해 주세요.',type:'TEXT',createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),deleted:false,attachment:null,reactions:[],readUserIds:[],unreadCount:0,deletable:false},{id:2,channelId:1,senderUserId:'alice',content:'네, 7층 중 회의실로 등록하겠습니다.',type:'TEXT',createdAt:new Date().toISOString(),updatedAt:new Date().toISOString(),deleted:false,attachment:null,reactions:[],readUserIds:[],unreadCount:2,deletable:true}],hasNext:false,nextCursor:null}
   else if(path.endsWith('/schedule-rooms')) data=['7층 중 회의실','8층 중 회의실']
   else if(path.endsWith('/schedule-reminders')) data=reminders
   else if(/schedule-reminders\/\d+\/snooze/.test(path)) {snoozed++;reminders=[]}
   else if(/schedule-reminders\/\d+\/ack/.test(path)) {acked++;reminders=[]}
   else if(path.includes('/schedules')) {
    if(method==='POST'||method==='PUT') {
      lastInput=req.postDataJSON();saved++
      const item={...lastInput,id:1,channelId:1,creator:'alice',location:lastInput.hasLocation?lastInput.location:null,revision:saved}
      schedules=[item];data=item
    } else if(method==='DELETE') schedules=[]
    else data=schedules
   }
   await route.fulfill({json:{success:true,data}})
  })
  await page.goto(`http://127.0.0.1:${server.address().port}`)
  await page.getByRole('heading',{name:'함께 일하는 동료'}).waitFor()
  await page.getByLabel('동료 검색').fill('박지은')
  assert.equal(await page.locator('.directory-person').count(),1)
  await page.getByLabel('동료 검색').fill('')
  await page.getByLabel('부서 선택').selectOption('디자인팀')
  assert.equal(await page.locator('.directory-person').count(),1)
  await page.getByLabel('부서 선택').selectOption('')
  await page.screenshot({path:'/tmp/messenger-users-applied.png'})
  await page.getByRole('button',{name:'대화',exact:true}).click()
  await page.getByRole('button',{name:'일정',exact:true}).click()
  await page.getByRole('button',{name:'＋ 새 일정 등록'}).click()
  await page.getByLabel('일정 제목').fill('서비스 기획 회의')
  const start=await page.getByLabel('시작 일시').inputValue()
  await page.getByLabel('종료 일시').fill(start)
  await page.getByRole('button',{name:'일정 등록',exact:true}).click()
  await page.getByRole('alert').filter({hasText:'종료 시간'}).waitFor()
  assert.equal(saved,0)
  const end=new Date(new Date(start).getTime()+3600000)
  const local=new Date(end-end.getTimezoneOffset()*60000).toISOString().slice(0,16)
  await page.getByLabel('종료 일시').fill(local)
  await page.getByLabel('장소 있음',{exact:true}).check()
  await page.getByLabel('미팅 장소').selectOption('7층 중 회의실')
  await page.screenshot({path:'/tmp/messenger-schedule-form-applied.png'})
  await page.getByRole('button',{name:'일정 등록',exact:true}).click()
  await page.locator('.schedule-card h4').waitFor()
  assert.equal(lastInput.location,'7층 중 회의실');assert(lastInput.endAt>lastInput.startAt)
  assert.equal(await page.locator('.messenger-message:not(.is-own) .messenger-bubble').evaluate(el=>getComputedStyle(el).color),'rgb(0, 0, 0)')
  assert.equal(await page.locator('.messenger-brand h1').evaluate(el=>getComputedStyle(el).fontSize),'15px')
  await page.screenshot({path:'/tmp/messenger-chat-applied.png'})
  await page.setViewportSize({width:1100,height:720})
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)
  await page.screenshot({path:'/tmp/messenger-chat-min-applied.png'})
  await page.getByRole('button',{name:'수정',exact:true}).click()
  await page.getByLabel('장소 없음',{exact:true}).check()
  assert.equal(await page.getByLabel('미팅 장소').count(),0)
  await page.getByRole('button',{name:'변경 저장'}).click()
  await page.locator('.schedule-card').getByText('⌖ 장소 없음').waitFor()
  assert.equal(lastInput.location,null)
  reminders=[{id:5,dueAt:Date.now(),schedule:schedules[0]}]
  await page.getByRole('dialog',{name:'일정 알림',exact:true}).waitFor({timeout:15000})
  await page.getByRole('button',{name:'5분 뒤 다시 알림'}).click()
  await page.getByRole('dialog',{name:'일정 알림',exact:true}).waitFor({state:'hidden'})
  assert.equal(snoozed,1)
  reminders=[{id:5,dueAt:Date.now()+1,schedule:schedules[0]}]
  await page.getByRole('dialog',{name:'일정 알림',exact:true}).waitFor({timeout:15000})
  await page.getByRole('button',{name:'대화방 열기',exact:true}).click()
  await page.getByRole('dialog',{name:'일정 알림',exact:true}).waitFor({state:'hidden'})
  assert.equal(acked,1)
  await page.getByRole('button',{name:'일정 취소',exact:true}).click()
  await page.getByRole('button',{name:'취소 확정',exact:true}).click()
  await page.locator('.schedule-empty').waitFor()
  assert.equal(schedules.length,0)
  assert.deepEqual(errors,[])
  console.log('PASS: directory filters, layout at 1440/1100, schedule validation/create/edit/cancel, popup snooze/open, bubble contrast')
 } finally {await browser.close()}
}
run().catch(error=>{console.error(error);process.exitCode=1}).finally(()=>server.close())
