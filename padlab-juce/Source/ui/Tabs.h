#pragma once
#include "FxRack.h"
#include "Views.h"
#include "Widgets.h"

namespace ui {

// ---------------------------------------------------------------------------------------------
class PadsTab : public juce::Component {
public:
    explicit PadsTab(UiState& s) : st(s) {
        for (int i = 0; i < 16; ++i) {
            views[(size_t) i] = std::make_unique<PadView>(st, i);
            addAndMakeVisible(*views[(size_t) i]);
        }
        fullBtn.setText("FULL LEVEL").setSize(11.f).setLed(true).setActive(true);
        fullBtn.onClick = [this] {
            st.fullLevel = !st.fullLevel;
            fullBtn.setActive(st.fullLevel).setText(st.fullLevel ? "FULL LEVEL" : "VELOCITY: TOUCH HEIGHT");
        };
        addAndMakeVisible(fullBtn);
    }

    void resized() override {
        auto r = getLocalBounds();
        fullBtn.setBounds(r.removeFromBottom(38));
        r.removeFromBottom(6);
        const int w = r.getWidth() / 4, h = r.getHeight() / 4;
        for (int i = 0; i < 16; ++i) views[(size_t) i]->setBounds(r.getX() + (i % 4) * w, r.getY() + (3 - i / 4) * h, w, h);
    }
    void tick() { for (auto& v : views) v->tick(); }
    void repaintPads() { for (auto& v : views) v->repaint(); }

private:
    UiState& st;
    std::array<std::unique_ptr<PadView>, 16> views;
    Btn fullBtn;
};

// ---------------------------------------------------------------------------------------------
class FxTab : public juce::Component {
public:
    explicit FxTab(UiState& s) : st(s), rack(s) {
        masterBtn.setText("MASTER BUS").setSize(11.5f).setAccent(CYAN);
        padBtn.setText("SELECTED PAD").setSize(11.5f).setAccent(CYAN);
        masterBtn.onClick = [this] { onPad = false; rebind(); };
        padBtn.onClick = [this] { onPad = true; rebind(); };
        addAndMakeVisible(masterBtn);
        addAndMakeVisible(padBtn);
        view.setViewedComponent(&rack, false);
        view.setScrollBarsShown(false, false);
        view.setScrollOnDragMode(juce::Viewport::ScrollOnDragMode::all);
        addAndMakeVisible(view);
    }

    void showPadFx() { onPad = true; rebind(); }

    void rebind() {
        masterBtn.setActive(!onPad);
        padBtn.setActive(onPad).setText(onPad ? juce::String(st.padTag(st.selected)) : juce::String("SELECTED PAD"));
        if (onPad) rack.bind(st.engine.chainFor(st.selected), st.padTag(st.selected));
        else rack.bind(st.engine.master, "MASTER");
    }
    bool padMode() const { return onPad; }

    void resized() override {
        auto r = getLocalBounds();
        auto row = r.removeFromTop(38);
        masterBtn.setBounds(row.removeFromLeft(row.getWidth() / 2).reduced(2, 0));
        padBtn.setBounds(row.reduced(2, 0));
        r.removeFromTop(6);
        view.setBounds(r);
        rack.setSize(r.getWidth(), FxRack::HEIGHT);
    }

private:
    UiState& st;
    bool onPad = false;
    Btn masterBtn, padBtn;
    juce::Viewport view;
    FxRack rack;
};

// ---------------------------------------------------------------------------------------------
class SeqTab : public juce::Component {
public:
    explicit SeqTab(UiState& s) : st(s), grid(s), kBpm("BPM", 40.f, 220.f, 90.f), kSwing("Swing", 0.f, 1.f, 0.3f) {
        addAndMakeVisible(grid);
        kBpm.setStep(1.f);
        kBpm.fmt = [](float v) { return juce::String(juce::roundToInt(v)); };
        kBpm.onChange = [this](float v) { st.engine.bpm = v; };
        kBpm.onEnd = [this] { st.engine.rewarpAll(); };
        kSwing.onChange = [this](float v) { st.engine.swing = v; };
        kBpm.set(st.engine.bpm);
        kSwing.set(st.engine.swing);
        tapBtn.setText("TAP TEMPO").setSize(11.f);
        clrPad.setText("CLEAR PAD").setSize(11.f);
        clrAll.setText("CLEAR ALL").setSize(11.f);
        tapBtn.onClick = [this] { tap(); };
        clrPad.onClick = [this] { st.engine.stepBits[st.selected] = 0; grid.repaint(); };
        clrAll.onClick = [this] { for (auto& b : st.engine.stepBits) b = 0; grid.repaint(); };
        for (auto* c : std::initializer_list<juce::Component*>{&kBpm, &kSwing, &tapBtn, &clrPad, &clrAll}) addAndMakeVisible(*c);
    }

