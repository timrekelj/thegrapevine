import type { Metadata } from 'next'
import Link from 'next/link'

export const metadata: Metadata = {
  title: 'Terms of Service | The Grapevine',
  description: 'Terms for using The Grapevine, a private newspaper curated by friends.',
}

export default function TermsPage() {
  return (
    <>
      <h1>Terms of Service</h1>
      <p className="meta">Last updated: October 18, 2026</p>
      <p>These terms govern your use of The Grapevine website and mobile apps (the “service”), operated by Tim Rekelj. By creating an account or using the service, you agree to these terms. If you do not agree, do not use the service.</p>

      <h2>1. The service</h2>
      <p>The Grapevine lets friends share links, organize stories into private group newspapers, and discuss them. Some headlines, summaries, categories, and story groupings are generated using artificial intelligence. Features may change, and some features may be experimental or temporarily unavailable.</p>

      <h2>2. Your account</h2>
      <p>You must be at least 16 years old to use the service. Provide accurate account information, protect your email account, sign-in credentials, and devices, and do not impersonate someone else. Contact us if you believe someone has accessed your account without permission.</p>
      <p>Google, Apple, and X sign-in are subject to the respective provider’s terms. The Grapevine is not endorsed by those providers. You are responsible for deciding which groups to join and whom to invite; keep invitation links and codes private.</p>

      <h2>3. Your content and group privacy</h2>
      <p>You retain the rights you hold in the content you submit. You give us a non-exclusive permission to store, process, reproduce, and display that content only as needed to operate, secure, and support the service, including sharing it with your group and using service providers as described in the <Link href="/privacy">Privacy Policy</Link>. This does not transfer ownership of your content to us.</p>
      <p>Only submit material you have the right to share, and respect other people’s privacy and intellectual property. Linking to an article does not transfer the publisher’s rights or remove its access restrictions.</p>
      <p>Group content is intended for group members, but members can copy, download, or share what they see. The service is not end-to-end encrypted. Do not upload confidential material, highly sensitive personal information, or anything you cannot lawfully share with the group and the service’s processors.</p>

      <h2>4. Acceptable use</h2>
      <ul>
        <li>Do not post illegal content, threats, harassment, hate, sexual exploitation, or other abusive material.</li>
        <li>Do not share someone’s private information without a lawful basis or infringe copyright or other rights.</li>
        <li>Do not send spam, distribute malware, attempt unauthorized access, bypass security or group permissions, or disrupt the service.</li>
        <li>Do not use automated access to overload the service or extract other users’ private content.</li>
      </ul>
      <p>Report abuse or rights infringements to <a href="mailto:hello@thegrapevine.si">hello@thegrapevine.si</a>. Include enough information to identify the content without unnecessarily forwarding sensitive material.</p>

      <h2>5. AI summaries and external sources</h2>
      <p>AI output may be inaccurate, incomplete, misleading, or out of date. Check original sources before relying on a story. The service does not provide professional, medical, legal, or financial advice. You can report incorrect story text using the app’s reporting feature when available or by emailing us.</p>
      <p>External links, embedded images, and GIFs come from third parties. Their availability, content, and terms are outside our control. Your use of an external website or service is subject to its own terms and privacy practices.</p>

      <h2>6. Access, removal, and account closure</h2>
      <p>We may restrict access or remove content when reasonably necessary to address abuse, security risks, violations of these terms, or legal obligations. Where appropriate, we will explain the reason and allow you to contact us about the decision, unless doing so would create a security risk or be prohibited by law.</p>
      <p>You may stop using the service at any time. To request account closure or deletion of your personal information, email <a href="mailto:hello@thegrapevine.si">hello@thegrapevine.si</a>, preferably from your account’s email address. Deletion requests are handled as described in the Privacy Policy; signing out or uninstalling the app does not delete your account.</p>

      <h2>7. Availability and responsibility</h2>
      <p>We take reasonable care in operating the service, but cannot promise uninterrupted availability, error-free AI output, or that data will never be lost. Keep a separate copy of material that matters to you.</p>
      <p>To the extent permitted by applicable law, we are not responsible for indirect or consequential losses caused by service interruptions, third-party content, or your reliance on AI output. Nothing in these terms excludes liability that cannot legally be excluded, including liability for fraud or intentional misconduct, or limits your mandatory consumer rights.</p>

      <h2>8. Changes and applicable rights</h2>
      <p>We may update these terms as the service changes. We will update the date on this page and provide reasonable notice of material changes through the service or by email where appropriate. Changes do not remove rights you already have under applicable law. If you do not agree to updated terms, stop using the service and contact us about closing your account.</p>
      <p>Mandatory consumer protections and any rights to bring a claim in your local courts remain unaffected.</p>

      <h2>9. Contact</h2>
      <p>The service is operated by Tim Rekelj. For support, legal notices, or questions about these terms, email <a href="mailto:hello@thegrapevine.si">hello@thegrapevine.si</a>.</p>
    </>
  )
}
