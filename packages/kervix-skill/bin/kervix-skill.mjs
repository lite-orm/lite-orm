#!/usr/bin/env node
import { mkdtemp, mkdir, rm, cp, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

const args = process.argv.slice(2);
if (args.includes('--help') || args.length === 0) {
  console.log('Usage: npx @kervix/skill install <codex|claude|cursor> [--version v1.0.0]');
  process.exit(args.length === 0 ? 1 : 0);
}
if (args[0] !== 'install' || !['codex', 'claude', 'cursor'].includes(args[1])) {
  console.error('Choose one target: codex, claude, or cursor.');
  process.exit(2);
}

const target = args[1];
const versionIndex = args.indexOf('--version');
const version = versionIndex >= 0 ? args[versionIndex + 1] : 'latest';
const assetUrl = version === 'latest'
  ? 'https://github.com/kervix/kervix/releases/latest/download/kervix-skill.tar.gz'
  : `https://github.com/kervix/kervix/releases/download/${version}/kervix-skill.tar.gz`;
const home = process.env.HOME ?? process.env.USERPROFILE;
if (!home) throw new Error('Cannot determine the user home directory.');
const destination = target === 'codex'
  ? join(process.env.CODEX_HOME ?? join(home, '.codex'), 'skills', 'kervix')
  : target === 'claude' ? join(home, '.claude', 'skills', 'kervix') : join(process.cwd(), '.cursor', 'skills', 'kervix');

const work = await mkdtemp(join(tmpdir(), 'kervix-skill-'));
try {
  const response = await fetch(assetUrl);
  if (!response.ok) throw new Error(`Download failed (${response.status}): ${assetUrl}`);
  const archive = join(work, 'kervix-skill.tar.gz');
  await writeFile(archive, Buffer.from(await response.arrayBuffer()));
  const extracted = spawnSync('tar', ['-xzf', archive, '-C', work], {stdio: 'inherit'});
  if (extracted.status !== 0) throw new Error('tar extraction failed.');
  await mkdir(join(destination, '..'), {recursive: true});
  await rm(destination, {recursive: true, force: true});
  await cp(join(work, 'kervix-skill'), destination, {recursive: true});
  console.log(`Installed Kervix skill to ${destination}`);
} finally {
  await rm(work, {recursive: true, force: true});
}
