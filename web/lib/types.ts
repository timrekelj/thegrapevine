export type PaperMember = { id: string; display_name: string; monogram: string };
type PaperSource = {
  id: string; publisher: string; domain: string; image_url: string | null;
  submitted_by: string | null; created_at: string;
};
type PaperMessage = {
  id: string; story_id: string; author_id: string; body: string | null; created_at: string;
};
export type PaperStory = {
  id: string; title: string; summary: string; category: string | null;
  last_activity_at: string; created_at: string;
  sources: PaperSource[]; messages: PaperMessage[];
};
export type RankedStory = PaperStory & {
  sourceCount: number; messageCount: number; publishers: string[];
  newSources: number; newMessages: number; submitters: PaperMember[];
  imageUrl: string | null;
};
