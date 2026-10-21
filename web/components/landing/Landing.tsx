import Link from 'next/link'
import styles from './landing.module.css'

// Fictional sample content, printed with the same plates and markers as the real paper.
const sampleMembers = ['L', 'N', 'J', 'M']

function SamplePlate({ src, className }: { src: string; className: string }) {
  return <div className={`riso ${className}`}>
    <div className="plate"><div className="ht">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={src} alt="" />
    </div></div>
  </div>
}

function Specimen() {
  return <figure className={styles.specimen} aria-label="A sample front page">
    <div className={styles.sheet} aria-hidden="true">
      <div className={styles.sheetTop}>
        <span className="label">Thursday 22 October</span>
        <span className={styles.sheetMasthead}>The Supper Club</span>
        <span className={styles.sheetMembers}>{sampleMembers.map(m => <span className="av" key={m}>{m}</span>)}</span>
      </div>
      <div className={styles.sheetSections}><b>Front page</b><span>Weather</span><span>Science</span><span>Sport</span></div>
      <div className={styles.sheetGrid}>
        <div className={styles.sheetLead}>
          <SamplePlate src="/seed/storm.jpg" className={styles.sheetHero} />
          <div className={styles.sheetCategory}><span className="label">Weather</span><span className="stamp rest">+1 source</span></div>
          <p className={styles.sheetHeadline}>Storm closes coastal roads as rivers rise overnight</p>
          <p className={styles.sheetSummary}>Three regional outlets now report the same closures. Crews expect the main bridge to reopen by Friday morning.</p>
          <div className={`meta tnum ${styles.sheetMeta}`}><span><b>4</b> sources</span><span><b>11</b> messages</span><span className="new">3 new</span></div>
        </div>
        <div className={styles.sheetSide}>
          <div className={styles.sheetStory}>
            <div><span className="label">Science</span><p>Reusable booster lands on its fourth flight</p><div className="meta tnum"><b>2</b> sources</div></div>
            <SamplePlate src="/seed/rocket.jpg" className={styles.sheetThumb} />
          </div>
          <div className={styles.sheetStory}>
            <div><span className="label">Sport</span><p>Stadium vote pushed to spring after deadlock</p><div className="meta tnum"><b>3</b> sources</div></div>
            <SamplePlate src="/seed/stadium.jpg" className={styles.sheetThumb} />
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

const principles = [
  { title: 'Private by default', body: 'Only the people in your paper can see its stories, links and conversation. You add friends with an invite link.' },
  { title: 'Summaries from your sources', body: 'Summaries are written from the links your group shared, and every source stays listed with the person who shared it. The opinions come from you and your friends.' },
  { title: 'Spot what’s new', body: 'Pink marks what you haven’t seen yet: new sources and new messages since your last visit.', demo: <span className="meta tnum"><span className="new">2 new sources</span><span className="new">5 new messages</span></span> },
  { title: 'No reshuffling while you read', body: 'The front page stays put while you read. If stories move, a line tells you so, and you choose when to reload.', demo: <span className={styles.later}>↑ Later edition · 2 stories moved</span> },
]

export default function Landing() {
  return <div className={styles.page}>
    <header className={styles.top}>
      <Link href="/" className="masthead">The Grapevine</Link>
      <nav className={styles.nav} aria-label="Main">
        <a href="#how-it-works">How it works</a>
        <Link href="/login" className="btn">Sign in</Link>
      </nav>
    </header>

    <main>
      <section className={styles.hero}>
        <div className={styles.pitch}>
          <p className="label">A private newspaper</p>
          <h1 className="display">A paper between friends.</h1>
          <p className={styles.lede}>Share links with your friends. The Grapevine groups them into stories, summarises each one, and gives every story its own conversation.</p>
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

      <section className={styles.principles}>
        <h2 className={styles.sectionHead}>Made for a small group</h2>
        <dl className={styles.principleList}>
          {principles.map(p => <div key={p.title}>
            <dt className="headline">{p.title}</dt>
            <dd>{p.body}</dd>
            {p.demo && <dd className={styles.demo} aria-hidden="true">{p.demo}</dd>}
          </div>)}
        </dl>
      </section>

      <section className={styles.closing}>
        <h2 className="display">Get the conversation out of the group chat.</h2>
        <p className={styles.lede}>Start a paper, invite a few friends and share the first link. Read it on the web or in the apps for iPhone and Android.</p>
        <Link href="/login" className="btn">Start your paper</Link>
      </section>
    </main>

    <footer className={styles.footer}>
      <span className={styles.footerMasthead}>The Grapevine</span>
      <nav aria-label="Footer">
        <Link href="/login">Sign in</Link>
        <Link href="/terms">Terms of Service</Link>
        <Link href="/privacy">Privacy Policy</Link>
        <a href="mailto:hello@thegrapevine.si">hello@thegrapevine.si</a>
      </nav>
    </footer>
  </div>
}
