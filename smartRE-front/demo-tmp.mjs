// Drives a real browser session through property management, capturing each screen.
import { chromium } from 'playwright';

const BASE = 'http://localhost:3000';
const OUT  = process.argv[2];
const EMAIL = 'landlord@smartre.demo';
const PASS  = 'Passw0rd!seed';

const shot = async (page, name) => {
  await page.waitForTimeout(1800);
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: true });
  console.log(`  captured ${name}  (${page.url()})`);
};

const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
page.on('console', m => { if (m.type() === 'error') console.log('  console error:', m.text().slice(0, 120)); });

try {
  console.log('login');
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await shot(page, '01-login');

  await page.fill('input[type="email"], input[name="email"]', EMAIL);
  await page.fill('input[type="password"], input[name="password"]', PASS);
  await page.click('button[type="submit"]');
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(2500);
  await shot(page, '02-after-login');

  for (const [name, path] of [
    ['03-portfolio-units', '/portfolio'],
  ]) {
    console.log(name);
    await page.goto(`${BASE}${path}`, { waitUntil: 'networkidle' }).catch(e => console.log('  nav issue:', e.message.slice(0, 80)));
    await shot(page, name);
  }
  for (const [name, tab] of [
    ['04-tenants',     'Tenants'],
    ['05-leases',      'Leases'],
    ['06-rent',        'Rent'],
    ['07-maintenance', 'Maintenance'],
  ]) {
    console.log(name);
    await page.goto(`${BASE}/portfolio`, { waitUntil: 'networkidle' }).catch(() => {});
    await page.getByRole('tab', { name: tab }).or(page.getByText(tab, { exact: true })).first().click().catch(e => console.log('  tab click:', e.message.slice(0, 60)));
    await shot(page, name);
  }
} catch (e) {
  console.log('FAILED:', e.message.slice(0, 200));
} finally {
  await browser.close();
}
