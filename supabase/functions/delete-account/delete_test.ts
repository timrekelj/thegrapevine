import { HttpError } from '../_shared/cors.ts';
import { handleDelete, listMedia, removeAll, type DeleteDeps } from './delete.ts';

function assert(value: unknown, message?: string) { if (!value) throw new Error(message ?? 'Assertion failed'); }

const USER = 'aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa';

function post(body?: string) {
  return new Request('https://example.com/delete-account', { method: 'POST', body });
}

function deps(overrides: Partial<DeleteDeps> = {}): DeleteDeps & { deleted: string[]; removed: string[][] } {
  const state = { deleted: [] as string[], removed: [] as string[][] };
  return {
    ...state,
    caller: async () => ({ id: USER }),
    mediaNames: async () => [],
    removeMedia: async paths => { state.removed.push(paths); },
    deleteUser: async id => { state.deleted.push(id); },
    ...overrides,
  };
}

Deno.test('only POST from the caller deletes that user', async () => {
  assert((await handleDelete(new Request('https://example.com', { method: 'OPTIONS' }), deps())).status === 200);
  assert((await handleDelete(new Request('https://example.com', { method: 'GET' }), deps())).status === 405);

  const state = deps();
  const response = await handleDelete(post(JSON.stringify({ user_id: 'someone-else' })), state);
  assert(response.status === 200);
  assert((await response.json()).deleted === true);
  assert(state.deleted.length === 1 && state.deleted[0] === USER);
});

Deno.test('a failed media listing deletes nothing', async () => {
  const state = deps({
    mediaNames: async () => { throw new Error('db down'); },
  });
  const response = await handleDelete(post(), state);
  assert(response.status === 500);
  assert((await response.json()).error.includes('Nothing was removed'));
  assert(state.deleted.length === 0 && state.removed.length === 0);
});

Deno.test('an auth failure deletes nothing', async () => {
  const state = deps({
    caller: async () => { throw new HttpError(401, 'Sign in first'); },
  });
  const response = await handleDelete(post(), state);
  assert(response.status === 401);
  assert(state.deleted.length === 0 && state.removed.length === 0);
});

Deno.test('storage cleanup runs only after the auth user is gone and keeps late uploads', async () => {
  let listed = 0;
  let deleted = false;
  const state = deps({
    mediaNames: async () => {
      listed += 1;
      return listed === 1 ? ['group/user/early.jpg'] : ['group/user/early.jpg', 'group/user/late.jpg'];
    },
    deleteUser: async id => { deleted = true; state.deleted.push(id); },
    removeMedia: async paths => {
      assert(deleted, 'media removed before the auth user');
      state.removed.push(paths);
    },
  });
  const response = await handleDelete(post(), state);
  assert(response.status === 200);
  assert(state.removed.length === 1);
  assert(state.removed[0].includes('group/user/early.jpg') && state.removed[0].includes('group/user/late.jpg'));
});

Deno.test('storage cleanup failure still reports the account deleted', async () => {
  const state = deps({
    mediaNames: async () => ['group/user/a.jpg'],
    removeMedia: async () => { throw new Error('storage down'); },
  });
  const response = await handleDelete(post(), state);
  assert(response.status === 200);
  assert((await response.json()).deleted === true);
  assert(state.deleted.length === 1);
});

Deno.test('media pages stop on a short page and refuse a stuck cursor', async () => {
  let calls = 0;
  const paths = await listMedia(USER, async (_userId, after) => {
    calls += 1;
    if (calls === 1) return Array.from({ length: 1000 }, (_, index) => `g/u/${String(index).padStart(4, '0')}`);
    assert(after === 'g/u/0999');
    return ['g/u/1000'];
  });
  assert(paths.length === 1001 && calls === 2);

  let exploded = false;
  try {
    await listMedia(USER, async () => Array.from({ length: 1000 }, () => 'g/u/same'));
  } catch { exploded = true; }
  assert(exploded);
});

Deno.test('removal is chunked', async () => {
  const chunks: string[][] = [];
  const paths = Array.from({ length: 250 }, (_, index) => `g/u/${index}`);
  await removeAll(paths, async chunk => { chunks.push(chunk); });
  assert(chunks.length === 3);
  assert(chunks[0].length === 100 && chunks[1].length === 100 && chunks[2].length === 50);
});
