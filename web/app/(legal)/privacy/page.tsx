import type { Metadata } from 'next'
import Link from '@/components/print/PressLink'

export const metadata: Metadata = {
  title: 'Privacy Policy | The Grapevine',
  description: 'How The Grapevine handles account information, private group content, AI processing, and privacy requests.',
}

export default function PrivacyPage() {
  return (
    <>
      <h1>Privacy Policy</h1>
      <p className="meta">Last updated: October 23, 2026</p>
      <p>The Grapevine is a private newspaper curated by friends. This policy explains how personal information is handled across our website and iOS and Android apps. Tim Rekelj operates the service and is the controller of personal information processed for its operation. Contact: <a href="mailto:hello@thegrapevine.si">hello@thegrapevine.si</a>.</p>

      <h2>1. Information we process</h2>
      <ul>
        <li><strong>Account information:</strong> your email address, account identifier, display name, and authentication records. When you choose Google, Apple, or X sign-in, the provider supplies an account identifier and the email or profile information authorized for sign-in. Apple may supply a relay email address. We do not receive your provider password, and signing in does not by itself authorize us to read your social feed or post on your behalf.</li>
        <li><strong>Group information and content:</strong> group names, membership and roles, invitation codes, submitted links and comments, source metadata and excerpts, messages, replies, reactions, uploaded images and videos, generated stories and their history, and correction reports.</li>
        <li><strong>Preferences and notifications:</strong> notification settings, read status, and, on mobile, push subscription identifiers and device information used to deliver notifications.</li>
        <li><strong>Usage and technical information:</strong> actions such as signing in, opening stories, or sending messages; timestamps; app and device characteristics; and diagnostic, crash, network, and security information. Infrastructure and other providers may also process IP addresses and request logs.</li>
        <li><strong>Communications:</strong> information you send us when asking for support, reporting a problem, or exercising a privacy right.</li>
      </ul>

      <h2>2. Why we use it</h2>
      <p>We use this information to authenticate you, provide your group’s newspaper and conversations, organize and summarize sources, deliver requested notifications, respond to support and correction requests, understand feature usage, diagnose errors, prevent abuse, and comply with legal obligations.</p>
      <p>Where data-protection law requires a legal basis, core account and service processing is necessary to perform our agreement with you. Security, abuse prevention, support, and service improvement rely on legitimate interests where permitted, balanced against your rights. We rely on legal obligations where applicable and consent for processing that requires it. You can object to processing based on legitimate interests and withdraw consent where consent is the basis.</p>

      <h2>3. Who can see group content</h2>
      <p>Group content is intended to be accessible to members of that group, rather than a public feed. Members may save or share it outside the app; we cannot control those copies. Treat invitation links and codes as access credentials. Authorized service administration and service providers may process data when needed to operate, support, or secure the service.</p>
      <p>The service is <strong>not end-to-end encrypted</strong>. Private group access does not mean content is visible only to participants at every stage of processing. Please do not submit highly sensitive information or other people’s information without a lawful basis.</p>

      <h2>4. Service providers and AI processing</h2>
      <p>We use the following services for the purposes described below. Data sent to a provider depends on the features you use and whether that integration is enabled.</p>
      <ul>
        <li><strong>Supabase:</strong> account authentication, database records, file storage, and backend functions.</li>
        <li><strong>Vercel:</strong> website hosting and processing web requests.</li>
        <li><strong>Google, Apple, and X:</strong> authentication when you choose that provider. Their own privacy policies also apply to your interaction with their sign-in services.</li>
        <li><strong>Mistral AI:</strong> source titles, publisher information, excerpts, generated story text, and relevant correction notes are processed to generate headlines, summaries, categories, and numerical text representations called embeddings.</li>
        <li><strong>OpenAI:</strong> source text and existing story titles and summaries may be processed to decide whether items describe the same event or contain conflicting information. These inputs can contain personal information present in the source material. Ordinary group chat messages and uploaded media are not sent to these AI services by the current editorial pipeline.</li>
        <li><strong>GitHub:</strong> when you send a bug report or an idea from the app, its text, any screenshots you attach, and app and device details (platform, app version, operating system, device model, and the error shown, if any) are published as an issue in our public GitHub repository, where anyone can read them. A title and summary of your report are drafted by Mistral AI. Your name and email address are not included. Screenshots are stored at public addresses so the issue can display them, and remain with the issue if you later delete your account.</li>
        <li><strong>PostHog:</strong> product events, account identifiers, device and technical properties, feature-flag evaluations, error reports, and AI performance metadata such as token usage and timing. Our current integration disables session replay and automatic interaction capture. We do not intentionally include group message text, uploaded media, or AI prompt and response content in analytics events.</li>
        <li><strong>OneSignal and Apple/Google push services:</strong> mobile push delivery, using subscription information, account identifiers, generic notification text, and story links. Current notification payloads do not include the text of group messages.</li>
        <li><strong>GIPHY:</strong> GIF search terms when search is enabled, and network information when GIFs are loaded. External image hosts and Google Fonts may also receive network information, such as your IP address, when the website or apps load their resources.</li>
      </ul>
      <p>When you submit a link, our backend requests the linked page to extract source information. When your device loads external content or follows a link, the destination provider may receive network information and handle it under its own privacy policy.</p>
      <p>We do not sell your personal information or use private group content for targeted advertising. Information may also be disclosed where required by law, to address abuse or protect people’s rights and safety, or in a transfer of the service subject to applicable privacy obligations.</p>

      <h2>5. Cookies, local storage, and device permissions</h2>
      <p>The website uses authentication cookies to keep you signed in. Mobile apps store session credentials using platform-protected storage. Our analytics integration can also use browser or device storage to recognize sessions and associate activity with an account. Blocking storage may affect sign-in and other features; a browser’s Do Not Track signal is not currently a built-in opt-out control for the service.</p>
      <p>You can control notification permission in your device settings and notification preferences in the app. You choose which photos or videos to upload through the platform’s media picker. Turning off notifications or uninstalling the app does not delete stored account or group data. Contact us to object to analytics processing or discuss your privacy choices.</p>

      <h2>6. Retention and deletion</h2>
      <p>Account and group information is retained while needed to provide the service. Shared stories, discussions, and story history are not automatically removed merely because you sign out, uninstall the app, or stop participating. Diagnostic and security records, support correspondence, provider logs, and backups may have different retention periods based on their purpose, provider settings, legal requirements, and security needs. We do not promise a single automatic deletion period for all data.</p>
      <p>You can delete your account from Account on the website. Deletion removes your profile, messages, reactions, and files you uploaded. Stories and links you shared stay in the paper without your name. A paper with no remaining members is deleted; if you owned a paper that others still belong to, ownership passes to the longest-standing other member. To ask for deletion another way, or for removal of other personal information, email <a href="mailto:hello@thegrapevine.si">hello@thegrapevine.si</a>, preferably from the email address associated with your account. We may need to verify your identity. We will assess what must be deleted, anonymized, or retained, including information in shared group records and any legal obligations, and explain applicable limits. Backup copies may remain until they expire through the backup lifecycle; copies independently saved by other members are outside our control.</p>

      <h2>7. International processing and security</h2>
      <p>Our providers may process information in countries outside your country of residence, including outside the European Economic Area. Where applicable law requires safeguards for a transfer, appropriate safeguards, such as standard contractual clauses or a recognized adequacy framework, are required. Contact us for information about the safeguards applicable to your data.</p>
      <p>We use measures such as authenticated access, group-level database access controls, and protected mobile credential storage to reduce unauthorized access. No online service can guarantee absolute security.</p>

      <h2>8. Your rights</h2>
      <p>Depending on applicable law, you may have rights to access or receive a copy of your personal information, correct it, request deletion, restrict or object to processing, and receive portable data. Where processing relies on consent, you can withdraw it without affecting the lawfulness of earlier processing. You may also complain to your local data-protection supervisory authority.</p>
      <p>Send requests to <a href="mailto:hello@thegrapevine.si">hello@thegrapevine.si</a>. We will respond within the time required by applicable law, generally one month for GDPR requests, and explain any lawful extension or restriction. Editorial AI organization is not intended to make legal or similarly significant decisions about you.</p>

      <h2>9. Children and policy changes</h2>
      <p>The service is intended for people aged 16 and over. If you believe a child under 16 has provided personal information, contact us so we can investigate and address it.</p>
      <p>We may update this policy as features, providers, or legal requirements change. We will update the date above and provide notice of material changes through the service or email where appropriate. See also our <Link href="/terms">Terms of Service</Link>.</p>
    </>
  )
}
