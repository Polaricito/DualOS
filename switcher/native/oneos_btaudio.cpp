// oneos_btaudio: Bluetooth audio for a guest on a Galaxy that serves only
// Samsung's Bluetooth audio HAL.
//
// The SM8750 and older Galaxies (S25, S24, S23, Z Fold7, Z Flip7/5, A5x) register
// only vendor.samsung.hardware.bluetooth.audio.ISehBluetoothAudioProviderFactory,
// not Android's android.hardware.bluetooth.audio.IBluetoothAudioProviderFactory,
// so a guest's Bluetooth stack finds no audio HAL: devices connect and the sound
// stays on the phone speaker (0.4.32 reports: S24 Ultra, S25 Ultra, A57).
//
// Samsung's interface is Android's version 2 under other names (read from One
// UI's libbluetooth_jni.so, which has the Seh client stubs): the same
// transaction codes (factory 1-2, provider 1-6, port 1-7), the same SessionType,
// PcmConfiguration, PresentationPosition and LatencyMode, and the same
// android.hardware.audio.common metadata. Two differences: the port has an 8th
// method, sehGetAudioConfiguration, and the codec unions (CodecConfiguration.
// CodecSpecific, CodecCapabilities.Capabilities) have Samsung's SSC at tag 6,
// so Android's vendor (6) and opus (7) are Samsung's 7 and 8.
//
// This process registers Android's factory and forwards every call to
// Samsung's with the parcel copied as it is, under the other interface name:
//   factory 1 getProviderCapabilities: SSC entries dropped, tags 7/8 -> 6/7
//   factory 2 openProvider: the provider wrapped
//   provider 2 startSession: the guest's port wrapped (as Samsung's port, VINTF
//     stability so the vendor HAL may call it), codec tags 6/7 -> 7/8
//   provider 5 updateAudioConfiguration: codec tags 6/7 -> 7/8
//   port 8 sehGetAudioConfiguration: the configuration of the last start or update
// It answers version 2 for Android's interfaces, so the guest's stack never asks
// for the version 4 methods Samsung does not have.
//
// The guest's stack asks servicemanager AServiceManager_isDeclared() before it
// looks for the factory, so the session also declares the instance in the
// device manifest (oneos_switch.sh, ONEOS_GUEST_BT_AUDIO_BRIDGE).
//
// Usage: oneos_btaudio            serve (until killed; AIDL or HIDL @2.0)
//        oneos_btaudio --hidl     serve using Samsung HIDL @2.0 backend
//        oneos_btaudio --hidl-probe  read-only probe of Samsung HIDL @2.0 BT audio HAL
//        oneos_btaudio --declared NAME   print 1 if servicemanager has NAME declared
//        oneos_btaudio --check     print what this phone has
//        oneos_btaudio --loop      a test bridge from Android's factory to itself (.../oneostest)
//        oneos_btaudio --client NAME   the first calls a guest's stack makes, against NAME
//        oneos_btaudio --suppress-host-svcs STAGE [TAG]   suppress host services without fork/execve
//        oneos_btaudio --suppress-host-svcs-check STAGE   dry-run check of init.svc.* & property wait
#include <android/binder_ibinder.h>
#include <android/binder_parcel.h>
#include <android/binder_status.h>
#include <android/log.h>
#include <dlfcn.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <sys/system_properties.h>
#include <time.h>
#include <unistd.h>

#include <memory>
#include <mutex>
#include <vector>

#include "oneos_btaudio_hidl.h"

