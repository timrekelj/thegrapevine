export const preferenceFor = {
  new_story: 'new_stories',
  direct_reply: 'direct_replies',
  mention: 'mentions',
} as const;

export type PushKind = keyof typeof preferenceFor;

export function pushPayload(notification: {
  id: string; user_id: string; story_id: string; kind: PushKind;
}) {
  const messages = {
    new_story: 'A new story is ready in your paper.',
    direct_reply: 'Someone replied to your message.',
    mention: 'You were mentioned in a conversation.',
  };
  return {
    app_id: '70ec7f59-3568-4f44-951e-2858742a02b8',
    include_aliases: { external_id: [notification.user_id.toLowerCase()] },
    target_channel: 'push',
    headings: { en: 'The Grapevine' },
    contents: { en: messages[notification.kind] },
    url: `https://thegrapevine.si/story/${notification.story_id}`,
    idempotency_key: notification.id,
    ttl: 86400,
  };
}
