import Link from '@/components/print/PressLink'
import InkDots from '@/components/print/InkDots'
import { Line, Lines, ProofPlate } from '@/components/print/Proof'
import './story.css'

/** A story as a galley proof: the article, its clippings and the conversation, set in pale ink. */
export default function Loading() {
  return <div className="story-page" aria-busy="true">
    <header className="story-top">
      <Link href="/" className="story-back meta">← Front page</Link>
      <Link href="/" className="masthead">The Grapevine</Link>
      <span />
    </header>
    <div className="double-rule" />

    <main className="story-layout galley">
      <article className="story-article">
        <div className="story-kicker"><Line w="72px" strong /><InkDots kicker="Setting the story" size="sm" /></div>
        <div style={{ display: 'grid', gap: 12, margin: '18px 0 22px' }}><Line strong w="96%" h="clamp(30px, 4.2vw, 58px)" /><Line strong w="64%" h="clamp(30px, 4.2vw, 58px)" /></div>
        <div className="story-byline"><Line w="220px" /></div>
        <div className="story-figure"><ProofPlate className="story-hero" /></div>
        <Lines n={5} className="story-summary" />
      </article>

      <aside className="story-clippings" aria-hidden="true">
        <div className="label story-clippings-head">Clippings</div>
        <div className="story-clipping-list">{[0, 1, 2].map(i => <div key={i} className="story-clipping">
          <ProofPlate className="story-clipping-thumb" />
          <div style={{ display: 'grid', gap: 8, alignContent: 'start' }}><Line w="48%" strong /><Line h={14} strong /><Line h={14} w="70%" strong /></div>
        </div>)}</div>
      </aside>

      <section className="story-conversation" aria-hidden="true">
        <div className="story-section-head">The conversation</div>
        {['88%', '54%', '72%'].map((last, i) => <div key={i} className="story-proof-entry">
          <span className="proof-av" />
          <Lines n={i === 1 ? 1 : 2} last={last} />
        </div>)}
      </section>
    </main>
  </div>
}
