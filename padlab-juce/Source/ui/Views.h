#pragma once
#include "UiState.h"
#include "Widgets.h"

namespace ui {

// ---------------------------------------------------------------------------------------------
class PadView : public juce::Component {
public:
    PadView(UiState& s, int index) : st(s), index(index) {
        setRepaintsOnMouseActivity(false);
    }

    int id() const { return st.bank * 16 + index; }
    void tick();                       // repaint while the hit flash is fading

    void paint(juce::Graphics& g) override;
    void mouseDown(const juce::MouseEvent& e) override;
    void mouseUp(const juce::MouseEvent&) override;

private:
    float glowLevel() const;
    UiState& st;
    int index;
    bool held = false;
    float lastGlow = 0.f;
    std::shared_ptr<const padlab::Sample> thumbFor;   // thumbnail cache
    std::vector<float> thumb;
};

// ---------------------------------------------------------------------------------------------
/** Top display: BPM, selected pad, stereo VU and the sequencer position LEDs. */
class Lcd : public juce::Component {
public:
    explicit Lcd(UiState& s) : st(s) {}
    void paint(juce::Graphics& g) override;
private:
    void vu(juce::Graphics& g, float x, float level);
    UiState& st;
};

// ---------------------------------------------------------------------------------------------
/** Waveform with draggable start/end handles (or read-only). */
class WaveView : public juce::Component {
public:
    std::function<void(float, float, bool)> onTrim;

    void set(std::shared_ptr<const padlab::Sample> s, float start, float end, juce::Colour accent, bool busy);
    void setReadOnly(bool r) { readOnly = r; }

    WaveView() { setViewportIgnoreDragFlag(true); }
    void paint(juce::Graphics& g) override;
    void mouseDown(const juce::MouseEvent& e) override;
    void mouseDrag(const juce::MouseEvent& e) override;
    void mouseUp(const juce::MouseEvent& e) override;

private:
    void handle(juce::Graphics& g, float x, const char* t, bool left);
    void drag(const juce::MouseEvent& e);
    std::shared_ptr<const padlab::Sample> sample, peaksFor;
    float start = 0, end = 1;
    std::vector<float> peaks;
    int peaksW = -1, dragging = -1;
    bool busy = false, readOnly = false;
    juce::Colour accent = ACCENT;
};

// ---------------------------------------------------------------------------------------------
/** 16 pads (current bank) x 16 steps: tap or drag to paint steps, tap a label to audition. */
class StepGrid : public juce::Component {
public:
    explicit StepGrid(UiState& s) : st(s) { setViewportIgnoreDragFlag(true); }
    void paint(juce::Graphics& g) override;
    void mouseDown(const juce::MouseEvent& e) override;
    void mouseDrag(const juce::MouseEvent& e) override;
    void mouseUp(const juce::MouseEvent& e) override;
    void tick();
private:
    float labelW() const { return 74.f; }
    float headH() const { return 18.f; }
    UiState& st;
    int paintRow = -1, paintCol = -1, auditioned = -1, lastStep = -2;
    bool paintValue = false;
};

} // namespace ui
