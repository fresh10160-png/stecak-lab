#include <juce_gui_basics/juce_gui_basics.h>
#include "AudioHost.h"
#include "MainComponent.h"
#include "core/DemoKit.h"

class PadLabApp : public juce::JUCEApplication {
public:
    const juce::String getApplicationName() override { return "PadLab"; }
    const juce::String getApplicationVersion() override { return "2.0.0"; }
    bool moreThanOneInstanceAllowed() override { return true; }

    void initialise(const juce::String& commandLine) override {
        juce::Desktop::getInstance().setOrientationsEnabled(juce::Desktop::upright);
        const bool shots = commandLine.contains("--shots");
        const bool audioOk = shots ? false : audio.startOutput();   // screenshots run without a sound card
        juce::ignoreUnused(audioOk);

        engine = std::make_unique<padlab::Engine>();
        padlab::loadDemoKit(*engine);
        audio.attach(engine.get());
        window = std::make_unique<MainWindow>(getApplicationName(), *engine, audio);

        if (shots) {
            auto dir = juce::File::getCurrentWorkingDirectory().getChildFile(commandLine.fromFirstOccurrenceOf("--shots", false, false).trim().unquoted());
            window->shoot(dir);
            quit();
        }
    }

    void shutdown() override {
        window.reset();
        audio.stop();
        engine.reset();
    }

    void systemRequestedQuit() override { quit(); }

private:
    class MainWindow : public juce::DocumentWindow {
    public:
        MainWindow(const juce::String& name, padlab::Engine& e, AudioHost& h)
            : DocumentWindow(name, ui::BG, DocumentWindow::closeButton) {
            setUsingNativeTitleBar(false);
            setTitleBarHeight(0);
            setContentOwned(main = new MainComponent(e, h), true);
           #if JUCE_ANDROID || JUCE_IOS
            setFullScreen(true);
           #else
            centreWithSize(390, 820);
            setResizable(true, false);
           #endif
            setVisible(true);
        }
        void closeButtonPressed() override { juce::JUCEApplication::getInstance()->systemRequestedQuit(); }
        void shoot(const juce::File& dir) { main->saveScreenshots(dir, 390, 820); }
    private:
        MainComponent* main = nullptr;
    };

    AudioHost audio;
    std::unique_ptr<padlab::Engine> engine;
    std::unique_ptr<MainWindow> window;
};

START_JUCE_APPLICATION(PadLabApp)
