import { chromium } from 'playwright';
const OUT = process.argv[2];
const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
try {
  await page.goto('http://localhost:3000/login', { waitUntil: 'networkidle' });
  await page.fill('input[type="email"]', 'landlord@smartre.demo');
  await page.fill('input[type="password"]', 'Passw0rd!seed');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(4000);
  await page.goto('http://localhost:3000/portfolio?tab=properties', { waitUntil: 'networkidle' });
  await page.waitForTimeout(3000);
  await page.screenshot({ path: `${OUT}/properties.png`, fullPage: true });
  console.log('captured', page.url());
} catch (e) { console.log('FAILED:', e.message.slice(0,150)); }
finally { await browser.close(); }
