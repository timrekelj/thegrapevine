import { HttpError, json } from '../_shared/cors.ts';

// Storage remove accepts a modest batch. The list RPC pages at 1000 names.
const MEDIA_CHUNK = 100;
const PAGE_SIZE = 1000;
const MAX_PAGES = 50;

export type DeleteDeps = {
  caller: (req: Request) => Promise<{ id: string }>;
  mediaNames: (userId: string, after: string) => Promise<string[]>;
  removeMedia: (paths: string[]) => Promise<void>;
  deleteUser: (userId: string) => Promise<void>;
};

/** Every object whose uploader segment is this user, paging until a short page. */
export async function listMedia(userId: string, mediaNames: DeleteDeps['mediaNames']) {
  const paths: string[] = [];
  let after = '';
  for (let page = 0; page < MAX_PAGES; page++) {
    const names = await mediaNames(userId, after);
    if (!Array.isArray(names) || names.some(name => typeof name !== 'string' || name.length === 0)) {
      throw new Error('media list');
    }
    paths.push(...names);
    if (names.length < PAGE_SIZE) return paths;
    const next = names[names.length - 1];
    if (next <= after) throw new Error('media list');
    after = next;
  }
  throw new Error('media list');
}

export async function removeAll(paths: string[], removeMedia: DeleteDeps['removeMedia']) {
  for (let index = 0; index < paths.length; index += MEDIA_CHUNK) {
    await removeMedia(paths.slice(index, index + MEDIA_CHUNK));
  }
}

/**
 * POST with the caller's JWT and no body. Deletes that auth user (relational
 * rows follow the existing foreign keys) and then their media files. A failure
 * before the auth user is deleted removes nothing. Storage cleanup after a
 * successful delete is best-effort: the account is already gone, so the client
 * cannot retry as that user.
 */
export async function handleDelete(req: Request, deps: DeleteDeps) {
  if (req.method === 'OPTIONS') return json({});
  if (req.method !== 'POST') return json({ error: 'POST required' }, 405);
  try {
    const user = await deps.caller(req);
    const before = await listMedia(user.id, deps.mediaNames);
    await deps.deleteUser(user.id);
    let paths = before;
    try {
      const after = await listMedia(user.id, deps.mediaNames);
      paths = [...new Set([...before, ...after])];
    } catch (error) {
      console.error('delete-account media rescan failed', error instanceof Error ? error.message : 'storage error');
    }
    try {
      await removeAll(paths, deps.removeMedia);
    } catch (error) {
      console.error('delete-account storage cleanup failed', error instanceof Error ? error.message : 'storage error');
    }
    return json({ deleted: true });
  } catch (error) {
    if (error instanceof HttpError) return json({ error: error.message }, error.status);
    console.error('delete-account failed', error instanceof Error ? error.message : 'error');
    return json({ error: 'Your account could not be deleted. Nothing was removed. Please try again.' }, 500);
  }
}
