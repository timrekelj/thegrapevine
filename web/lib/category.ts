// Same pick as Theme.section in the iOS app, so a category keeps its colour on both.
export const categoryColor = (name: string) =>
  `var(--cat-${name.split('').reduce((sum, c) => sum + c.charCodeAt(0), 0) % 7})`;
