// Walks every screen of the demo as each persona, in a real browser, and reports what is wrong.
//
//   node e2e/demo-walk.mjs [outDir]            (BASE defaults to http://localhost:3400)
//
// For every page it records: HTTP failures from the API, console errors, images that did not
// load, text that should never reach a user ("undefined", "NaN", "[object Object]"), pages so
// short they are probably empty, and the page's root font size and largest border radius so the
// house UI rules (12-13px root, small radii) can be checked mechanically. A screenshot of each
// page is saved. Exit status is non-zero if any page has a hard problem.
import { chromium } from 'playwright'
import { mkdirSync, writeFileSync } from 'node:fs'

const BASE = process.env.BASE || 'http://localhost:3400'
const OUT = process.argv[2] || 'demo-walk-out'
const PASSWORD = process.env.SEED_PASSWORD || 'SmartRE-Demo-2026!'
mkdirSync(OUT, { recursive: true })

const PERSONAS = {
  public: { email: null, routes: ['/', '/properties', '/login', '/register', '/forgot-password'] },
  admin: {
    email: 'demo.admin@smartre.test',
    routes: ['/overview', '/users', '/verification-queue', '/manage-listings', '/admin/reviews', '/reports', '/revenue',
      '/notifications', '/profile'],
  },
  landlord: {
    email: 'demo.landlord@smartre.test',
    routes: ['/dashboard', '/portfolio', '/listings', '/properties/new', '/payments', '/viewings', '/reviews',
      '/verification', '/ownership', '/notifications', '/profile'],
  },
  bigLandlord: { email: 'wanjiru.properties@smartre.test', routes: ['/dashboard', '/portfolio', '/payments'] },
  seller: {
    email: 'demo.seller@smartre.test',
    routes: ['/dashboard', '/listings', '/viewings', '/payments', '/reviews', '/ownership', '/verification', '/notifications'],
  },
  buyer: {
    email: 'demo.buyer@smartre.test',
    routes: ['/dashboard', '/viewings', '/payments', '/reviews', '/notifications', '/profile'],
  },
  tenant: { email: 'david.kimani@example.co.ke', routes: ['/dashboard', '/my-tenancy', '/payments', '/notifications', '/profile'] },
}

const BAD_TEXT = /\bundefined\b|\bNaN\b|\[object Object\]|Lorem ipsum|TODO|Application error|Something went wrong/i
const browser = await chromium.launch({ executablePath: process.env.CHROME || '/usr/bin/google-chrome', args: ['--no-sandbox'] })
const summary = []
let hard = 0

