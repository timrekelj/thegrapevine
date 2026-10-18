import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

const source = readFileSync(new URL('./telemetry-config.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText;
const { sanitizeProperties } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);
const input = {
  $current_url: 'https://thegrapevine.si/login?email=private@example.com#code',
  $set_once: { $initial_referrer: 'https://thegrapevine.si/invite?code=secret' },
  $exception_message: 'private text',
  $exception_list: [{ type: 'TypeError', value: 'private message', stacktrace: { frames: [{ function: 'send' }] } }],
};
const result = sanitizeProperties(input);
assert.equal(result.$current_url, 'https://thegrapevine.si/login');
assert.equal(result.$set_once.$initial_referrer, 'https://thegrapevine.si/invite');
assert.equal(result.$exception_message, undefined);
assert.equal(result.$exception_list[0].value, 'Redacted');
assert.equal(result.$exception_list[0].stacktrace.frames[0].function, 'send');
assert.equal(input.$exception_list[0].value, 'private message');
console.log('Telemetry privacy checks passed.');
