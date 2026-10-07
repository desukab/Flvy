from pathlib import Path

p = Path("app/src/main/assets/splitflap/src/app.js")
s = p.read_text()
needle = "    this.bind();\n"
insert = """    this.bind();
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
p.write_text(s.replace(needle, insert, 1))
