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

## One-time steps (on your build machine / in your repo, not on the phone)

1. Download the real `cloudflared` **Linux ARM64** binary (this project
   only builds for `arm64-v8a`, per `app/build.gradle.kts`) from
   Cloudflare's own releases:
   `https://github.com/cloudflare/cloudflared/releases`
   → look for a file named like `cloudflared-linux-arm64`

2. Rename it to `libcloudflared.so`
   (Android's packager only accepts `.so`-named files under `jniLibs` —
   the name is just a required extension, it's still the real cloudflared
   binary, unmodified otherwise).

3. Place it at:
   ```
   app/src/main/jniLibs/arm64-v8a/libcloudflared.so
   ```

4. Make sure it's executable in git (some setups need this explicitly):
   ```bash
   chmod +x app/src/main/jniLibs/arm64-v8a/libcloudflared.so
   git update-index --chmod=+x app/src/main/jniLibs/arm64-v8a/libcloudflared.so
   ```

5. Commit and push. GitHub Actions builds the APK with the binary baked in.

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
