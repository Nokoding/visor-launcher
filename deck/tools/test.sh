#!/usr/bin/env bash
# Compiles the pure logic classes and runs the JVM tests. Usage: tools/test.sh <org.json jar>
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
JSON="$1"
OUT="${DECK_TEST_OUT:-$HERE/build/test}"
rm -rf "$OUT"; mkdir -p "$OUT"
P="$HERE/src/com/nokoding/deck"
# 2.8 pure classes and their tests
X28=("$P/TaskList.java" "$P/KillCmd.java" "$P/ForceStopMatcher.java" "$P/DisplayRules.java" "$P/DisplayState.java" "$P/ProfileCmd.java"
  "$P/GameProfiles.java" "$P/GameBundle.java" "$P/ThemeSpec.java" "$P/ThemeStore.java" "$P/ProfileMachine.java" "$P/TouchVol.java" "$P/CcModel.java" "$P/CcLayout.java" "$P/CcPrefs.java"
  "$HERE/tests/Tests28A.java" "$HERE/tests/Tests28B.java" "$HERE/tests/Tests28C.java" "$HERE/tests/Guards28.java")
# 2.9 pure classes and their tests
X29=("$P/ComboTable.java" "$P/ComboGate.java" "$P/ComboLearn.java" "$P/TutorialModel.java" "$P/TutorialProgress.java" "$P/StickVol.java" "$P/HoldKey.java" "$P/PadOutline.java" "$P/PerfMode.java" "$P/GyroIntro.java"
  "$HERE/tests/Tests210.java" "$HERE/tests/Tests217.java" "$HERE/tests/Tests29A.java" "$HERE/tests/Tests29B.java" "$HERE/tests/Guards29.java")
javac -encoding UTF-8 -Xlint:all -Werror -cp "$JSON" -d "$OUT" \
  "$P/Spring.java" "$P/FrameStats.java" "$P/PanModel.java" "$P/WheelMath.java" "$P/StickDial.java" \
  "$P/OrderList.java" "$P/InputMode.java" "$P/GyroFilter.java" "$P/Fmt.java" "$P/SoundMap.java" "$P/Synth.java" "$P/HapticPatterns.java" "$P/HumModel.java" "$P/PointerMath.java" \
  "$P/PackZip.java" "$P/Recents.java" "$P/VolumeMath.java" "$P/DeckCmd.java" "$P/IconPick.java" "$P/PlayerLeds.java" \
  "$P/PadTest.java" "$P/Confetti.java" "$P/Mat3D.java" "$P/Attitude.java" "$P/ControllerModel.java" "$P/ModelObj.java" "$P/VideoLimits.java" "$P/VideoMath.java" "$P/IconPacks.java" "$P/StickRoute.java" \
  "$P/TickScheduler.java" "$P/TextReveal.java" "$P/FlyoutAnim.java" "$P/AnimOption.java" "$P/AnimSpec.java" "$P/AnimConfig.java" \
  "$P/SensorClock.java" "$P/Stillness.java" "$P/GyroCal.java" "$P/GyroBias.java" "$P/AxisMap.java" "$P/CalCheck.java" \
  "$P/FrameGate.java" "$P/ChipFocus.java" "$P/PsGate.java" "$P/PsKill.java" "$P/PsRules.java" "$P/PsSetup.java" "$P/PsStick.java" \
  "$P/RevealPlan.java" "$P/RowFit.java" "$P/Marquee.java" "$P/RowHelp.java" \
  "$P/RemapKey.java" "$P/RemapAction.java" "$P/RemapTable.java" "$P/RemapGate.java" "$P/RemapLearn.java" "$P/RemapHold.java" \
  "$P/ClockFmt.java" "$P/WallpaperSpec.java" "$P/Wallpapers.java" "$P/WallpaperOptions.java" "$P/ParallaxMath.java" "$P/WallpaperMem.java" \
  "$P/AnimPlay.java" "$P/BurnIn.java" "$P/DreamRules.java" "$P/ScreensaverCmd.java" \
  "${X28[@]}" "${X29[@]}" \
  "$HERE/tests/LogicTests.java" "$HERE/tests/Tests27.java" "$HERE/tests/Guards27.java" "$HERE/tests/Tests28.java" "$HERE/tests/Tests29.java"
# deck.src lets the row help guard test read the app sources (every Panel.Row label needs a help sentence)
java -Ddeck.src="$P" -cp "$OUT:$JSON" com.nokoding.deck.LogicTests
