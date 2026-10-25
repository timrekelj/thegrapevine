/**
 * Each member owns one hue, in the order they joined the paper: the founder raspberry, then marigold,
 * terra, sky, grape, teal and cocoa. `order` is the paper's member ids by join time (ties by id);
 * someone not in it (a deleted account) falls back to a hash that is the same in Swift, Kotlin and JS.
 */
export function memberIndex(seed: string, order: readonly string[] = []): number {
  const position = order.indexOf(seed)
  if (position >= 0) return position % memberColors.length
  let hash = 0
  for (const character of seed.toLowerCase()) hash = (Math.imul(hash, 31) + character.charCodeAt(0)) >>> 0
  return hash % memberColors.length
}
export const memberColors = ['#D23D7E', '#E3A512', '#E2552B', '#2F7BEA', '#8E4FD8', '#12A29A', '#9C6B3C']
export const memberLights = ['#E66A9C', '#F2BE3E', '#F07A55', '#5C9BF5', '#AB7AE6', '#3FC1B9', '#B98858']