    void resized() override {
        auto r = getLocalBounds();
        auto bottom = r.removeFromBottom(100);
        grid.setBounds(r);
        bottom.removeFromTop(6);
        kBpm.setBounds(bottom.removeFromLeft(bottom.getWidth() * 27 / 100));
        kSwing.setBounds(bottom.removeFromLeft(bottom.getWidth() * 35 / 100));
        bottom.removeFromLeft(6);
        const int h = bottom.getHeight() / 3;
        tapBtn.setBounds(bottom.removeFromTop(h).reduced(0, 2));
        clrPad.setBounds(bottom.removeFromTop(h).reduced(0, 2));
        clrAll.setBounds(bottom.reduced(0, 2));
    }

    void tick() { grid.tick(); }
    void refresh() { grid.repaint(); }
    void syncBpm() { kBpm.set(st.engine.bpm); }

private:
    void tap() {
        const auto now = juce::Time::currentTimeMillis();
        if (tapCount > 0 && now - taps[(size_t) ((tapCount - 1) % 4)] > 2000) tapCount = 0;
        taps[(size_t) (tapCount % 4)] = now;
        ++tapCount;
        const int n = std::min(tapCount, 4);
        if (n >= 2) {
            const auto first = taps[(size_t) ((tapCount - n) % 4)], last = taps[(size_t) ((tapCount - 1) % 4)];
            const float bpm = juce::jlimit(40.f, 220.f, 60000.f * (float) (n - 1) / (float) (last - first));
            st.engine.bpm = bpm;
            kBpm.set(bpm);
        }
    }

    UiState& st;
    StepGrid grid;
    Knob kBpm, kSwing;
    Btn tapBtn, clrPad, clrAll;
    int64_t taps[4] = {};
    int tapCount = 0;
};

// ---------------------------------------------------------------------------------------------
/** Segmented tab selector with a sliding highlight. */
class TabBar : public juce::Component, private juce::Timer {
public:
    std::function<void(int)> onSelect;

    explicit TabBar(std::vector<juce::String> names) : labels(std::move(names)) {}

    void select(int i) { selected = i; startTimerHz(60); repaint(); }
    void snap(int i) { selected = i; pillPos = (float) i; stopTimer(); repaint(); }

    void paint(juce::Graphics& g) override {
        const auto b = getLocalBounds().toFloat().reduced(1.f);
        g.setGradientFill(juce::ColourGradient::vertical(juce::Colour(0xFF14161B), b.getY(), juce::Colour(0xFF101216), b.getBottom()));
        g.fillRoundedRectangle(b, 12.f);
        g.setColour(STROKE);
        g.drawRoundedRectangle(b, 12.f, 1.f);
        const float w = b.getWidth() / (float) labels.size();
        auto pill = juce::Rectangle<float>(b.getX() + pillPos * w, b.getY(), w, b.getHeight()).reduced(3.f);
        ui::glow(g, pill, 9.f, ACCENT, 0.6f, 4);
        g.setGradientFill(juce::ColourGradient::vertical(ACCENT.brighter(0.2f), pill.getY(), ACCENT.darker(0.3f), pill.getBottom()));
        g.fillRoundedRectangle(pill, 9.f);
        g.setFont(caps(12.f));
        for (int i = 0; i < (int) labels.size(); ++i) {
            g.setColour(i == selected ? INK : TEXT.withAlpha(0.8f));
            g.drawText(labels[(size_t) i], juce::Rectangle<float>(b.getX() + (float) i * w, b.getY(), w, b.getHeight()), juce::Justification::centred, false);
        }
    }

    void mouseDown(const juce::MouseEvent& e) override {
        const int i = juce::jlimit(0, (int) labels.size() - 1, e.x * (int) labels.size() / juce::jmax(1, getWidth()));
        select(i);
        if (onSelect) onSelect(i);
    }

private:
    void timerCallback() override {
        const float d = (float) selected - pillPos;
        if (std::abs(d) < 0.01f) { pillPos = (float) selected; stopTimer(); } else pillPos += d * 0.35f;
        repaint();
    }
    std::vector<juce::String> labels;
    int selected = 0;
    float pillPos = 0.f;
};

} // namespace ui
