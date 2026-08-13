# Spotter

**Put the phone on the floor, half-open. It stands up, watches you train, and tells you the one thing to fix — out loud, while you can still fix it.**

A foldable is the only phone that stands on the floor by itself, at an angle you can aim, with no
tripod and nothing to prop it against. That is the entire reason this app exists on this hardware:
the bottom half is the base, the top half is a camera pointed at you.

Everything runs on the device. No video is recorded, and nothing is uploaded.

---

## What it does

- Counts your reps — **squats and push-ups**.
- Tells you the **one** thing wrong with the rep you are in — out loud, while you can still fix it.
- Scores nothing you cannot check. Every number comes from the coordinates of your own joints.

| | Squat | Push-up |
|---|---|---|
| Phone goes | in front of you | beside you |
| Measured by | knee angle | elbow angle |
| Catches | knees caving, back rounding | hips sagging, hips piking |
| Also | not deep enough | not deep enough |

The placement differs because the faults do. **Knee cave is invisible from the side and hip sag is
invisible from the front** — an app that accepted either angle for either movement would be
confidently coaching from a viewpoint that cannot see the problem, so it tells you where to put the
phone.

## Running it

```bash
git clone https://github.com/Amritha902/spotter
cd spotter
```

Add an SDK path (this file is gitignored, so it will not exist after cloning):

```
# local.properties
sdk.dir=/path/to/your/android-sdk
```

Then:

```bash
gradle :app:installDebug
```

**No API keys are needed.** Everything — pose detection, the geometry, the voice — runs on the
phone. The app asks for camera permission on first launch and does nothing until you grant it.

Minimum Android 11 (API 30). Built for arm64 only, which is every foldable it targets.

### Tests

```bash
gradle :app:testDebugUnitTest :app:checkReachable
```

56 tests, no device required. See *Why the maths is separate from the camera* below for why that
number matters.

---

## How it decides

### Why the maths is separate from the camera

Verifying "does it correctly spot a knee caving in" by running the app needs a person, a camera, a
gym, and someone willing to do a bad squat on purpose. That happens once, badly, and never again.

So the judgement never touches an ML Kit type. `Squat`, `PushUp` and `RepCounter` take plain
coordinates and are tested exhaustively at a desk with synthetic bodies — including the case that
matters most:
the same squat filmed from two metres and four metres must give the same verdict, or a lifter who
steps back from the camera gets told their form improved.

The camera layer only has to be trusted to deliver coordinates.

### One abstraction, arrived at on the second exercise

Squats and push-ups differ in every particular — the joint that measures the rep, which joints must
be visible, what counts as a fault, which side of the body the camera sees. But they share one
shape: an angle that starts high, falls, and rises. `RepCounter` needs only that shape, so it
counts both without knowing which it is watching, and a third movement costs nothing there.

Written at the second exercise rather than guessed at the first.

### One fault at a time

Someone at the bottom of a loaded squat can act on exactly one instruction. Three at once is noise
they will ignore — and then they ignore the one that mattered. Knee cave outranks everything else
because it is the fault that injures people.

### Silence when it cannot see you

ML Kit returns confident-looking coordinates for a joint that is out of frame. Coaching someone on
a knee the camera never saw is the fastest way to be muted, so a body that is not fully visible
produces no verdict at all.

### Rep counting is a state machine, not a threshold

Knee angle jitters a few degrees every frame. Anything built on `angle < 100` racks up thirty reps
for a lifter *pausing* at the bottom. A full standing → deep → standing cycle makes that impossible
rather than unlikely.

### Rules about when to shut up

The screen is unreadable from the bottom of a squat — head down, about a second to react — so
speech is the primary output here and the display is secondary. But pose runs at ~30fps, and the
naive version says "knees out" thirty times a second.

So `SpokenCoach` is pure and stateful, driven frame by frame in tests:

- one correction per rep
- nothing twice inside 1.8s, which is shorter than a squat
- a correction **suppresses that rep's count** — the number is the least useful thing that could be
  said in that second
- the same fault three reps running escalates once (*"drop the weight"* — the honest advice at that
  point) and then goes quiet, because a fourth repetition is nagging
- but going quiet about knees must not mute the coach in general; a different fault is new
  information

### The split goes at the hinge

Not at a tidy midpoint. A hinge that is not dead centre, or a window offset by the system bars,
puts the rep count directly on the crease — bent away from the person reading it, the one place on
this screen text must never land.

---

## What is not yet true

**No real body has ever been in front of this.** Every threshold — `KNEE_CAVE_RATIO`,
`HIP_SAG_RATIO`, `HIP_PIKE_RATIO` — is a first estimate from coaching norms, verified against
synthetic coordinates and an emulator containing no people. Whether they fire on a genuinely
caving knee or a genuinely sagging hip — and stay quiet on a good rep — needs hardware and someone
willing to do a bad one on purpose.

Until then this is a well-tested hypothesis rather than a working coach. The thresholds are
gathered as named constants on each exercise in `Exercise.kt`, with comments saying which are the
least trustworthy, because tuning them against a real body is the next real piece of work.


## Licence

AGPL-3.0. See [LICENSE](LICENSE).
