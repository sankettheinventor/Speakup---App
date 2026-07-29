import { getCollection } from 'astro:content';

export const catSlug = (c: string) =>
  c.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');

// Brand-tinted cover gradient per category (kept in one place)
const GRAD: Record<string, string> = {
  'Image Tools': 'linear-gradient(135deg,#6b4dff,#ff5e7e)',
  'Finance': 'linear-gradient(135deg,#0ea5e9,#6b4dff)',
  'Web Design': 'linear-gradient(135deg,#15a06b,#0ea5e9)',
  'Color': 'linear-gradient(135deg,#f59e0b,#ff5e7e)',
  'Text': 'linear-gradient(135deg,#8b5cf6,#0ea5e9)',
  'Guides': 'linear-gradient(135deg,#f59e0b,#ff5e7e)',
};
export const cover = (c: string) => `background:${GRAD[c] || GRAD['Guides']}`;

export async function getCategories() {
  const posts = await getCollection('blog', ({ data }) => !data.draft);
  return Array.from(new Set(posts.map((p) => p.data.category)));
}
