#pragma once
#include <functional>
#include "../core/Engine.h"
#include "Theme.h"

namespace ui {

using SamplePtr = std::shared_ptr<padlab::Sample>;

/** Shared UI state + the few callbacks child views need from MainComponent. */
struct UiState {
    explicit UiState(padlab::Engine& e) : engine(e) {}

    using SamplePtrC = std::shared_ptr<padlab::Sample>;

    padlab::Engine& engine;
    int bank = 0, selected = 0;
    bool fullLevel = true;

    std::function<void(int)> select;                      // change the selected pad (cheap)
    std::function<void()> padsChanged;                    // a pad's sample/params changed: refresh views
    std::function<void(std::function<void(SamplePtr)>)> record;   // start mic capture; callback on stop
    std::function<void()> stopRecord;
    std::function<bool()> isRecording;
    std::function<void(int)> showTab;

    std::string padTag(int id) const {
        char b[16];
        std::snprintf(b, sizeof b, "PAD %c%02d", 'A' + id / 16, id % 16 + 1);
        return b;
    }
};

enum Tab { PADS, EDIT, FX, SEQ, VOICE, NUM_TABS };

} // namespace ui
