import assert from 'node:assert/strict';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { ESLint } from 'eslint';

const eslint = new ESLint({ cwd: fileURLToPath(new URL('../', import.meta.url)) });
const emDash = '\u2014';

for (const [filePath, code] of [
  ['src/probe.ts', `// ${emDash}\nexport const message: string = "${emDash}${emDash}";`],
  ['src/probe.astro', `---\nconst message: string = "${emDash}";\n---\n<p>{message} ${emDash}</p><!-- ${emDash} -->`],
  ['src/content/docs/probe.md', `---\ntitle: Probe ${emDash}\n---\nText ${emDash} more ${emDash} text.\n`],
  ['src/content/docs/probe.mdx', `import Example from './Example.astro';\n\n<Example title="${emDash}" />\n\nText ${emDash} more ${emDash} text.\n`],
]) {
  test(`reports em dashes through ESLint in ${filePath}`, async () => {
    const [result] = await eslint.lintText(code, { filePath });
    assert.equal(result.fatalErrorCount, 0);
    assert.equal(result.errorCount, 3);
    assert.ok(result.messages.every(message => message.ruleId === 'kayan/no-em-dashes'));
    const indexes = [...code.matchAll(/\u2014/gu)].map(match => match.index);
    assert.deepEqual(result.messages.map(message => [message.line, message.column]), indexes.map(index => {
      const prefix = code.slice(0, index).split('\n');
      return [prefix.length, prefix.at(-1).length + 1];
    }));
  });
}

test('allows ordinary hyphens, en dashes, and escaped Unicode', async () => {
  const [result] = await eslint.lintText('// ordinary - and en dash \u2013\nexport const escaped = "\\u2014";', {
    filePath: 'src/probe.ts',
  });
  assert.equal(result.errorCount, 0);
});

test('ignores generated output and dependencies', async () => {
  for (const filePath of ['dist/probe.ts', '.astro/probe.ts', 'node_modules/probe.ts', 'public/api/probe.ts']) {
    assert.equal(await eslint.isPathIgnored(filePath), true);
  }
});

test('enables the recommended JavaScript and TypeScript rules', async () => {
  for (const [filePath, code, ruleId] of [
    ['probe.mjs', 'missingFunction();', 'no-undef'],
    ['src/probe.ts', 'export const value: any = 1;', '@typescript-eslint/no-explicit-any'],
  ]) {
    const [result] = await eslint.lintText(code, { filePath });
    assert.equal(result.fatalErrorCount, 0);
    assert.ok(result.messages.some(message => message.ruleId === ruleId));
  }
});
