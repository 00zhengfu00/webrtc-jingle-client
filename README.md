webrtc-jingle for android 
=============
(libjingle signaling + webrtc voice engine)

About:
---------------------
* Working example app of libjingle and webrtc voice backend, with a few buttons.
* Our demo apk with support for arm/armv7 comes to 6.4 mb, approx a third when zipped in an apk. Without compiler optimizations it's a bit less.
* Based on libjingle trunk and webrtc trunk updated on regular intervals.
* Stability improvements needed for the c layer and misc. pieces that were needed for Android support.
* Calling between two of these clients may require your own stun server, which is also buildable and included in libjingle. If one client is web based gmail for example, just for testing, I haven't seen any problems.

Prereqs:
---------------------
* Get yourself an [android NDK r8](http://developer.android.com/sdk/ndk/index.html). I have tested r8b and it doesn't work. Please use r8. Just copy the link on the page, and modify to be r8.
* Get an [Android SDK](http://developer.android.com/sdk/installing.html) installed as well.
* Install [eclipse](http://www.eclipse.org/downloads/)
* Install the [Android SDK plugin](http://developer.android.com/sdk/eclipse-adt.html) for eclipse if you didn't in the sdk steps.
* Install Maven, and add the following to your .bashrc or .bash_profile.
```
#mvn variables
export ANDROID_HOME=$ANDROID_SDK_ROOT
export ANDROID_NDK_HOME=$ANDROID_NDK_ROOT
```

Get started with the code:
----------------------
```
# mkdir webrtcjingleproject
# cd webrtcjingleproject
# gclient config https://github.com/lukeweber/webrtc-jingle-client.git --name trunk
# gclient sync

# cd trunk
# export CHROME_SRC=`pwd`
# source build/android/envsetup.sh
# gclient runhooks
```
* Set your username, pass and connection setttings in voice-client-example/src/main/java/com/tuenti/voice/example/ui/VoiceClientActivity.java.
* cd trunk/voice-client-core && ./build.sh
* cd trunk && mvn clean install
* To install on a connected device: mvn -pl voice-client-example android:run
* To run a debugger ex.: build/android/gdb_apk -p com.tuenti.voice.example -l voice-client-core/obj/local/armeabi-v7a/
 

Todo/Issues:
--------------------------
* See Tickets
