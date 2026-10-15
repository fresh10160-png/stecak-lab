#include "VoicePanel.h"
#include <thread>

using namespace juce;

namespace ui {

// ================================ RollView ====================================================
void RollView::set(std::vector<padlab::Melody::Out> n, double step, int sel) {
    notes = std::move(n);
    gridStep = step;
    selected = sel;
    double end = 2;
    int mn = 127, mx = 0;
    for (auto& o : notes) {
        end = std::max(end, o.start + o.dur + 0.2);
        mn = std::min(mn, o.midi);
        mx = std::max(mx, o.midi);
    }
    total = end;
    if (!notes.empty()) {
        lo = mn - 2;
        hi = mx + 2;
        while (hi - lo < 12) { --lo; ++hi; }
    }
    repaint();
}

int RollView::contentWidth() const { return (int) (GUTTER + total * PX_PER_SEC); }

void RollView::paint(Graphics& g) {
    static const bool black[12] = {false, true, false, true, false, false, true, false, true, false, true, false};
    const float w = (float) getWidth(), h = (float) getHeight();
    const int rows = hi - lo + 1;
    const float rh = h / (float) rows;
    g.setGradientFill(ColourGradient::vertical(Colour(0xFF090B0E), 0.f, Colour(0xFF0E1116), h));
    g.fillRect(0.f, 0.f, w, h);
    for (int m = lo; m <= hi; ++m) {
        const float y = h - (float) (m - lo + 1) * rh;
        g.setColour(black[((m % 12) + 12) % 12] ? Colour(0xFF0E1013) : Colour(0xFF14171B));
        g.fillRect(GUTTER, y, w - GUTTER, rh);
        g.setColour(Colour(0xFF1C2026));
        g.fillRect(GUTTER, y + rh - 1.f, w - GUTTER, 1.f);
    }
    const float pxs = (w - GUTTER) / (float) total;
    if (gridStep > 0) {
        int i = 0;
        for (double t = 0; t < total; t += gridStep, ++i) {
            g.setColour(i % 4 == 0 ? Colour(0xFF2D323B) : Colour(0xFF1B1F25));
            g.drawVerticalLine((int) (GUTTER + (float) t * pxs), 0.f, h);
        }
    } else {
        g.setColour(Colour(0xFF22262D));
        for (int s = 0; s < total; ++s) g.drawVerticalLine((int) (GUTTER + (float) s * pxs), 0.f, h);
    }
    g.setColour(Colour(0xFF101216));
    g.fillRect(0.f, 0.f, GUTTER, h);
    g.setFont(mono(9.f));
    g.setColour(DIM);
    for (int m = lo; m <= hi; ++m)
        if (m % 12 == 0 || m % 12 == 7)
            g.drawText(String(padlab::Melody::noteName(m)), Rectangle<float>(3.f, h - (float) (m - lo + 1) * rh, GUTTER - 4.f, rh), Justification::centredLeft, false);
    if (notes.empty()) {
        g.setFont(caps(12.f));
        g.drawText("SING INTO THE MIC  -  NOTES APPEAR HERE", getLocalBounds(), Justification::centred, false);
        return;
    }
    for (const auto& o : notes) {
        const float x0 = GUTTER + (float) o.start * pxs, x1 = GUTTER + (float) (o.start + o.dur) * pxs - 1.5f;
        const float y1 = h - (float) (o.midi - lo) * rh;
        Rectangle<float> r(x0, y1 - rh + 1.f, std::max(x1 - x0, 4.f), rh - 2.f);
        const bool sel = o.src == selected;
        const Colour c = mix(Colour(0xFF5A2D0A), ACCENT, o.vel);
        g.setColour(c.withAlpha(0.25f));
        g.fillRoundedRectangle(r.expanded(2.f), 6.f);
        g.setGradientFill(ColourGradient::vertical(c.brighter(0.2f), r.getY(), c.darker(0.2f), r.getBottom()));
        g.fillRoundedRectangle(r, 4.f);
        if (sel) { g.setColour(Colours::white); g.drawRoundedRectangle(r, 4.f, 2.f); }
        if (r.getWidth() > 26.f && rh > 11.f) {
            g.setFont(mono(std::min(10.f, rh * 0.8f)));
            g.setColour(INK);
            g.drawText(String(padlab::Melody::noteName(o.midi)), r.reduced(4.f, 0.f), Justification::centredLeft, false);
        }
    }
}

void RollView::mouseUp(const MouseEvent& e) {
    if (e.getDistanceFromDragStart() > 10) return;
    const float pxs = ((float) getWidth() - GUTTER) / (float) total;
    const double t = ((double) e.x - GUTTER) / pxs;
    int best = -1;
    double bd = 1e9;
    for (const auto& o : notes) {
        const double d = (t >= o.start && t <= o.start + o.dur) ? 0.0 : std::min(std::abs(t - o.start), std::abs(t - o.start - o.dur));
        if (d < bd) { bd = d; best = o.src; }
    }
    if (best >= 0 && bd < 0.25 && onSelect) onSelect(best);
}

// ================================ VoicePanel ==================================================
VoicePanel::VoicePanel(UiState& s)
    : st(s), kKey("Key", 0.f, 11.f, 0.f), kScale("Scale", 0.f, (float) padlab::Melody::NUM_SCALES - 1.f, 0.f),
      kOct("Octave", -2.f, 2.f, 0.f), kRoot("Smp root", 36.f, 84.f, 60.f) {
    target = st.selected;

    singBtn.setText("SING").setIcon(Btn::MIC).setAccent(REC);
    singBtn.onClick = [this] { toggleSing(); };
    addAndMakeVisible(singBtn);

    status.setFont(mono(12.f));
    status.setColour(Label::textColourId, DIM);
    status.setText("Tap SING, hum or sing a melody, tap STOP.", dontSendNotification);
    status.setMinimumHorizontalScale(0.8f);
    addAndMakeVisible(status);

    wave.setReadOnly(true);
    wave.set(nullptr, 0.f, 1.f, REC, false);
    addAndMakeVisible(wave);

    roll.onSelect = [this](int i) { selNote = i; if (melody) roll.set(melody->build(root, scale, octave, snap, st.engine.bpm), snap ? 60.0 / st.engine.bpm / 4 : 0, selNote); };
    rollView.setViewedComponent(&roll, false);
    rollView.setScrollBarsShown(false, false);
    rollView.setScrollOnDragMode(Viewport::ScrollOnDragMode::all);
    addAndMakeVisible(rollView);

    capInst.setText("INSTRUMENT", dontSendNotification);
    capKey.setText("KEY, SCALE & RANGE", dontSendNotification);
    for (auto* l : {&capInst, &capKey}) { l->setFont(caps(11.f)); l->setColour(Label::textColourId, DIM); addAndMakeVisible(*l); }

    int x = 0;
    for (int i = 0; i < padlab::Synth::NUM; ++i) {
        auto b = std::make_unique<Btn>(padlab::Synth::NAMES[i]);
        b->setSize(11.5f).setAccent(CYAN);
        const int w = (int) GlyphArrangement::getStringWidth(caps(11.5f), padlab::Synth::NAMES[i]) + 30;
        b->setBounds(x, 0, w, 40);
        b->onClick = [this, i] { pick(i); };
        chipRow.addAndMakeVisible(*b);
        chips.push_back(std::move(b));
        x += w + 6;
    }
    chipRow.setSize(x, 40);
    chipView.setViewedComponent(&chipRow, false);
    chipView.setScrollBarsShown(false, false);
    chipView.setScrollOnDragMode(Viewport::ScrollOnDragMode::all);
    addAndMakeVisible(chipView);

    kKey.setStep(1.f);
    kKey.fmt = [](float v) { return String(padlab::Melody::KEYS[roundToInt(v)]); };
    kKey.onChange = [this](float v) { root = roundToInt(v); };
    kKey.onEnd = [this] { rerender(true); };
    kScale.setStep(1.f);
    kScale.fmt = [](float v) { String n(padlab::Melody::SCALE_NAMES[roundToInt(v)]); return n == "CHROMATIC" ? String("CHROM") : n; };
    kScale.onChange = [this](float v) { scale = roundToInt(v); };
    kScale.onEnd = [this] { rerender(true); };
    kOct.setStep(1.f).setBipolar(true);
    kOct.fmt = [](float v) { const int o = roundToInt(v); return o > 0 ? "+" + String(o) : String(o); };
    kOct.onChange = [this](float v) { octave = roundToInt(v); };
    kOct.onEnd = [this] { rerender(true); };
    kRoot.setStep(1.f).setAccent(CYAN);
    kRoot.fmt = [](float v) { return String(padlab::Melody::noteName(roundToInt(v))); };
    kRoot.onChange = [this](float v) { sampleRoot = roundToInt(v); };
    kRoot.onEnd = [this] { if (inst == padlab::Synth::SAMPLE) rerender(true); };
    for (auto* k : {&kKey, &kScale, &kOct, &kRoot}) addAndMakeVisible(*k);

    snapBtn.setText("SNAP 1/16").setSize(11.5f).setLed(true).setActive(true);
    snapBtn.onClick = [this] { snap = !snap; snapBtn.setActive(snap); rerender(true); };
    down.setText("NOTE -1").setSize(11.5f);
    up.setText("NOTE +1").setSize(11.5f);
    del.setText("DELETE").setSize(11.5f);
    down.onClick = [this] { editNote(-1, false); };
    up.onClick = [this] { editNote(1, false); };
    del.onClick = [this] { editNote(0, true); };
    for (auto* b : {&snapBtn, &down, &up, &del}) addAndMakeVisible(*b);

    targetBtn.setSize(11.5f).setAccent(GREEN);
    targetBtn.onClick = [this] { target = st.selected; rerender(false); };
    playBtn.setText("PLAY").setIcon(Btn::PLAY).setAccent(GREEN);
    playBtn.onPress = [this](bool d) { if (d) st.engine.trigger(target, 1.f); };
    addAndMakeVisible(targetBtn);
    addAndMakeVisible(playBtn);

    hint.setFont(sans(11.f));
    hint.setColour(Label::textColourId, DIM);
    hint.setJustificationType(Justification::topLeft);
    hint.setText("The result is written into the target pad, so you can still trim it, stretch it, add pad FX and sequence it. "
                 "SAMPLE plays any other pad's sample as an instrument: select that pad first, then tap SAMPLE.", dontSendNotification);
    addAndMakeVisible(hint);
    updateChips();
}

void VoicePanel::resized() {
    auto r = getLocalBounds();
    auto top = r.removeFromTop(54);
    singBtn.setBounds(top.removeFromLeft(120));
    top.removeFromLeft(10);
    status.setBounds(top);
    r.removeFromTop(10);
    wave.setBounds(r.removeFromTop(90));
    r.removeFromTop(10);
    rollView.setBounds(r.removeFromTop(160));
    roll.setSize(std::max(rollView.getWidth(), roll.contentWidth()), 160);
    capInst.setBounds(r.removeFromTop(28).withTrimmedTop(10));
    chipView.setBounds(r.removeFromTop(42));
    capKey.setBounds(r.removeFromTop(28).withTrimmedTop(10));
    auto kr = r.removeFromTop(90);
    const int kw = kr.getWidth() / 4;
    for (auto* k : {&kKey, &kScale, &kOct, &kRoot}) k->setBounds(kr.removeFromLeft(kw));
    r.removeFromTop(6);
    auto er = r.removeFromTop(40);
    const int w1 = er.getWidth();
    snapBtn.setBounds(er.removeFromLeft(w1 * 28 / 100).reduced(2, 0));
    down.setBounds(er.removeFromLeft(w1 * 24 / 100).reduced(2, 0));
    up.setBounds(er.removeFromLeft(w1 * 24 / 100).reduced(2, 0));
    del.setBounds(er.reduced(2, 0));
    r.removeFromTop(8);
    auto orow = r.removeFromTop(44);
    playBtn.setBounds(orow.removeFromRight(orow.getWidth() / 3).reduced(2, 0));
    targetBtn.setBounds(orow.reduced(2, 0));
    hint.setBounds(r.withTrimmedTop(10));
}

void VoicePanel::tick() {
    if (st.isRecording && st.isRecording()) {
        const float pk = st.engine.micPeak;
        const int bars = jmin(14, roundToInt(std::sqrt(pk) * 14.f));
        String b;
        for (int i = 0; i < 14; ++i) b += i < bars ? juce::String::charToString((juce_wchar) 0x2588) : juce::String::charToString((juce_wchar) 0x2591);
        setStatus(String::formatted("REC %.1fs  ", (Time::currentTimeMillis() - recStartMs) / 1000.0) + b);
    }
    targetBtn.setText("-> " + String(st.padTag(target)) + "   (tap: use selected pad)");
}

void VoicePanel::onRecordingChanged() {
    const bool rec = st.isRecording && st.isRecording();
    singBtn.setActive(rec).setText(rec ? "STOP" : "SING");
}

void VoicePanel::debugDemo() {
    const int sr = padlab::SR;
    const double spec[6][3] = {{0.2, 0.8, 60}, {1.0, 1.6, 64}, {1.8, 2.5, 67}, {2.7, 3.2, 65}, {3.3, 3.9, 64}, {4.1, 5.0, 60}};
    std::vector<float> x((size_t) (sr * 5.4), 0.f);
    for (auto& n : spec) {
        double ph = 0;
        for (int i = (int) (n[0] * sr); i < (int) (n[1] * sr); ++i) {
            const double t = i / (double) sr, tn = t - n[0];
            ph += 2 * M_PI * 440 * std::pow(2.0, (n[2] - 69) / 12.0 + 0.0002 * std::sin(2 * M_PI * 5.5 * t)) / sr;
            const double env = std::min(1.0, tn / 0.03) * std::min(1.0, (n[1] - t) / 0.03);
            double s = 0;
            for (int h = 1; h <= 6; ++h) s += std::sin(h * ph) / h;
            x[(size_t) i] = (float) (0.4 * s * env);
        }
    }
    recording = padlab::Sample::makeMono("sung", x);
    melody = std::make_shared<padlab::Melody>(padlab::Melody::analyze(recording));
    wave.set(recording, 0.f, 1.f, REC, false);
    selNote = 2;
    root = 0; scale = 1;
    roll.set(melody->build(root, scale, octave, snap, st.engine.bpm), 60.0 / st.engine.bpm / 4, selNote);
    roll.setSize(std::max(rollView.getWidth(), roll.contentWidth()), 160);
    setStatus("Recorded 5.4s, peak -8 dB - " + String((int) melody->notes.size()) + " notes");
    kScale.set(1.f);
    inst = 2;
    updateChips();
}

void VoicePanel::toggleSing() {
    if (st.isRecording && st.isRecording()) { st.stopRecord(); return; }
    target = st.selected;
    recStartMs = Time::currentTimeMillis();
    SafePointer<Component> self(this);
    st.record([this, self](UiState::SamplePtrC s) { if (self != nullptr) gotRecording(std::move(s)); });
    onRecordingChanged();
}

void VoicePanel::gotRecording(UiState::SamplePtrC s) {
    onRecordingChanged();
    if (!s) { setStatus("Nothing recorded. Check the microphone permission."); return; }
    recording = s;
    melody = nullptr;
    selNote = -1;
    roll.set({}, 0, -1);
    wave.set(s, 0.f, 1.f, REC, false);
    float pk = 0;
    for (float f : s->l) pk = std::max(pk, std::abs(f));
    const String info = String::formatted("%.1fs, peak %.0f dB", s->n() / (float) padlab::SR, 20 * std::log10(std::max(pk, 1e-6f)));
    setStatus("Recorded " + info + " - analysing...");
    auto aliveFlag = alive;
    SafePointer<Component> self(this);
    std::thread([this, s, info, self, aliveFlag] {
        auto m = std::make_shared<padlab::Melody>(padlab::Melody::analyze(s));
        MessageManager::callAsync([this, m, info, self, aliveFlag] {
            if (self != nullptr && *aliveFlag) gotMelody(m, info);
        });
    }).detach();
}

void VoicePanel::gotMelody(std::shared_ptr<padlab::Melody> m, const String& info) {
    if (m->notes.empty()) {
        setStatus("Recorded " + info + " - no notes detected. ORIGINAL still plays your take.");
        return;
    }
    melody = m;
    selNote = -1;
    setStatus("Recorded " + info + " - " + String((int) m->notes.size()) + " notes");
    rerender(true);
}

void VoicePanel::pick(int i) {
    if (!melody && !recording) { setStatus("Sing a melody first: tap SING, sing, tap STOP."); return; }
    if (!melody && i != padlab::Synth::ORIGINAL) { setStatus("No notes were detected in that take: sing again, or use ORIGINAL."); return; }
    if (i == padlab::Synth::SAMPLE) {
        auto cand = st.engine.pads[(size_t) st.selected].sample.get();
        if (cand && cand != lastRender) sampleSrc = cand;
        if (!sampleSrc) { setStatus("Select a pad that holds a sample first, then tap SAMPLE."); return; }
    }
    inst = i;
    updateChips();
    rerender(true);
}

void VoicePanel::updateChips() {
    for (size_t i = 0; i < chips.size(); ++i) chips[i]->setActive((int) i == inst);
    kRoot.setDim(inst != padlab::Synth::SAMPLE);
}

void VoicePanel::editNote(int dShift, bool delNote) {
    if (!melody || selNote < 0 || selNote >= (int) melody->notes.size()) { setStatus("Tap a note in the roll first."); return; }
    auto& n = melody->notes[(size_t) selNote];
    if (delNote) { n.deleted = true; selNote = -1; } else n.shift += dShift;
    rerender(true);
}

void VoicePanel::rerender(bool audition) {
    if (!melody && !(inst == padlab::Synth::ORIGINAL && recording)) return;
    const double bpm = st.engine.bpm;
    std::vector<padlab::Melody::Out> notes;
    if (melody) notes = melody->build(root, scale, octave, snap, bpm);
    roll.set(notes, snap ? 60.0 / bpm / 4 : 0, selNote);
    roll.setSize(std::max(rollView.getWidth(), roll.contentWidth()), 160);
    if (notes.empty() && inst != padlab::Synth::ORIGINAL) { setStatus("All notes deleted"); return; }

    const int myGen = ++gen, ins = inst, smpRoot = sampleRoot, tgt = target;
    const bool sn = snap;
    auto rec = recording;
    auto src = sampleSrc;
    setStatus(String("Rendering ") + padlab::Synth::NAMES[ins] + "...");
    auto aliveFlag = alive;
    SafePointer<Component> self(this);
    std::thread([=] {
        auto out = padlab::Synth::render(notes, rec, ins, src, smpRoot, sn, bpm);
        MessageManager::callAsync([=] {
            if (self == nullptr || !*aliveFlag || myGen != gen.load()) return;
            if (!out) { setStatus("Nothing to render"); return; }
            lastRender = out;
            padlab::Pad& p = st.engine.pads[(size_t) tgt];
            p.sample.set(out);
            p.name = padlab::Synth::NAMES[ins];
            p.start = 0.f;
            p.end = 1.f;
            p.mode = padlab::Pad::ONE_SHOT;
            p.reverse = false;
            p.pitch = 0;
            p.tempo = 1.f;
            p.fitBeats = 0;
            p.rendered.set(nullptr);
            p.renderedKey = 0;
            if (st.padsChanged) st.padsChanged();
            setStatus(String((int) notes.size()) + " notes - " + padlab::Synth::NAMES[ins] + " -> " + String(st.padTag(tgt)));
            if (audition) st.engine.trigger(tgt, 1.f);
        });
    }).detach();
}

} // namespace ui
