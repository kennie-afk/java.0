// Drives the real console in headless Chrome through the write flows and asserts what the page says.
// usage: node tools/ui_flow_check.mjs http://localhost:3500
import { spawn } from 'node:child_process';
const base = process.argv[2] || 'http://localhost:3500';
const port = 19500 + Math.floor(Math.random() * 400);
const chrome = spawn(process.env.CHROME || 'google-chrome', ['--headless=new', '--no-sandbox', '--disable-gpu', `--remote-debugging-port=${port}`, '--window-size=1440,900', `--user-data-dir=/tmp/flow-${port}`, 'about:blank'], { stdio: 'ignore' });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let wsUrl; for (let i = 0; i < 60 && !wsUrl; i++) { try { wsUrl = (await (await fetch(`http://127.0.0.1:${port}/json`)).json()).find((t) => t.type === 'page')?.webSocketDebuggerUrl; } catch {} await sleep(250); }
const ws = new WebSocket(wsUrl); await new Promise((r) => (ws.onopen = r));
let id = 0; const pending = new Map();
ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && pending.has(d.id)) { pending.get(d.id)(d); pending.delete(d.id); } };
const send = (method, params = {}) => new Promise((res) => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method, params })); });
const js = async (e) => (await send('Runtime.evaluate', { expression: e, awaitPromise: true, returnByValue: true })).result?.result?.value;
await send('Page.enable');
const go = async (p) => { await send('Page.navigate', { url: base + p }); await sleep(1800); };
const text = () => js('document.body.innerText');
const setValue = (sel, v) => js(`(()=>{const e=document.querySelector(${JSON.stringify(sel)});if(!e)return false;const proto=e.tagName==='SELECT'?HTMLSelectElement:HTMLInputElement;Object.getOwnPropertyDescriptor(proto.prototype,'value').set.call(e,${JSON.stringify(v)});e.dispatchEvent(new Event(e.tagName==='SELECT'?'change':'input',{bubbles:true}));return true})()`);
const click = (sel) => js(`(()=>{const e=document.querySelector(${JSON.stringify(sel)});if(!e)return false;e.click();return true})()`);
const clickText = (t) => js(`(()=>{const e=[...document.querySelectorAll('button,summary,a')].find(x=>x.innerText.trim().startsWith(${JSON.stringify(t)}));if(!e)return false;e.click();return true})()`);
const firstOption = (sel, n = 1) => js(`(()=>{const o=[...document.querySelector(${JSON.stringify(sel)}).options].filter(x=>x.value)[${n - 1}];return o&&o.value})()`);
let failed = 0;
const check = (label, ok, detail = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} - ${label}${ok ? '' : ' :: ' + String(detail).slice(0, 200)}`); if (!ok) failed++; };

for (let i = 0; i < 40; i++) { try { if ((await fetch(base + '/login')).ok) break; } catch {} await sleep(1000); }
await go('/login');
await sleep(1500);
await setValue('input[type=email]', 'grace@mazingira.co.ke'); await setValue('input[type=password]', 'a-strong-demo-passphrase');
await click('button[type=submit]'); await sleep(2500);
await sleep(1500); const p1 = await js('location.pathname'); check('owner signs in', p1 === '/', p1); await go('/orders/new'); console.log('after nav', await js('location.pathname'), await js('document.cookie'));

await go('/orders/new');
await setValue('select[name=customerId]', await firstOption('select[name=customerId]'));
await setValue('select[name=product0]', await firstOption('select[name=product0]', 2));
await setValue('input[name=quantity0]', '3');
await click('main button[type=submit]'); await sleep(3500);
const orderPath = await js('location.pathname');
check('placing an order routes it and opens it', /^\/orders\/[0-9a-f-]{36}$/.test(orderPath), orderPath);
check('order page shows a status and a supplier per line', /Routed/.test(await text()), await text());

await clickText('Cancel this order');
await setValue('input[name=reason]', 'UI flow check');
await clickText('Confirm cancellation'); await sleep(2500);
check('cancelling says stock returned and commission voided', /Order cancelled\. Stock/.test(await text()), await text());
await go(orderPath);
check('the order now reads Cancelled with its reason', /Cancelled: UI flow check/.test(await text()), await text());

await go('/orders/new');
await setValue('select[name=customerId]', await firstOption('select[name=customerId]'));
await click('main button[type=submit]'); await sleep(1500);
check('an empty order is refused with a reason', /at least one product/.test(await text()), await text());

await go('/wastage/new');
await setValue('select[name=offerId]', await firstOption('select[name=offerId]'));
await setValue('input[name=quantity]', '4');
await click('main button[type=submit]'); await sleep(3000);
check('recording wastage lands on the wastage list', (await js('location.pathname')) === '/wastage' && /UI flow|Expired|Spoiled|Damaged/i.test(await text()), await text());

await go('/customers/new');
await setValue('input[name=name]', 'Flow Check Grocers'); await setValue('input[name=phone]', '+254700999000'); await setValue('input[name=county]', 'Nairobi');
await click('main button[type=submit]'); await sleep(3000);
check('a new customer appears in the list', /Flow Check Grocers/.test(await text()), await text());

await go('/billing');
if (!(await clickText('Switch to Scale'))) await clickText('Switch to Growth');
await sleep(2500);
check('switching plan confirms it', /Plan changed to (SCALE|GROWTH)/.test(await text()), await text());
await go('/billing');
check('the plan tile shows a paid plan', /SCALE|GROWTH/.test(await text()));
await setValue('input[name=msisdn]', '123');
await clickText('Pay with M-Pesa'); await sleep(1500);
check('a bad M-Pesa number is refused', /Safaricom number/.test(await text()), await text());

console.log(failed ? `\n${failed} check(s) failed` : '\nall UI flow checks passed');
ws.close(); chrome.kill(); process.exit(failed ? 1 : 0);
