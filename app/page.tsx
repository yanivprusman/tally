const marks = [0, 1, 2, 3];

const points = [
  {
    title: "Several at once",
    body: "A trip, a job, a month — each tally keeps its own entries, its own currency and its own running balance. They never mix.",
  },
  {
    title: "Two taps to record",
    body: "Money in or money out, an amount on the app's own keypad, and what it was for. That is the whole interaction.",
  },
  {
    title: "Reset or delete, safely",
    body: "Clear a tally back to zero and keep it, or remove it entirely. Both are one Undo away for as long as the snackbar is up.",
  },
];

export default function Home() {
  return (
    <main className="min-h-screen bg-[#0D1116] text-[#E9EDF3] antialiased">
      <div className="mx-auto max-w-2xl px-6 py-24 sm:py-32">
        <div className="relative h-16 w-24" aria-hidden>
          {marks.map((i) => (
            <span
              key={i}
              className="absolute top-0 h-full w-[7px] rounded-full bg-[#E9EDF3]/35"
              style={{ left: `${i * 22}px` }}
            />
          ))}
          <span className="absolute top-1/2 left-[-6px] h-[7px] w-[104px] -translate-y-1/2 -rotate-[13deg] rounded-full bg-[#F5B544]" />
        </div>

        <h1 className="mt-10 text-4xl font-bold tracking-tight sm:text-5xl">Tally</h1>
        <p className="mt-4 text-lg leading-relaxed text-[#9AA7B8]">
          An Android app for keeping count of what came in and what went out — on a trip, on a job,
          over a week. You name the tally, and it holds the running balance until you reset it.
        </p>

        <dl className="mt-14 space-y-8">
          {points.map((p) => (
            <div key={p.title} className="rounded-2xl bg-[#161C24] p-6">
              <dt className="text-base font-semibold">{p.title}</dt>
              <dd className="mt-2 text-[15px] leading-relaxed text-[#9AA7B8]">{p.body}</dd>
            </div>
          ))}
        </dl>

        <p className="mt-14 text-sm leading-relaxed text-[#64717F]">
          Everything a tally holds is stored on the phone itself. There is no account, nothing is
          uploaded, and it all works with the radio off — which is the point, because a tally gets
          written standing in a bus queue.
        </p>
      </div>
    </main>
  );
}
