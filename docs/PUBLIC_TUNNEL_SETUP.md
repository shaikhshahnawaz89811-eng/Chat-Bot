# Public Tunnel — one-time project setup (no Termux, no other app)

The tunnel runs *inside Brain itself* as a real child process. Nothing to
install on the phone separately — the only one-time step is adding the
real `cloudflared` binary to the project **before you build the APK**.
After that, Start/Stop in the app controls the tunnel directly, forever.

## Why a binary has to be added at all
Android lets an app ship and run its own native helper binaries (the same
mechanism VPN apps and many others use), but it can't download and place
an executable at runtime and expect the OS to let it run — Android's
exec-permission rules only apply to files that were packaged into the
APK's `jniLibs` folder at build time. So the binary has to be part of the
project once, before building.

## Build-time dependency

The repository deliberately does **not** ship a prebundled cloudflared file.
That prevents an accidental wrong-architecture executable from ever being
installed in the APK. The GitHub Actions workflow downloads Cloudflare's
official latest `cloudflared-linux-arm64` release, verifies that the downloaded
file is an ARM64/aarch64 executable, marks it executable, and then runs the
normal Android build.

The source URL is Cloudflare's official release endpoint:
`https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64`

If you build outside GitHub Actions, add the same real ARM64 binary at:
```
app/src/main/jniLibs/arm64-v8a/libcloudflared.so
```
and make it executable before building. Never replace it with an x86-64
binary just because the filename is `libcloudflared.so`.

## After that
Install the APK → open Brain → Local API → **Start**. The Local API
Server and the Public Tunnel start together automatically; the live
`https://....trycloudflare.com` URL appears on that same screen once
cloudflared reports it (a few seconds). **Stop** kills both. Nothing to
type, nothing else to install — it's a real subprocess of Brain itself.

This is a free *quick tunnel* — no Cloudflare account needed, but the URL
is different each time you start it fresh (see PROGRESS.md if you later
want a fixed URL via a named tunnel + your own free DuckDNS subdomain —
that's a separate, bigger change).
