#include "MainComponent.h"
#include <thread>

using namespace juce;

MainComponent::MainComponent(padlab::Engine& e, AudioHost& h)
    : engine(e), host(h), st(e), lcd(st),
      tabBar({"PADS", "EDIT", "FX", "SEQ", "VOICE"}), padsTab(st), editPanel(st), fxTab(st), seqTab(st), voicePanel(st),
      masterKnob("Master", 0.f, 1.f, 0.8f) {
    formats.registerBasicFormats();

    st.select = [this](int id) { selectPad(id); };
    st.padsChanged = [this] { refreshAll(); };
    st.record = [this](std::function<void(ui::SamplePtr)> cb) { beginRecord(std::move(cb)); };
    st.stopRecord = [this] { endRecord(); };
    st.isRecording = [this] { return recording; };
    st.showTab = [this](int t) { tabBar.select(t); showTab(t); };

    addAndMakeVisible(lcd);
    for (int i = 0; i < 4; ++i) {
        bankBtns[(size_t) i].setText(String("BANK ") + String::charToString((juce_wchar) ('A' + i))).setSize(11.f).setAccent(ui::bankColour(i));
        bankBtns[(size_t) i].onClick = [this, i] { setBank(i); };
        addAndMakeVisible(bankBtns[(size_t) i]);
    }
    addAndMakeVisible(tabBar);
    tabBar.onSelect = [this](int t) { showTab(t); };

    addChildComponent(padsTab);
    editPanel.pickFile = [this] { pickFile(); };
    editPanel.openPadFx = [this] { fxTab.showPadFx(); st.showTab(ui::FX); };
    editView.setViewedComponent(&editPanel, false);
    editView.setScrollBarsShown(false, false);
    editView.setScrollOnDragMode(Viewport::ScrollOnDragMode::all);
    addChildComponent(editView);
    addChildComponent(fxTab);
    addChildComponent(seqTab);
    voiceView.setViewedComponent(&voicePanel, false);
    voiceView.setScrollBarsShown(false, false);
    voiceView.setScrollOnDragMode(Viewport::ScrollOnDragMode::all);
    addChildComponent(voiceView);

    playBtn.setText("PLAY").setIcon(ui::Btn::PLAY).setAccent(ui::GREEN);
    dubBtn.setText("OVERDUB").setSize(11.5f).setIcon(ui::Btn::REC_ICON).setAccent(ui::REC);
    micBtn.setText("MIC").setIcon(ui::Btn::MIC).setAccent(ui::REC);
    playBtn.onClick = [this] { if (engine.playing) engine.stop(); else engine.play(); updateTransport(); };
    dubBtn.onClick = [this] { engine.overdub = !engine.overdub; updateTransport(); };
    micBtn.onClick = [this] {
        if (recording) { endRecord(); return; }
        const int target = st.selected;
        beginRecord([this, target](ui::SamplePtr s) {
            if (s) assign(target, s); else toast("Nothing recorded - is the microphone allowed?");
        });
    };
    masterKnob.onChange = [this](float v) { engine.masterVol = v; };
    masterKnob.set(engine.masterVol);
    addAndMakeVisible(playBtn);
    addAndMakeVisible(dubBtn);
    addAndMakeVisible(micBtn);
    addAndMakeVisible(masterKnob);

    toastLabel.setFont(ui::sans(13.f, true));
    toastLabel.setColour(Label::textColourId, ui::TEXT);
    toastLabel.setColour(Label::backgroundColourId, Colour(0xE61D2027));
    toastLabel.setJustificationType(Justification::centred);
    addChildComponent(toastLabel);

    setBank(0);
    showTab(ui::PADS);
    updateTransport();
    Desktop::getInstance().setScreenSaverEnabled(false);
    startTimerHz(60);
    toast("Audio: " + host.describe());
}

MainComponent::~MainComponent() { stopTimer(); }

void MainComponent::paint(Graphics& g) {
    g.setGradientFill(ColourGradient::vertical(Colour(0xFF14161B), 0.f, ui::BG, (float) getHeight()));
    g.fillAll();
}

