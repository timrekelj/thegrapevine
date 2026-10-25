import Link from '@/components/print/PressLink'
import Plate from '@/components/print/Plate'
import Avatar from '@/components/print/Avatar'
import Bunch from '@/components/print/Bunch'
import styles from './landing.module.css'

const sources = ['Luka', 'Nika', 'Tim', 'Maja'].map(name => ({ id: name, submitted_by: name }))

export default function Landing() {
  return <div className={styles.page}>
    <header className={styles.header}><Link href="/" className="masthead">The Grapevine</Link><nav aria-label="Main"><a href="#how-it-works">How it works</a><Link href="/login" className="btn ghost">Sign in</Link></nav></header>
    <main>
      <section className={styles.hero}>
        <div className={styles.pitch}><Bunch sources={sources} fresh={1} size={88} /><h1>Links from friends,<br />grown into stories.</h1><p>Share what caught your eye. Read the story your links tell together, then talk it over with your people.</p><Link href="/login" className="btn">Start your paper</Link><span className="meta">Private groups · Web, iPhone and Android</span></div>
        <figure className={styles.specimen} aria-label="A sample paper"><div className={styles.sampleHeader}><b>The Supper Club</b><div>{['Luka', 'Nika', 'Jure', 'Maja'].map(name => <Avatar key={name} seed={name} name={name} />)}</div></div><p className={styles.new}>3 new since your last visit</p><article className={styles.sampleCard}><Plate src="/seed/rocket.jpg" label="Sample rocket launch" className={styles.samplePhoto} eager /><div className={styles.sampleBody}><span className="meta">Space · Nika, 20:29</span><h2>Reusable booster lands at sea on its second try</h2><div className={styles.sourceCount}><Bunch sources={sources} fresh={1} size={40} /><span>4 sources, one new<br />5 messages</span></div><div className={styles.sampleReply}><Avatar seed="Nika" name="Nika" /><span><b>Nika</b> This is the actual news.</span></div></div></article><figcaption className="meta">Sample paper with fictional stories and people.</figcaption></figure>
      </section>
      <section id="how-it-works" className={styles.how}><h2>A paper you make together.</h2><div>{[
        ['Share a link', 'An article or an X post, with a line about why it caught your eye.'],
        ['Read the catch-up', 'Links about the same event become one story. Its sources stay a tap away.'],
        ['Tell the others', 'Each story has its own conversation, with replies, reactions, photos and GIFs.'],
      ].map(([title, body], index) => <article key={title}><span className={styles.step}>{index + 1}</span><h3>{title}</h3><p>{body}</p></article>)}</div></section>
      <section className={styles.invite}><h2>Your people.<br />Your paper.</h2><p>Only members can read your paper. Invite a few friends and share the first link.</p><Link href="/login" className="btn">Start your paper</Link></section>
    </main>
    <footer className={styles.footer}><span className="masthead">The Grapevine</span><nav aria-label="Footer"><Link href="/terms">Terms</Link><Link href="/privacy">Privacy</Link><a href="mailto:hello@thegrapevine.si">Get in touch</a></nav></footer>
  </div>
}
