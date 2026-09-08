import { chromium } from 'playwright';
const OUT = process.argv[2];
const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 950 } });
try {
  await page.goto('http://localhost:3000/login', { waitUntil: 'networkidle' });
  await page.fill('input[type="email"]', 'mary.wanjiku@example.co.ke');
  await page.fill('input[type="password"]', 'Passw0rd!seed');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(4500);
  await page.goto('http://localhost:3000/my-tenancy', { waitUntil: 'networkidle' });
  await page.waitForTimeout(3500);
  await page.screenshot({ path: `${OUT}/tenant.png`, fullPage: true });
  console.log('captured', page.url());
} catch (e) { console.log('FAILED:', e.message.slice(0,150)); }
finally { await browser.close(); }
