import type { Metadata } from "next";
import "./globals.css";
import { FeedbackChat } from '@automate/feedback-lib/FeedbackChat';

export const metadata: Metadata = {
  title: "tally",
  description: "Track money in and money out across several independent tallies at once — a trip, a job, a month. Each tally has its own entries, running balance, reset and delete.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en">
      <body>{children}
        <FeedbackChat issuesPath="/feedback-lib-issues" />
</body>
    </html>
  );
}
