/** A member's portrait, drawn by /avatar from a stable seed. Decorative: the name is always printed or labelled nearby. */
export default function Avatar({ seed, name }: { seed: string; name?: string }) {
  return <span className="av" title={name}>
    {/* An SVG from our own route; next/image adds nothing here. */}
    {/* eslint-disable-next-line @next/next/no-img-element */}
    <img src={`/avatar/${encodeURIComponent(seed)}`} alt="" width={64} height={64} loading="lazy" decoding="async" />
  </span>
}
