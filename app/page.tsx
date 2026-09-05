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
    title: "Delete means delete",
    body: "Reset and delete remove the rows. Undo puts back exactly what was taken while the snackbar is up, and after that it is gone — the records are protected from accidents, not from you.",
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
          The records live in a database on the home network, reachable over the VPN — the phone
          holds a view of them, not the only copy. Wiping the app, losing the handset or
          reinstalling costs nothing. The trade is that the app needs to reach home to work, and
          says so plainly when it cannot.
        </p>
      </div>
    </main>
  );
}
