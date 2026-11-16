# Language Keyboard

An Android keyboard for practising a language in everyday typing — chats, searches, anything — while what you send stays in your own language.

You **type in the language you're learning**. A small bar above the keys shows what you typed, and the
text box receives the **translation into your own language**, updated live as you type.

```
┌──────────────────────────────────────────────┐
│ App's text box:     "I'm going to the beach" │  ← your language
├──────────────────────────────────────────────┤
│ [ES|EN]  Voy a la playa|           ✕   ⚙     │  ← what you typed (practice bar)
│  q  w  e  r  t  y  u  i  o  p                │
│   a  s  d  f  g  h  j  k  l                  │
│  ⇧  z  x  c  v  b  n  m  ⌫                   │
│ ?123  ,  [ Español → English ]  .   ✓        │
└──────────────────────────────────────────────┘
```

## How it works

- Translation uses [Google ML Kit on-device translation](https://developers.google.com/ml-kit/language/translation).
  Each language pack is downloaded once (about 30 MB). After that, translating runs **on the phone, offline**,
  and what you type isn't sent to a server.
- While you type, the translation sits in the text box as editable "composing" text (usually underlined) and is
  replaced as your sentence grows.
- **✓** (where Enter normally is) locks the current translation in, so you can start the next sentence.
  When nothing is pending, the key goes back to Enter / Send.
- Sending the message from the app, or tapping somewhere else in the text, also locks the phrase in.
- **✕** removes the current phrase from the text box.
- The **ES | EN** switch above the keys shows which language you're typing in (the highlighted half); tap the
  other half to change:
  - **ES — practice mode:** you type in the language you're learning; the app gets it in your language.
  - **EN — direct mode:** you type in your own language straight into the app, and the bar above the keys
    shows it live in the language you're learning, so you still pick some up.
- Password, email, URL and number fields are never translated (and use a plain QWERTY layout).
- **The keys follow the language you're typing in.** With translation on, you get the layout of the language
  you're practising; in direct mode, your own language's layout. See the list below.
- **Gesture typing, suggestions and autocorrect** (each can be switched off in settings) work in whichever
  language you're typing:
  - Suggestions ignore accents, so `cancion` offers `canción` and `esta` also offers `está` — handy when learning.
  - Autocorrect fixes typos and missing accents when you press space or punctuation (`tambien` → `también`,
    `graicas` → `gracias`). Press backspace straight after to undo it.
  - Your own word appears in quotes on the left of the suggestion bar; tap it to keep it and teach it to the keyboard.
  - Swipe across the letters to type a word; other guesses appear in the suggestion bar, and backspace removes the
    whole swiped word.
- **Slide along the space bar** to move the cursor, one character at a time, like Gboard: it moves relative to
  your thumb (about one character per 10dp). Rest your thumb at the far left or right edge of the keyboard to keep
  going to the start or end. Tap the practice text to put the cursor there. In direct mode the slide moves the
  cursor in the app's own text box.
- **Mistakes in the language you're practising get squiggly underlines** in the practice bar, like a word
  processor: red for misspelt words and missing accents, and — if you turn on the online grammar check — blue for
  grammar problems. Tap a marked word to see what's wrong and tap a fix. Pressing ✓ while mistakes are marked shows them first;
  press ✓ again to send it as it is.
  - Spelling and accent marks work offline from the word lists.
  - Grammar check is optional and off by default: it sends the practice text (not the translation) to
    [LanguageTool](https://languagetool.org)'s free public API when you pause typing.
- Long-press letters for accents (á, ñ, ü, ß, …) and the top row for digits. Use the 🌐 button under the keyboard to switch keyboards.

## Language Lens (companion app)

**Language Lens** shows the messages you receive in WhatsApp and Messenger in the language you're learning,
right on top of the originals — no copying or tapping needed. Tap a message to peek at the original, tap again to
go back; scrolling works as normal, even starting on a translation. The quick-settings tile pauses it (showing every
original); it hides as soon as you switch apps.

- It's an Android Accessibility service that reads text only in WhatsApp (and WhatsApp Business) and Messenger
  (and Messenger Lite); for other apps it only notices that you've switched away, so it can hide. Language detection and translation run on the phone with ML Kit; messages are never stored,
  logged or sent anywhere. The only internet use is the one-time language-pack download.
- It's a separate app from the keyboard so each only has the permissions it needs.
- Because it's installed outside the Play Store, Android 13+ asks you to allow it first: *App info → ⋮ → Allow
  restricted settings*, then turn it on under *Accessibility*. The app's setup screen walks you through it.
- Download it from the same release:
  **https://github.com/mariawarnes/language-keyboard/releases/download/latest/language-lens.apk**

## Install

The app isn't on the Play Store. Every push builds it and publishes the APK as the **Latest build** release:

1. On your phone, open
   **https://github.com/mariawarnes/language-keyboard/releases/download/latest/language-keyboard.apk**
   (or the repository's **Releases** page → *Latest build* → `language-keyboard.apk`).
2. Open the downloaded file. Android will ask you to allow installing apps from that source. If Play Protect
   warns about it, choose **More details → Install anyway**.
3. Open **Language Keyboard** and follow the steps on screen:
   1. Pick the language for the app's own text (settings and keyboard messages): English, Español, Français,
      Deutsch, Italiano or Português — by default it follows *My language*.
   2. Enable the keyboard in system settings, then switch to it.
   3. Choose the **language you're learning** and **your language** (what gets sent), then tap
      **Download language packs** (Wi-Fi recommended).
   4. Try it in the test box.

Later builds install as updates over the previous one (they're all signed with the same key), keeping your settings.

When you enable any third-party keyboard, Android warns that it could collect what you type. This one has no
analytics; it only uses the internet to download language packs and word lists, and (only if you turn it on) for
the online grammar check.

## iPhone version (in progress)

`ios/` has an iOS version of the keyboard (the Lens can't exist on iOS: apps there can't read or draw over other apps).

- Same idea: practice mode (type in the language you're learning; the translation goes into the message as you type, ✓ keeps it) and direct mode (type in your own language with a preview in the one you're learning), the ES | EN switch, ✕ and ⚙ in the bar, per-language layouts, Hangul, space-bar cursor and long-press accents.
- Translation uses Apple's on-device Translation (iOS 26+). iOS only lets apps download language packs, so you download each pair in the Language Keyboard app first. The keyboard doesn't need "Full Access".
- Not there yet: suggestions, gesture typing, mistake marking, and kana/pinyin conversion (Japanese and Chinese currently use a QWERTY layout with the system's other keyboards still available via 🌐).
- **Installing:** GitHub builds it on every change and publishes an *unsigned* `language-keyboard-unsigned.ipa` to the [`ios-latest` release](../../releases/tag/ios-latest). An iPhone only installs apps signed with an Apple account, so for now it can't be installed directly. A free Apple ID can sideload it onto your own phone (re-sign it with a tool such as Sideloadly or AltStore; it expires after 7 days). Giving it to other people needs a paid Apple Developer account ($99/year), through TestFlight or the App Store.

## Build it yourself

You need JDK 17 and the Android SDK (Android Studio installs both).

```sh
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press Run.

## Keyboard layouts

| Layout | Languages |
| --- | --- |
| QWERTY + ñ / ç | Spanish, Galician / Catalan |
| QWERTY (accents on long-press) | English, Portuguese, Italian, Dutch, Polish, Romanian, Lithuanian, Latvian, Vietnamese, Indonesian, Malay, Swahili, Tagalog, Afrikaans, Irish, Welsh, Haitian Creole, Esperanto, Maltese |
| QWERTY + å ö ä / æ ø / ð æ þ / ü õ ö ä | Swedish, Finnish / Danish, Norwegian / Icelandic / Estonian |
| QWERTZ (+ ü ö ä, ú ů, ç ë) | German, Czech, Albanian, Slovak, Hungarian, Croatian, Slovenian |
| AZERTY | French |
| Turkish Q | Turkish |
| ЙЦУКЕН / phonetic | Russian, Ukrainian, Belarusian / Bulgarian, Macedonian |
| Greek | Greek |
| Hebrew, Arabic, Persian, Urdu (phonetic) | Hebrew, Arabic, Persian, Urdu |
| Georgian | Georgian |
| Thai Kedmanee (with Shift page) | Thai |
| InScript (with Shift page) | Hindi, Marathi, Bengali, Gujarati, Kannada, Tamil, Telugu |
| 2-set Hangul, building syllables as you type | Korean |
| 12-key kana pad with flick input; kana → kanji / katakana in the suggestion bar | Japanese |
| Pinyin; characters from the suggestion bar (space picks the first) | Chinese (Simplified) |

Suggestions, autocorrect and gesture typing use word lists from
[FrequencyWords](https://github.com/hermitdave/FrequencyWords) by Hermit Dave (built from OpenSubtitles, CC BY-SA 4.0).
Each language's list (~650 KB) downloads automatically the first time you type in it. Japanese uses the
[SKK](https://github.com/skk-dev/dict) dictionary (SKK-JISYO.M, GPL-2.0-or-later, downloaded on first use) for kana →
kanji, and Chinese pinyin uses [pinyin4j](https://github.com/belerweb/pinyin4j) (BSD). Not available yet for
Belarusian, Haitian Creole, Irish, Maltese, Swahili and Welsh.

## Limitations (for now)

- Japanese kanji conversion uses the SKK dictionary's plain words (nouns etc.); verbs and adjectives with okurigana
  (書く, 高い) stay in kana for now. Chinese is Simplified only, word by word from a 50k word list.
- Word-based checks (spelling squiggles, autocorrect, gesture typing) are off for Thai, Japanese and Chinese, which
  don't separate words with spaces, and for Korean.
- ML Kit's translations are decent but not perfect, and the translation of half a sentence can look odd until you
  finish it.
- No next-word prediction yet (suggestions are for the word you're typing).
