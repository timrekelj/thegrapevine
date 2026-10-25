export default function FilingMark({ busy = false }: { busy?: boolean }) {
  return <svg className={`filing-mark ${busy ? 'reading' : ''}`} width="48" height="56" viewBox="0 0 48 56" aria-hidden="true"><path d="M24 17C14 5 5 10 6 17C13 23 20 22 24 17ZM24 19C23 10 28 7 34 4" fill="var(--red)" stroke="var(--red)" strokeWidth="2" /><circle cx="24" cy="33" r="14" fill="var(--ink)" /><circle className="filing-ring" cx="24" cy="33" r="19" fill="none" stroke="var(--red)" strokeWidth="3" /></svg>
}
