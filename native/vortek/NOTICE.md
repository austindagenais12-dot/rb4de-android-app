# Android Vortek renderer integration

This app uses the real Android Vortek transport server compiled from the
**pinned public source revisions**:

- JICA98/vortek commit df8183df5c2b024f116a5f796a9e4147aa696cd0
  (server source, LGPL-2.1)
- JICA98/winlator-app commit 72ec347c9ced676e206fbc3762b9d567852cb3e3
  (support libraries, including the libadrenotools subproject)
- The corresponding Linux glibc Vortek client is
  JICA98/vortek commit 9325b6060fc1c690234e102fcbbb1e0283b8892e.

The Android server is linked to an app-private Unix socket and sends shared
memory file descriptors to the pinned Linux Vulkan client. **This milestone does
not yet present GPU image buffers to an Android display, track native X11
window IDs, or establish that Rock Band 4 itself boots.** These require an
Android compositor/surface integration and guest compatibility work.

Keep upstream source, license files, and commit references publicly accessible
for LGPL compliance. The bridge itself is licensed LGPL-2.1-or-later.
