# Public Tunnel — real build and runtime setup

The tunnel runs inside Brain as a real `cloudflared` child process. Nothing
has to be installed separately on the phone.

## Build-time dependency

The repository does not commit the `cloudflared` executable itself. The
GitHub Actions workflow downloads the official ARM64 release during the APK
build, verifies its SHA-256 checksum, verifies the ELF architecture, and then
packages it into:

```text
app/src/main/jniLibs/arm64-v8a/libcloudflared.so
```

The workflow currently pins `cloudflared` **2026.7.3** instead of using a
moving `latest` URL. This makes the APK build reproducible and prevents a
future release from changing the binary underneath an otherwise identical
source commit.

If you build outside GitHub Actions, provide the same real ARM64 executable
at the path above and verify its architecture before building. Never rename
an x86-64 executable to look like an ARM64 library.

## Runtime flow

1. Open **Local API**.
2. Tap **Start Server**.
3. The real Local API foreground service starts NanoHTTPD on port `11434`.
4. The Public Tunnel remains **Off** at this point.
5. Tap **Start Public Tunnel** only when you actually want public access.
6. Brain starts the bundled `cloudflared` process and points it at the real
   local API endpoint `http://127.0.0.1:11434`.
7. The UI changes to **Running** only after a real
   `https://....trycloudflare.com` URL is parsed from cloudflared output.
8. **Stop Public Tunnel** terminates that real child process.
9. **Stop Server** also stops any active tunnel before shutting down the
   Local API server.

If the executable is missing, cannot execute, exits early, or never reports a
real tunnel URL, the UI shows an actual error rather than a fabricated
running state.

## Important distinction

The Local API and the Public Tunnel are separate runtime controls. Starting
the Local API no longer automatically creates public exposure. The tunnel is
an explicit user action.

The quick-tunnel URL is temporary and can change after a fresh tunnel start.
A fixed named tunnel/domain is outside this feature's current scope.
