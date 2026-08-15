# CRUX — voice assistant for Android

This project implements the MVP workflow (Voice → Speech-to-Text → command understanding
→ execute → Text-to-Speech) plus the five new features: rename to CRUX, wake word,
sensitive-action confirmation, manual contact mapping, and SMS send-intent.

## First-time setup (do this before building)

1. Open the `CRUX/` folder in Android Studio (Hedgehog or newer). It will offer to
   generate the Gradle wrapper on first sync — accept that.
2. **Wake word requires two things this project can't generate for you** (both free,
   one-time, on Picovoice's console — see `voice/WakeWordHelper.kt` for details):
   - A Picovoice AccessKey, pasted into `WakeWordHelper.accessKey`.
   - A trained "Hey CRUX" `.ppn` file, placed at
     `app/src/main/assets/hey_crux_android.ppn`.
   Until both exist, leave the wake-word toggle off and test everything else via the
   manual mic button — nothing else in the app depends on Porcupine.
3. Build and run on a **real device**, not the emulator — speech recognition and TTS
   are unreliable on emulators.

## Suggested build/test order (matches the original plan)

1. Rename is already done throughout (see below) — just confirm the app installs as "CRUX".
2. Test the contacts screen (`ui/ContactMappingActivity.kt`) standalone: add/edit/remove
   a name → number mapping.
3. Test the confirmation flow by saying "send hi to \<a name you saved\>" — confirm CRUX
   asks before doing anything, and that saying "no" cancels cleanly.
4. Confirm the SMS app opens pre-filled and CRUX does **not** press send for you.
5. Only then turn on the wake-word toggle and test "Hey CRUX" (needs Picovoice setup above).

## File-by-file guide

### `command/` — deciding what to do, and whether it's safe to do it

- **`Command.kt`** — the closed set of every action CRUX can perform, including the new
  `Command.Sensitive` sub-type (currently just `SendSms`) and `Command.ContactNotFound`.
  Add new capabilities here first.
- **`CommandProcessor.kt`** — keyword matching from raw recognized text to a `Command`.
  The new bit is `parseSendMessage()`, which recognizes "send/message/text ... to ..."
  and looks the name up via `ContactStore` — never guesses a number.
- **`ConfirmationManager.kt`** — **new, generic** yes/no state machine. Deliberately has
  zero SMS-specific code, so the next sensitive command (e.g. "delete a contact") reuses
  it as-is. Any unclear or negative answer cancels; nothing here can default to "yes".
- **`ActionExecutor.kt`** — actually performs a `Command`. The new `sendSms()` method uses
  `ACTION_SENDTO` with an `smsto:` URI, which opens the user's SMS app pre-filled and
  requires them to tap send themselves — see the comment there for why that matters.
- **`AssistantEngine.kt`** — **new.** Glues `CommandProcessor` + `ConfirmationManager` +
  `ActionExecutor` + TTS into one pipeline: "here's recognized text, here's what CRUX
  says/does." Both the manual mic-tap flow (`MainViewModel`) and the wake-word flow
  (`WakeWordService`) call the same instance-per-context of this class, so confirmation
  logic isn't duplicated between entry points.

### `voice/` — turning sound into text and text into sound

- **`SpeechToTextHelper.kt`** — unchanged in spirit from the MVP; wraps
  `SpeechRecognizer` for a single utterance.
- **`TextToSpeechHelper.kt`** — unchanged in spirit; wraps `TextToSpeech`. Now supports an
  `onDone` callback, used so CRUX can start listening again right after asking a
  confirmation question.
- **`WakeWordHelper.kt`** — **new.** Thin wrapper around Porcupine for on-device "Hey CRUX"
  detection only (not full speech recognition — that's still `SpeechToTextHelper`, invoked
  after the wake word fires). Needs the Picovoice AccessKey + `.ppn` file from setup above.
- **`WakeWordService.kt`** — **new.** The required foreground service for background mic
  use, with the persistent "CRUX is listening" notification. Owns its own
  `SpeechToTextHelper` + `AssistantEngine` so it can fully handle a wake-word-triggered
  command (including a confirmation round-trip) independent of whether `MainActivity` is
  open.

### `data/` — the manual contact mapping (feature 4)

- **`Contact.kt`** — plain `name`/`phoneNumber` pair.
- **`ContactStore.kt`** — **new.** Local-only DataStore persistence, no `READ_CONTACTS`,
  no network. `findByName()` returns `null` on no match — callers must treat that as "ask
  the user to add them," never as a reason to search elsewhere.

### `ui/` — the one non-voice screen

- **`ContactMappingActivity.kt`** — **new.** List + add form for the manual mapping.
- **`ContactAdapter.kt`** — **new.** Plain `RecyclerView.Adapter` for that list.

### App entry points

- **`MainActivity.kt`** — the single screen: status text, mic button (still works as
  manual fallback/toggle even with wake word on), the wake-word on/off switch, and a
  button into the contacts screen. Requests `RECORD_AUDIO` and (for wake word)
  `POST_NOTIFICATIONS`, one at a time, each with an in-app explanation shown first.
- **`MainViewModel.kt`** — **new.** Holds the manual-tap `AssistantEngine` instance and
  exposes a status callback for the UI.

### Resources

- **`AndroidManifest.xml`** — only three permissions: `RECORD_AUDIO`,
  `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` (for the wake-word service), and
  `POST_NOTIFICATIONS`. `READ_CONTACTS` and `SEND_SMS` are deliberately absent — see the
  manifest's own comments.
- **`res/values/strings.xml`** — every spoken line and permission explanation, all
  CRUX-branded, in one place so tone/wording is easy to review or change.
- **`res/layout/*.xml`** — plain View-based layouts (matches the MVP's simple single-screen
  style rather than introducing Compose).

## Where each safety property lives

- **Closed command set, no free-form execution:** `Command.kt` + the fact that
  `ActionExecutor.execute()` only accepts a `Command`, never a `String`.
- **No autonomous SMS sending:** `ActionExecutor.sendSms()` uses `ACTION_SENDTO`, not
  `SmsManager`; `SEND_SMS` is never requested.
- **No silent contact access:** `ContactStore` is the only source of phone numbers;
  `READ_CONTACTS` is never requested.
- **No action without confirmation for sensitive commands:** enforced structurally —
  `ActionExecutor` is only ever called with a `Command.Sensitive` from inside
  `AssistantEngine.handleConfirmationAnswer()`, which only runs after
  `ConfirmationManager.Resolution.Confirmed`.
- **Unrecognized speech never guesses:** `CommandProcessor` falls through to
  `Command.Unknown` for anything that doesn't match a known shape.
