#pragma once
#include "../core/Synth.h"
#include "UiState.h"
#include "Widgets.h"
#include "Views.h"

namespace ui {

/** Piano roll of the (shaped) melody. Tap a note to select it. */
class RollView : public juce::Component {
public:
    std::function<void(int)> onSelect;
    void set(std::vector<padlab::Melody::Out> n, double gridStep, int selected);
    int contentWidth() const;
    void paint(juce::Graphics& g) override;
    void mouseUp(const juce::MouseEvent& e) override;
    RollView() { setViewportIgnoreDragFlag(false); }
private:
    static constexpr float GUTTER = 34.f, PX_PER_SEC = 90.f;
    std::vector<padlab::Melody::Out> notes;
    double total = 2, gridStep = 0;
    int lo = 55, hi = 72, selected = -1;
};

/** Sing -> notes -> pick an instrument. The rendered melody is written straight into a pad. */
class VoicePanel : public juce::Component {
public:
    explicit VoicePanel(UiState& s);
    ~VoicePanel() override { *alive = false; }
    void resized() override;
    void tick();
    void onRecordingChanged();
    void debugDemo();                 // screenshots: pretend a take was just analysed
    static constexpr int HEIGHT = 700;

private:
    void toggleSing();
    void gotRecording(UiState::SamplePtrC s);
    void gotMelody(std::shared_ptr<padlab::Melody> m, const juce::String& info);
    void pick(int i);
    void updateChips();
    void editNote(int dShift, bool del);
    void rerender(bool audition);
    void setStatus(const juce::String& s) { status.setText(s, juce::dontSendNotification); }

    UiState& st;
    std::shared_ptr<bool> alive = std::make_shared<bool>(true);
    std::shared_ptr<padlab::Melody> melody;
    std::shared_ptr<const padlab::Sample> recording, sampleSrc, lastRender;
    int inst = 2, root = 0, scale = 0, octave = 0, sampleRoot = 60, selNote = -1, target = 0;
    bool snap = true;
    std::atomic<int> gen{0};
    int64_t recStartMs = 0;

    Btn singBtn, snapBtn, down, up, del, targetBtn, playBtn;
    std::vector<std::unique_ptr<Btn>> chips;
    juce::Label status, hint, capInst, capKey;
    WaveView wave;
    juce::Viewport rollView, chipView;
    RollView roll;
    juce::Component chipRow;
    Knob kKey, kScale, kOct, kRoot;
};

} // namespace ui