void MainComponent::resized() {
    auto r = getLocalBounds();
    if (auto* d = Desktop::getInstance().getDisplays().getPrimaryDisplay())
        if (getPeer() != nullptr) r = d->safeAreaInsets.subtractedFrom(r);
    r.reduce(10, 8);
    lcd.setBounds(r.removeFromTop(92));
    r.removeFromTop(8);
    auto banks = r.removeFromTop(36);
    const int bw = banks.getWidth() / 4;
    for (auto& b : bankBtns) b.setBounds(banks.removeFromLeft(bw).reduced(2, 0));
    r.removeFromTop(6);
    tabBar.setBounds(r.removeFromTop(44));
    r.removeFromTop(8);
    auto transport = r.removeFromBottom(80);
    r.removeFromBottom(6);
    auto row = transport;
    masterKnob.setBounds(row.removeFromRight(80));
    const int total = row.getWidth();
    playBtn.setBounds(row.removeFromLeft(total * 36 / 100).reduced(3, 12));
    dubBtn.setBounds(row.removeFromLeft(total * 36 / 100).reduced(3, 12));
    micBtn.setBounds(row.reduced(3, 12));

    padsTab.setBounds(r);
    editView.setBounds(r);
    editPanel.setSize(r.getWidth(), ui::EditPanel::HEIGHT);
    fxTab.setBounds(r);
    seqTab.setBounds(r);
    voiceView.setBounds(r);
    voicePanel.setSize(r.getWidth(), ui::VoicePanel::HEIGHT);
    toastLabel.setBounds(r.getX() + 20, r.getBottom() - 46, r.getWidth() - 40, 34);
}

void MainComponent::showTab(int t) {
    tab = t;
    padsTab.setVisible(t == ui::PADS);
    editView.setVisible(t == ui::EDIT);
    fxTab.setVisible(t == ui::FX);
    seqTab.setVisible(t == ui::SEQ);
    voiceView.setVisible(t == ui::VOICE);
    tabBar.select(t);
    if (t == ui::EDIT) editPanel.refresh();
    if (t == ui::FX) fxTab.rebind();
    if (t == ui::SEQ) seqTab.syncBpm();
}

void MainComponent::selectPad(int id) {
    if (st.selected == id) return;
    st.selected = id;
    if (tab == ui::EDIT) editPanel.refresh();
    else if (tab == ui::FX && fxTab.padMode()) fxTab.rebind();
    padsTab.repaintPads();
    seqTab.refresh();
}

void MainComponent::setBank(int b) {
    st.bank = b;
    st.selected = b * 16 + st.selected % 16;
    for (int i = 0; i < 4; ++i) bankBtns[(size_t) i].setActive(i == b);
    refreshAll();
}

void MainComponent::refreshAll() {
    padsTab.repaintPads();
    seqTab.refresh();
    if (tab == ui::EDIT) editPanel.refresh();
    if (tab == ui::FX) fxTab.rebind();
    lcd.repaint();
}

void MainComponent::updateTransport() {
    playBtn.setText(engine.playing ? "STOP" : "PLAY").setIcon(engine.playing ? ui::Btn::STOP : ui::Btn::PLAY).setActive(engine.playing);
    dubBtn.setActive(engine.overdub);
    micBtn.setActive(recording).setText(recording ? "STOP" : "MIC");
    voicePanel.onRecordingChanged();
}

void MainComponent::toast(const String& msg) {
    toastLabel.setText(msg, dontSendNotification);
    toastLabel.setVisible(true);
    toastLabel.toFront(false);
    toastUntil = Time::currentTimeMillis() + 3500;
}

void MainComponent::timerCallback() {
    ++frame;
    lcd.repaint();
    padsTab.tick();
    seqTab.tick();
    if ((frame % 6) == 0) {
        if (tab == ui::EDIT) editPanel.tick();
        if (tab == ui::VOICE) voicePanel.tick();
        if (recording && engine.recordedFrames() >= padlab::SR * 60 - 2048) endRecord();
    }
    if (toastLabel.isVisible() && Time::currentTimeMillis() > toastUntil) toastLabel.setVisible(false);
}

// ---- recording -----------------------------------------------------------------------------
void MainComponent::beginRecord(std::function<void(ui::SamplePtr)> cb) {
    if (recording) return;
    auto proceed = [this, cb] {
        if (!host.setInput(true)) { toast("Microphone not available"); cb(nullptr); return; }
        engine.startRecording();
        recording = true;
        recordDone = cb;
        updateTransport();
    };
    if (RuntimePermissions::isRequired(RuntimePermissions::recordAudio) && !RuntimePermissions::isGranted(RuntimePermissions::recordAudio)) {
        RuntimePermissions::request(RuntimePermissions::recordAudio, [this, proceed, cb](bool granted) {
            if (granted) proceed(); else { toast("Microphone permission denied"); cb(nullptr); }
        });
    } else {
        proceed();
    }
}

