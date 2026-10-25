// Renders the Notetaker's camera cards (Recall's automatic_video_output: 1280x720 JPEG) from bot-card.svg.
// Run from anywhere with the pre-installed Playwright: node ops/notetaker-bot-card/render.cjs
const { chromium } = require('playwright');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');

const here = __dirname;
const out = join(here, '../../apps/api/src/main/resources/notetaker');
const template = readFileSync(join(here, 'bot-card.svg'), 'utf8');

const cards = [
  { file: 'bot-recording.jpg', status: 'Recording', color: '#f2626b', tint: 'rgba(242,98,107,0.12)', width: 280 },
  { file: 'bot-not-recording.jpg', status: 'Not recording yet', color: '#a6adbb', tint: 'rgba(166,173,187,0.10)', width: 380 },
];

(async () => {
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
for (const card of cards) {
  const svg = template
    .replaceAll('{{STATUS}}', card.status)
    .replaceAll('{{PILL_COLOR}}', card.color)
    .replaceAll('{{PILL_TINT}}', card.tint)
    .replaceAll('{{PILL_WIDTH}}', String(card.width))
    .replaceAll('{{PILL_HALF}}', String(card.width / 2))
    .replaceAll('{{DOT_X}}', String(card.width / 2 - 40));
  await page.setContent(`<html><body style="margin:0;background:#08090b">${svg}</body></html>`);
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: join(out, card.file), type: 'jpeg', quality: 88 });
}
await browser.close();
})();
