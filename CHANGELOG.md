# Changelog

## 0.1.4

- Chart rebuilt on Vico (compose-m3 2.1.4): candlestick pane plus a volume pane sharing one scroll/zoom state, so dragging and pinching move both panes together; right price axis, bottom time axis, tap a candle for an OHLC marker, and S1 buy/sell triangles drawn by a custom decoration that tracks its candle while scrolling and zooming.
- Period row now scrolls the selected period into view, so the default 60分钟 is no longer cut off at the right edge.
- Toolchain moved to Kotlin 2.1.21, AGP 8.7.3, Compose BOM 2025.04.01, compileSdk 35 for the Vico upgrade.

## 0.1.3

- Chart rebuilt following MPAndroidChart / Vico anatomy (no new dependency): right price axis with gridlines, bottom time axis, volume pane, dashed crosshair with OHLC info box and axis price tags, last-price line colored by up/down.
- Fix: volume bars were invisible because server volume is a float and the parser used longOrNull (every bar became 0).
- Market prices are now bold; chart hint text updated.

## 0.1.2

- Fix: chart page stuck on "loading" — server sends S1 pos_lines as a dict, not a list, and the parser threw it away; now both shapes parse, and a failed chart load shows an error with a retry button instead of loading forever.
- Chart period row scrolls horizontally and highlights the selected period.
- Polish: brighter up/down colors for the dark theme, direction (加仓/减仓) colored in signal rows, tab icons remapped to fit each section, custom K-line launcher icon.

## 0.1.1

- Fix: allow cleartext HTTP to the local-network server (Android blocks it by default, which showed "server unreachable" while the browser worked).
- All in-app text and notifications are now in Chinese.

## 0.1.0

- Initial version: local-network client with five sections, native charts, and local notifications.
