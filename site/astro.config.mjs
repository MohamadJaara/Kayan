// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

const isProd = process.env.NODE_ENV === 'production';
const apiReferenceLink = '/api/gradle-plugin/';

// https://astro.build/config
export default defineConfig({
	site: isProd ? 'https://mohamadjaara.github.io' : 'http://localhost:4321',
	base: isProd ? '/Kayan' : '/',
	integrations: [
		starlight({
			title: 'Kayan',
			logo: {
				src: './src/assets/kayan-logo-transparent.png',
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
					label: 'Getting started',
					items: [
						{ label: 'Overview', slug: 'docs/overview' },
						{ label: 'Quick start', slug: 'docs/quick-start' },
					],
				},
				{
					label: 'Configuration',
					items: [
						{ label: 'Resolution order', slug: 'docs/resolution-order' },
						{ label: 'Config file shape', slug: 'docs/json-shape' },
						{ label: 'Gradle usage', slug: 'docs/gradle-usage' },
						{ label: 'Target-specific generation', slug: 'docs/target-specific-generation' },
						{ label: 'Build-time config access', slug: 'docs/build-time-config' },
						{ label: 'Schema types', slug: 'docs/schema-types' },
						{ label: 'Custom adapters', slug: 'docs/custom-adapters' },
						{ label: 'Schema export', slug: 'docs/schema-export' },
					],
				},
				{
					label: 'Guides',
					items: [
						{ label: 'BuildConfig migration', slug: 'docs/buildconfig-migration' },
						{ label: 'White-label setup', slug: 'docs/white-label' },
						{ label: 'Multi-module shared config', slug: 'docs/multi-module-shared-config' },
					],
				},
				{
					label: 'Reference',
					items: [
						{ label: 'Validation', slug: 'docs/validation' },
						{ label: 'Security', slug: 'docs/security' },
						{ label: 'Commands', slug: 'docs/commands' },
						{ label: 'API reference', link: apiReferenceLink },
					],
				},
			],
		}),
	],
});
