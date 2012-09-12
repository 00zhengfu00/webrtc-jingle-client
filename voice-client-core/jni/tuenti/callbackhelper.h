/*
 * webrtc-jingle
 * Copyright 2012 Tuenti Technologies
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#ifndef _JNI_TUENTI_CALLBACKHELPER_H_
#define _JNI_TUENTI_CALLBACKHELPER_H_

#include <string>
#include "jni.h"
#include "tuenti/logging.h"
#include "tuenti/voiceclientnotify.h"
#include "com_tuenti_voice_core_VoiceClient.h"

#include "talk/p2p/base/session.h"
#include "talk/media/base/mediachannel.h"
#include "talk/session/media/mediamessages.h"
#include "talk/session/media/mediasessionclient.h"
#include "talk/xmpp/xmppclient.h"
#include "talk/examples/login/xmpppump.h"

namespace tuenti {

typedef enum {
  ADD,
  REMOVE,
  RESET
} BuddyList;

class CallbackHelper : public VoiceClientNotify {
 public:
  CallbackHelper() {}

  void setJvm(JavaVM* jvm);
  void setReferenceObject(jobject reference_object);
  void CallNativeDispatchEvent(jint type, jint code,
          const std::string& message, jlong call_id);
  void OnXmppStateChange(buzz::XmppEngine::State state);
  void OnCallStateChange(cricket::Session* session,
      cricket::Session::State state, uint32 call_id);
  void OnXmppError(buzz::XmppEngine::Error error);
  void OnBuddyListAdd(std::string user_key, std::string nick);
  void OnBuddyListRemove(std::string user_key);
  void OnBuddyListReset();

 private:
  JavaVM* jvm_;
  jobject reference_object_;
};
}  // Namespace tuenti
#endif  // _JNI_TUENTI_CALLBACKHELPER_H__
