import { getCollection } from 'astro:content';
import { catSlug } from '../lib/categories';

// Self-generated sitemap for all current pages (incl. blog posts + categories).
export async function GET(context: { site?: URL }) {
  const site = context.site?.href ?? 'https://snapjar.sankettoraskar-business.workers.dev/';
  const posts = await getCollection('blog', ({ data }) => !data.draft);
  const cats = Array.from(new Set(posts.map((p) => p.data.category)));
  const paths = [
    '', 'blog',
    // Scan & read
    'tools/scanner', 'tools/qr-scan', 'tools/text-scan', 'tools/pdf-studio',
    // PDF tools
    'tools/pdf-tools', 'tools/compress-pdf', 'tools/organize-pdf', 'tools/split-pdf',
    'tools/remove-pages', 'tools/extract-pages', 'tools/rotate-pdf',
    'tools/add-page-numbers', 'tools/watermark-pdf',
    // Create & convert
    'tools/jpg-to-pdf', 'tools/pdf-to-jpg', 'tools/text-to-pdf',
    'tools/word-to-pdf', 'tools/pdf-to-word',
    'about', 'privacy-policy', 'terms',
  ];
  const urls = [
    ...paths.map((p) => new URL(p, site).href),
    ...posts.map((p) => new URL(`blog/${p.slug}`, site).href),
    ...cats.map((c) => new URL(`blog/category/${catSlug(c)}`, site).href),
  ];
  const body =
    `<?xml version="1.0" encoding="UTF-8"?>\n` +
    `<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n` +
    urls.map((u) => `  <url><loc>${u}</loc></url>`).join('\n') +
    `\n</urlset>\n`;
  return new Response(body, { headers: { 'Content-Type': 'application/xml' } });
}
