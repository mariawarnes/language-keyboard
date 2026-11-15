# Language Keyboard

An Android keyboard for practising a language while chatting with friends who speak a different one.

You **type in the language you're learning**. A small bar above the keys shows what you typed, and the
text box receives the **translation into your friends' language**, updated live as you type.

```
┌──────────────────────────────────────────────┐
│ Chat app text box:  "I'm going to the beach" │  ← your friends' language
├──────────────────────────────────────────────┤
│ [ES → EN]  Voy a la playa▏         ✕   ⚙     │  ← what you typed (practice bar)
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
- The **ES → EN** chip switches between two modes:
  - **Practice mode** (chip blue): you type in the language you're learning; your friends get it in theirs.
  - **Direct mode** (chip grey): you type in your own language straight into the app, and the bar above the keys
    shows it live in the language you're learning, so you still pick some up.
- Password, email, URL and number fields are never translated (and use a plain QWERTY layout).
- **The keys follow the language you're typing in.** With translation on, you get the layout of the language
  you're practising; in direct mode, your friends' language's layout. See the list below.
- **Gesture typing, suggestions and autocorrect** (each can be switched off in settings) work in whichever
  language you're typing:
  - Suggestions ignore accents, so `cancion` offers `canción` and `esta` also offers `está` — handy when learning.
  - Autocorrect fixes typos and missing accents when you press space or punctuation (`tambien` → `también`,
    `graicas` → `gracias`). Press backspace straight after to undo it.
  - Your own word appears in quotes on the left of the suggestion bar; tap it to keep it and teach it to the keyboard.
  - Swipe across the letters to type a word; other guesses appear in the suggestion bar, and backspace removes the
    whole swiped word.
- **Hold and slide along the space bar** to move the cursor through what you've typed (the practice bar scrolls to
  follow it), or tap the practice text to put the cursor there. In direct mode, the slide moves the cursor
  in the app's own text box.
- **Mistakes in the language you're practising get squiggly underlines** in the practice bar, like a word
  processor: red for misspelt words and missing accents, and — if you turn on the online grammar check — blue for
  grammar problems. Tap a marked word to see what's wrong and tap a fix. Pressing ✓ while mistakes are marked shows them first;
  press ✓ again to send it as it is.
  - Spelling and accent marks work offline from the word lists.
  - Grammar check is optional and off by default: it sends the practice text (not the translation) to
    [LanguageTool](https://languagetool.org)'s free public API when you pause typing.
- Long-press letters for accents (á, ñ, ü, ß, …) and the top row for digits. Use the 🌐 button under the keyboard to switch keyboards.

## Install

The app isn't on the Play Store. Every push builds it and publishes the APK as the **Latest build** release:

1. On your phone, open
   **https://github.com/mariawarnes/language-keyboard/releases/download/latest/language-keyboard.apk**
   (or the repository's **Releases** page → *Latest build* → `language-keyboard.apk`).
2. Open the downloaded file. Android will ask you to allow installing apps from that source. If Play Protect
   warns about it, choose **More details → Install anyway**.
3. Open **Language Keyboard** and follow the steps on screen:
   1. Enable the keyboard in system settings, then switch to it.
   2. Choose the language you type in and the language it gets sent in, then tap **Download language packs**
      (Wi-Fi recommended).
   3. Try it in the test box.

Later builds install as updates over the previous one (they're all signed with the same key), keeping your settings.

When you enable any third-party keyboard, Android warns that it could collect what you type. This one has no
analytics; it only uses the internet to download language packs and word lists, and (only if you turn it on) for
the online grammar check.

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

Suggestions, autocorrect and gesture typing use word lists from
[FrequencyWords](https://github.com/hermitdave/FrequencyWords) by Hermit Dave (built from OpenSubtitles, CC BY-SA 4.0).
Each language's list (~650 KB) downloads automatically the first time you type in it. Not available yet for
Belarusian, Haitian Creole, Irish, Maltese, Swahili and Welsh.

## Limitations (for now)

- No layout yet for Japanese, Chinese, Korean, Thai or the Indian languages (Hindi, Bengali, Tamil, …). These
  need more than one key per character (kana/kanji conversion, Hangul syllable building, conjuncts), so they fall
  back to QWERTY for now. They still work as the language it's *sent in*.
- ML Kit's translations are decent but not perfect, and the translation of half a sentence can look odd until you
  finish it.
- No next-word prediction yet (suggestions are for the word you're typing).
