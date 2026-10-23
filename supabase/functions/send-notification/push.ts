export const preferenceFor = {
  new_story: 'new_stories',
  direct_reply: 'direct_replies',
  mention: 'mentions',
} as const;

// Receipts for the member's own link, filed after submit-source responded. They answer
// something the member just did, so paper preferences and mute do not apply.
export const receipts = ['source_filed', 'source_failed'] as const;

export type PushKind = keyof typeof preferenceFor | typeof receipts[number];

export function isReceipt(kind: string): kind is typeof receipts[number] {
  return (receipts as readonly string[]).includes(kind);
}

export function isPushKind(kind: string): kind is PushKind {
  return Object.hasOwn(preferenceFor, kind) || isReceipt(kind);
}

export function pushPayload(notification: {
  id: string; user_id: string; story_id: string | null; kind: PushKind;
}) {
  const messages = {
    new_story: 'A new story is ready in your paper.',
    direct_reply: 'Someone replied to your message.',
    mention: 'You were mentioned in a conversation.',
    source_filed: 'Your link is on the paper.',
    source_failed: notification.story_id
      ? 'Your link was saved, but it could not be summarized. Open it to report and retry.'
      : 'Your link could not be shared. Please try again.',
  };
  return {
    app_id: '70ec7f59-3568-4f44-951e-2858742a02b8',
    include_aliases: { external_id: [notification.user_id.toLowerCase()] },
    target_channel: 'push',
    headings: { en: 'The Grapevine' },
    contents: { en: messages[notification.kind] },
    // Without a story, tapping just opens the app.
    ...notification.story_id ? { url: `https://thegrapevine.si/story/${notification.story_id}` } : {},
    idempotency_key: notification.id,
    ttl: 86400,
  };
}
