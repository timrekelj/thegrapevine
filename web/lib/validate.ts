// Only web links and same-site paths may become an href or src: shared links, previews and
// attachments are member data and could carry javascript:, data: or protocol-relative URLs.
export const safeUrl = (url: string | null | undefined) =>
  url && (/^https?:\/\//i.test(url) || /^\/(?![/\\])/.test(url)) ? url : undefined

export const isUuid = (value: unknown): value is string =>
  typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