namespace {

const char* kTag = "OneOSBtAudio";
const char* kAospFactoryName = "android.hardware.bluetooth.audio.IBluetoothAudioProviderFactory/default";
const char* kSehFactoryName = "vendor.samsung.hardware.bluetooth.audio.ISehBluetoothAudioProviderFactory/default";

const char* kAospFactory = "android.hardware.bluetooth.audio.IBluetoothAudioProviderFactory";
const char* kAospProvider = "android.hardware.bluetooth.audio.IBluetoothAudioProvider";
const char* kAospPort = "android.hardware.bluetooth.audio.IBluetoothAudioPort";
const char* kSehFactory = "vendor.samsung.hardware.bluetooth.audio.ISehBluetoothAudioProviderFactory";
const char* kSehProvider = "vendor.samsung.hardware.bluetooth.audio.ISehBluetoothAudioProvider";
const char* kSehPort = "vendor.samsung.hardware.bluetooth.audio.ISehBluetoothAudioPort";

// --loop (a test on a phone that has Android's HAL): the "Samsung" side is
// Android's own factory, codec tags are left as they are, and the bridge
// registers as .../oneostest, which nothing but --client uses.
bool gLoop = false;
const char* kLoopName = "android.hardware.bluetooth.audio.IBluetoothAudioProviderFactory/oneostest";

// android.hardware.bluetooth.audio version 2 (aidl_api/.../2/.hash): Samsung's
// interface matches it, so the guest's stack is told this one.
const int32_t kAospVersion = 2;
const char* kAospHash = "b82d5b9f717bbf63bef774ee8f72572065ca61ce";
// What One UI's BnSehBluetoothAudioPort answers.
const int32_t kSehPortVersion = 2;

const transaction_code_t kGetInterfaceVersion = FIRST_CALL_TRANSACTION + 16777214;
const transaction_code_t kGetInterfaceHash = FIRST_CALL_TRANSACTION + 16777213;

void logf(const char* fmt, ...) {
    va_list a, b;
    va_start(a, fmt);
    va_copy(b, a);
    __android_log_vprint(ANDROID_LOG_INFO, kTag, fmt, a);
    vfprintf(stderr, fmt, b);
    fputc('\n', stderr);
    va_end(b);
    va_end(a);
}

// Not in the NDK headers (LL-NDK / platform), but exported by every
// libbinder_ndk.so since Android 12.
struct Platform {
    AIBinder* (*checkService)(const char*) = nullptr;
    binder_exception_t (*addService)(AIBinder*, const char*) = nullptr;
    bool (*isDeclared)(const char*) = nullptr;
    void (*markVintf)(AIBinder*) = nullptr;
    bool (*setThreads)(uint32_t) = nullptr;
    void (*startPool)() = nullptr;
    void (*joinPool)() = nullptr;
    bool load() {
        void* h = dlopen("libbinder_ndk.so", RTLD_NOW);
        if (!h) return false;
        checkService = reinterpret_cast<decltype(checkService)>(dlsym(h, "AServiceManager_checkService"));
        addService = reinterpret_cast<decltype(addService)>(dlsym(h, "AServiceManager_addService"));
        isDeclared = reinterpret_cast<decltype(isDeclared)>(dlsym(h, "AServiceManager_isDeclared"));
        markVintf = reinterpret_cast<decltype(markVintf)>(dlsym(h, "AIBinder_markVintfStability"));
        setThreads = reinterpret_cast<decltype(setThreads)>(dlsym(h, "ABinderProcess_setThreadPoolMaxThreadCount"));
        startPool = reinterpret_cast<decltype(startPool)>(dlsym(h, "ABinderProcess_startThreadPool"));
        joinPool = reinterpret_cast<decltype(joinPool)>(dlsym(h, "ABinderProcess_joinThreadPool"));
        return checkService && addService && isDeclared && markVintf && setThreads && startPool && joinPool;
    }
} P;

AIBinder_Class* gAospFactoryCls;
AIBinder_Class* gAospProviderCls;
AIBinder_Class* gAospPortCls;  // client side: the guest's port
AIBinder_Class* gSehFactoryCls;  // client side
AIBinder_Class* gSehProviderCls;  // client side
AIBinder_Class* gSehPortCls;
AIBinder_Class* gHidlFactoryCls;
AIBinder_Class* gHidlProviderCls;

AIBinder* gSehFactory;
bool gForceHidl = false;

// The configuration of a session, as Samsung's parcel (for sehGetAudioConfiguration).
struct Session {
    std::mutex m;
    AParcel* config = nullptr;  // owned
    ~Session() {
        if (config) AParcel_delete(config);
    }
};

struct ProviderData {
    AIBinder* seh;  // strong ref held
    std::shared_ptr<Session> session = std::make_shared<Session>();
};

struct PortData {
    AIBinder* aosp;  // the guest's IBluetoothAudioPort, strong ref held
    std::shared_ptr<Session> session;
};

// Android's codec tags -> Samsung's (CodecSpecific / Capabilities unions).
int32_t codecTagToSeh(int32_t t) { return !gLoop && t >= 6 ? t + 1 : t; }

binder_status_t writeOkStatus(AParcel* out) {
    AStatus* ok = AStatus_newOk();
    binder_status_t st = AParcel_writeStatusHeader(out, ok);
    AStatus_delete(ok);
    return st;
}

binder_status_t writeExceptionStatus(AParcel* out, binder_exception_t ex) {
    AStatus* err = AStatus_fromExceptionCode(ex);
    binder_status_t st = AParcel_writeStatusHeader(out, err);
    AStatus_delete(err);
    return st;
}

binder_status_t writeServiceErrorStatus(AParcel* out, int32_t errCode) {
    AStatus* err = AStatus_fromServiceSpecificError(errCode);
    binder_status_t st = AParcel_writeStatusHeader(out, err);
    AStatus_delete(err);
    return st;
}

binder_status_t copyRest(const AParcel* in, AParcel* to, int32_t from) {
    int32_t end = AParcel_getDataSize(in);
    if (end <= from) return STATUS_OK;
    return AParcel_appendFrom(in, to, from, end - from);
}

// Sends `code` to `target` with the argument bytes of `in` from `from` on and
// copies the whole reply (status header included) into `out`.
binder_status_t forward(AIBinder* target, transaction_code_t code, const AParcel* in, int32_t from, AParcel* out) {
    AParcel* req = nullptr;
    binder_status_t st = AIBinder_prepareTransaction(target, &req);
    if (st != STATUS_OK) return st;
    st = copyRest(in, req, from);
    if (st != STATUS_OK) {
        AParcel_delete(req);
        return st;
    }
    AParcel* rep = nullptr;
    st = AIBinder_transact(target, code, &req, &rep, 0);
    if (st != STATUS_OK) return st;
    st = AParcel_appendFrom(rep, out, 0, AParcel_getDataSize(rep));
    AParcel_delete(rep);
    return st;
}

// Reads the header of a parcelable or union written as a value: the non-null
// marker, and for a union its tag, the member's marker and the member's size
// (a parcelable member starts with its size, which counts itself). Fills the
// extent of the whole value. One shape for an argument, a vector element and a
// union member: [1] [tag] [1] [size] fields...
struct Value {
    int32_t start = 0, end = 0;
    int32_t tag = -1;
    int32_t member = -1;  // where the member's size is, or -1 (null)
};
binder_status_t readUnionValue(const AParcel* p, Value* v) {
    v->start = AParcel_getDataPosition(p);
    int32_t marker = 0, inner = 0, size = 0;
    if (AParcel_readInt32(p, &marker) != STATUS_OK) return STATUS_BAD_VALUE;
    if (marker == 0) {  // null
        v->end = AParcel_getDataPosition(p);
        return STATUS_OK;
    }
    if (AParcel_readInt32(p, &v->tag) != STATUS_OK || AParcel_readInt32(p, &inner) != STATUS_OK) return STATUS_BAD_VALUE;
    if (inner == 0) {  // a null member
        v->end = AParcel_getDataPosition(p);
        return STATUS_OK;
    }
    v->member = AParcel_getDataPosition(p);
    if (AParcel_readInt32(p, &size) != STATUS_OK || size < 4) return STATUS_BAD_VALUE;
    v->end = v->member + size;
    if (v->end > AParcel_getDataSize(p)) return STATUS_BAD_VALUE;
    return AParcel_setDataPosition(p, v->end);
}

// Copies an AudioConfiguration from `in` (at its current position, which it
// leaves after the value) to `to`, Android's codec tags made Samsung's.
binder_status_t copyAudioConfiguration(const AParcel* in, AParcel* to) {
    Value v;
    binder_status_t st = readUnionValue(in, &v);
    if (st != STATUS_OK) return st;
    int32_t start = v.start, end = v.end, ps = v.member;
    // a2dpConfig: CodecConfiguration {size, codecType, encodedAudioBitrate,
    // peerMtu, isScmstEnabled, CodecSpecific config: [1] [tag] ...}
    if (v.tag == 1 && ps >= 0 && end - ps >= 28) {
        int32_t tagPos = ps + 24;
        AParcel_setDataPosition(in, tagPos);
        int32_t inner = 0;
        AParcel_readInt32(in, &inner);
        int32_t mapped = codecTagToSeh(inner);
        st = AParcel_appendFrom(in, to, start, tagPos - start);
        if (st == STATUS_OK) st = AParcel_writeInt32(to, mapped);
        if (st == STATUS_OK) st = AParcel_appendFrom(in, to, tagPos + 4, end - tagPos - 4);
    } else {
        st = AParcel_appendFrom(in, to, start, end - start);
    }
    AParcel_setDataPosition(in, end);
    return st;
}

void saveConfig(const std::shared_ptr<Session>& s, const AParcel* in, int32_t from) {
    AParcel* p = AParcel_create();
    AParcel_setDataPosition(in, from);
    if (copyAudioConfiguration(in, p) != STATUS_OK) {
        AParcel_delete(p);
        return;
    }
    std::lock_guard<std::mutex> l(s->m);
    if (s->config) AParcel_delete(s->config);
    s->config = p;
}

void* onCreatePass(void* args) { return args; }
void onDestroyNothing(void*) {}
binder_status_t onTransactNone(AIBinder*, transaction_code_t, const AParcel*, AParcel*) {
    return STATUS_UNKNOWN_TRANSACTION;
}

binder_status_t answerMeta(transaction_code_t code, AParcel* out, int32_t version, const char* hash) {
    binder_status_t st = writeOkStatus(out);
    if (st != STATUS_OK) return st;
    if (code == kGetInterfaceVersion) return AParcel_writeInt32(out, version);
    return AParcel_writeString(out, hash, strlen(hash));
}

// ---- Samsung's port, served to the vendor HAL for the guest's port ----

void portDestroy(void* d) {
    auto* pd = static_cast<PortData*>(d);
    AIBinder_decStrong(pd->aosp);
    delete pd;
}

binder_status_t portTransact(AIBinder* self, transaction_code_t code, const AParcel* in, AParcel* out) {
    auto* pd = static_cast<PortData*>(AIBinder_getUserData(self));
    if (code == kGetInterfaceVersion || code == kGetInterfaceHash)
        return answerMeta(code, out, kSehPortVersion, "notfrozen");
    if (code == FIRST_CALL_TRANSACTION + 7) {  // sehGetAudioConfiguration
        std::lock_guard<std::mutex> l(pd->session->m);
        if (!pd->session->config) return STATUS_FAILED_TRANSACTION;
        binder_status_t st = writeOkStatus(out);
        if (st != STATUS_OK) return st;
        return AParcel_appendFrom(pd->session->config, out, 0, AParcel_getDataSize(pd->session->config));
    }
    if (code < FIRST_CALL_TRANSACTION || code > FIRST_CALL_TRANSACTION + 6) return STATUS_UNKNOWN_TRANSACTION;
    return forward(pd->aosp, code, in, AParcel_getDataPosition(in), out);
}

// ---- Android's provider, served to the guest ----

void providerDestroy(void* d) {
    auto* pd = static_cast<ProviderData*>(d);
    AIBinder_decStrong(pd->seh);
    delete pd;
}

binder_status_t providerTransact(AIBinder* self, transaction_code_t code, const AParcel* in, AParcel* out) {
    auto* pd = static_cast<ProviderData*>(AIBinder_getUserData(self));
    if (code == kGetInterfaceVersion || code == kGetInterfaceHash)
        return answerMeta(code, out, kAospVersion, kAospHash);
    switch (code - FIRST_CALL_TRANSACTION) {
        case 0:  // endSession
        case 2:  // streamStarted
        case 3:  // streamSuspended
        case 5:  // setLowLatencyModeAllowed
            return forward(pd->seh, code, in, AParcel_getDataPosition(in), out);
        case 1: {  // startSession(port, AudioConfiguration, LatencyMode[])
            AIBinder* guestPort = nullptr;
            binder_status_t st = AParcel_readStrongBinder(in, &guestPort);
            if (st != STATUS_OK || !guestPort) return st != STATUS_OK ? st : STATUS_UNEXPECTED_NULL;
            if (!AIBinder_associateClass(guestPort, gAospPortCls)) {
                AIBinder_decStrong(guestPort);
                return STATUS_BAD_TYPE;
            }
            auto* portData = new PortData{guestPort, pd->session};  // takes the ref
            AIBinder* sehPort = AIBinder_new(gSehPortCls, portData);
            P.markVintf(sehPort);
            int32_t cfgPos = AParcel_getDataPosition(in);
            saveConfig(pd->session, in, cfgPos);
            AParcel_setDataPosition(in, cfgPos);

            AParcel* req = nullptr;
            st = AIBinder_prepareTransaction(pd->seh, &req);
            if (st == STATUS_OK) st = AParcel_writeStrongBinder(req, sehPort);
            AIBinder_decStrong(sehPort);  // the parcel holds it now
            if (st == STATUS_OK) st = copyAudioConfiguration(in, req);
            if (st == STATUS_OK) st = copyRest(in, req, AParcel_getDataPosition(in));
            if (st != STATUS_OK) {
                if (req) AParcel_delete(req);
                return st;
            }
            AParcel* rep = nullptr;
            st = AIBinder_transact(pd->seh, code, &req, &rep, 0);
            if (st != STATUS_OK) {
                logf("startSession: Samsung's provider failed: %d", st);
                return st;
            }
            st = AParcel_appendFrom(rep, out, 0, AParcel_getDataSize(rep));
            AParcel_delete(rep);
            logf("startSession forwarded");
            return st;
        }
        case 4: {  // updateAudioConfiguration(AudioConfiguration)
            int32_t cfgPos = AParcel_getDataPosition(in);
            saveConfig(pd->session, in, cfgPos);
            AParcel_setDataPosition(in, cfgPos);
            AParcel* req = nullptr;
            binder_status_t st = AIBinder_prepareTransaction(pd->seh, &req);
            if (st == STATUS_OK) st = copyAudioConfiguration(in, req);
            if (st != STATUS_OK) {
                if (req) AParcel_delete(req);
                return st;
            }
            AParcel* rep = nullptr;
            st = AIBinder_transact(pd->seh, code, &req, &rep, 0);
            if (st != STATUS_OK) return st;
            st = AParcel_appendFrom(rep, out, 0, AParcel_getDataSize(rep));
            AParcel_delete(rep);
            return st;
        }
        default:
            return STATUS_UNKNOWN_TRANSACTION;
    }
}

// ---- Android's factory, served to the guest ----

// getProviderCapabilities reply: Samsung's AudioCapabilities[] made Android's:
// SSC codec entries dropped, codec tags 7/8 -> 6/7, unknown union members dropped.
binder_status_t translateCapabilities(const AParcel* rep, AParcel* out) {
    AStatus* status = nullptr;
    binder_status_t st = AParcel_readStatusHeader(rep, &status);
    if (st != STATUS_OK) return st;
    bool ok = AStatus_isOk(status);
    AStatus_delete(status);
    if (!ok) return AParcel_appendFrom(rep, out, 0, AParcel_getDataSize(rep));
    int32_t n = 0;
    if (AParcel_readInt32(rep, &n) != STATUS_OK) return STATUS_BAD_VALUE;
    struct Elem { int32_t start, end, tagPos, tag; };
    std::vector<Elem> keep;
    for (int32_t i = 0; i < n; i++) {
        Value v;
        if (readUnionValue(rep, &v) != STATUS_OK) return STATUS_BAD_VALUE;
        if (v.tag > 2) continue;  // not one of Android's
        int32_t end = v.end, ps = v.member;
        Elem e{v.start, end, -1, 0};
        // a2dpCapabilities: CodecCapabilities {size, codecType, Capabilities: [1] [tag] ...}
        if (v.tag == 1 && ps >= 0 && end - ps >= 16) {
            e.tagPos = ps + 12;
            AParcel_setDataPosition(rep, e.tagPos);
            AParcel_readInt32(rep, &e.tag);
            AParcel_setDataPosition(rep, end);
            if (!gLoop) {
                if (e.tag == 6) continue;  // Samsung's SSC
                if (e.tag > 6) e.tag -= 1;
            }
        }
        keep.push_back(e);
    }
    st = writeOkStatus(out);
    if (st == STATUS_OK) st = AParcel_writeInt32(out, static_cast<int32_t>(keep.size()));
    for (const Elem& e : keep) {
        if (st != STATUS_OK) break;
        if (e.tagPos < 0) {
            st = AParcel_appendFrom(rep, out, e.start, e.end - e.start);
            continue;
        }
        st = AParcel_appendFrom(rep, out, e.start, e.tagPos - e.start);
        if (st == STATUS_OK) st = AParcel_writeInt32(out, e.tag);
        if (st == STATUS_OK) st = AParcel_appendFrom(rep, out, e.tagPos + 4, e.end - e.tagPos - 4);
    }
    if (n != static_cast<int32_t>(keep.size()))
        logf("capabilities: %d of Samsung's %d entries passed on", static_cast<int>(keep.size()), n);
    return st;
}

// A strong reference, released at the end of the scope.
struct Ref {
    AIBinder* b;
    ~Ref() {
        if (b) AIBinder_decStrong(b);
    }
};

// Samsung's factory, fetched again after its process (the audio HAL) died.
// The caller gets its own reference.
std::mutex gFactoryLock;
AIBinder* sehFactory() {
    std::lock_guard<std::mutex> l(gFactoryLock);
    if (!AIBinder_isAlive(gSehFactory)) {
        AIBinder* f = P.checkService(kSehFactoryName);
        if (f && AIBinder_associateClass(f, gSehFactoryCls)) {
            AIBinder_decStrong(gSehFactory);
            gSehFactory = f;
            logf("Samsung's factory fetched again (its process restarted)");
        } else if (f) {
            AIBinder_decStrong(f);
        }
    }
    AIBinder_incStrong(gSehFactory);  // the dead one if none came back: the call fails as it would have
    return gSehFactory;
}

binder_status_t factoryTransact(AIBinder*, transaction_code_t code, const AParcel* in, AParcel* out) {
    if (code == kGetInterfaceVersion || code == kGetInterfaceHash)
        return answerMeta(code, out, kAospVersion, kAospHash);
    Ref sehRef{sehFactory()};
    AIBinder* seh = sehRef.b;
    if (code == FIRST_CALL_TRANSACTION) {  // getProviderCapabilities(SessionType)
        AParcel* req = nullptr;
        binder_status_t st = AIBinder_prepareTransaction(seh, &req);
        if (st != STATUS_OK) return st;
        st = copyRest(in, req, AParcel_getDataPosition(in));
        if (st != STATUS_OK) {
            AParcel_delete(req);
            return st;
        }
        AParcel* rep = nullptr;
        st = AIBinder_transact(seh, code, &req, &rep, 0);
        if (st != STATUS_OK) return st;
        st = translateCapabilities(rep, out);
        AParcel_delete(rep);
        return st;
    }
    if (code == FIRST_CALL_TRANSACTION + 1) {  // openProvider(SessionType)
        int32_t sessionType = -1;
        int32_t pos = AParcel_getDataPosition(in);
        AParcel_readInt32(in, &sessionType);
        AParcel* req = nullptr;
        binder_status_t st = AIBinder_prepareTransaction(seh, &req);
        if (st != STATUS_OK) return st;
        st = copyRest(in, req, pos);
        if (st != STATUS_OK) {
            AParcel_delete(req);
            return st;
        }
        AParcel* rep = nullptr;
        st = AIBinder_transact(seh, code, &req, &rep, 0);
        if (st != STATUS_OK) return st;
        AStatus* status = nullptr;
        st = AParcel_readStatusHeader(rep, &status);
        if (st != STATUS_OK) {
            AParcel_delete(rep);
            return st;
        }
        if (!AStatus_isOk(status)) {
            const char* why = AStatus_getDescription(status);
            logf("openProvider(session type %d): Samsung's factory refused: %s", sessionType, why);
            AStatus_deleteDescription(why);
            AStatus_delete(status);
            st = AParcel_appendFrom(rep, out, 0, AParcel_getDataSize(rep));
            AParcel_delete(rep);
            return st;
        }
        AStatus_delete(status);
        AIBinder* prov = nullptr;
        st = AParcel_readStrongBinder(rep, &prov);
        AParcel_delete(rep);
        if (st != STATUS_OK) return st;
        AIBinder* mine = nullptr;
        if (prov) {
            if (!AIBinder_associateClass(prov, gSehProviderCls)) {
                AIBinder_decStrong(prov);
                return STATUS_BAD_TYPE;
            }
            mine = AIBinder_new(gAospProviderCls, new ProviderData{prov});
        }
        logf("openProvider(session type %d): %s", sessionType, prov ? "Samsung's provider" : "none");
        st = writeOkStatus(out);
        if (st == STATUS_OK) st = AParcel_writeStrongBinder(out, mine);
        if (mine) AIBinder_decStrong(mine);
        return st;
    }
    return STATUS_UNKNOWN_TRANSACTION;
}

// ---- Samsung HIDL @2.0 -> AIDL v2 backend (for Galaxy A53 and older HIDL HALs) ----

struct HidlProviderData {
    uint32_t handle = 0;
    int32_t sessionType = 0;
    bool isV21 = false;
    std::mutex m;
    std::shared_ptr<hidl_bridge::HwBinderBridge::PortEntry> activePort;
};

void hidlProviderDestroy(void* d) {
    auto* pd = static_cast<HidlProviderData*>(d);
    std::shared_ptr<hidl_bridge::HwBinderBridge::PortEntry> port;
    {
        std::lock_guard<std::mutex> l(pd->m);
        port = std::move(pd->activePort);
    }
    if (port) hidl_bridge::HwBinderBridge::instance().deactivatePort(port);
    if (pd->handle) hidl_bridge::HwBinderBridge::instance().releaseHandle(pd->handle);
    delete pd;
}

binder_status_t backfillSize(AParcel* p, int32_t sizePos) {
    int32_t end = AParcel_getDataPosition(p);
    binder_status_t st = AParcel_setDataPosition(p, sizePos);
    if (st == STATUS_OK) st = AParcel_writeInt32(p, end - sizePos);
    if (st == STATUS_OK) st = AParcel_setDataPosition(p, end);
    return st;
}

bool readAidlPcmConfig(const AParcel* in, hidl_bridge::HidlPcmParameters* outPcm, int32_t* outRateHz = nullptr,
                       int8_t* outCh = nullptr, int8_t* outBps = nullptr) {
    int32_t unionNonNull = 0, tag = -1, memNonNull = 0, memSize = 0;
    int32_t sampleRateHz = 0, dataIntervalUs = 0;
    int8_t channelMode = 0, bitsPerSample = 0;
    if (AParcel_readInt32(in, &unionNonNull) != STATUS_OK || unionNonNull == 0 ||
        AParcel_readInt32(in, &tag) != STATUS_OK || tag != 0 ||
        AParcel_readInt32(in, &memNonNull) != STATUS_OK || memNonNull == 0 ||
        AParcel_readInt32(in, &memSize) != STATUS_OK || memSize < 16 ||
        AParcel_readInt32(in, &sampleRateHz) != STATUS_OK ||
        AParcel_readByte(in, &channelMode) != STATUS_OK ||
        AParcel_readByte(in, &bitsPerSample) != STATUS_OK ||
        AParcel_readInt32(in, &dataIntervalUs) != STATUS_OK) {
        return false;
    }
    uint32_t rateMask = hidl_bridge::HwBinderBridge::sampleRateToMask(sampleRateHz);
    uint8_t bpsMask = hidl_bridge::HwBinderBridge::aidlBitsToMask(bitsPerSample);
    if (rateMask == 0 || (channelMode != 1 && channelMode != 2) || bpsMask == 0) {
        logf("HIDL PCM cfg unsupported: rate=%d ch=%d bps=%d",
             sampleRateHz, static_cast<int>(channelMode), static_cast<int>(bitsPerSample));
        return false;
    }
    *outPcm = {
        rateMask,
        static_cast<uint8_t>(channelMode),
        bpsMask,
        0,
        dataIntervalUs > 0 ? static_cast<uint32_t>(dataIntervalUs) : 0,
    };
    if (outRateHz) *outRateHz = sampleRateHz;
    if (outCh) *outCh = channelMode;
    if (outBps) *outBps = bitsPerSample;
    return true;
}

binder_status_t hidlProviderTransact(AIBinder* self, transaction_code_t code, const AParcel* in, AParcel* out) {
    auto* pd = static_cast<HidlProviderData*>(AIBinder_getUserData(self));
    if (code == kGetInterfaceVersion || code == kGetInterfaceHash)
        return answerMeta(code, out, kAospVersion, kAospHash);

    auto& br = hidl_bridge::HwBinderBridge::instance();
    switch (code - FIRST_CALL_TRANSACTION) {
        case 0: {  // endSession()
            logf("HIDL endSession (sessionType=%d)", pd->sessionType);
            std::shared_ptr<hidl_bridge::HwBinderBridge::PortEntry> port;
            {
                std::lock_guard<std::mutex> l(pd->m);
                port = std::move(pd->activePort);
            }
            if (port) br.deactivatePort(port);
            hidl_bridge::HidlParcel req;
            req.writeInterfaceToken(hidl_bridge::kSehHidlProviderFqName);
            binder_transaction_data rep{};
            if (br.transact(pd->handle, hidl_bridge::kProviderSehEndSession, req, &rep, true)) {
                br.freeBuffer(rep.data.ptr.buffer);
            }
            return writeOkStatus(out);
        }
        case 1: {  // startSession(port, AudioConfiguration, LatencyMode[])
            AIBinder* guestPort = nullptr;
            binder_status_t st = AParcel_readStrongBinder(in, &guestPort);
            if (st != STATUS_OK || !guestPort) return st != STATUS_OK ? st : STATUS_UNEXPECTED_NULL;
            if (!AIBinder_associateClass(guestPort, gAospPortCls)) {
                AIBinder_decStrong(guestPort);
                return STATUS_BAD_TYPE;
            }
            hidl_bridge::HidlPcmParameters pcm{};
            int32_t sampleRateHz = 0;
            int8_t channelMode = 0, bitsPerSample = 0;
            if (!readAidlPcmConfig(in, &pcm, &sampleRateHz, &channelMode, &bitsPerSample)) {
                AIBinder_decStrong(guestPort);
                return writeExceptionStatus(out, EX_ILLEGAL_ARGUMENT);
            }

            auto portEntry = br.registerPort(guestPort);
            AIBinder_decStrong(guestPort);  // portEntry owns the strong ref now
            {
                std::shared_ptr<hidl_bridge::HwBinderBridge::PortEntry> prev;
                {
                    std::lock_guard<std::mutex> l(pd->m);
                    prev = std::move(pd->activePort);
                    pd->activePort = portEntry;
                }
                if (prev) br.deactivatePort(prev);
            }

            hidl_bridge::HidlAudioConfiguration21 cfg21{};
            cfg21.discriminator = 0;  // pcmConfig
            cfg21.pcm = pcm;
            br.setPortAudioConfig(portEntry->cookie, cfg21);

            hidl_bridge::HidlAudioConfiguration20 cfg20{};
            cfg20.discriminator = 0;  // pcmConfig
            cfg20.pcm = pcm;

            binder_transaction_data hrep{};
            bool okTx = false;
            bool usedV21 = false;
            if (pd->isV21) {
                hidl_bridge::HidlParcel hreq21;
                hreq21.writeInterfaceToken(hidl_bridge::kSehHidlProviderFqName21);
                hreq21.writeLocalBinder(reinterpret_cast<void*>(portEntry->cookie));
                hreq21.writeBuffer(&cfg21, sizeof(cfg21));
                if (br.transact(pd->handle, hidl_bridge::kProviderSehStartSession21, hreq21, &hrep, true)) {
                    hidl_bridge::HidlReader checkR(hrep, br.mmapBase(), br.mmapSize());
                    int32_t hstCheck = -1;
                    if (checkR.readInt32(&hstCheck) && hstCheck == 0) {
                        okTx = true;
                        usedV21 = true;
                    } else {
                        br.freeBuffer(hrep.data.ptr.buffer);
                        hrep = {};
                    }
                }
            }
            if (!okTx) {
                hidl_bridge::HidlParcel hreq;
                hreq.writeInterfaceToken(hidl_bridge::kSehHidlProviderFqName);
                hreq.writeLocalBinder(reinterpret_cast<void*>(portEntry->cookie));
                hreq.writeBuffer(&cfg20, sizeof(cfg20));
                okTx = br.transact(pd->handle, hidl_bridge::kProviderSehStartSession, hreq, &hrep, true);
            }
            if (!okTx) {
                logf("HIDL startSession: binder transact failed");
                br.deactivatePort(portEntry);
                return writeServiceErrorStatus(out, -1);
            }

            hidl_bridge::HidlReader r(hrep, br.mmapBase(), br.mmapSize());
            int32_t hst = -1;
            uint8_t sehSt = 2;
            const void* mqRaw = nullptr;
            hidl_bridge::HidlMQDescriptor mq{};
            const hidl_bridge::HidlNativeHandle* nh = nullptr;
            std::vector<int> fds;
            std::vector<int32_t> ints;
            std::vector<hidl_bridge::HidlGrantorDescriptor> grantors;

            bool parsed = r.readInt32(&hst) && hst == 0 && r.readUint8(&sehSt) &&
                          r.readBuffer(sizeof(hidl_bridge::HidlMQDescriptor), &mqRaw) && mqRaw != nullptr;
            if (parsed) {
                memcpy(&mq, mqRaw, sizeof(mq));
                nh = r.findEmbeddedNativeHandle(mq.handlePtr);
                if (nh) {
                    for (int32_t i = 0; i < nh->numFds; i++) fds.push_back(nh->data[i]);
                    for (int32_t i = 0; i < nh->numInts; i++) ints.push_back(nh->data[nh->numFds + i]);
                }
                if (mq.grantors.size > 0) {
                    if (mq.grantors.size <= 32 &&
                        r.inMmap(mq.grantors.ptr, mq.grantors.size * sizeof(hidl_bridge::HidlGrantorDescriptor))) {
                        const auto* gp = reinterpret_cast<const hidl_bridge::HidlGrantorDescriptor*>(mq.grantors.ptr);
                        grantors.assign(gp, gp + mq.grantors.size);
                    } else {
                        parsed = false;
                    }
                }
            }

            auto closeFdsAndFree = [&]() {
                for (int fd : fds) {
                    if (fd >= 0) close(fd);
                }
                br.freeBuffer(hrep.data.ptr.buffer);
            };

            if (!parsed || sehSt != 0) {
                logf("HIDL startSession refused: parsed=%d hst=%d sehStatus=%u", parsed ? 1 : 0, hst, sehSt);
                closeFdsAndFree();
                br.deactivatePort(portEntry);
                if (sehSt == 1) return writeExceptionStatus(out, EX_ILLEGAL_ARGUMENT);
                return writeServiceErrorStatus(out, parsed ? static_cast<int32_t>(sehSt) : -1);
            }

            // Serialize AIDL MQDescriptor<byte, SynchronizedReadWrite>
            st = writeOkStatus(out);
            if (st == STATUS_OK) st = AParcel_writeInt32(out, 1);  // non-null MQDescriptor
            int32_t mqStart = AParcel_getDataPosition(out);
            if (st == STATUS_OK) st = AParcel_writeInt32(out, 0);  // size placeholder
            if (st == STATUS_OK) st = AParcel_writeInt32(out, static_cast<int32_t>(grantors.size()));
            for (const auto& g : grantors) {
                if (st != STATUS_OK) break;
                st = AParcel_writeInt32(out, 1);  // non-null GrantorDescriptor
                int32_t gStart = AParcel_getDataPosition(out);
                if (st == STATUS_OK) st = AParcel_writeInt32(out, 0);
                if (st == STATUS_OK) st = AParcel_writeInt32(out, static_cast<int32_t>(g.fdIndex));
                if (st == STATUS_OK) st = AParcel_writeInt32(out, static_cast<int32_t>(g.offset));
                if (st == STATUS_OK) st = AParcel_writeInt64(out, static_cast<int64_t>(g.extent));
                if (st == STATUS_OK) st = backfillSize(out, gStart);
            }
            if (st == STATUS_OK) st = AParcel_writeInt32(out, nh ? 1 : 0);
            if (st == STATUS_OK && nh) {
                int32_t hStart = AParcel_getDataPosition(out);
                st = AParcel_writeInt32(out, 0);
                if (st == STATUS_OK) st = AParcel_writeInt32(out, static_cast<int32_t>(fds.size()));
                for (int fd : fds) {
                    if (st != STATUS_OK) break;
                    st = AParcel_writeParcelFileDescriptor(out, fd);
                }
                if (st == STATUS_OK)
                    st = AParcel_writeInt32Array(out, ints.empty() ? nullptr : ints.data(),
                                                 static_cast<int32_t>(ints.size()));
                if (st == STATUS_OK) st = backfillSize(out, hStart);
            }
            if (st == STATUS_OK) st = AParcel_writeInt32(out, static_cast<int32_t>(mq.quantum));
            if (st == STATUS_OK) st = AParcel_writeInt32(out, static_cast<int32_t>(mq.flags));
            if (st == STATUS_OK) st = backfillSize(out, mqStart);

            logf("HIDL startSession@2.%d OK (sessionType=%d rate=%d ch=%d bps=%d grantors=%zu fds=%zu st=%d)",
                 usedV21 ? 1 : 0, pd->sessionType, sampleRateHz, static_cast<int>(channelMode),
                 static_cast<int>(bitsPerSample), grantors.size(), fds.size(), st);
            closeFdsAndFree();
            return st;
        }
        case 2:    // streamStarted(BluetoothAudioStatus)
        case 3: {  // streamSuspended(BluetoothAudioStatus)
            int32_t aidlStatus = 0;
            binder_status_t st = AParcel_readInt32(in, &aidlStatus);
            if (st != STATUS_OK) return st;
            uint32_t hcode = (code - FIRST_CALL_TRANSACTION == 2) ? hidl_bridge::kProviderSehStreamStarted
                                                                  : hidl_bridge::kProviderSehStreamSuspended;
            hidl_bridge::HidlParcel req;
            req.writeInterfaceToken(hidl_bridge::kSehHidlProviderFqName);
            req.writeUint8(hidl_bridge::HwBinderBridge::aidlStatusToHidl(aidlStatus));
            binder_transaction_data rep{};
            if (br.transact(pd->handle, hcode, req, &rep, true)) {
                br.freeBuffer(rep.data.ptr.buffer);
            }
            return writeOkStatus(out);
        }
        case 4: {  // updateAudioConfiguration
            hidl_bridge::HidlPcmParameters pcm{};
            if (readAidlPcmConfig(in, &pcm)) {
                hidl_bridge::HidlAudioConfiguration21 cfg21{};
                cfg21.discriminator = 0;
                cfg21.pcm = pcm;
                std::shared_ptr<hidl_bridge::HwBinderBridge::PortEntry> port;
                {
                    std::lock_guard<std::mutex> l(pd->m);
                    port = pd->activePort;
                }
                if (port) br.setPortAudioConfig(port->cookie, cfg21);
                if (pd->isV21) {
                    hidl_bridge::HidlParcel req;
                    req.writeInterfaceToken(hidl_bridge::kSehHidlProviderFqName21);
                    req.writeBuffer(&cfg21, sizeof(cfg21));
                    binder_transaction_data rep{};
                    if (br.transact(pd->handle, hidl_bridge::kProviderSehUpdateAudioConfiguration21, req, &rep, true)) {
                        br.freeBuffer(rep.data.ptr.buffer);
                    }
                }
            }
            return writeOkStatus(out);
        }
        case 5:  // setLowLatencyModeAllowed
            return writeOkStatus(out);
        default:
            return STATUS_UNKNOWN_TRANSACTION;
    }
}

binder_status_t hidlFactoryTransact(AIBinder*, transaction_code_t code, const AParcel* in, AParcel* out) {
    if (code == kGetInterfaceVersion || code == kGetInterfaceHash)
        return answerMeta(code, out, kAospVersion, kAospHash);

    auto& br = hidl_bridge::HwBinderBridge::instance();
    bool facV21 = false;
    uint32_t fac = br.getFactoryHandle(&facV21);
    if (!fac) return STATUS_FAILED_TRANSACTION;

    if (code == FIRST_CALL_TRANSACTION) {  // getProviderCapabilities(SessionType)
        int32_t sessionType = -1;
        if (AParcel_readInt32(in, &sessionType) != STATUS_OK) return STATUS_BAD_VALUE;
        if (sessionType != hidl_bridge::kSessionA2dpSw && sessionType != hidl_bridge::kSessionHearingAidSw) {
            binder_status_t st = writeOkStatus(out);
            if (st == STATUS_OK) st = AParcel_writeInt32(out, 0);
            return st;
        }
        hidl_bridge::HidlParcel req;
        req.writeInterfaceToken(hidl_bridge::kSehHidlFactoryFqName);
        req.writeUint8(static_cast<uint8_t>(sessionType));
        binder_transaction_data rep{};
        if (!br.transact(fac, hidl_bridge::kFactorySehGetCapabilities, req, &rep, true)) return STATUS_FAILED_TRANSACTION;

        hidl_bridge::HidlReader r(rep, br.mmapBase(), br.mmapSize());
        int32_t hst = -1;
        const void* vecPtr = nullptr;
        const void* elemPtr = nullptr;
        hidl_bridge::HidlVec hv{};
        std::vector<hidl_bridge::HidlPcmParameters> pcms;
        bool ok = r.readInt32(&hst) && hst == 0 && r.readBuffer(sizeof(hv), &vecPtr) && vecPtr != nullptr;
        if (ok) {
            memcpy(&hv, vecPtr, sizeof(hv));
            if (hv.size > 0 && hv.size <= 32) {
                if (r.readEmbeddedBuffer(hv.size * sizeof(hidl_bridge::HidlAudioCapabilities20), &elemPtr) && elemPtr) {
                    const auto* elems = static_cast<const hidl_bridge::HidlAudioCapabilities20*>(elemPtr);
                    for (uint32_t i = 0; i < hv.size; i++) {
                        if (elems[i].discriminator == 0) pcms.push_back(elems[i].pcm);
                    }
                } else {
                    ok = false;
                }
            }
        }
        br.freeBuffer(rep.data.ptr.buffer);
        if (!ok) return STATUS_FAILED_TRANSACTION;

        logf("HIDL getProviderCapabilities(sessionType=%d): %zu PCM entries", sessionType, pcms.size());

        binder_status_t st = writeOkStatus(out);
        if (st == STATUS_OK) st = AParcel_writeInt32(out, static_cast<int32_t>(pcms.size()));
        for (const auto& p : pcms) {
            if (st != STATUS_OK) break;
            auto rates = hidl_bridge::HwBinderBridge::maskToSampleRates(p.sampleRateMask);
            auto chans = hidl_bridge::HwBinderBridge::maskToChannelModes(p.channelModeMask);
            auto bits = hidl_bridge::HwBinderBridge::maskToBitsPerSample(p.bitsPerSampleMask);
            st = AParcel_writeInt32(out, 1);  // non-null AudioCapabilities union
            if (st == STATUS_OK) st = AParcel_writeInt32(out, 0);  // tag pcmCapabilities
            if (st == STATUS_OK) st = AParcel_writeInt32(out, 1);  // non-null PcmCapabilities
            int32_t pStart = AParcel_getDataPosition(out);
            if (st == STATUS_OK) st = AParcel_writeInt32(out, 0);
            if (st == STATUS_OK)
                st = AParcel_writeInt32Array(out, rates.empty() ? nullptr : rates.data(),
                                             static_cast<int32_t>(rates.size()));
            if (st == STATUS_OK)
                st = AParcel_writeByteArray(out, chans.empty() ? nullptr : chans.data(),
                                            static_cast<int32_t>(chans.size()));
            if (st == STATUS_OK)
                st = AParcel_writeByteArray(out, bits.empty() ? nullptr : bits.data(),
                                            static_cast<int32_t>(bits.size()));
            if (st == STATUS_OK) st = AParcel_writeInt32Array(out, nullptr, 0);  // dataIntervalUs
            if (st == STATUS_OK) st = backfillSize(out, pStart);
        }
        return st;
    }

    if (code == FIRST_CALL_TRANSACTION + 1) {  // openProvider(SessionType)
        int32_t sessionType = -1;
        if (AParcel_readInt32(in, &sessionType) != STATUS_OK) return STATUS_BAD_VALUE;
        if (sessionType != hidl_bridge::kSessionA2dpSw && sessionType != hidl_bridge::kSessionHearingAidSw) {
            logf("HIDL openProvider(session type %d): software-only HAL refused offload/LE type", sessionType);
            return writeExceptionStatus(out, EX_ILLEGAL_ARGUMENT);
        }
        int32_t hst = -1;
        uint8_t sehSt = 2;
        uint32_t provHandle = 0;
        bool isNull = true;
        bool ok = false;
        bool provV21 = false;
        if (facV21) {
            hidl_bridge::HidlParcel req21;
            req21.writeInterfaceToken(hidl_bridge::kSehHidlFactoryFqName21);
            req21.writeUint8(static_cast<uint8_t>(sessionType));
            binder_transaction_data rep21{};
            if (br.transact(fac, hidl_bridge::kFactorySehOpenProvider21, req21, &rep21, true)) {
                hidl_bridge::HidlReader r21(rep21, br.mmapBase(), br.mmapSize());
                int32_t h21 = -1;
                uint8_t s21 = 2;
                uint32_t ph21 = 0;
                bool n21 = true;
                if (r21.readInt32(&h21) && h21 == 0 && r21.readUint8(&s21) && r21.readStrongBinder(&ph21, &n21)) {
                    if (s21 == 0 && !n21 && ph21 != 0) br.acquireHandle(ph21);
                    hst = h21;
                    sehSt = s21;
                    provHandle = ph21;
                    isNull = n21;
                    ok = true;
                    provV21 = true;
                }
                br.freeBuffer(rep21.data.ptr.buffer);
            }
        }
        if (!ok) {
            hidl_bridge::HidlParcel req;
            req.writeInterfaceToken(hidl_bridge::kSehHidlFactoryFqName);
            req.writeUint8(static_cast<uint8_t>(sessionType));
            binder_transaction_data rep{};
            if (!br.transact(fac, hidl_bridge::kFactorySehOpenProvider, req, &rep, true))
                return STATUS_FAILED_TRANSACTION;

            hidl_bridge::HidlReader r(rep, br.mmapBase(), br.mmapSize());
            ok = r.readInt32(&hst) && hst == 0 && r.readUint8(&sehSt) && r.readStrongBinder(&provHandle, &isNull);
            if (ok && sehSt == 0 && !isNull && provHandle != 0) {
                br.acquireHandle(provHandle);
            }
            br.freeBuffer(rep.data.ptr.buffer);
        }
        if (!ok) return STATUS_FAILED_TRANSACTION;
        if (sehSt != 0 || isNull || provHandle == 0) {
            logf("HIDL openProvider(session type %d): Samsung HIDL factory refused (%u)", sessionType, sehSt);
            return sehSt == 1 ? writeExceptionStatus(out, EX_ILLEGAL_ARGUMENT)
                              : writeServiceErrorStatus(out, static_cast<int32_t>(sehSt));
        }
        logf("HIDL openProvider(session type %d): wrapped @2.%d handle %u", sessionType, provV21 ? 1 : 0, provHandle);
        AIBinder* mine = AIBinder_new(gHidlProviderCls, new HidlProviderData{provHandle, sessionType, provV21, {}, nullptr});
        binder_status_t st = writeOkStatus(out);
        if (st == STATUS_OK) st = AParcel_writeStrongBinder(out, mine);
        if (mine) AIBinder_decStrong(mine);
        return st;
    }
    return STATUS_UNKNOWN_TRANSACTION;
}

void defineClasses() {
    gAospFactoryCls = AIBinder_Class_define(kAospFactory, onCreatePass, onDestroyNothing, factoryTransact);
    gAospProviderCls = AIBinder_Class_define(kAospProvider, onCreatePass, providerDestroy, providerTransact);
    gAospPortCls = AIBinder_Class_define(kAospPort, onCreatePass, onDestroyNothing, onTransactNone);
    gSehFactoryCls = AIBinder_Class_define(gLoop ? kAospFactory : kSehFactory, onCreatePass, onDestroyNothing, onTransactNone);
    gSehProviderCls = AIBinder_Class_define(gLoop ? kAospProvider : kSehProvider, onCreatePass, onDestroyNothing, onTransactNone);
    gSehPortCls = AIBinder_Class_define(gLoop ? kAospPort : kSehPort, onCreatePass, portDestroy, portTransact);
    gHidlFactoryCls = AIBinder_Class_define(kAospFactory, onCreatePass, onDestroyNothing, hidlFactoryTransact);
    gHidlProviderCls = AIBinder_Class_define(kAospProvider, onCreatePass, hidlProviderDestroy, hidlProviderTransact);
}

// --client NAME: what a guest's stack does first, against NAME.
int client(const char* name) {
    AIBinder* f = P.checkService(name);
    if (!f) {
        printf("no %s\n", name);
        return 1;
    }
    AIBinder_Class* fc = AIBinder_Class_define(kAospFactory, onCreatePass, onDestroyNothing, onTransactNone);
    AIBinder_Class* pc = AIBinder_Class_define(kAospProvider, onCreatePass, onDestroyNothing, onTransactNone);
    if (!AIBinder_associateClass(f, fc)) {
        printf("%s is not %s\n", name, kAospFactory);
        return 1;
    }
    auto call = [](AIBinder* b, transaction_code_t code, int32_t arg, bool hasArg, AParcel** rep) {
        AParcel* req = nullptr;
        binder_status_t st = AIBinder_prepareTransaction(b, &req);
        if (st == STATUS_OK && hasArg) st = AParcel_writeInt32(req, arg);
        if (st == STATUS_OK) st = AIBinder_transact(b, code, &req, rep, 0);
        if (st != STATUS_OK) return st;
        AStatus* s = nullptr;
        st = AParcel_readStatusHeader(*rep, &s);
        if (st == STATUS_OK && !AStatus_isOk(s)) st = STATUS_FAILED_TRANSACTION;
        AStatus_delete(s);
        return st;
    };
    AParcel* rep = nullptr;
    int32_t v = -1;
    if (call(f, kGetInterfaceVersion, 0, false, &rep) == STATUS_OK) AParcel_readInt32(rep, &v);
    if (rep) AParcel_delete(rep), rep = nullptr;
    printf("factory version %d\n", v);
    for (int32_t type : {1, 2}) {
        int32_t n = -1, tags[8] = {0};
        binder_status_t st = call(f, FIRST_CALL_TRANSACTION, type, true, &rep);
        if (st == STATUS_OK) {
            AParcel_readInt32(rep, &n);
            for (int32_t i = 0; i < n && i < 8; i++) {
                Value v;
                if (readUnionValue(rep, &v) != STATUS_OK) break;
                tags[i] = v.tag;
                if (v.tag == 1 && v.member >= 0) {  // the codec's tag, as 10 + tag
                    int32_t t = -1;
                    AParcel_setDataPosition(rep, v.member + 12);
                    AParcel_readInt32(rep, &t);
                    tags[i] = 10 + t;
                    AParcel_setDataPosition(rep, v.end);
                }
            }
        }
        if (rep) AParcel_delete(rep), rep = nullptr;
        printf("capabilities(session %d): status %d, %d entries, tags", type, st, n);
        for (int32_t i = 0; i < n && i < 8; i++) printf(" %d", tags[i]);
        printf("\n");
        AIBinder* prov = nullptr;
        st = call(f, FIRST_CALL_TRANSACTION + 1, type, true, &rep);
        if (st == STATUS_OK) AParcel_readStrongBinder(rep, &prov);
        if (rep) AParcel_delete(rep), rep = nullptr;
        int32_t pv = -1;
        if (prov && AIBinder_associateClass(prov, pc) && call(prov, kGetInterfaceVersion, 0, false, &rep) == STATUS_OK)
            AParcel_readInt32(rep, &pv);
        if (rep) AParcel_delete(rep), rep = nullptr;
        printf("openProvider(session %d): status %d, provider %s, version %d\n", type, st, prov ? "yes" : "no", pv);
        if (prov) AIBinder_decStrong(prov);
    }
    AIBinder_decStrong(f);
    return 0;
}

int suppressHostSvcs(const char* stage, bool dryOnce) {
    char sfFrozenPath[512];
    char guestAudioPath[512];
    if (snprintf(sfFrozenPath, sizeof(sfFrozenPath), "%s/data/oneos_host_sf_frozen", stage) >=
            static_cast<int>(sizeof(sfFrozenPath)) ||
        snprintf(guestAudioPath, sizeof(guestAudioPath), "%s/data/guest_audio.flag", stage) >=
            static_cast<int>(sizeof(guestAudioPath))) {
        fprintf(stderr, "suppressHostSvcs: stage path too long\n");
        return 1;
    }

    static const char* const kAlways[] = {
        "zygote", "zygote_secondary", "bootanim",
        "dumpstate", "dumpstatez", "bugreport", "bugreportd",
    };

    auto checkSvc = [dryOnce](const char* name) -> int {
        char prop[64];
        char val[PROP_VALUE_MAX] = {};
        snprintf(prop, sizeof(prop), "init.svc.%s", name);
        __system_property_get(prop, val);
        if (dryOnce) {
            printf("%s=%s\n", prop, val);
            return 0;
        }
        if (strcmp(val, "running") == 0) {
            __system_property_set("ctl.stop", name);
            return 1;
        }
        return 0;
    };

    auto checkOnce = [&]() -> int {
        int stopped = 0;
        for (const char* name : kAlways) stopped += checkSvc(name);
        if (access(sfFrozenPath, F_OK) != 0) stopped += checkSvc("surfaceflinger");
        if (access(guestAudioPath, F_OK) == 0) stopped += checkSvc("audioserver");
        return stopped;
    };

    if (dryOnce) {
        uint32_t s0 = __system_property_area_serial();
        checkOnce();
        uint32_t s1 = s0;
        struct timespec ts{0, 50 * 1000 * 1000};
        bool changed = __system_property_wait(nullptr, s0, &s1, &ts);
        printf("serial=%u->%u changed=%d\n", s0, s1, changed ? 1 : 0);
        return 0;
    }

    auto monoMs = []() -> int64_t {
        struct timespec ts{};
        clock_gettime(CLOCK_MONOTONIC, &ts);
        return static_cast<int64_t>(ts.tv_sec) * 1000 + ts.tv_nsec / 1000000;
    };

    uint32_t serial = __system_property_area_serial();
    int64_t lastWakeMs = monoMs();
    while (true) {
        int stopped = checkOnce();
        uint32_t cur = __system_property_area_serial();
        int64_t nowMs = monoMs();
        if (stopped == 0 && (nowMs - lastWakeMs) < 50) {
            usleep(50 * 1000);
        }
        lastWakeMs = monoMs();
        if (cur != serial) {
            serial = cur;
            continue;
        }
        uint32_t nextSerial = serial;
        struct timespec ts{0, 200 * 1000 * 1000};
        if (__system_property_wait(nullptr, serial, &nextSerial, &ts)) {
            serial = nextSerial;
        }
    }
    return 0;
}

}  // namespace