void MainComponent::endRecord() {
    if (!recording) return;
    auto s = engine.stopRecording();
    host.setInput(false);
    recording = false;
    updateTransport();
    auto cb = std::move(recordDone);
    recordDone = nullptr;
    if (cb) cb(s);
}

// ---- samples -------------------------------------------------------------------------------
void MainComponent::assign(int pad, std::shared_ptr<padlab::Sample> s) {
    padlab::Pad& p = engine.pads[(size_t) pad];
    p.sample.set(s);
    p.name = s->name;
    p.start = 0.f;
    p.end = 1.f;
    p.rendered.set(nullptr);
    p.renderedKey = 0;
    engine.requestWarp(pad);
    refreshAll();
}

void MainComponent::pickFile() {
    chooser = std::make_unique<FileChooser>("Choose an audio file", File(), "*.wav;*.mp3;*.ogg;*.flac;*.aif;*.aiff", true);
    const int target = st.selected;
    chooser->launchAsync(FileBrowserComponent::openMode | FileBrowserComponent::canSelectFiles, [this, target](const FileChooser& fc) {
        auto urls = fc.getURLResults();
        if (!urls.isEmpty()) loadFromUrl(urls[0], target);
    });
}

void MainComponent::loadFromUrl(const URL& url, int targetPad) {
    toast("Loading...");
    SafePointer<MainComponent> self(this);
    std::thread([this, url, targetPad, self] {
        std::shared_ptr<padlab::Sample> out;
        String err;
        AudioFormatManager fm;
        fm.registerBasicFormats();
        std::unique_ptr<InputStream> in = url.isLocalFile() ? std::unique_ptr<InputStream>(url.getLocalFile().createInputStream())
                                                            : url.createInputStream(URL::InputStreamOptions(URL::ParameterHandling::inAddress));
        if (in == nullptr) err = "cannot open file";
        else {
            MemoryBlock mb;
            in->readIntoMemoryBlock(mb, 96 * 1024 * 1024);
            std::unique_ptr<AudioFormatReader> reader(fm.createReaderFor(std::make_unique<MemoryInputStream>(mb, true)));
            if (!reader) err = "unsupported format";
            else {
                const int rate = (int) reader->sampleRate;
                const int len = (int) std::min<int64>(reader->lengthInSamples, (int64) rate * 90);
                const int ch = (int) std::min<unsigned>(reader->numChannels, 2u);
                AudioBuffer<float> buf(ch, len);
                reader->read(&buf, 0, len, 0, true, true);
                std::vector<float> l(buf.getReadPointer(0), buf.getReadPointer(0) + len), r;
                if (ch > 1) r.assign(buf.getReadPointer(1), buf.getReadPointer(1) + len);
                String name = url.getFileName().upToLastOccurrenceOf(".", false, false);
                if (name.length() > 12) name = name.substring(0, 12);
                out = padlab::Sample::prepare(name.toStdString(), std::move(l), std::move(r), rate);
            }
        }
        MessageManager::callAsync([self, out, err, targetPad] {
            if (self == nullptr) return;
            if (out) self->assign(targetPad, out);
            else self->toast("Load failed: " + err);
        });
    }).detach();
}

// ---- screenshots ---------------------------------------------------------------------------
void MainComponent::saveScreenshots(const File& dir, int width, int height) {
    dir.createDirectory();
    setSize(width, height);
    const char* names[5] = {"pads", "edit", "fx", "seq", "voice"};
    PNGImageFormat png;
    auto snap = [&](const String& name) {
        resized();
        auto img = createComponentSnapshot(getLocalBounds(), true, 2.0f);
        FileOutputStream out(dir.getChildFile(name + ".png"));
        out.setPosition(0);
        out.truncate();
        png.writeImageToStream(img, out);
    };
    toastLabel.setVisible(false);
    toastUntil = 0;
    for (int t = 0; t < 5; ++t) {
        if (t == ui::VOICE) voicePanel.debugDemo();
        showTab(t);
        tabBar.snap(t);
        toastLabel.setVisible(false);
        snap(names[t]);
    }
    fxTab.showPadFx();
    showTab(ui::FX);
    tabBar.snap(ui::FX);
    snap("fx_pad");
}
