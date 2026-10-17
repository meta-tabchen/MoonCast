#pragma once
#include <jni.h>

// Only the scope which attached a native thread may detach it. Nested helpers
// borrow Java/caller-owned environments and never invalidate the caller's JNIEnv.
class MoonCastJniScope {
    JavaVM* vm_;
    bool attached_=false;
public:
    JNIEnv* env=nullptr;
    explicit MoonCastJniScope(JavaVM* vm):vm_(vm){
        if(!vm_)return;
        jint status=vm_->GetEnv(reinterpret_cast<void**>(&env),JNI_VERSION_1_6);
        if(status==JNI_EDETACHED){
            env=nullptr;
            if(vm_->AttachCurrentThread(&env,nullptr)==JNI_OK)attached_=true;
        }else if(status!=JNI_OK)env=nullptr;
    }
    MoonCastJniScope(const MoonCastJniScope&)=delete;
    MoonCastJniScope& operator=(const MoonCastJniScope&)=delete;
    ~MoonCastJniScope(){if(attached_)vm_->DetachCurrentThread();}
};