int main(int argc, char** argv) {
    if (argc >= 3 && !strcmp(argv[1], "--suppress-host-svcs"))
        return suppressHostSvcs(argv[2], false);
    if (argc >= 3 && !strcmp(argv[1], "--suppress-host-svcs-check"))
        return suppressHostSvcs(argv[2], true);
    if (!P.load()) {
        logf("libbinder_ndk.so lacks the service manager calls");
        return 2;
    }
    if (argc == 3 && !strcmp(argv[1], "--declared")) {
        printf("%d\n", P.isDeclared(argv[2]) ? 1 : 0);
        return 0;
    }
    if (argc == 3 && !strcmp(argv[1], "--client")) return client(argv[2]);
    if (argc == 2 && !strcmp(argv[1], "--loop")) {
        gLoop = true;
        kSehFactoryName = kAospFactoryName;
        kAospFactoryName = kLoopName;
    }
    if (argc == 2 && !strcmp(argv[1], "--hidl")) {
        gForceHidl = true;
    } else if (argc == 2 && !strcmp(argv[1], "--hidl-probe")) {
        auto& br = hidl_bridge::HwBinderBridge::instance();
        br.setLog(logf);
        if (!br.ensureOpen()) {
            logf("hidl-probe: could not open /dev/hwbinder: %s", strerror(errno));
            return 1;
        }
        br.startLooperPool();
        return br.runProbe();
    } else if (argc == 2 && !strcmp(argv[1], "--check")) {
        AIBinder* s = P.checkService(kSehFactoryName);
        bool sh = hidl_bridge::HwBinderBridge::instance().factoryExists();
        AIBinder* a = P.checkService(kAospFactoryName);
        printf("samsung_factory=%d samsung_hidl_factory=%d android_factory=%d android_declared=%d\n",
               s != nullptr, sh ? 1 : 0, a != nullptr, P.isDeclared(kAospFactoryName) ? 1 : 0);
        if (s) AIBinder_decStrong(s);
        if (a) AIBinder_decStrong(a);
        return 0;
    }
    defineClasses();
    P.setThreads(4);
    P.startPool();
    auto& br = hidl_bridge::HwBinderBridge::instance();
    br.setLog(logf);

    bool useHidl = false;
    for (int i = 0; i < 100 && !gSehFactory && !useHidl; i++) {
        if (!gForceHidl) gSehFactory = P.checkService(kSehFactoryName);
        if (!gSehFactory && !gLoop && br.factoryExists()) {
            useHidl = true;
            break;
        }
        if (!gSehFactory) usleep(100 * 1000);
    }
    if (!gSehFactory && !useHidl) {
        logf("no %s (AIDL or HIDL @2.0): nothing to bridge", kSehFactoryName);
        return 1;
    }
    AIBinder* factory = nullptr;
    bool facV21 = false;
    if (useHidl) {
        br.startLooperPool();
        if (!br.getFactoryHandle(&facV21)) {
            logf("could not open Samsung HIDL factory on /dev/hwbinder");
            return 1;
        }
        factory = AIBinder_new(gHidlFactoryCls, nullptr);
    } else {
        if (!AIBinder_associateClass(gSehFactory, gSehFactoryCls)) {
            logf("%s is not Samsung's factory", kSehFactoryName);
            return 1;
        }
        factory = AIBinder_new(gAospFactoryCls, nullptr);
    }
    binder_exception_t ex = P.addService(factory, kAospFactoryName);
    if (ex != EX_NONE) {
        logf("could not register %s: exception %d", kAospFactoryName, ex);
        return 1;
    }
    logf("serving %s for Samsung's %sfactory (declared: %d)", kAospFactoryName,
         useHidl ? (facV21 ? "HIDL @2.1 " : "HIDL @2.0 ") : "", P.isDeclared(kAospFactoryName) ? 1 : 0);
    // Keep the name. Samsung's audio HAL also carries Qualcomm's provider for
    // Android's factory and registers it under the same name once the guest's
    // Bluetooth opens (it can, now that the session declares the name). That
    // replaces this bridge, and the guest's stack then opens its A2DP sessions
    // on Qualcomm's provider, which the audio HAL's own Bluetooth path (PAL)
    // never listens to: it waits for Samsung's sessions, so every playback start
    // fails with -38 and the sound stays on the speaker (Galaxy S23 on ColorOS,
    // report c49cdbce: "Registered AIDL 1.0 service ... status=0" 52 s after
    // this bridge, then "Rx device start failed with status -38"). 0.4.97 checked the name
    // once a second and re-registered when another process held it, but that never fired
    // (report dcb7e310, S23 on 0.4.97: the takeover 45 s in, no re-registration logged):
    // most likely this process's service lookups are answered from libbinder's own cache,
    // which still holds our binder (not proven on a phone). So register again every second without asking: servicemanager takes
    // the same binder again as a no-op, and after a takeover the name is ours within a
    // second. A log line every 10 minutes shows the bridge is alive.
    int rounds = 0, last = EX_NONE;
    while (true) {
        sleep(1);
        ex = P.addService(factory, kAospFactoryName);
        rounds++;
        if (ex != last || rounds % 600 == 0)
            logf("%s registered again (round %d): exception %d", kAospFactoryName, rounds, ex);
        last = ex;
    }
    return 0;
}
