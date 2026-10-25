import { memberColors, memberIndex, memberLights } from '@/lib/identity'

export default function Bunch({ sources, fresh = 0, size = 44, order }: {
  sources: { id: string; submitted_by: string | null; created_at?: string }[]; fresh?: number; size?: number; order?: readonly string[]
}) {
  const grapes = [...sources].sort((a,b) => (b.created_at || "").localeCompare(a.created_at || "")).slice(0, 7)
  const positions = [[13, 19], [31, 19], [22, 29], [13, 37], [31, 37], [22, 46], [22, 16]]
  return <svg className="bunch" width={size} height={size * 1.2} viewBox="0 0 44 54" aria-hidden="true">
    <path d="M22 12C21 7 24 3 29 2M22 10C12 2 7 7 7 11C13 14 18 13 22 10Z" fill="var(--red)" stroke="var(--red)" strokeWidth="2" strokeLinecap="round" />
    {grapes.map((source, i) => {
      const index = memberIndex(source.submitted_by || source.id, order)
      const [cx, cy] = positions[i]
      return <g key={source.id}>
        <circle cx={cx} cy={cy} r="8" fill={memberColors[index]} stroke="var(--paper)" strokeWidth="2" />
        <circle cx={cx - 2} cy={cy - 2} r="3" fill={memberLights[index]} opacity=".75" />
        {i < fresh && <circle cx={cx} cy={cy} r="10" fill="none" stroke="var(--red)" strokeWidth="2" />}
      </g>
    })}
  </svg>
}
