# Everything installed for this project is listed here, so it can all be removed again.
#   Homebrew packages:  Brewfile
#   Android SDK:        $(ANDROID_HOME)
#   Gradle caches:      ~/.gradle   (also used by any other Android/Java project)
#   adb keys:           ~/.android

export JAVA_HOME    := $(firstword $(wildcard \
  /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home))
export ANDROID_HOME := $(HOME)/Library/Android/sdk
SDK_PACKAGES := "platform-tools" "platforms;android-35" "build-tools;35.0.0"
APK := app/build/outputs/apk/debug/app-debug.apk

.PHONY: setup build test install run test-songs clean uninstall-tools

setup:            ## Install the Mac tools and the Android SDK
	brew bundle --file=Brewfile
	yes | sdkmanager --sdk_root="$(ANDROID_HOME)" --licenses > /dev/null
	sdkmanager --sdk_root="$(ANDROID_HOME)" --install $(SDK_PACKAGES)

build:            ## Build the debug APK
	./gradlew assembleDebug

test:             ## Run the tests that don't need the phone
	./gradlew testDebugUnitTest

install:          ## Install the APK on the phone (over Wi-Fi)
	adb install -r $(APK)

run: install      ## Install and start the app
	adb shell am start -n com.radiogolha/.MainActivity

test-songs:       ## Generate 8 tagged test songs and put them in Music/Golha/ on the phone
	rm -rf build/test-songs
	python3 -I scripts/make_test_songs.py build/test-songs
	adb shell mkdir -p /sdcard/Music/Golha
	adb push build/test-songs/*.flac /sdcard/Music/Golha/
	for f in build/test-songs/*.flac; do adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
	  -d "\"file:///sdcard/Music/Golha/$$(basename "$$f")\"" > /dev/null; done

clean:
	./gradlew clean

uninstall-tools:  ## Remove everything `make setup` installed
	-./gradlew --stop
	-brew uninstall --zap openjdk@17 android-commandlinetools android-platform-tools
	rm -rf "$(ANDROID_HOME)"
	@echo "Left in place (shared with other projects, delete by hand if unused): ~/.gradle ~/.android"
