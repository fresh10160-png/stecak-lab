#include "Views.h"
#include <cstdio>

using namespace juce;

namespace ui {

// ================================ PadView =====================================================
float PadView::glowLevel() const {
    if (held) return 1.f;
    const int64_t last = st.engine.lastHit[id()].load();
    if (last == 0) return 0.f;
    const float ms = (float) ((padlab::Engine::nowNs() - last) / 1000000);
    return jmax(0.f, 1.f - ms / 260.f);
}

void PadView::tick() {
    const float g = glowLevel();
    if (g > 0.f || lastGlow > 0.f) repaint();
    lastGlow = g;
}

void PadView::paint(Graphics& g) {
    const int pid = id();
    const padlab::Pad& p = st.engine.pads[(size_t) pid];
    const Colour bank = bankColour(st.bank);
    const float glw = glowLevel();
    const bool loaded = p.sample.get() != nullptr;

    auto b = getLocalBounds().toFloat().reduced(3.f);
    const float rad = 15.f;
    if (held) {
        const auto c = b.getCentre();
        g.addTransform(AffineTransform::scale(0.975f, 0.975f, c.x, c.y));
    }
    if (glw > 0.02f) ui::glow(g, b, rad, bank, glw * 1.1f, 6);

    // body: slightly raised rubber pad
    Colour top = loaded ? Colour(0xFF3A3E49) : Colour(0xFF22252B), bot = loaded ? Colour(0xFF23262D) : Colour(0xFF16181C);
    top = mix(top, bank.brighter(0.2f), glw * 0.95f);
    bot = mix(bot, bank.darker(0.2f), glw * 0.95f);
    g.setColour(Colours::black.withAlpha(0.55f)); // contact shadow
    g.fillRoundedRectangle(b.translated(0.f, 2.5f), rad);
    g.setGradientFill(ColourGradient::vertical(top, b.getY(), bot, b.getBottom()));
    g.fillRoundedRectangle(b, rad);
    // inner bevel
    g.setColour(Colours::white.withAlpha(0.10f + 0.1f * glw));
    g.drawRoundedRectangle(b.reduced(1.f), rad - 1.f, 1.f);
    g.setColour(Colours::black.withAlpha(0.35f));
    g.drawRoundedRectangle(b, rad, 1.f);
    if (st.selected == pid) {
        g.setColour(Colours::white.withAlpha(0.9f));
        g.drawRoundedRectangle(b.reduced(0.5f), rad, 2.f);
    }

    if (loaded) {                     // faint waveform thumbnail
        auto smp = p.sample.get();
        if (smp != thumbFor) {
            thumbFor = smp;
            thumb.assign(36, 0.f);
            const int n = smp->n();
            for (int i = 0; i < 36; ++i) {
                const int a = (int) ((int64_t) i * n / 36), e = std::max(a + 1, (int) ((int64_t) (i + 1) * n / 36));
                const int stride = std::max(1, (e - a) / 32);
                float pk = 0;
                for (int k = a; k < e && k < n; k += stride) pk = std::max(pk, std::abs(smp->l[(size_t) k]));
                thumb[(size_t) i] = pk;
            }
        }
        const float mid = b.getCentreY() + 4.f, maxH = b.getHeight() * 0.26f, bw = (b.getWidth() - 28.f) / 36.f;
        g.setColour((glw > 0.5f ? INK : bank).withAlpha(glw > 0.5f ? 0.35f : 0.30f));
        for (int i = 0; i < 36; ++i) {
            const float hh = std::max(1.5f, thumb[(size_t) i] * maxH);
            g.fillRoundedRectangle(b.getX() + 14.f + (float) i * bw + 0.5f, mid - hh, std::max(1.f, bw - 1.f), hh * 2.f, 1.f);
        }
    }

    const Colour fg = glw > 0.5f ? INK : TEXT;
    g.setFont(mono(12.f));
    g.setColour(glw > 0.5f ? INK : bank);
    g.drawText(String(index + 1), b.reduced(10.f, 6.f).toNearestInt(), Justification::topLeft, false);

    // tags (top-right)
    g.setFont(caps(9.f));
    String tags;
    if (p.mode == padlab::Pad::LOOP) tags += "LOOP  ";
    else if (p.mode == padlab::Pad::GATE) tags += "GATE  ";
    if (p.hasFx()) tags += "FX  ";
    if (loaded && p.warped()) tags += "TS";
    g.setColour(glw > 0.5f ? INK : (p.hasFx() || p.warped() ? CYAN : DIM));
    g.drawText(tags.trim(), b.reduced(10.f, 8.f).toNearestInt(), Justification::topRight, false);

    g.setFont(caps(12.f));
    g.setColour(fg);
    String name = String(p.label()).toUpperCase();
    if (name.length() > 9) name = name.substring(0, 9);
    g.drawText(name, b.reduced(6.f, 8.f).toNearestInt(), Justification::centredBottom, false);
}

void PadView::mouseDown(const MouseEvent& e) {
    held = true;
    const float vel = st.fullLevel ? 1.f : jlimit(0.2f, 1.f, 0.25f + 0.75f * (float) e.y / (float) getHeight());
    st.engine.trigger(id(), vel);
    if (st.select) st.select(id());
    repaint();
}

void PadView::mouseUp(const MouseEvent&) {
    if (!held) return;
    held = false;
    st.engine.release(id());
    repaint();
}

// ================================ Lcd =========================================================
void Lcd::vu(Graphics& g, float x, float level) {
    const int segs = 14;
    const float h = 54.f, top = 14.f, seg = h / (float) segs;
    const float lv = std::min(1.f, std::sqrt(level));
    for (int i = 0; i < segs; ++i) {
        const bool lit = lv > ((float) i + 0.5f) / (float) segs;
        const Colour col = i >= 12 ? REC : i >= 9 ? Colour(0xFFFFC83D) : GREEN;
        const float y1 = top + h - (float) i * seg;
        if (lit) { g.setColour(col.withAlpha(0.25f)); g.fillRoundedRectangle(x - 1.5f, y1 - seg - 0.5f, 14.f, seg + 1.f, 2.f); }
        g.setColour(lit ? col : Colour(0xFF1B1E24));
        g.fillRoundedRectangle(x, y1 - seg + 1.5f, 11.f, seg - 3.f, 2.f);
    }
}

void Lcd::paint(Graphics& g) {
    auto& e = st.engine;
    const auto b = getLocalBounds().toFloat().reduced(0.5f);
    const float w = b.getWidth(), h = b.getHeight();
    g.setGradientFill(ColourGradient::vertical(Colour(0xFF0D1014), b.getY(), LCD, b.getBottom()));
    g.fillRoundedRectangle(b, 14.f);
    g.setColour(STROKE);
    g.drawRoundedRectangle(b, 14.f, 1.f);
    g.setColour(Colours::white.withAlpha(0.05f));
    g.drawLine(14.f, 1.5f, w - 14.f, 1.5f);

    const Colour bank = bankColour(st.selected / 16);
    g.setFont(caps(9.f));
    g.setColour(DIM);
    g.drawText("BPM", Rectangle<float>(16.f, 8.f, 60.f, 12.f), Justification::centredLeft, false);
    // glowing numerals
    const String bpm(roundToInt(e.bpm.load()));
    g.setFont(mono(36.f));
    g.setColour(ACCENT.withAlpha(0.22f));
    g.drawText(bpm, Rectangle<float>(14.f, 20.f, 110.f, 42.f), Justification::centredLeft, false);
    g.setColour(ACCENT);
    g.drawText(bpm, Rectangle<float>(14.f, 20.f, 110.f, 42.f), Justification::centredLeft, false);
    g.setFont(caps(9.5f));
    g.setColour(e.playing ? GREEN : DIM);
    g.drawText(e.playing ? "PLAYING" : "STOPPED", Rectangle<float>(16.f, 61.f, 90.f, 12.f), Justification::centredLeft, false);

    const float mx = w * 0.36f;
    const padlab::Pad& pad = e.pads[(size_t) st.selected];
    g.setFont(caps(9.f));
    g.setColour(DIM);
    g.drawText("PAD", Rectangle<float>(mx, 8.f, 60.f, 12.f), Justification::centredLeft, false);
    char tag[8];
    std::snprintf(tag, sizeof tag, "%c%02d", 'A' + st.selected / 16, st.selected % 16 + 1);
    g.setFont(mono(28.f));
    g.setColour(bank.withAlpha(0.25f));
    g.drawText(tag, Rectangle<float>(mx - 1.f, 18.f, 90.f, 34.f), Justification::centredLeft, false);
    g.setColour(bank);
    g.drawText(tag, Rectangle<float>(mx, 18.f, 90.f, 34.f), Justification::centredLeft, false);
    auto smp = pad.sample.get();
    String name = smp ? String(pad.label()).toUpperCase() : String("- EMPTY -");
    if (name.length() > 12) name = name.substring(0, 12);
    g.setFont(caps(12.f));
    g.setColour(TEXT);
    g.drawText(name, Rectangle<float>(mx, 47.f, 120.f, 14.f), Justification::centredLeft, false);
    if (smp) {
        char info[48];
        std::snprintf(info, sizeof info, "%.2fs  %s", smp->n() / (float) padlab::SR, padlab::Pad::MODES[pad.mode.load()]);
        g.setFont(sans(10.f));
        g.setColour(DIM);
        g.drawText(info, Rectangle<float>(mx, 62.f, 130.f, 12.f), Justification::centredLeft, false);
    }

    const float vx = w - 46.f;
    vu(g, vx, e.vuL);
    vu(g, vx + 17.f, e.vuR);

    const int cur = e.curStep;
    const float gx = 16.f, gw = w - 32.f, cell = gw / 16.f;
    for (int i = 0; i < 16; ++i) {
        const bool on = i == cur;
        const Rectangle<float> r(gx + (float) i * cell + 1.f, h - 10.f, cell - 2.f, 4.f);
        if (on) { g.setColour(ACCENT.withAlpha(0.35f)); g.fillRoundedRectangle(r.expanded(2.f), 3.f); }
        g.setColour(on ? ACCENT : (i % 4 == 0 ? Colour(0xFF2A2E37) : Colour(0xFF1B1E24)));
        g.fillRoundedRectangle(r, 2.f);
    }
}

// ================================ WaveView ====================================================
void WaveView::set(std::shared_ptr<const padlab::Sample> s, float st_, float en_, Colour acc, bool b) {
    if (s == sample && st_ == start && en_ == end && acc == accent && b == busy) return;
    sample = std::move(s);
    start = st_;
    end = en_;
    accent = acc;
    busy = b;
    repaint();
}

void WaveView::paint(Graphics& g) {
    const int w = getWidth(), h = getHeight();
    const auto bounds = getLocalBounds().toFloat();
    g.setGradientFill(ColourGradient::vertical(Colour(0xFF090B0E), 0.f, Colour(0xFF0E1116), (float) h));
    g.fillRoundedRectangle(bounds, 12.f);
    g.saveState();
    Path clip;
    clip.addRoundedRectangle(bounds, 12.f);
    g.reduceClipRegion(clip);
    g.setColour(Colour(0xFF1B1E24));
    for (int i = 1; i < 8; ++i) g.drawVerticalLine(w * i / 8, 0.f, (float) h);
    g.drawHorizontalLine(h / 2, 0.f, (float) w);

    if (!sample) {
        g.setColour(DIM);
        g.setFont(caps(12.f));
        g.drawText("EMPTY  -  LOAD A FILE OR RECORD", getLocalBounds(), Justification::centred, false);
        g.restoreState();
        return;
    }
    if (peaksW != w || peaksFor != sample) {
        peaks.assign((size_t) w, 0.f);
        peaksW = w;
        peaksFor = sample;
        const int n = sample->n();
        for (int x = 0; x < w; ++x) {
            const int a = (int) ((int64_t) x * n / w), b = std::max(a + 1, (int) ((int64_t) (x + 1) * n / w));
            const int stride = std::max(1, (b - a) / 48);
            float pk = 0;
            for (int i = a; i < b && i < n; i += stride) pk = std::max(pk, std::abs(sample->l[(size_t) i]));
            peaks[(size_t) x] = pk;
        }
    }
    const float sx = start * (float) w, ex = end * (float) w;
    for (int x = 0; x < w; ++x) {
        const bool in = (float) x >= sx && (float) x <= ex;
        const float a = std::max(1.f, peaks[(size_t) x] * (float) h * 0.46f);
        g.setColour(in ? accent : Colour(0xFF3A3F49));
        g.drawVerticalLine(x, (float) h / 2.f - a, (float) h / 2.f + a);
    }
    g.setColour(Colours::black.withAlpha(0.6f));
    g.fillRect(0.f, 0.f, sx, (float) h);
    g.fillRect(ex, 0.f, (float) w - ex, (float) h);
    if (!readOnly) {
        handle(g, sx, "S", true);
        handle(g, ex, "E", false);
    }
    g.setFont(mono(10.f));
    g.setColour(DIM);
    char t[24];
    std::snprintf(t, sizeof t, "%.2fs", (end - start) * sample->n() / (float) padlab::SR);
    g.drawText(t, getLocalBounds().reduced(10, 6), Justification::bottomLeft, false);
    if (busy) {
        g.setColour(CYAN);
        g.drawText("STRETCHING...", getLocalBounds().reduced(10, 6), Justification::bottomRight, false);
    }
    g.restoreState();
}

void WaveView::handle(Graphics& g, float x, const char* t, bool left) {
    g.setColour(TEXT);
    g.drawLine(x, 0.f, x, (float) getHeight(), 2.f);
    const float fw = 20.f, fh = 20.f, x0 = left ? x : x - fw;
    g.fillRoundedRectangle(x0, 0.f, fw, fh, 4.f);
    g.setColour(INK);
    g.setFont(sans(12.f, true));
    g.drawText(t, Rectangle<float>(x0, 0.f, fw, fh), Justification::centred, false);
}

void WaveView::mouseDown(const MouseEvent& e) {
    if (!sample || readOnly) return;
    const float x = (float) e.x / (float) getWidth();
    dragging = std::abs(x - start) <= std::abs(x - end) ? 0 : 1;
    if (std::abs(start - end) < 0.02f) dragging = x < start ? 0 : 1;
    drag(e);
}
void WaveView::mouseDrag(const MouseEvent& e) { if (sample && !readOnly) drag(e); }
void WaveView::mouseUp(const MouseEvent&) {
    if (!sample || readOnly) return;
    if (onTrim) onTrim(start, end, true);
    dragging = -1;
}
void WaveView::drag(const MouseEvent& e) {
    const float x = jlimit(0.f, 1.f, (float) e.x / (float) getWidth());
    if (dragging == 0) start = jmax(0.f, jmin(end - 0.01f, x));
    else if (dragging == 1) end = jmin(1.f, jmax(start + 0.01f, x));
    repaint();
    if (onTrim) onTrim(start, end, false);
}

// ================================ StepGrid ====================================================
void StepGrid::tick() {
    const int cur = st.engine.curStep;
    if (cur != lastStep) { lastStep = cur; repaint(); }
}

void StepGrid::paint(Graphics& g) {
    auto& e = st.engine;
    const int cur = e.curStep;
    const Colour bank = bankColour(st.bank);
    const float W = (float) getWidth(), H = (float) getHeight();
    const float lw = labelW(), hh = headH();
    const float cw = (W - lw) / 16.f, ch = (H - hh) / 16.f;

    g.setFont(mono(9.f));
    for (int s = 0; s < 16; ++s) {
        g.setColour(s == cur ? ACCENT : (s % 4 == 0 ? TEXT : DIM));
        g.drawText(String(s + 1), Rectangle<float>(lw + (float) s * cw, 0.f, cw, hh - 2.f), Justification::centred, false);
    }
    const float pad = 1.6f, rad = 5.f;
    for (int row = 0; row < 16; ++row) {
        const int id = st.bank * 16 + (15 - row);
        const float top = hh + (float) row * ch;
        const bool sel = st.selected == id;
        g.setFont(caps(jmin(10.5f, ch * 0.55f)));
        g.setColour(sel ? bank : DIM);
        String t = String::formatted("%02d ", 16 - row) + String(e.pads[(size_t) id].label()).toUpperCase();
        if (t.length() > 10) t = t.substring(0, 10);
        g.drawText(t, Rectangle<float>(2.f, top, lw - 2.f, ch), Justification::centredLeft, false);
        const uint16_t bits = e.stepBits[id].load();
        for (int s = 0; s < 16; ++s) {
            const bool on = (bits >> s) & 1;
            const Rectangle<float> r(lw + (float) s * cw + pad, top + pad, cw - 2 * pad, ch - 2 * pad);
            if (on) {
                const Colour c = s == cur ? mix(bank, Colours::white, 0.55f) : bank;
                g.setColour(c.withAlpha(0.22f));
                g.fillRoundedRectangle(r.expanded(1.5f), rad + 1.5f);
                g.setGradientFill(ColourGradient::vertical(c.brighter(0.15f), r.getY(), c.darker(0.25f), r.getBottom()));
            } else {
                g.setColour(((s / 4) % 2 == 0) ? Colour(0xFF22252C) : Colour(0xFF191B21));
            }
            g.fillRoundedRectangle(r, rad);
        }
    }
    if (cur >= 0) {
        g.setColour(Colours::white.withAlpha(0.08f));
        g.fillRect(lw + (float) cur * cw, hh, cw, H - hh);
    }
}

void StepGrid::mouseDown(const MouseEvent& e) {
    const float W = (float) getWidth(), H = (float) getHeight();
    const float cw = (W - labelW()) / 16.f, ch = (H - headH()) / 16.f;
    const int row = (int) (((float) e.y - headH()) / ch), col = (int) (((float) e.x - labelW()) / cw);
    if (row < 0 || row > 15) return;
    const int id = st.bank * 16 + (15 - row);
    if ((float) e.x < labelW()) {
        if (st.select) st.select(id);
        st.engine.trigger(id, 1.f);
        auditioned = id;
        repaint();
        return;
    }
    if (col < 0 || col > 15) return;
    const uint16_t bit = (uint16_t) (1u << col);
    paintValue = !(st.engine.stepBits[id].load() & bit);
    if (paintValue) st.engine.stepBits[id].fetch_or(bit); else st.engine.stepBits[id].fetch_and((uint16_t) ~bit);
    paintRow = row;
    paintCol = col;
    if (st.select) st.select(id);
    repaint();
}

void StepGrid::mouseDrag(const MouseEvent& e) {
    if (paintRow < 0) return;
    const float W = (float) getWidth(), H = (float) getHeight();
    const float cw = (W - labelW()) / 16.f, ch = (H - headH()) / 16.f;
    const int row = (int) (((float) e.y - headH()) / ch), col = (int) (((float) e.x - labelW()) / cw);
    if (row < 0 || row > 15 || col < 0 || col > 15) return;
    if (row == paintRow && col == paintCol) return;
    const int id = st.bank * 16 + (15 - row);
    const uint16_t bit = (uint16_t) (1u << col);
    if (paintValue) st.engine.stepBits[id].fetch_or(bit); else st.engine.stepBits[id].fetch_and((uint16_t) ~bit);
    paintRow = row;
    paintCol = col;
    repaint();
}

void StepGrid::mouseUp(const MouseEvent&) {
    if (auditioned >= 0) st.engine.release(auditioned);
    auditioned = -1;
    paintRow = -1;
}

} // namespace ui
