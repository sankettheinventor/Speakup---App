import { defineCollection, z } from 'astro:content';

// Blog posts live in src/content/blog/*.md and are edited via the CMS at /admin
const blog = defineCollection({
  type: 'content',
  schema: z.object({
    title: z.string(),
    description: z.string(),
    date: z.coerce.date(),
    category: z.string().default('Guides'),
    emoji: z.string().default('📄'),
    tool: z.string().optional(),       // related tool URL for the in-article CTA
    toolName: z.string().optional(),
    draft: z.boolean().default(false),
  }),
});

export const collections = { blog };
