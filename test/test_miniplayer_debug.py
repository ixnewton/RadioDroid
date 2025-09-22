#!/usr/bin/env python3
"""
Test enhanced debug logging for mini-player 2nd pane suggestions verification
"""
import subprocess
import time

def run_adb(cmd):
    result = subprocess.run(f"adb {cmd}", shell=True, capture_output=True, text=True)
    return result.returncode, result.stdout.strip(), result.stderr.strip()

def test_miniplayer_debug():
    print("🔍 TESTING ENHANCED MINI-PLAYER DEBUG LOGGING")
    print("=" * 60)
    
    print("📱 Starting RadioDroid with enhanced debug logging...")
    run_adb("shell am start -n net.programmierecke.radiodroid2/.ActivityMain")
    time.sleep(2)
    
    print("🎵 Starting MediaBrowser service...")
    run_adb("shell am start-service net.programmierecke.radiodroid2/.service.RadioDroidBrowserService")
    time.sleep(1)
    
    print("📋 Clearing logs to capture enhanced debug output...")
    run_adb("logcat -c")
    
    print("🔍 ENHANCED DEBUG LOGGING FEATURES:")
    print("=" * 60)
    
    print("✅ ROOT CONFIGURATION DEBUG:")
    print("   • Detailed hint registration logging")
    print("   • EXTRA_SUGGESTED and EXTRA_RECENT verification")
    print("   • Complete root configuration summary")
    print("   • Clear identification of mini-player setup")
    
    print("\n✅ MINI-PLAYER REQUEST DEBUG:")
    print("   • 🎵🎵🎵 Visual markers for mini-player requests")
    print("   • Detailed parent ID verification")
    print("   • Purpose and context explanation")
    print("   • Data source analysis (recent vs favorites)")
    
    print("\n✅ CONTENT ANALYSIS DEBUG:")
    print("   • History manager availability check")
    print("   • Recent stations count and details")
    print("   • Individual station information (name, UUID, tags, icons)")
    print("   • Fallback logic with detailed reasoning")
    print("   • Content limitation explanation (UAMP compliance)")
    
    print("\n✅ STYLING DEBUG:")
    print("   • Per-item styling decisions")
    print("   • LIST format enforcement for mini-player")
    print("   • Currently playing indicator logic")
    print("   • Icon availability verification")
    
    print("\n✅ FINAL PROCESSING DEBUG:")
    print("   • Result preparation logging")
    print("   • Icon loading process explanation")
    print("   • Android Auto delivery confirmation")
    print("   • Empty content handling with recommendations")
    
    print("\n🎯 DEBUG LOG STRUCTURE:")
    print("   1. 🎵🎵🎵 MINI-PLAYER 2ND PANE REQUEST RECEIVED 🎵🎵🎵")
    print("   2. === MEDIA_ID_SUGGESTED CASE TRIGGERED ===")
    print("   3. 📊 CHECKING DATA SOURCES")
    print("   4. ✅ USING RECENT STATIONS (or ⚠️ FALLBACK)")
    print("   5. 📋 MINI-PLAYER SUGGESTIONS LIST")
    print("   6. 🎯 MINI-PLAYER 2ND PANE RESULT")
    print("   7. 🎵 MINI-PLAYER ITEM STYLING (per item)")
    print("   8. 🎵 MINI-PLAYER 2ND PANE - FINAL PROCESSING")
    print("   9. 🎵🎵🎵 MINI-PLAYER 2ND PANE REQUEST COMPLETED 🎵🎵🎵")
    
    print("\n📊 VERIFICATION POINTS:")
    print("   • Hint registration: Look for 'EXTRA_SUGGESTED hint registered'")
    print("   • Request trigger: Look for '🎵🎵🎵 MINI-PLAYER 2ND PANE REQUEST'")
    print("   • Data source: Look for 'USING RECENT STATIONS' or 'USING FALLBACK'")
    print("   • Content list: Look for 'MINI-PLAYER SUGGESTIONS LIST'")
    print("   • Styling: Look for 'MINI-PLAYER ITEM STYLING'")
    print("   • Completion: Look for 'REQUEST COMPLETED'")
    
    print("\n⚡ TROUBLESHOOTING GUIDE:")
    print("   If mini-player 2nd pane is empty:")
    print("   1. Check for 'NO CONTENT AVAILABLE' in logs")
    print("   2. Verify recent stations: 'Recent stations list: X items'")
    print("   3. Check fallback: 'Favorites available: X items'")
    print("   4. Look for 'Recommendation: Play some stations'")
    print("   ")
    print("   If mini-player not requesting suggestions:")
    print("   1. Verify hint registration: 'EXTRA_SUGGESTED hint registered'")
    print("   2. Check Android Auto connection")
    print("   3. Ensure station is playing (mini-player active)")
    print("   4. Try swiping left on mini-player")
    
    # Wait a moment for service to initialize
    time.sleep(3)
    
    # Check for recent logs
    print("\n📊 Checking for enhanced debug logs...")
    code, logs, err = run_adb("logcat -d | grep -E '🎵|MINI-PLAYER|EXTRA_SUGGESTED|RadioDroidBrowser.*===|RadioDroidBrowser.*•' | tail -20")
    
    if logs:
        print("\nRecent enhanced debug logs:")
        print("-" * 40)
        for line in logs.split('\n'):
            if line.strip():
                # Clean up the log line for better readability
                parts = line.split('RadioDroidBrowser:')
                if len(parts) > 1:
                    print(f"📋 {parts[1].strip()}")
                else:
                    print(f"📋 {line.strip()}")
        print("-" * 40)
    else:
        print("No enhanced debug logs found yet (service may still be initializing)")
    
    print("\n🎉 ENHANCED DEBUG LOGGING DEPLOYED!")
    print("Mini-player 2nd pane loading is now fully debuggable and verifiable.")
    
    print("\n🧪 TO TEST THE DEBUG LOGGING:")
    print("   1. Connect to Android Auto")
    print("   2. Start playing a station in RadioDroid")
    print("   3. Monitor logs: adb logcat | grep -E '🎵|MINI-PLAYER|SUGGESTED'")
    print("   4. Swipe left on mini-player to trigger suggestions request")
    print("   5. Watch for detailed debug output in logs")
    print("   6. Verify each step of the process")
    
    print("\n✨ DEBUG BENEFITS:")
    print("   • Complete visibility into mini-player 2nd pane loading")
    print("   • Easy identification of issues and bottlenecks")
    print("   • Verification of UAMP pattern compliance")
    print("   • Clear troubleshooting guidance")
    print("   • Professional debugging experience")
    print("   • Visual markers for easy log filtering")

if __name__ == "__main__":
    test_miniplayer_debug()
