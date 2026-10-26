import { readdir, readFile } from 'node:fs/promises';
import { extname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const siteDirectory = fileURLToPath(new URL('../', import.meta.url));
const repositoryDirectory = fileURLToPath(new URL('../../', import.meta.url));
const excludedDirectories = new Set(['.astro', '.git', 'build', 'coverage', 'dist', 'node_modules']);
const generatedApiDirectory = join(siteDirectory, 'public', 'api');
const sourceExtensions = new Set([
  '.astro', '.cjs', '.css', '.cts', '.html', '.js', '.json', '.jsx',
  '.md', '.mdx', '.mjs', '.mts', '.svg', '.ts', '.tsx', '.txt', '.yaml', '.yml',
]);
let violations = 0;

async function checkDirectory(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  entries.sort((left, right) => left.name.localeCompare(right.name));

  for (const entry of entries) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      if (!excludedDirectories.has(entry.name) && path !== generatedApiDirectory) {
        await checkDirectory(path);
      }
    } else if (entry.isFile() && sourceExtensions.has(extname(entry.name)) && entry.name !== 'package-lock.json') {
      const content = await readFile(path, 'utf8');
      for (const [lineIndex, line] of content.split(/\r\n?|\n/u).entries()) {
        for (const match of line.matchAll(/\u2014/gu)) {
          console.error(
            `${relative(repositoryDirectory, path)}:${lineIndex + 1}:${match.index + 1}: ` +
            'Em dashes (U+2014) are not allowed. Use a hyphen or rewrite the sentence.',
          );
          violations += 1;
        }
      }
    }
  }
}

await checkDirectory(siteDirectory);
if (violations > 0) {
  process.exitCode = 1;
}
