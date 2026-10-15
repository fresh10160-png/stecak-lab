#include "FxRack.h"

using namespace juce;

namespace ui {

// ================================ FxTiles =====================================================
void FxTiles::paint(Graphics& g) {
    if (!chain) return;
    const int n = (int) chain->size();
    const float tw = (float) getWidth() / (float) COLS, th = (float) getHeight() / (float) rows();
    for (int i = 0; i <= n; ++i) {
        const float x = (float) (i % COLS) * tw, y = (float) (i / COLS) * th;
        const Rectangle<float> r(x + 3.f, y + 3.f, tw - 6.f, th - 6.f);
        const bool all = i == n, on = !all && (*chain)[(size_t) i]->on, sel = !all && i == selected;
        if (on) ui::glow(g, r, 10.f, CYAN, 0.7f, 4);
        Colour top = on ? mix(PANEL2, CYAN, 0.28f) : (all ? PANEL : Colour(0xFF252932));
        Colour bot = on ? mix(PANEL2, CYAN, 0.12f) : (all ? Colour(0xFF121418) : Colour(0xFF1B1E24));
        g.setGradientFill(ColourGradient::vertical(top, r.getY(), bot, r.getBottom()));
        g.fillRoundedRectangle(r, 10.f);
        g.setColour(sel ? Colours::white : (on ? CYAN : STROKE));
        g.drawRoundedRectangle(r, 10.f, sel ? 2.f : 1.f);
        if (!all) {
            const Point<float> led(r.getX() + 12.f, r.getY() + 12.f);
            if (on) { g.setColour(CYAN.withAlpha(0.3f)); g.fillEllipse(led.x - 7.f, led.y - 7.f, 14.f, 14.f); }
            g.setColour(on ? CYAN : Colour(0xFF343842));
            g.fillEllipse(led.x - 3.5f, led.y - 3.5f, 7.f, 7.f);
        }
        g.setFont(caps(10.f));
        g.setColour(on ? TEXT : (all ? DIM : Colour(0xFFB5BAC4)));
        const String nm = all ? String("ALL OFF") : String((*chain)[(size_t) i]->name);
        const int sp = nm.indexOfChar(' ');
        if (sp > 0 && !all) {
            g.drawText(nm.substring(0, sp), r.withTrimmedTop(r.getHeight() * 0.38f).withHeight(14.f), Justification::centred, false);
            g.drawText(nm.substring(sp + 1), r.withTrimmedTop(r.getHeight() * 0.38f + 13.f).withHeight(14.f), Justification::centred, false);
        } else {
            g.drawText(nm, r, Justification::centred, false);
        }
    }
}

void FxTiles::mouseUp(const MouseEvent& e) {
    if (!chain || e.getDistanceFromDragStart() > 10) return;
    const int col = (int) ((float) e.x / ((float) getWidth() / (float) COLS));
    const int row = (int) ((float) e.y / ((float) getHeight() / (float) rows()));
    const int i = row * COLS + col;
    if (i >= 0 && i <= (int) chain->size() && onTap) onTap(i);
}

// ================================ FxRack ======================================================
std::vector<FxRack::Preset> FxRack::presets() {
    using padlab::Fx;
    return {
        {"CLEAN", {}, {}},
        {"303 VINYL", {Fx::COMP, Fx::FILTER, Fx::VINYL}, {{0.5f, 0.35f, 0.55f}, {0.82f, 0.15f, 0.1f}, {0.4f, 0.5f, 0.5f}}},
        {"DUSTY LO-FI", {Fx::LOFI, Fx::FILTER, Fx::VINYL}, {{0.35f, 0.4f, 0.35f}, {0.68f, 0.2f, 0.25f}, {0.55f, 0.65f, 0.4f}}},
        {"TAPE ECHO", {Fx::FILTER, Fx::DELAY, Fx::REVERB}, {{0.75f, 0.1f, 0.1f}, {0.5f, 0.65f, 0.4f}, {0.45f, 0.5f, 0.2f}}},
        {"ISO: NO LOW", {Fx::ISOLATOR}, {{0.f, 1.f, 1.f}}},
        {"ISO: MID ONLY", {Fx::ISOLATOR}, {{0.f, 1.f, 0.f}}},
        {"RING DRONE", {Fx::RING, Fx::RESO}, {{0.35f, 0.5f}, {0.3f, 0.85f, 0.6f}}},
        {"202 SPACE", {Fx::FLANGER, Fx::DELAY, Fx::REVERB}, {{0.3f, 0.55f, 0.5f}, {0.6f, 0.4f, 0.3f}, {0.8f, 0.4f, 0.4f}}},
        {"GATE CHOP", {Fx::SLICER, Fx::COMP}, {{0.5f, 1.f, 0.5f}, {0.7f, 0.3f, 0.6f}}},
        {"FILTER SWEEP", {Fx::FILTER}, {{0.3f, 0.75f, 0.35f}}},
    };
}

FxRack::FxRack(UiState& s) : st(s) {
    addAndMakeVisible(presetView);
    presetView.setViewedComponent(&presetRow, false);
    presetView.setScrollBarsShown(false, false);
    presetView.setScrollOnDragMode(Viewport::ScrollOnDragMode::all);
    int x = 0;
    for (const auto& p : presets()) {
        auto b = std::make_unique<Btn>(p.name);
        b->setSize(11.f);
        const int w = (int) GlyphArrangement::getStringWidth(caps(11.f), p.name) + 30;
        b->setBounds(x, 2, w, 34);
        b->onClick = [this, p] { apply(p); };
        presetRow.addAndMakeVisible(*b);
        presetBtns.push_back(std::move(b));
        x += w + 6;
    }
    presetRow.setSize(x, 38);

    addAndMakeVisible(tiles);
    tiles.onTap = [this](int i) {
        if (!chain) return;
        if (i == (int) chain->size()) {
            for (auto& f : *chain) f->enable(false);
        } else if (i == sel) {
            cur().enable(!cur().on);
        } else {
            sel = i;
        }
        refresh();
    };

    title.setFont(sans(16.f, true));
    title.setColour(Label::textColourId, TEXT);
    addAndMakeVisible(title);
    power.setLed(true).setAccent(CYAN).setText("ON");
    power.onClick = [this] { if (chain) { cur().enable(!cur().on); refresh(); } };
    addAndMakeVisible(power);

    for (int i = 0; i < 3; ++i) {
        knobs[(size_t) i] = std::make_unique<Knob>("", 0.f, 1.f, 0.5f);
        knobs[(size_t) i]->setAccent(CYAN);
        knobs[(size_t) i]->onChange = [this, i](float v) {
            if (!chain) return;
            cur().p[(size_t) i] = v;
            xy.set(cur().p[0], cur().labels.size() > 1 ? cur().p[1].load() : 0.5f);
        };
        knobs[(size_t) i]->fmt = [this, i](float) { return chain ? String(cur().text(i)) : String(); };
        addAndMakeVisible(*knobs[(size_t) i]);
    }
    xy.onMove = [this](float x, float y) {
        if (!chain) return;
        auto& f = cur();
        f.p[0] = x;
        if (f.labels.size() > 1) f.p[1] = y;
        for (int i = 0; i < 3; ++i) knobs[(size_t) i]->set(i < f.params() ? f.p[(size_t) i].load() : 0.f);
        if (!f.on) { f.enable(true); refresh(); }
    };
    addAndMakeVisible(xy);

    caption.setFont(sans(11.f));
    caption.setColour(Label::textColourId, DIM);
    caption.setJustificationType(Justification::centred);
    addAndMakeVisible(caption);
}

void FxRack::bind(std::shared_ptr<padlab::Chain> c, const String& n) {
    chain = std::move(c);
    name = n;
    if (chain && sel >= (int) chain->size()) sel = 0;
    tiles.bind(chain, sel);   // tile height depends on the chain length, so bind before laying out
    resized();
    refresh();
}

void FxRack::resized() {
    auto r = getLocalBounds();
    presetView.setBounds(r.removeFromTop(40));
    r.removeFromTop(6);
    tiles.setBounds(r.removeFromTop(tiles.preferredHeight()));
    r.removeFromTop(10);
    auto head = r.removeFromTop(40);
    power.setBounds(head.removeFromRight(88).reduced(0, 2));
    title.setBounds(head);
    auto kr = r.removeFromTop(92);
    const int kw = kr.getWidth() / 3;
    for (auto& k : knobs) k->setBounds(kr.removeFromLeft(kw));
    r.removeFromTop(6);
    xy.setBounds(r.removeFromTop(170));
    caption.setBounds(r.removeFromTop(22));
}

void FxRack::refresh() {
    if (!chain || chain->empty()) return;
    auto& f = cur();
    tiles.bind(chain, sel);
    title.setText(name + "  >  " + String(f.name), dontSendNotification);
    power.setActive(f.on).setText(f.on ? "ON" : "OFF");
    for (int i = 0; i < 3; ++i) {
        const bool has = i < f.params();
        knobs[(size_t) i]->setVisible(has);
        if (has) {
            knobs[(size_t) i]->setLabel(String(f.labels[(size_t) i])).setDim(!f.on);
            knobs[(size_t) i]->set(f.p[(size_t) i]);
        }
    }
    xy.set(f.p[0], f.params() > 1 ? f.p[1].load() : 0.5f);
    xy.setLabels(String(f.labels[0]), f.params() > 1 ? String(f.labels[1]) : String());
    caption.setText("Drag the pad to play " + String(f.labels[0]) + (f.params() > 1 ? " x " + String(f.labels[1]) : String()), dontSendNotification);
    repaint();
}

void FxRack::apply(const Preset& p) {
    if (!chain) return;
    for (auto& f : *chain) { f->enable(false); f->reset(); }
    for (size_t i = 0; i < p.ids.size(); ++i) {
        auto& f = *(*chain)[(size_t) p.ids[i]];
        for (size_t k = 0; k < p.vals[i].size(); ++k) f.p[k] = p.vals[i][k];
        f.enable(true);
    }
    if (!p.ids.empty()) sel = p.ids[0];
    refresh();
}

} // namespace ui
