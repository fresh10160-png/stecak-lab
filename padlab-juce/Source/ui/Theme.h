#pragma once
#include <juce_graphics/juce_graphics.h>
#include <juce_gui_basics/juce_gui_basics.h>

namespace ui {

inline const juce::Colour BG{0xFF0B0C0F}, PANEL{0xFF15171C}, PANEL2{0xFF1D2027}, STROKE{0xFF2B2F38},
    TEXT{0xFFEDEFF3}, DIM{0xFF7B828E}, ACCENT{0xFFFF7A1A}, CYAN{0xFF2FD6C8}, REC{0xFFFF3B3B},
    GREEN{0xFF5BE37D}, LCD{0xFF07090B}, INK{0xFF111111};

inline juce::Colour bankColour(int bank) {
    static const juce::uint32 c[4] = {0xFFFF7A1A, 0xFF2FD6C8, 0xFFD64BE0, 0xFF7BE35B};
    return juce::Colour(c[bank & 3]);
}

inline juce::Font sans(float h, bool bold = false) {
    return juce::Font(juce::FontOptions(h, bold ? juce::Font::bold : juce::Font::plain));
}

inline juce::Font mono(float h) {
    return juce::Font(juce::FontOptions(juce::Font::getDefaultMonospacedFontName(), h, juce::Font::bold));
}

/** Small-caps style label font with a little letter-spacing. */
inline juce::Font caps(float h) {
    return juce::Font(juce::FontOptions(h, juce::Font::bold)).withExtraKerningFactor(0.06f);
}

inline juce::Colour mix(juce::Colour a, juce::Colour b, float t) { return a.interpolatedWith(b, t); }

/** Soft coloured glow around a rounded rect (cheap: a few expanding translucent outlines). */
inline void glow(juce::Graphics& g, juce::Rectangle<float> r, float radius, juce::Colour c, float strength, int layers = 5) {
    for (int i = layers; i >= 1; --i) {
        g.setColour(c.withAlpha(strength * 0.16f * (float) (layers - i + 1) / (float) layers));
        g.fillRoundedRectangle(r.expanded((float) i * 1.6f), radius + (float) i * 1.6f);
    }
}

} // namespace ui
