# Spotter

**Put the phone on the floor, half-open. It stands up, watches you squat, and tells you when your knees cave in.**

A foldable is the only phone that stands on the floor by itself, at an angle you can aim, with no
tripod and nothing to prop it against. That is the entire reason this app exists on this hardware:
the bottom half is the base, the top half is a camera pointed at you.

Everything runs on the device. No video is recorded, and nothing is uploaded.

---

## What it does

- Counts your reps.
- Tells you the **one** thing wrong with the rep you are in — out loud, while you can still fix it.
- Scores nothing you cannot check. Every number comes from the coordinates of your own joints.

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

32 tests, no device required. See *Why the maths is separate from the camera* below for why that
number matters.

---

## How it decides

### Why the maths is separate from the camera

Verifying "does it correctly spot a knee caving in" by running the app needs a person, a camera, a
gym, and someone willing to do a bad squat on purpose. That happens once, badly, and never again.

So the judgement never touches an ML Kit type. `SquatForm` and `RepCounter` take plain coordinates
and are tested exhaustively at a desk with synthetic bodies — including the case that matters most:
the same squat filmed from two metres and four metres must give the same verdict, or a lifter who
steps back from the camera gets told their form improved.

The camera layer only has to be trusted to deliver coordinates.

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

**No real body has ever been in front of this.** Every threshold — `KNEE_CAVE_RATIO` in particular
— is a first estimate from coaching norms, verified against synthetic coordinates and an emulator
containing no people. Whether it fires on a genuinely caving knee, and stays quiet on a good squat,
needs hardware and a person doing a deliberately bad rep.

Until then this is a well-tested hypothesis rather than a working coach, and the thresholds are
collected in one place (`SquatForm`) with comments saying so, because tuning them is the next real
piece of work.

Squats only, so far.

## Licence

AGPL-3.0. See [LICENSE](LICENSE).
