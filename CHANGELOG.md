# Changelog

## 0.1.2

- Fix: chart page stuck on "loading" — server sends S1 pos_lines as a dict, not a list, and the parser threw it away; now both shapes parse, and a failed chart load shows an error with a retry button instead of loading forever.
- Chart period row scrolls horizontally and highlights the selected period.
- Polish: brighter up/down colors for the dark theme, direction (加仓/减仓) colored in signal rows, tab icons remapped to fit each section, custom K-line launcher icon.

## 0.1.1

- Fix: allow cleartext HTTP to the local-network server (Android blocks it by default, which showed "server unreachable" while the browser worked).
- All in-app text and notifications are now in Chinese.

## 0.1.0

- Initial version: local-network client with five sections, native charts, and local notifications.
