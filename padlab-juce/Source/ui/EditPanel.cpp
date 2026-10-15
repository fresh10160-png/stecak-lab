#include "EditPanel.h"

using namespace juce;

namespace ui {

EditPanel::EditPanel(UiState& s)
    : st(s), kVol("Volume", 0.f, 1.5f, 0.9f), kPan("Pan", -1.f, 1.f, 0.f), kPitch("Pitch", -24.f, 24.f, 0.f),
      kTempo("Tempo", 0.5f, 2.f, 1.f) {
    wave.onTrim = [this](float a, float b, bool finished) {
        pad().start = a;
        pad().end = b;
        if (finished) { st.engine.requestWarp(st.selected); refresh(); }
    };
    addAndMakeVisible(wave);

    for (auto* l : {&capMode, &capLevel, &capWarp}) { l->setFont(caps(11.f)); l->setColour(Label::textColourId, DIM); addAndMakeVisible(*l); }
    capMode.setText("PLAY MODE", dontSendNotification);
    capLevel.setText("LEVEL & PITCH", dontSendNotification);
    capWarp.setText("TIME-STRETCH  (pitch-preserving)", dontSendNotification);

    for (int i = 0; i < 3; ++i) {
        modeBtns[(size_t) i] = std::make_unique<Btn>(padlab::Pad::MODES[i]);
        modeBtns[(size_t) i]->setSize(11.5f);
        modeBtns[(size_t) i]->onClick = [this, i] { pad().mode = i; refresh(); if (st.padsChanged) st.padsChanged(); };
        addAndMakeVisible(*modeBtns[(size_t) i]);
    }
    revBtn.setText("REVERSE").setSize(11.5f).setLed(true);
    revBtn.onClick = [this] { pad().reverse = !pad().reverse; refresh(); };
    chokeBtn.setSize(11.5f);
    chokeBtn.onClick = [this] { pad().choke = (pad().choke + 1) % 5; refresh(); };
    padFxBtn.setText("PAD FX  >").setSize(11.5f).setAccent(CYAN);
    padFxBtn.onClick = [this] { if (openPadFx) openPadFx(); };

    kVol.onChange = [this](float v) { pad().vol = v; };
    kPan.setBipolar(true);
    kPan.fmt = [](float v) { const int n = roundToInt(std::abs(v) * 100); return n < 3 ? String("C") : String(v < 0 ? "L" : "R") + String(n); };
    kPan.onChange = [this](float v) { pad().pan = v; };
    kPitch.setStep(1.f).setBipolar(true);
    kPitch.fmt = [](float v) { return String(v > 0 ? "+" : "") + String(roundToInt(v)) + "st"; };
    kPitch.onChange = [this](float v) { pad().pitch = roundToInt(v); };
    kPitch.onEnd = [this] { warpChanged(); };
    kTempo.setAccent(CYAN);
    kTempo.fmt = [](float v) { return String(roundToInt(v * 100)) + "%"; };
    kTempo.onChange = [this](float v) { pad().tempo = v; };
    kTempo.onEnd = [this] { warpChanged(); };
    for (auto* k : {&kVol, &kPan, &kPitch, &kTempo}) addAndMakeVisible(*k);

    keyLockBtn.setText("KEY LOCK").setSize(11.5f).setLed(true).setAccent(CYAN);
    keyLockBtn.onClick = [this] { pad().keyLock = !pad().keyLock; warpChanged(); };
    fitBtn.setSize(11.5f).setAccent(CYAN);
    fitBtn.onClick = [this] {
        int i = 0;
        for (int k = 0; k < 6; ++k) if (padlab::Pad::FIT[k] == pad().fitBeats.load()) i = k;
        pad().fitBeats = padlab::Pad::FIT[(i + 1) % 6];
        warpChanged();
    };
    warpStatus.setFont(mono(11.f));
    warpStatus.setJustificationType(Justification::centred);
    warpStatus.setColour(Label::textColourId, DIM);
    for (auto* b : {&revBtn, &chokeBtn, &padFxBtn, &keyLockBtn, &fitBtn}) addAndMakeVisible(*b);
    addAndMakeVisible(warpStatus);

    loadBtn.setText("LOAD FILE").setSize(11.5f);
    clearBtn.setText("CLEAR PAD").setSize(11.5f);
    prevBtn.setText("PREVIEW").setSize(11.5f).setIcon(Btn::PLAY).setAccent(GREEN);
    loadBtn.onClick = [this] { if (pickFile) pickFile(); };
    clearBtn.onClick = [this] {
        auto& p = pad();
        p.sample.set(nullptr);
        p.rendered.set(nullptr);
        p.name.clear();
        p.start = 0.f; p.end = 1.f; p.pitch = 0; p.tempo = 1.f; p.fitBeats = 0; p.pan = 0.f; p.vol = 0.9f;
        p.renderedKey = 0; p.pendingKey = 0;
        if (st.padsChanged) st.padsChanged();
    };
    prevBtn.onPress = [this](bool d) { if (d) st.engine.trigger(st.selected, 1.f); else st.engine.release(st.selected); };
    for (auto* b : {&loadBtn, &clearBtn, &prevBtn}) addAndMakeVisible(*b);
    refresh();
}

void EditPanel::warpChanged() {
    st.engine.requestWarp(st.selected);
    refresh();
    if (st.padsChanged) st.padsChanged();
}

void EditPanel::resized() {
    auto r = getLocalBounds();
    wave.setBounds(r.removeFromTop(140));
    capMode.setBounds(r.removeFromTop(30).withTrimmedTop(10));
    auto row = r.removeFromTop(38);
    int w = row.getWidth() / 3;
    for (auto& b : modeBtns) b->setBounds(row.removeFromLeft(w).reduced(2, 0));
    r.removeFromTop(6);
    row = r.removeFromTop(38);
    w = row.getWidth() / 3;
    revBtn.setBounds(row.removeFromLeft(w).reduced(2, 0));
    chokeBtn.setBounds(row.removeFromLeft(w).reduced(2, 0));
    padFxBtn.setBounds(row.reduced(2, 0));
    capLevel.setBounds(r.removeFromTop(30).withTrimmedTop(10));
    row = r.removeFromTop(88);
    w = row.getWidth() / 4;
    for (auto* k : {&kVol, &kPan, &kPitch, &kTempo}) k->setBounds(row.removeFromLeft(w));
    capWarp.setBounds(r.removeFromTop(30).withTrimmedTop(10));
    row = r.removeFromTop(38);
    keyLockBtn.setBounds(row.removeFromLeft(row.getWidth() * 3 / 10).reduced(2, 0));
    fitBtn.setBounds(row.removeFromLeft(row.getWidth() * 4 / 7).reduced(2, 0));
    warpStatus.setBounds(row);
    r.removeFromTop(14);
    row = r.removeFromTop(40);
    w = row.getWidth() / 3;
    loadBtn.setBounds(row.removeFromLeft(w).reduced(2, 0));
    clearBtn.setBounds(row.removeFromLeft(w).reduced(2, 0));
    prevBtn.setBounds(row.reduced(2, 0));
}

void EditPanel::refresh() {
    auto& p = pad();
    kVol.set(p.vol);
    kPan.set(p.pan);
    kPitch.set((float) p.pitch.load());
    kTempo.set(p.tempo);
    kTempo.setDim(p.fitBeats > 0);
    for (int i = 0; i < 3; ++i) modeBtns[(size_t) i]->setActive(p.mode == i);
    revBtn.setActive(p.reverse);
    chokeBtn.setText(p.choke == 0 ? "CHOKE OFF" : "CHOKE " + String(p.choke.load())).setActive(p.choke > 0);
    keyLockBtn.setActive(p.keyLock);
    fitBtn.setText(p.fitBeats == 0 ? "FIT: OFF" : "FIT: " + String(p.fitBeats.load()) + (p.fitBeats == 1 ? " BEAT" : " BEATS")).setActive(p.fitBeats > 0);
    tick();
}

void EditPanel::tick() {
    auto& p = pad();
    const bool busy = st.engine.warpBusy.load() > 0;
    wave.set(p.sample.get(), p.start, p.end, bankColour(st.selected / 16), busy);
    warpStatus.setText(busy ? "RENDERING..." : (p.sample.get() && p.warped() && p.rendered.get() ? "STRETCHED" : "NO STRETCH"), dontSendNotification);
    warpStatus.setColour(Label::textColourId, busy ? CYAN : DIM);
}

} // namespace ui
