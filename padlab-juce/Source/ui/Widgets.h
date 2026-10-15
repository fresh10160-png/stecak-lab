#pragma once
#include <functional>
#include "Theme.h"

namespace ui {

// ---------------------------------------------------------------------------------------------
class Btn : public juce::Component {
public:
    enum Icon { NONE, PLAY, STOP, REC_ICON, MIC };

    explicit Btn(juce::String t = {}) : text(std::move(t)) {}

    std::function<void()> onClick;

    Btn& setText(const juce::String& t) { if (t != text) { text = t; repaint(); } return *this; }
    Btn& setIcon(Icon i) { icon = i; repaint(); return *this; }
    Btn& setAccent(juce::Colour c) { accent = c; repaint(); return *this; }
    Btn& setLed(bool l) { led = l; repaint(); return *this; }
    Btn& setSize(float h) { fontSize = h; repaint(); return *this; }
    Btn& setActive(bool a) { if (a != active) { active = a; repaint(); } return *this; }
    bool isActive() const { return active; }
    const juce::String& getText() const { return text; }

    void paint(juce::Graphics& g) override {
        auto r = getLocalBounds().toFloat().reduced(1.f);
        const float rad = 10.f;
        if (active) ui::glow(g, r, rad, accent, led ? 0.9f : 0.45f, 4);
        juce::Colour top = active ? accent.brighter(0.25f) : juce::Colour(0xFF2C3039);
        juce::Colour bot = active ? accent.darker(0.35f) : juce::Colour(0xFF1D2026);
        if (pressed) { top = top.brighter(0.15f); bot = bot.brighter(0.15f); }
        g.setGradientFill(juce::ColourGradient::vertical(top, r.getY(), bot, r.getBottom()));
        g.fillRoundedRectangle(r, rad);
        g.setColour(active ? accent.brighter(0.5f).withAlpha(0.6f) : juce::Colour(0xFF3A3F4A));
        g.drawRoundedRectangle(r, rad, 1.f);
        g.setColour(juce::Colours::white.withAlpha(active ? 0.25f : 0.07f)); // top sheen
        g.drawLine(r.getX() + rad, r.getY() + 1.5f, r.getRight() - rad, r.getY() + 1.5f, 1.f);

        const juce::Colour fg = active ? INK : TEXT;
        const auto font = caps(fontSize);
        g.setFont(font);
        const float tw = text.isEmpty() ? 0.f : juce::GlyphArrangement::getStringWidth(font, text);
        const float iw = icon != NONE ? 14.f : 0.f, gap = (iw > 0 && tw > 0) ? 7.f : 0.f, lw = led ? 14.f : 0.f;
        float x = r.getCentreX() - (tw + iw + gap + lw) / 2.f;
        const float cy = r.getCentreY();
        if (led) {
            g.setColour(active ? INK.withAlpha(0.7f) : juce::Colour(0xFF3A3F49));
            g.fillEllipse(x + 1.f, cy - 3.5f, 7.f, 7.f);
            x += lw;
        }
        if (icon != NONE) {
            drawIcon(g, juce::Rectangle<float>(x, cy - iw / 2, iw, iw), active ? INK : (icon == REC_ICON ? REC : fg));
            x += iw + gap;
        }
        if (tw > 0) {
            g.setColour(fg);
            g.drawText(text, juce::Rectangle<float>(x, r.getY(), tw + 4.f, r.getHeight()), juce::Justification::centredLeft, false);
        }
    }

    /** Fires on touch-down (true) and on release/cancel (false): hold-to-play buttons. */
    std::function<void(bool)> onPress;

    void mouseDown(const juce::MouseEvent&) override { pressed = true; repaint(); if (onPress) onPress(true); }
    void mouseDrag(const juce::MouseEvent& e) override {
        const bool in = getLocalBounds().contains(e.getPosition());
        if (in != pressed) { pressed = in; repaint(); }
    }
    void mouseUp(const juce::MouseEvent& e) override {
        const bool fire = pressed && getLocalBounds().contains(e.getPosition()) && e.getDistanceFromDragStart() < 14;
        pressed = false;
        repaint();
        if (onPress) onPress(false);
        if (fire && onClick) onClick();
    }

private:
    void drawIcon(juce::Graphics& g, juce::Rectangle<float> b, juce::Colour c) {
        g.setColour(c);
        switch (icon) {
            case PLAY: {
                juce::Path p;
                p.addTriangle(b.getX(), b.getY(), b.getRight(), b.getCentreY(), b.getX(), b.getBottom());
                g.fillPath(p);
                break;
            }
            case STOP: g.fillRoundedRectangle(b.reduced(1.f), 2.f); break;
            case REC_ICON: g.fillEllipse(b.reduced(1.f)); break;
            case MIC: {
                g.fillRoundedRectangle(b.getX() + b.getWidth() * 0.3f, b.getY(), b.getWidth() * 0.4f, b.getHeight() * 0.62f, 3.f);
                juce::Path arc; // U-shaped holder under the capsule (juce arcs start at 12 o'clock, clockwise)
                arc.addCentredArc(b.getCentreX(), b.getY() + b.getHeight() * 0.40f, b.getWidth() * 0.40f, b.getHeight() * 0.32f, 0.f,
                                  juce::MathConstants<float>::halfPi, juce::MathConstants<float>::pi * 1.5f, true);
                g.strokePath(arc, juce::PathStrokeType(1.5f));
                g.drawLine(b.getCentreX(), b.getY() + b.getHeight() * 0.72f, b.getCentreX(), b.getBottom(), 1.5f);
                break;
            }
            default: break;
        }
    }

