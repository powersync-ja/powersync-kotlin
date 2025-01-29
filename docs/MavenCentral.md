# Powersync Kotlin SDK

## Maven Central

When uploading the package to Maven Central you need a GPG key to sign the package. The information for these are stored in Github Secrets under `SIGNING_KEY`, `SIGNING_KEY_ID` and `SIGNING_PASSWORD`. These correspond to
```
-PsigningInMemoryKey="${{ secrets.SIGNING_KEY }}" \
-PsigningInMemoryKeyId="${{ secrets.SIGNING_KEY_ID }}" \
-PsigningInMemoryKeyPassword="${{ secrets.SIGNING_PASSWORD }}" \
```
in the `deploy` GitHub Action.

In the event that you need to create a new key due to expiry follow these steps (current expiry is 2028-01-29):

1. Run `gpg --gen-key` and fill in real name as `JourneyApps` and email address using `platform@journeyapps.com`. You will also need to create a passphrase which will be the `SIGNING_PASSWORD` in GitHub secrets so update it there. You should see output similar to this:

  ```bash
  gpg (GnuPG) 2.4.5; Copyright (C) 2024 g10 Code GmbH
This is free software: you are free to change and redistribute it.
There is NO WARRANTY, to the extent permitted by law.

Note: Use "gpg --full-generate-key" for a full featured key generation dialog.

GnuPG needs to construct a user ID to identify your key.

Real name: JourneyApps
Email address: platform@journeyapps.com
You selected this USER-ID:
    "JourneyApps <platform@journeyapps.com>"

Change (N)ame, (E)mail, or (O)kay/(Q)uit? O
We need to generate a lot of random bytes. It is a good idea to perform
some other action (type on the keyboard, move the mouse, utilize the
disks) during the prime generation; this gives the random number
generator a better chance to gain enough entropy.
We need to generate a lot of random bytes. It is a good idea to perform
some other action (type on the keyboard, move the mouse, utilize the
disks) during the prime generation; this gives the random number
generator a better chance to gain enough entropy.
gpg: directory '/Users/dominic/.gnupg/openpgp-revocs.d' created
gpg: revocation certificate stored as '/Users/dominic/.gnupg/openpgp-revocs.d/9E1F4A4844F2B89544F874A4A37DD86BFF6185D8.rev'
public and secret key created and signed.

pub   ed25519 2025-01-29 [SC] [expires: 2028-01-29]
      9E1F4A4844F2B89544F874A4A37DD86BFF6185D8
uid                      JourneyApps <platform@journeyapps.com>
sub   cv25519 2025-01-29 [E] [expires: 2028-01-29]
```

2. Run `gpg --list-keys --keyid-format short` and you should see something similar to:

```bash
[keyboxd]
---------
pub   ed25519/A75FC02F 2024-02-01 [SC] [expires: 2027-01-31]
      384D816F5916E311ED3E8DE378DD0F00A75FC02F
uid         [ unknown] journeyapps <platform@journeyapps.com>
sub   cv25519/93E4B9CB 2024-02-01 [E] [expires: 2027-01-31]
```

Note that the `SECRET_KEY_ID` is taken from `ed25519/A75FC02F` and is `A75FC02F`. Update the secret in Github.

3. You now need to upload the public key to gpg key servers to Maven Central can access it. In the above example this is `384D816F5916E311ED3E8DE378DD0F00A75FC02F`. To do that run the following:
```bash
gpg --keyserver keyserver.ubuntu.com --send-keys 384D816F5916E311ED3E8DE378DD0F00A75FC02F <- use your key not this one
```

```bash
gpg --keyserver keys.openpgp.org --send-keys 384D816F5916E311ED3E8DE378DD0F00A75FC02F <- use your key not this one
```

```bash
gpg --keyserver pgp.mit.edu --send-keys 384D816F5916E311ED3E8DE378DD0F00A75FC02F <- use your key not this one
```

4. Now you need to get the `SECRET_KEY`. To do this run `gpg --export-secret-keys --armor 384D816F5916E311ED3E8DE378DD0F00A75FC02F <- use your key not this one | pbcopy`. Which will copy the secret to your clipboard. Paste this into the Github secret `SECRET_KEY` and remove the new line at the end if there is one.
