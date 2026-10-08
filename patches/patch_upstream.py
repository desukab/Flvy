from pathlib import Path

# FLVY-specific hooks into the upstream MacLaine engine.
app = Path("app/src/main/assets/splitflap/src/app.js")
s = app.read_text()

needle = "    this.bind();\n"
insert = """    this.bind();

    // FLVY is an AMOLED-first Android app. The upstream web app follows the
    // phone/OS light-mode preference; that would create a white flash or chrome
    // around the board. Keep the upstream editor fully functional but force its
    // chrome to the dark token set whenever it asks to change theme.
    const __flvyChromeTheme = this.chromeTheme.bind(this);
    this.chromeTheme = () => {
      __flvyChromeTheme();
      document.documentElement.dataset.chrome = 'dark';
    };
    this.chromeTheme();

    // Native wallpaper renderer uses this to render the WebView only while the
    // actual MacLaine board is moving, avoiding a permanent 60fps render loop.
    window.__flvyBoardIdle = () => !this.board || this.board.isIdle();

    addEventListener('flvy-notification', e => {
      const d = e.detail || {};
      const msg = [d.title, d.body].filter(Boolean).join('  · ').replace(/\s+/g, ' ').trim().slice(0, 120);
      if (!msg || !this.boards[this.active]) return;

      const idx = this.active;
      const original = this.boards[idx];
      const temporary = clone(original);
      temporary.name = 'Notification';
      temporary.pages = [{
        id: newId('p'),
        name: 'Notification',
        layout: 'full',
        dur: 8,
        wins: [],
        zones: [{ ch: 'message', o: { text: msg } }]
      }];

      this.boards[idx] = temporary;
      this.S.pageIdx = 0;
      this.S.pageStart = Date.now();
      this.refresh(true);

      clearTimeout(this.flvyNotificationTimer);
      this.flvyNotificationTimer = setTimeout(() => {
        if (this.boards[idx] === temporary) {
          this.boards[idx] = original;
          this.S.pageIdx = 0;
          this.S.pageStart = Date.now();
          this.refresh(true);
        }
      }, 8000);
    });
"""
assert s.count(needle) == 1
app.write_text(s.replace(needle, insert, 1))

# AMOLED black: the upstream Black theme intentionally has a dark room backdrop.
# FLVY wants the unlit OLED pixels to be literally black.
renderer = Path("app/src/main/assets/splitflap/src/renderer.js")
rs = renderer.read_text()
old = "backdrop: ['#141518', '#0A0A0C'], shadow: 0.6,"
new = "backdrop: ['#000000', '#000000'], shadow: 0,"
assert old in rs
renderer.write_text(rs.replace(old, new, 1))

# Kiosk chrome/body is also forced to black so no grey WebView wall leaks around the board.
css = Path("app/src/main/assets/splitflap/src/app.css")
cs = css.read_text()
cs = cs.replace("--bg: #13151A;", "--bg: #000000;", 1)
cs = cs.replace("html, body { margin: 0; background: #13151A; }",
                "html, body { margin: 0; background: #000000; }", 1)

# Never allow the upstream light chrome to leak into FLVY. Keep the board's
# own look/theme intact; this only normalizes the surrounding UI/background.
cs += """
\n/* FLVY Android: dark-only chrome. The board look itself remains user-selectable. */
html[data-chrome="light"] .sf {
  --bg: #000000; --bg-2: #080808; --surface: #0A0A0A;
  --text: #EDE6D6; --muted: #868991; --pale: #A3A8B0;
  --accent: #C8974A; --accent-soft: rgba(200,151,74,0.12); --accent-line: rgba(200,151,74,0.38);
  --danger: #E07A66; --danger-soft: rgba(224,122,102,0.12); --danger-line: rgba(224,122,102,0.45);
  --border: rgba(237,230,214,0.08); --border-2: rgba(237,230,214,0.15);
}
html, body { background: #000000 !important; color-scheme: dark; }
.sf-main, .sf-stage, .sf-canvas-wrap { background: #000000; }
"""
css.write_text(cs)
