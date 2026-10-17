#!/usr/bin/env python3
"""Actual archive helper + proposed patch under a mock JNI pthread harness.
Tests ownership/destructor mechanics, NOT Android ART or device crash reproduction.
"""
from pathlib import Path
import hashlib,json,tarfile,tempfile,subprocess
root=Path(__file__).resolve().parents[1];native=root/'native/airplay'
manifest=json.loads((native/'prebuilt-manifest.json').read_text())
archive=native/'upstream-source.tar.gz'
assert hashlib.sha256(archive.read_bytes()).hexdigest()==manifest['sources'][archive.name]
with tarfile.open(archive) as source:
 original=source.extractfile('android-airplay-server/app/src/main/cpp/android_raop_callbacks.c').read().decode()
harness=r'''
#include <pthread.h>
#include <assert.h>
#include <stdio.h>
#include <stdatomic.h>
#define JNI_OK 0
#define JNI_EDETACHED -2
#define JNI_VERSION_1_6 0x10006
struct Env; typedef const struct Env *JNIEnv;
struct VM; typedef const struct VM *JavaVM;
struct Env { int (*ExceptionCheck)(JNIEnv*); void (*ExceptionDescribe)(JNIEnv*); void (*ExceptionClear)(JNIEnv*); };
struct VM { int (*GetEnv)(JavaVM*,void**,int); int (*AttachCurrentThread)(JavaVM*,JNIEnv**,void*); int (*DetachCurrentThread)(JavaVM*); };
typedef struct { JavaVM *jvm; } android_callback_ctx_t;
static _Thread_local int attached, java_owned;
static atomic_int attaches,detaches;
static int exception(JNIEnv* e){return 0;} static void no_op(JNIEnv* e){}
static struct Env env_table={exception,no_op,no_op};static JNIEnv env=&env_table;
static int get(JavaVM* j,void** e,int v){*e=attached||java_owned?&env:NULL;return attached||java_owned?JNI_OK:JNI_EDETACHED;}
static int attach(JavaVM* j,JNIEnv** e,void* a){attached=1;atomic_fetch_add(&attaches,1);*e=&env;return JNI_OK;}
static int detach(JavaVM* j){assert(attached&&!java_owned);attached=0;atomic_fetch_add(&detaches,1);return JNI_OK;}
static struct VM vm_table={get,attach,detach};static JavaVM vm=&vm_table;
'''
main=r'''
static void* callback(void* java){java_owned=(long)java;android_callback_ctx_t ctx={&vm};assert(_get_env(&ctx));assert(_get_env(&ctx));return NULL;}
int main(){for(int i=0;i<64;i++){pthread_t t;assert(!pthread_create(&t,NULL,callback,(void*)0));pthread_join(t,NULL);}
for(int i=0;i<8;i++){pthread_t t;assert(!pthread_create(&t,NULL,callback,(void*)1));pthread_join(t,NULL);}
assert(attaches==64);assert(detaches==EXPECTED);printf("attaches=%d detaches=%d Java-owned=8\n",attaches,detaches);}
'''
with tempfile.TemporaryDirectory(prefix='mooncast-jni-') as tmp:
 work=Path(tmp);(work/'android_raop_callbacks.c').write_text(original)
 subprocess.run(['patch','--batch','--fuzz=0','-p1','-i',str(native/'bridge-patches/0001-detach-owned-callback-threads.patch')],cwd=work,check=True)
 patched=(work/'android_raop_callbacks.c').read_text()
 for label,text,expected in [('baseline',original,0),('candidate',patched,64)]:
  start=text.index('static pthread_key_t') if label=='candidate' else text.index('static JNIEnv *_get_env')
  helper=text[start:text.index('\nvoid android_callbacks_init',start)]
  code=work/(label+'.c');code.write_text(harness+helper+main)
  exe=work/label
  subprocess.run(['cc','-std=c11','-pthread',f'-DEXPECTED={expected}',str(code),'-o',str(exe)],check=True)
  subprocess.run([str(exe)],check=True)
 print('PASS: baseline helper leaks 64 owned attachments; candidate detaches 64 on pthread exit and leaves 8 Java-owned threads attached. Mock JNI host test only.')
