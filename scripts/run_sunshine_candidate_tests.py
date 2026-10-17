#!/usr/bin/env python3
"""Compile actual Sunshine encoder configuration statements and JNI scope with host fakes.
Verifies policy/ownership only; not Android codec support or Moonlight interoperability.
"""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
s=(r/'native/sunshine/sunshine.cpp').read_text();start=s.index('        const int encodeFrameRate = config.framerate;');end=s.index('        if (config.videoFormat == 1)',start)
configure=s[start:end]
bridge=(r/'native/sunshine/airplay_bridge.cpp').read_text();bridge=bridge[bridge.index('void sendVideoFrame('):bridge.index('} // namespace airplay_bridge')]
chain=s[s.index('    void callJavaOnVideoFrame('):s.rindex('}')]
symbols='static jclass airPlayServiceClass=(void*)1;static jmethodID airPlaySendFrameMethod=(void*)1;' 
header='''#pragma once
using jint=int;
#define JNI_VERSION_1_6 0x00010006
#define JNI_OK 0
#define JNI_EDETACHED -2
#include <cstdint>
#include <cstddef>
using jclass=void*;using jmethodID=void*;using jbyteArray=void*;using jbyte=char;using jboolean=bool;
struct JNIEnv {jbyteArray NewByteArray(size_t);void SetByteArrayRegion(jbyteArray,int,size_t,const jbyte*);void CallStaticVoidMethod(jclass,jmethodID,jbyteArray,jboolean);void DeleteLocalRef(void*);bool ExceptionCheck();void ExceptionDescribe();void ExceptionClear();};
struct JavaVM {jint GetEnv(void**,jint);jint AttachCurrentThread(JNIEnv**,void*);jint DetachCurrentThread();};
'''
source=r'''
#include <cassert>
#include <atomic>
#include <map>
#include <string>
#include <thread>
#include <vector>
#include "jni_scope.h"
std::atomic<int> attaches{0},detaches{0};thread_local bool attached=false;thread_local JNIEnv environment;bool fail=false;thread_local bool failedAllocation=false,pendingException=false;
jint JavaVM::GetEnv(void** out,jint){*out=attached?&environment:nullptr;return attached?JNI_OK:JNI_EDETACHED;}
jint JavaVM::AttachCurrentThread(JNIEnv** out,void*){if(fail)return -1;attached=true;*out=&environment;++attaches;return JNI_OK;}
jint JavaVM::DetachCurrentThread(){assert(attached);attached=false;++detaches;return JNI_OK;}
jbyteArray JNIEnv::NewByteArray(size_t){assert(attached);if(failedAllocation){pendingException=true;return nullptr;}return (void*)1;}
void JNIEnv::SetByteArrayRegion(jbyteArray,int,size_t,const jbyte*){assert(attached);}
void JNIEnv::CallStaticVoidMethod(jclass,jmethodID,jbyteArray,jboolean){assert(attached);}
void JNIEnv::DeleteLocalRef(void*){assert(attached);}
bool JNIEnv::ExceptionCheck(){return pendingException;}void JNIEnv::ExceptionDescribe(){}void JNIEnv::ExceptionClear(){pendingException=false;}
JavaVM* jvm=nullptr;
namespace airplay_bridge { BRIDGE }
namespace sunshine_callbacks { CHAIN }
using AMediaFormat=std::map<std::string,int>;
#define AMEDIAFORMAT_KEY_WIDTH "width"
#define AMEDIAFORMAT_KEY_HEIGHT "height"
#define AMEDIAFORMAT_KEY_BIT_RATE "bitrate"
#define AMEDIAFORMAT_KEY_FRAME_RATE "fps"
#define AMEDIAFORMAT_KEY_I_FRAME_INTERVAL "iframe"
#define AMEDIAFORMAT_KEY_COLOR_FORMAT "color"
void AMediaFormat_setInt32(AMediaFormat* f,const char* key,int value){(*f)[key]=value;}
void AMediaFormat_delete(AMediaFormat*){}
bool error=false;void showEncoderError(const char*){error=true;}
struct Config {int width,height,framerate,bitrate;};
void configure(const Config& config,AMediaFormat* format){
CONFIGURE
}
int main(){
 JavaVM vm;jvm=&vm;std::vector<std::thread> threads;
 for(int i=0;i<64;i++)threads.emplace_back([&]{MoonCastJniScope owner(&vm);assert(owner.env);{MoonCastJniScope nested(&vm);assert(nested.env==owner.env);}assert(attached);uint8_t frame[4]={0,0,1,5};sunshine_callbacks::callJavaOnVideoFrame(frame,4,true);assert(attached);failedAllocation=true;sunshine_callbacks::callJavaOnVideoFrame(frame,4,true);assert(attached&&!pendingException);});
 for(auto& t:threads)t.join();assert(attaches==64&&detaches==64);
 attached=true;{MoonCastJniScope borrowed(&vm);assert(borrowed.env);}assert(attached&&detaches==64);attached=false;
 fail=true;{MoonCastJniScope failed(&vm);assert(!failed.env);}assert(attaches==64&&detaches==64);fail=false;
 {MoonCastJniScope missing(nullptr);assert(!missing.env);}
 int checks=0;
 for(Config config:std::vector<Config>{{1280,720,24,5000},{1920,1080,30,10000},{3840,2160,60,40000},{1920,1080,30,8000},{1280,720,120,12000}}){
  AMediaFormat format;configure(config,&format);assert(!error);assert(format["width"]==config.width&&format["height"]==config.height&&format["fps"]==config.framerate&&format["bitrate"]==config.bitrate*1000);checks++;
 }
 AMediaFormat invalid;configure({1920,1080,0,5000},&invalid);assert(error&&invalid.empty());
 printf("PASS actual encoder configuration: %d transitions preserve viewport/FPS/bitrate; invalid request rejected. JNI scope:64 owned attaches/detaches, nested/Java-owned/failed scopes safe. Host mocks only.\n",checks);
}
'''.replace('CONFIGURE',configure).replace('BRIDGE',symbols+bridge).replace('CHAIN',chain)
with tempfile.TemporaryDirectory(prefix='mooncast-sunshine-host-') as directory:
 d=Path(directory);(d/'jni.h').write_text(header);(d/'test.cpp').write_text(source)
 subprocess.run(['c++','-std=c++20','-pthread','-I',str(d),'-I',str(r/'native/sunshine'),str(d/'test.cpp'),'-o',str(d/'tests')],check=True)
 subprocess.run([str(d/'tests')],check=True)
