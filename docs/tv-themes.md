# TV standby themes

The TV Settings menu includes **Background theme**. Four locally rendered choices are available:

- Moonlit lake: the existing generated image asset (default).
- Northern lights: original static Canvas gradients and an aurora ribbon.
- Misty mountains: original static Canvas layered silhouettes.
- Midnight: original static Canvas dark gradients.

Remote left/right changes the preview only. OK applies the focused theme, marks it selected,
and saves its stable identifier in receiver preferences. Back or Close leaves unapplied
previews unsaved. New home views restore the applied choice; unknown identifiers fall back
to Moonlit lake. No download, extra permission, animation loop, or protocol change is involved.

Themes draw only in the standby branch. Playback controls retain the existing translucent
scrim and floating card; no theme artwork is drawn above video. The theme preview in Settings
is an explicitly opened, bounded preview, not a replacement of the player or video surface.

## Validation

`TvUiTestDriver.themeChecks` covers unknown-ID fallback, focus-only preview, Back cancellation,
explicit selection and persistence, restoration into a new home view, and opaque standby /
translucent playback pixels for all four themes. It restores the prior preference afterward.
Run the existing Android TV instrumentation workflow to execute it and capture each preview.
Compilation or lint alone does not establish actual remote navigation, rendered appearance,
or ongoing playback on a device. No new device evidence is claimed by this change.

## Artwork provenance and license

The Moonlit lake image was generated specifically for this project and already shipped in the cinema preview. The other three backgrounds are original MoonCast Canvas code, supplied as source with this GPLv3 application; they are not downloaded stock photographs or third-party branded artwork. No separate stock-image license or external image service is required at runtime. They are rendered at the actual view size, not advertised as native 4K imagery.
