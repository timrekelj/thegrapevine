// The paper is printed for one place: dates and times are set in Ljubljana time on the server
// and in the browser alike, so server-rendered pages don't print UTC and hydration matches.
const timeZone = 'Europe/Ljubljana'

export const clock = (date: string | Date) =>
  new Date(date).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', timeZone })

export const dayline = (date: string | Date, year = false) =>
  new Date(date).toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', ...(year && { year: 'numeric' }), timeZone })

export const plural = (n: number, word: string, words = `${word}s`) => `${n} ${n === 1 ? word : words}`
