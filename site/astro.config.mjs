// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

const isProd = process.env.NODE_ENV === 'production';

// https://astro.build/config
export default defineConfig({
	site: isProd ? 'https://mohamadjaara.github.io' : 'http://localhost:4321',
	base: isProd ? '/Kayan' : '/',
	integrations: [
		starlight({
			title: 'Kayan',
			logo: {
				src: './src/assets/kayan-icon.png',
				alt: 'Kayan',
			},
			favicon: '/favicon.png',
			social: [
				{
					icon: 'github',
					label: 'GitHub',
					href: 'https://github.com/MohamadJaara/Kayan',
				},
			],
			customCss: ['./src/styles/custom.css'],
			sidebar: [
				{
					label: 'Getting Started',
					items: [
						{ label: 'Overview', slug: 'docs/overview' },
						{ label: 'Quick Start', slug: 'docs/quick-start' },
					],
				},
				{
					label: 'Configuration',
					items: [
						{ label: 'Resolution Order', slug: 'docs/resolution-order' },
						{ label: 'JSON Shape', slug: 'docs/json-shape' },
						{ label: 'Gradle Usage', slug: 'docs/gradle-usage' },
						{ label: 'Schema Types', slug: 'docs/schema-types' },
						{ label: 'Schema Export', slug: 'docs/schema-export' },
					],
				},
				{
					label: 'Guides',
					items: [
						{ label: 'BuildConfig Migration', slug: 'docs/buildconfig-migration' },
						{ label: 'White-Label Setup', slug: 'docs/white-label' },
					],
				},
				{
					label: 'Reference',
					items: [
						{ label: 'Validation', slug: 'docs/validation' },
						{ label: 'Commands', slug: 'docs/commands' },
					],
				},
			],
		}),
	],
});