    juce::String text;
    Icon icon = NONE;
    juce::Colour accent = ACCENT;
    bool active = false, led = false, pressed = false;
    float fontSize = 12.f;
};

// ---------------------------------------------------------------------------------------------
class Knob : public juce::Component {
public:
    Knob(juce::String label, float mn, float mx, float def)
        : label(std::move(label)), minV(mn), maxV(mx), defV(def), value(def), raw(def) {
        setViewportIgnoreDragFlag(true);
        fmt = [](float v) { return juce::String(juce::roundToInt(v * 100)) + "%"; };
    }

    std::function<juce::String(float)> fmt;
    std::function<void(float)> onChange;
    std::function<void()> onEnd;

    Knob& setStep(float s) { step = s; return *this; }
    Knob& setBipolar(bool b) { bipolar = b; return *this; }
    Knob& setAccent(juce::Colour c) { accent = c; repaint(); return *this; }
    Knob& setLabel(const juce::String& l) { label = l; repaint(); return *this; }
    Knob& setDim(bool d) { if (d != dim) { dim = d; repaint(); } return *this; }
    float get() const { return value; }
    void set(float v) { value = raw = juce::jlimit(minV, maxV, v); repaint(); }

    void paint(juce::Graphics& g) override {
        const auto b = getLocalBounds().toFloat();
        const float labelH = 16.f;
        const float d = juce::jmin(b.getWidth(), b.getHeight() - labelH) - 8.f;
        const juce::Point<float> c(b.getCentreX(), (b.getHeight() - labelH) / 2.f + 2.f);
        const float rad = d / 2.f;
        const float t = (value - minV) / (maxV - minV);
        const juce::Colour col = dim ? DIM : accent;
        const float a0 = -juce::MathConstants<float>::pi * 0.75f, sweep = juce::MathConstants<float>::pi * 1.5f;

        juce::Path track;
        track.addCentredArc(c.x, c.y, rad, rad, 0.f, a0, a0 + sweep, true);
        g.setColour(juce::Colour(0xFF262A32));
        g.strokePath(track, juce::PathStrokeType(4.5f, juce::PathStrokeType::curved, juce::PathStrokeType::rounded));

        juce::Path val;
        if (bipolar) {
            const float mid = (0.f - minV) / (maxV - minV);
            val.addCentredArc(c.x, c.y, rad, rad, 0.f, a0 + sweep * juce::jmin(mid, t), a0 + sweep * juce::jmax(mid, t), true);
        } else if (t > 0.002f) {
            val.addCentredArc(c.x, c.y, rad, rad, 0.f, a0, a0 + sweep * t, true);
        }
        if (!val.isEmpty()) {
            g.setColour(col.withAlpha(0.28f));
            g.strokePath(val, juce::PathStrokeType(9.f, juce::PathStrokeType::curved, juce::PathStrokeType::rounded));
            g.setColour(col);
            g.strokePath(val, juce::PathStrokeType(4.5f, juce::PathStrokeType::curved, juce::PathStrokeType::rounded));
        }

        const float cap = rad - 8.f;
        g.setColour(juce::Colours::black.withAlpha(0.5f)); // drop shadow
        g.fillEllipse(c.x - cap, c.y - cap + 2.5f, cap * 2, cap * 2);
        g.setGradientFill(juce::ColourGradient(juce::Colour(0xFF3C414D), c.x - cap * 0.5f, c.y - cap, juce::Colour(0xFF181A1F), c.x + cap * 0.4f,
                                              c.y + cap, false));
        g.fillEllipse(c.x - cap, c.y - cap, cap * 2, cap * 2);
        g.setColour(juce::Colour(0xFF4B515E).withAlpha(0.8f));
        g.drawEllipse(c.x - cap, c.y - cap, cap * 2, cap * 2, 1.f);

        const float ang = a0 + sweep * t - juce::MathConstants<float>::halfPi; // juce arcs start at 12 o'clock
        const juce::Point<float> p0(c.x + std::cos(ang) * cap * 0.5f, c.y + std::sin(ang) * cap * 0.5f),
            p1(c.x + std::cos(ang) * cap * 0.92f, c.y + std::sin(ang) * cap * 0.92f);
        g.setColour(col);
        g.drawLine(juce::Line<float>(p0, p1), 2.8f);

        const juce::String vs = fmt ? fmt(value) : juce::String();
        g.setColour(dim ? DIM : TEXT);
        g.setFont(mono(vs.length() > 6 ? 9.f : 10.5f));
        g.drawText(vs, juce::Rectangle<float>(c.x - cap, c.y - 8.f, cap * 2, 16.f), juce::Justification::centred, false);

        g.setColour(DIM);
        g.setFont(caps(10.5f));
        g.drawText(label.toUpperCase(), juce::Rectangle<float>(0, b.getHeight() - labelH, b.getWidth(), labelH), juce::Justification::centred, false);
    }

