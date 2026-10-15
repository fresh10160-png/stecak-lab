#pragma once
#include "UiState.h"
#include "Widgets.h"

namespace ui {

/** Grid of effect slots in signal order, plus an ALL OFF slot. */
class FxTiles : public juce::Component {
public:
    static constexpr int COLS = 4;
    std::function<void(int)> onTap;

    void bind(std::shared_ptr<padlab::Chain> c, int sel) { chain = std::move(c); selected = sel; repaint(); }
    int rows() const { return chain ? ((int) chain->size() + 1 + COLS - 1) / COLS : 1; }
    int preferredHeight() const { return rows() * 54; }

    void paint(juce::Graphics& g) override;
    void mouseUp(const juce::MouseEvent& e) override;

private:
    std::shared_ptr<padlab::Chain> chain;
    int selected = 0;
};

/** Presets + effect tiles + knobs + XY pad, bound to the master chain or to one pad's chain. */
class FxRack : public juce::Component {
public:
    explicit FxRack(UiState& s);
    void bind(std::shared_ptr<padlab::Chain> chain, const juce::String& name);
    void resized() override;
    static constexpr int HEIGHT = 560;

private:
    struct Preset { juce::String name; std::vector<int> ids; std::vector<std::vector<float>> vals; };
    static std::vector<Preset> presets();
    void refresh();
    void apply(const Preset& p);
    padlab::Fx& cur() { return *(*chain)[(size_t) sel]; }

    UiState& st;
    std::shared_ptr<padlab::Chain> chain;
    int sel = 0;
    juce::String name;

    juce::Viewport presetView;
    juce::Component presetRow;
    std::vector<std::unique_ptr<Btn>> presetBtns;
    FxTiles tiles;
    juce::Label title;
    Btn power;
    std::array<std::unique_ptr<Knob>, 3> knobs;
    XYPad xy;
    juce::Label caption;
};

} // namespace ui
