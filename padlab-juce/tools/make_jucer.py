#!/usr/bin/env python3
"""Regenerates PadLab.jucer from the files under Source/. Run after adding/removing source files."""
import os, hashlib

root = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
os.chdir(root)

def rid(s):
    h = hashlib.sha1(s.encode()).hexdigest()
    alphabet = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
    return ''.join(alphabet[int(h[i * 2:i * 2 + 2], 16) % len(alphabet)] for i in range(6))

def tree(d, indent):
    out = []
    pad = '  ' * indent
    for name in sorted(os.listdir(d)):
        p = os.path.join(d, name)
        if os.path.isdir(p):
            out.append(f'{pad}<GROUP id="{{{rid(p).upper()}-GROUP}}" name="{name}">')
            out += tree(p, indent + 1)
            out.append(f'{pad}</GROUP>')
        elif name.endswith(('.cpp', '.h')):
            comp = 1 if name.endswith('.cpp') else 0
            rel = p.replace(os.sep, '/')
            out.append(f'{pad}<FILE id="{rid(p)}" name="{name}" compile="{comp}" resource="0" file="{rel}"/>')
    return out

modules = ['juce_audio_basics', 'juce_audio_devices', 'juce_audio_formats', 'juce_core', 'juce_data_structures',
           'juce_events', 'juce_graphics', 'juce_gui_basics']
icon_id = rid('Assets/icon.png')

x = []
x.append('<?xml version="1.0" encoding="UTF-8"?>\n')
x.append('<JUCERPROJECT id="PadLb1" name="PadLab" projectType="guiapp" bundleIdentifier="com.stecaklab.padlab" version="2.0.0"')
x.append('              companyName="StecakLab" useAppConfig="0" addUsingNamespaceToJuceHeader="0" jucerFormatVersion="1"')
x.append('              cppLanguageStandard="17" displaySplashScreen="1" reportAppUsage="0">')
x.append('  <MAINGROUP id="PadLg1" name="PadLab">')
x.append('    <GROUP id="{SRC00000-0000-0000-0000-000000000000}" name="Source">')
x += tree('Source', 3)
x.append('    </GROUP>')
x.append('    <GROUP id="{AST00000-0000-0000-0000-000000000000}" name="Assets">')
x.append(f'      <FILE id="{icon_id}" name="icon.png" compile="0" resource="0" file="Assets/icon.png"/>')
x.append('    </GROUP>')
x.append('  </MAINGROUP>')
x.append('  <JUCEOPTIONS JUCE_STRICT_REFCOUNTEDPOINTER="1" JUCE_USE_MP3AUDIOFORMAT="1" JUCE_WEB_BROWSER="0" JUCE_USE_CURL="0"/>')
x.append('  <EXPORTFORMATS>')
x.append(f'    <ANDROIDSTUDIO targetFolder="Builds/Android" androidMinimumSDK="24" androidTargetSDK="35" microphonePermissionNeeded="1"')
x.append(f'                   androidScreenOrientation="portrait" androidVersionCode="10" androidKeyAlias="padlab"')
x.append('                   androidInternetNeeded="0" androidReadMediaAudioPermission="0" androidReadMediaImagesPermission="0"')
x.append('                   androidReadMediaVideoPermission="0" androidExternalReadNeeded="0" androidExternalWriteNeeded="0"')
x.append(f'                   smallIcon="{icon_id}" bigIcon="{icon_id}" androidArchitectures="arm64-v8a">')
x.append('      <CONFIGURATIONS>')
x.append('        <CONFIGURATION isDebug="1" name="Debug" androidArchitectures="arm64-v8a"/>')
x.append('        <CONFIGURATION isDebug="0" name="Release" optimisation="3" androidArchitectures="arm64-v8a"/>')
x.append('      </CONFIGURATIONS>')
x.append('      <MODULEPATHS>')
for m in modules:
    x.append(f'        <MODULEPATH id="{m}" path="JUCE/modules"/>')
x.append('      </MODULEPATHS>')
x.append('    </ANDROIDSTUDIO>')
x.append('  </EXPORTFORMATS>')
x.append('  <MODULES>')
for m in modules:
    x.append(f'    <MODULE id="{m}" showAllCode="1" useLocalCopy="0" useGlobalPath="0"/>')
x.append('  </MODULES>')
x.append('  <LIVE_SETTINGS>')
x.append('    <OSX/>')
x.append('  </LIVE_SETTINGS>')
x.append('</JUCERPROJECT>')
open('PadLab.jucer', 'w').write('\n'.join(x) + '\n')
print('PadLab.jucer written')
