import InkDots from '@/components/print/InkDots'
import Brand from '@/components/print/Brand'
import { Line, Lines } from '@/components/print/Proof'
import './story.css'

/** A story while it loads: the title, the catch-up card, the sources and the conversation as plain shapes. */
export default function Loading() {
  return <div className="story-page" aria-busy="true">
    <header className="story-top"><Brand /><InkDots label="Loading the story" size="sm" /></header>
    <main className="story-layout galley">
      <article className="story-article">
        <div className="story-kicker"><Line w="96px" /></div>
        <div style={{ display: 'grid', gap: 12, margin: '14px 0 22px' }}><Line strong w="96%" h="clamp(30px, 3.6vw, 46px)" /><Line strong w="64%" h="clamp(30px, 3.6vw, 46px)" /></div>
        <div className="story-catch-up" style={{ background: 'var(--tint)' }}><Lines n={4} className="story-summary" /></div>
      </article>
      <aside className="story-clippings" aria-hidden="true">
        <div className="story-clipping-list">{[0, 1, 2].map(i => <div key={i} className="story-clipping">
          <span className="proof-av" />
          <div style={{ display: 'grid', gap: 8 }}><Line w="48%" strong /><Line h={12} w="80%" /></div>
        </div>)}</div>
      </aside>
      <section className="story-conversation" aria-hidden="true">
        {['88%', '54%', '72%'].map((last, i) => <div key={i} className="story-proof-entry">
          <span className="proof-av" />
          <Lines n={i === 1 ? 1 : 2} last={last} />
        </div>)}
      </section>
    </main>
  </div>
}
