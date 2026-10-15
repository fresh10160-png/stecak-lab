#pragma once
#include <juce_audio_formats/juce_audio_formats.h>
#include "AudioHost.h"
#include "ui/EditPanel.h"
#include "ui/Tabs.h"
#include "ui/VoicePanel.h"

class MainComponent : public juce::Component, private juce::Timer {
public:
    MainComponent(padlab::Engine& e, AudioHost& h);
    ~MainComponent() override;

    void paint(juce::Graphics& g) override;
    void resized() override;

    /** Renders every tab to PNGs (used for screenshots/CI). */
    void saveScreenshots(const juce::File& dir, int width, int height);

private:
    void timerCallback() override;
    void showTab(int t);
    void selectPad(int id);
    void setBank(int b);
    void refreshAll();
    void updateTransport();
    void toast(const juce::String& msg);
    void beginRecord(std::function<void(ui::SamplePtr)> cb);
    void endRecord();
    void pickFile();
    void loadFromUrl(const juce::URL& url, int targetPad);
    void assign(int pad, std::shared_ptr<padlab::Sample> s);

    padlab::Engine& engine;
    AudioHost& host;
    ui::UiState st;
    int tab = 0;
    bool recording = false;
    std::function<void(ui::SamplePtr)> recordDone;
    std::unique_ptr<juce::FileChooser> chooser;
    juce::AudioFormatManager formats;

    ui::Lcd lcd;
    std::array<ui::Btn, 4> bankBtns;
    ui::TabBar tabBar;
    ui::PadsTab padsTab;
    ui::EditPanel editPanel;
    juce::Viewport editView, voiceView;
    ui::FxTab fxTab;
    ui::SeqTab seqTab;
    ui::VoicePanel voicePanel;
    ui::Btn playBtn, dubBtn, micBtn;
    ui::Knob masterKnob;
    juce::Label toastLabel;
    int64_t toastUntil = 0;
    int frame = 0;
};