    void mouseDown(const juce::MouseEvent& e) override {
        lastY = (float) e.y;
        if (e.getNumberOfClicks() >= 2) {
            value = raw = defV;
            repaint();
            if (onChange) onChange(value);
            if (onEnd) onEnd();
        }
    }
    void mouseDrag(const juce::MouseEvent& e) override {
        const float dv = (lastY - (float) e.y) / 170.f * (maxV - minV);
        lastY = (float) e.y;
        raw = juce::jlimit(minV, maxV, raw + dv);
        float nv = step > 0 ? std::round(raw / step) * step : raw;
        nv = juce::jlimit(minV, maxV, nv);
        if (nv != value) {
            value = nv;
            repaint();
            if (onChange) onChange(value);
        }
    }
    void mouseUp(const juce::MouseEvent& e) override {
        if (e.mouseWasDraggedSinceMouseDown() && onEnd) onEnd();
    }

private:
    juce::String label;
    float minV, maxV, defV, value, raw, step = 0.f, lastY = 0.f;
    bool bipolar = false, dim = false;
    juce::Colour accent = ACCENT;
};

// ---------------------------------------------------------------------------------------------
class XYPad : public juce::Component {
public:
    XYPad() { setViewportIgnoreDragFlag(true); }

    std::function<void(float, float)> onMove;

    void set(float nx, float ny) { x = nx; y = ny; repaint(); }
    void setLabels(const juce::String& xl, const juce::String& yl) { xLabel = xl; yLabel = yl; repaint(); }
    void setAccent(juce::Colour c) { accent = c; }

    void paint(juce::Graphics& g) override {
        const auto b = getLocalBounds().toFloat().reduced(0.5f);
        g.setGradientFill(juce::ColourGradient::vertical(juce::Colour(0xFF0A0C0F), b.getY(), juce::Colour(0xFF10131A), b.getBottom()));
        g.fillRoundedRectangle(b, 12.f);
        g.setColour(STROKE.withAlpha(0.7f));
        for (int i = 1; i < 4; ++i) {
            g.drawLine(b.getX() + b.getWidth() * (float) i / 4.f, b.getY(), b.getX() + b.getWidth() * (float) i / 4.f, b.getBottom(), 1.f);
            g.drawLine(b.getX(), b.getY() + b.getHeight() * (float) i / 4.f, b.getRight(), b.getY() + b.getHeight() * (float) i / 4.f, 1.f);
        }
        g.setColour(STROKE);
        g.drawRoundedRectangle(b, 12.f, 1.f);

        const float px = b.getX() + x * b.getWidth(), py = b.getY() + (1.f - y) * b.getHeight();
        g.setColour(accent.withAlpha(0.5f));
        g.drawLine(px, b.getY(), px, b.getBottom(), 1.f);
        g.drawLine(b.getX(), py, b.getRight(), py, 1.f);
        g.setColour(accent.withAlpha(touching ? 0.3f : 0.16f));
        g.fillEllipse(px - 24.f, py - 24.f, 48.f, 48.f);
        g.setColour(accent);
        g.fillEllipse(px - 8.f, py - 8.f, 16.f, 16.f);
        g.setColour(juce::Colours::white.withAlpha(0.6f));
        g.fillEllipse(px - 3.f, py - 5.f, 5.f, 4.f);

        g.setColour(DIM);
        g.setFont(caps(10.f));
        g.drawText(xLabel.toUpperCase() + " \xe2\x86\x92", b.reduced(12.f, 8.f).withTrimmedLeft(16.f).toNearestInt(), juce::Justification::bottomLeft, false);
        g.saveState();
        // y label runs upward along the left edge, starting above the x label
        g.addTransform(juce::AffineTransform::rotation(-juce::MathConstants<float>::halfPi, 14.f, b.getBottom() - 32.f));
        g.drawText(yLabel.toUpperCase() + " \xe2\x86\x92", juce::Rectangle<int>(14, (int) b.getBottom() - 32 - 8, 140, 16), juce::Justification::centredLeft, false);
        g.restoreState();
    }

    void mouseDown(const juce::MouseEvent& e) override { touching = true; update(e); }
    void mouseDrag(const juce::MouseEvent& e) override { update(e); }
    void mouseUp(const juce::MouseEvent&) override { touching = false; repaint(); }

private:
    void update(const juce::MouseEvent& e) {
        x = juce::jlimit(0.f, 1.f, (float) e.x / (float) getWidth());
        y = juce::jlimit(0.f, 1.f, 1.f - (float) e.y / (float) getHeight());
        repaint();
        if (onMove) onMove(x, y);
    }
    float x = 0.5f, y = 0.5f;
    bool touching = false;
    juce::String xLabel, yLabel;
    juce::Colour accent = CYAN;
};

} // namespace ui