async function audit(page, label, path) {
  const issues = []
  const apiFailures = []
  const consoleErrors = []
  page.removeAllListeners('response')
  page.removeAllListeners('console')
  page.on('response', (r) => {
    const u = r.url()
    if (r.status() >= 400 && (u.includes('/api/') || u.startsWith(BASE)) && !u.includes('/_next/image') ) apiFailures.push(`${r.status()} ${new URL(u).pathname}`)
  })
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 140)) })
  const res = await page.goto(BASE + path, { waitUntil: 'domcontentloaded', timeout: 45000 }).catch((e) => ({ status: () => 0, error: e }))
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {})
  // Scroll through the page so anything that animates in on scroll has appeared before the shot.
  await page.evaluate(async () => {
    for (let y = 0; y < document.body.scrollHeight; y += 500) { window.scrollTo(0, y); await new Promise((r) => setTimeout(r, 80)) }
    window.scrollTo(0, 0)
  })
  await page.waitForTimeout(500)
  const status = res?.status?.() ?? 0
  const url = new URL(page.url())
  const info = await page.evaluate(() => {
    const text = document.body.innerText || ''
    const imgs = [...document.images].filter((i) => i.offsetParent !== null)
    const broken = imgs.filter((i) => i.complete && i.naturalWidth === 0).map((i) => i.currentSrc.slice(0, 90))
    let maxRadius = 0
    const radii = {}
    for (const e of document.querySelectorAll('body *')) {
      const cs = getComputedStyle(e)
      const r = parseFloat(cs.borderTopLeftRadius) || 0
      if (r > 0 && r < 500 && e.getBoundingClientRect().width < 900) { radii[r] = (radii[r] || 0) + 1; if (r > maxRadius) maxRadius = r }
    }
    const hoverMotion = [...document.styleSheets].reduce((n, s) => {
      try { for (const rule of s.cssRules) if (rule.selectorText && /:hover/.test(rule.selectorText) && /translate|scale/.test(rule.cssText) && !/scale\(1\.0\d?\)|scale3d\(1\.0/.test(rule.cssText)) n++ } catch {}
      return n
    }, 0)
    return { text, broken, rootPx: getComputedStyle(document.documentElement).fontSize, maxRadius, radii, hoverMotion, title: document.title, images: imgs.length }
  })
  const name = `${label}_${path.replace(/[^a-z0-9]+/gi, '_').replace(/^_|_$/g, '') || 'home'}`
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true }).catch(() => {})
  if (status >= 400 || status === 0) issues.push(`HTTP ${status}`)
  if (BAD_TEXT.test(info.text)) issues.push(`bad text: ${(info.text.match(BAD_TEXT) || [])[0]}`)
  if (info.broken.length) issues.push(`broken images: ${info.broken.length} (${info.broken[0]})`)
  if (apiFailures.length) issues.push(`API failures: ${[...new Set(apiFailures)].slice(0, 4).join(', ')}`)
  if (consoleErrors.length) issues.push(`console: ${[...new Set(consoleErrors)].slice(0, 2).join(' | ')}`)
  const soft = []
  if (info.text.trim().length < 160) soft.push(`very short page (${info.text.trim().length} chars)`)
  if (info.maxRadius > 8) soft.push(`radius up to ${info.maxRadius}px`)
  if (info.hoverMotion > 0) soft.push(`${info.hoverMotion} hover rule(s) that move things`)
  const row = { persona: label, path, landed: url.pathname, status, issues, soft, rootPx: info.rootPx, maxRadius: info.maxRadius }
  summary.push(row)
  if (issues.length) hard++
  console.log(`${issues.length ? '✗' : soft.length ? '~' : '✓'} ${label.padEnd(11)} ${path.padEnd(22)} -> ${url.pathname}  ${[...issues, ...soft].join('; ')}`)
  return info
}

async function login(context, email) {
  const page = await context.newPage()
  await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
  await page.fill('input[type="email"], input[name="email"]', email)
  await page.fill('input[type="password"], input[name="password"]', PASSWORD)
  await Promise.all([
    page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {}),
    page.click('button[type="submit"]'),
  ])
  await page.waitForTimeout(1500)
  return page
}

for (const [label, persona] of Object.entries(PERSONAS)) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = persona.email ? await login(context, persona.email) : await context.newPage()
  if (persona.email && new URL(page.url()).pathname.startsWith('/login')) {
    console.log(`✗ ${label}: could not sign in as ${persona.email}`)
    hard++
    await context.close()
    continue
  }
  const routes = [...persona.routes]
  for (const path of routes) {
    const info = await audit(page, label, path)
    // Follow one detail page per list, so the detail screens are checked too.
    if (label === 'public' && path === '/properties') {
      const href = await page.$$eval('a[href^="/properties/"]', (as) => as.map((a) => a.getAttribute('href')).find((h) => /\/properties\/[0-9a-f-]{36}/.test(h)))
      if (href) {
        await audit(page, label, href)
        const seller = await page.$$eval('a[href^="/sellers/"]', (as) => as.map((a) => a.getAttribute('href'))[0])
        if (seller) await audit(page, label, seller)
      }
    }
    if (path === '/payments') {
      const href = await page.$$eval('a[href^="/payments/"]', (as) => as.map((a) => a.getAttribute('href'))[0])
      if (href) await audit(page, label, href)
    }
  }
  await context.close()
}
await browser.close()
writeFileSync(`${OUT}/summary.json`, JSON.stringify(summary, null, 2))
const soft = summary.filter((r) => r.soft.length).length
console.log(`\n${summary.length} pages walked: ${summary.length - hard} clean of hard problems, ${hard} with hard problems, ${soft} with style/emptiness notes.`)
process.exit(hard ? 1 : 0)
