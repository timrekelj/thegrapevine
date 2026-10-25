import type { CSSProperties } from 'react'
import { memberColors, memberIndex, memberLights } from '@/lib/identity'

export default function Avatar({ seed, name, order }: { seed: string; name?: string; order?: readonly string[] }) {
  const index = memberIndex(seed, order)
  const style = { '--member-fill': `radial-gradient(circle at 30% 25%, ${memberLights[index]}, ${memberColors[index]} 70%)` } as CSSProperties
  return <span className="av" style={style} title={name} aria-hidden="true">{name?.trim().slice(0, 1).toLocaleUpperCase() || '·'}</span>
}
