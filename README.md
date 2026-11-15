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
- Tap the **ES → EN** chip to turn translation off and type directly, e.g. for names. Tap it again to turn it back on.
- Password, email, URL and number fields are never translated.
- Long-press letters for accents (á, ñ, ü, ß, …) and the top row for digits. Long-press the space bar to switch keyboards.

## Install

The app isn't on the Play Store. To install it:

1. Open the repository's **Actions** tab on GitHub, choose the latest successful **Build APK** run, and download
   the `language-keyboard-debug-apk` artifact (a zip containing `app-debug.apk`).
2. Copy the APK to your phone and open it. Android will ask you to allow installing apps from that source.
3. Open **Language Keyboard** and follow the three steps on screen:
   1. Enable the keyboard in system settings, then switch to it.
   2. Choose the language you type in and the language it gets sent in, then tap **Download language packs**
      (Wi-Fi recommended).
   3. Try it in the test box.

When you enable any third-party keyboard, Android warns that it could collect what you type. This one has no
analytics and only uses the internet to download ML Kit language packs.

## Build it yourself

You need JDK 17 and the Android SDK (Android Studio installs both).

```sh
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press Run.

## Limitations (for now)

- The keyboard layout is Latin QWERTY with accents, so it works best when the language you type in is written in
  the Latin alphabet (Spanish, French, German, Italian, Portuguese, Dutch, Polish, Turkish, Vietnamese, …).
  The language it's *sent in* can be anything ML Kit supports.
- ML Kit's translations are decent but not perfect, and the translation of half a sentence can look odd until you
  finish it.
- No word suggestions or autocorrect yet.
