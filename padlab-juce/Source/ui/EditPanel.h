#pragma once
#include "UiState.h"
#include "Views.h"
#include "Widgets.h"

namespace ui {

/** Per-pad editor: trim, play mode, level/pan/pitch, time-stretch, load/clear. */
class EditPanel : public juce::Component {
public:
    explicit EditPanel(UiState& s);
    std::function<void()> pickFile, openPadFx;

    void refresh();                 // pull everything from the selected pad
    void tick();                    // cheap periodic update (stretch progress)
    void resized() override;
    static constexpr int HEIGHT = 540;

private:
    padlab::Pad& pad() { return st.engine.pads[(size_t) st.selected]; }
    void warpChanged();

    UiState& st;
    WaveView wave;
    juce::Label capMode, capLevel, capWarp, warpStatus;
    std::array<std::unique_ptr<Btn>, 3> modeBtns;
    Btn revBtn, chokeBtn, padFxBtn, keyLockBtn, fitBtn, loadBtn, clearBtn, prevBtn;
    Knob kVol, kPan, kPitch, kTempo;
};

} // namespace ui
