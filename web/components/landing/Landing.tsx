import Link from 'next/link'
import Plate from '@/components/print/Plate'
import styles from './landing.module.css'

// Fictional sample content, printed with the same plates and markers as the real paper.
const sampleMembers = ['L', 'N', 'J', 'M']

const wire = [
  'Storm closes coastal roads as rivers rise overnight',
  'Reusable booster lands on its fourth flight',
  'Stadium vote pushed to spring after deadlock',
  'Night bus pilot approved for weekends',
  'Data centre heat will warm a thousand flats',
  'Two-person studio’s puzzle game tops the charts',
]

function Specimen() {
  return <figure className={styles.specimen} aria-label="A sample front page">
    <div className={styles.sheet} aria-hidden="true">
      <div className={styles.sheetTop}>
        <span className={styles.sheetEar}>No. 52</span>
        <span className={styles.sheetMasthead}>The Supper Club</span>
        <span className={styles.sheetMembers}>{sampleMembers.map(m => <span className="av" key={m}>{m}</span>)}</span>
      </div>
      <div className={styles.sheetRule} />
      <div className={styles.sheetSections}><b>Front page</b><span>Weather</span><span>Science</span><span>Sport</span></div>
      <div className={styles.sheetGrid}>
        <div className={styles.sheetLead}>
          <div className={styles.sheetCategory}><span className="label">Weather</span><span className="stamp rest">+1 source</span></div>
          <p className={styles.sheetHeadline}>Storm closes coastal roads as rivers rise overnight</p>
          <Plate src="/seed/storm.jpg" label="Delo" className={styles.sheetHero} eager />
          <p className={styles.sheetSummary}>Three regional outlets now report the same closures. Crews expect the main bridge to reopen by Friday morning.</p>
          <div className={`meta tnum ${styles.sheetMeta}`}><span><b>4</b> sources</span><span><b>11</b> messages</span><span className="new">3 new</span></div>
        </div>
        <div className={styles.sheetSide}>
          <div className={styles.sheetStory}>
            <Plate src="/seed/rocket.jpg" label="Ars Technica" className={styles.sheetThumb} eager />
            <span className="label">Science</span><p>Reusable booster lands on its fourth flight</p><div className="meta tnum"><b>2</b> sources</div>
          </div>
          <div className={styles.sheetStory}>
            <span className="label">Sport</span><p>Stadium vote pushed to spring after deadlock</p><div className="meta tnum"><b>3</b> sources</div>
          </div>
        </div>
      </div>
      <div className={styles.sheetTable}>
        <q>Finally a summary that explains why the bridge matters.</q>
        <span className="meta"><b>Nika</b> on Storm closes coastal roads</span>
      </div>
    </div>
    <figcaption className="meta">Sample paper. Stories, people and pictures are made up.</figcaption>
  </figure>
}

const steps = [
  { title: 'Share a link', body: 'Drop in an article or an X post, with a line about why it caught your eye. Do it on the web or in the app.' },
  { title: 'It becomes a story', body: 'Links about the same event are filed together under one headline, with a summary you can read in about a minute. The summary is rewritten when new sources arrive.' },
  { title: 'Talk it over', body: 'Each story has its own conversation. Replies quote the message they answer, and you can react with emoji or a GIF. Nothing gets buried in a thread.' },
]

const notices = [
  { title: 'Private by default', body: 'Only the people in your paper can see its stories, links and conversation. You add friends with an invite link.' },
  { title: 'Summaries from your sources', body: 'Summaries are written from the links your group shared, and every source stays listed with the person who shared it. The opinions come from you and your friends.' },
  { title: 'Spot what’s new', body: 'A red mark shows what you haven’t seen yet: new sources and new messages since your last visit.', demo: <span className="meta tnum"><span className="new">2 new sources</span><span className="new">5 new messages</span></span> },
  { title: 'No reshuffling while you read', body: 'The front page stays put while you read. If stories move, a line tells you so, and you choose when to reload.', demo: <span className={styles.later}>↑ Later edition · 2 stories moved</span> },
]

export default function Landing() {
  const today = new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
  return <div className={styles.page}>
    <div className={styles.strip}>
      <span className="label">Est. 2026 · A private newspaper</span>
      <nav className={styles.nav} aria-label="Main">
        <a href="#how-it-works">How it works</a>
        <Link href="/login" className="btn">Sign in</Link>
      </nav>
    </div>

    <header className={styles.flag}>
      <div className={styles.flagRow}>
        <p className={styles.ear}>“All the links<br />fit to share”</p>
        <Link href="/" className={`nameplate ${styles.nameplate}`}>The Grapevine</Link>
        <p className={`${styles.ear} ${styles.earRight}`}>Private edition<br />For friends only</p>
      </div>
      <div className="double-rule" />
      <div className={styles.dateline}>
        <span>{today}</span>
        <span className={styles.datelineMid}>A newspaper written by your friends’ links</span>
        <span>Web · iPhone · Android</span>
      </div>
      <div className={styles.ticker} aria-hidden="true">
        <span className={styles.tickerLabel}>On the wire</span>
        <div className={styles.tickerWindow}><div className={styles.tickerTrack}>
          {[...wire, ...wire].map((line, i) => <span key={i}>{line}</span>)}
        </div></div>
      </div>
    </header>

    <main>
      <section className={styles.hero}>
        <div className={styles.pitch}>
          <span className="label">Today’s lead</span>
          <h1 className={`display ${styles.headline}`}>A paper between friends.</h1>
          <p className={`column dropcap ${styles.lede}`}>Share links with your friends. The Grapevine groups them into stories, summarises each one, and gives every story its own conversation. It reads like a morning paper, and everything in it was picked by someone you know.</p>
          <div className={styles.actions}>
            <Link href="/login" className="btn">Start your paper</Link>
            <Link href="/login" className={styles.textLink}>I already have an account</Link>
          </div>
          <p className="meta">Sign in with Google, Apple, X or an email code. You don’t need a password.</p>
        </div>
        <Specimen />
      </section>

      <section id="how-it-works" className={styles.how}>
        <h2 className={styles.sectionHead}>How it works</h2>
        <ol className={styles.steps}>
          {steps.map((step, i) => <li key={step.title}>
            <span className={styles.numeral}>{i + 1}</span>
            <h3 className="headline">{step.title}</h3>
            <p>{step.body}</p>
          </li>)}
        </ol>
      </section>

      <section className={styles.notices}>
        <h2 className={styles.sectionHead}>Notices for a small group</h2>
        <dl className={styles.noticeGrid}>
          {notices.map(n => <div key={n.title} className={styles.notice}>
            <dt>{n.title}</dt>
            <dd>{n.body}</dd>
            {n.demo && <dd className={styles.demo} aria-hidden="true">{n.demo}</dd>}
          </div>)}
        </dl>
      </section>

      <section className={styles.closing}>
        <span className="label">Late edition</span>
        <h2 className="display">Get the conversation out of the group chat.</h2>
        <p className={styles.closingLede}>Start a paper, invite a few friends and share the first link. Read it on the web or in the apps for iPhone and Android.</p>
        <Link href="/login" className="btn">Start your paper</Link>
      </section>
    </main>

    <footer className={styles.footer}>
      <span className="masthead">The Grapevine</span>
      <nav aria-label="Footer">
        <Link href="/login">Sign in</Link>
        <Link href="/terms">Terms of Service</Link>
        <Link href="/privacy">Privacy Policy</Link>
        <a href="mailto:hello@thegrapevine.si">hello@thegrapevine.si</a>
      </nav>
    </footer>
  </div>
}
