# Offline unlock codes

A locked app shows a **challenge**. The admin signs it with an Ed25519 private
key and reads back a **response**. The app verifies that signature against the
public key built into it and opens for that one session. Nothing here needs a
network, a server or a shared secret: the app only ever holds the public key.

Both codes are digits only, so they survive being read out over the phone.

> **Security note.** This mechanism stops anyone without the private key from
> producing responses the original app accepts. It cannot stop a user who
> controls the code and the device from modifying or rebuilding an open source
> app to skip the lock check altogether.

## The protocol

| | |
|---|---|
| Signature | Ed25519 (RFC 8032), 64 bytes |
| Challenge | 40 random digits (~132.9 bits) from a cryptographic generator, plus 2 check digits |
| Response | the signature as 155 decimal digits, plus 2 check digits |
| Check digits | ISO 7064 MOD 97-10, so a mistyped code is caught before verifying |

The signed bytes are built from three fields, each prefixed with its own
16 bit big endian length, in this order:

```
uint16(len(protocol_version)) || protocol_version   # "1"
uint16(len(application_id))   || application_id     # "lesser.evil.app-unlock"
uint16(len(challenge))        || challenge          # the 40 digits, without the check digits
```

The length prefixes are what makes the encoding unambiguous: no two different
sets of fields can produce the same bytes, so a signature made for anything
else cannot be replayed here.

Every lock screen draws a new challenge, and the previous one is dropped the
moment it does. A response is spent as soon as it works, and it only ever opens
the session in front of you.

Both sides of this protocol live in two places, and their tests share a fixed
case so they cannot drift apart:

- the app: `app/src/main/java/lesser/evil/AdminUnlockCode.kt`
- the admin: `tools/admin-unlock/admin_unlock.py`

## Setting up

The tool needs Python 3.9+ and [cryptography](https://cryptography.io):

```sh
pip install cryptography
```

### 1. Create the key pair, once

```sh
python3 tools/admin-unlock/admin_unlock.py keygen
```

It writes the private key to `~/.config/lesser-evil/admin-unlock-key.pem`,
readable by you alone, and prints the public key. The private key never leaves
that file: it is not printed, not logged, and the repository ignores `*.pem`
and `*.key` so it cannot be committed by accident.

Use `--key-file PATH` or `LESSER_EVIL_UNLOCK_KEY_FILE` to keep it somewhere
else, such as a removable drive. `LESSER_EVIL_UNLOCK_KEY` holds a base64 seed
instead, for a machine where no key file should be left behind.

Running `keygen` again refuses to touch an existing key. `--rotate` replaces
it after a warning and a typed confirmation, because a new key pair means a new
public key: builds carrying the old one stop accepting your responses, and a
device locked with the old key can only be opened again by installing a build
that carries the new public key.

### 2. Embed the public key in the app

Print it any time with:

```sh
python3 tools/admin-unlock/admin_unlock.py pubkey
```

Paste those 64 hex characters into `app/src/main/java/lesser/evil/AdminUnlockKey.kt`:

```kotlin
const val AdminUnlockPublicKeyHex = "8aa40c02...c218544b"
```

Then build the app. While the constant is empty the unlock code is hidden from
the lock screen entirely. Only the public key goes in: the app carries no
private key, no seed, no shared secret and no way to produce a response.

### 3. Produce a response

The user reads out the challenge from their lock screen:

```sh
python3 tools/admin-unlock/admin_unlock.py sign "307418 529630 741852 963074 185296 307418 529646"
```

The challenge may be pasted with spaces or without; everything that is not a
digit is ignored, and the check digits catch a mistyped one. The response is
printed in groups of five digits to read back. The user types it into the lock
screen, where spaces and dashes are equally ignored.

To check a response the way the app does, with the public key alone:

```sh
python3 tools/admin-unlock/admin_unlock.py verify CHALLENGE RESPONSE --public-key 8aa40c02...
```

## Tests

```sh
python3 -m unittest discover tools/admin-unlock   # the admin side
./gradlew :app:testDebugUnitTest                  # the app side
```

Both suites check the same fixed challenge, public key and response, so the app
and the tool cannot drift apart without a test failing. Every key in the tests
is generated on the spot and thrown away.
