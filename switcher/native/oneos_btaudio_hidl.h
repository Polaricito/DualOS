#pragma once

// Raw /dev/hwbinder bridge between AOSP Stable AIDL v2 (guest-facing, served via libbinder_ndk)
// and Samsung's HIDL @2.0/@2.1 Bluetooth Audio HAL (vendor-facing, on /dev/hwbinder).
//
// Verified against A53 (SM-A536E) vendor libraries:
//   - vendor.samsung.hardware.bluetooth.audio@2.1::ISehBluetoothAudioProvidersFactory/default (extends @2.0)
//   - vendor.samsung.hardware.bluetooth.audio@2.1-impl.so forwards @2.1 -> BluetoothAudioSession_2_1
//     which audio.bluetooth.default.so uses for A2DP_SOFTWARE_ENCODING_DATAPATH (1)
//     and HEARING_AID_SOFTWARE_ENCODING_DATAPATH (3).

#include <android/binder_ibinder.h>
#include <android/binder_parcel.h>
#include <android/binder_status.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/android/binder.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <unistd.h>

#include <atomic>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace hidl_bridge {

inline const char* kHwMgrFqName = "android.hidl.manager@1.0::IServiceManager";
inline const char* kSehHidlFactoryFqName = "vendor.samsung.hardware.bluetooth.audio@2.0::ISehBluetoothAudioProvidersFactory";
inline const char* kSehHidlFactoryFqName21 = "vendor.samsung.hardware.bluetooth.audio@2.1::ISehBluetoothAudioProvidersFactory";
inline const char* kSehHidlProviderFqName = "vendor.samsung.hardware.bluetooth.audio@2.0::ISehBluetoothAudioProvider";
inline const char* kSehHidlProviderFqName21 = "vendor.samsung.hardware.bluetooth.audio@2.1::ISehBluetoothAudioProvider";
inline const char* kSehHidlPortFqName = "vendor.samsung.hardware.bluetooth.audio@2.0::ISehBluetoothAudioPort";
inline const char* kSehHidlPortFqName21 = "vendor.samsung.hardware.bluetooth.audio@2.1::ISehBluetoothAudioPort";
inline const char* kHidlBaseFqName = "android.hidl.base@1.0::IBase";
inline const char* kDefaultInstance = "default";

// BnHwBase system transaction codes (verified in A53 libhidlbase.so BnHwBase::onTransact).
constexpr uint32_t kHidlInterfaceChain = 256067662u;     // 0x0f43484e
constexpr uint32_t kHidlGetHashChain = 256398152u;       // 0x0f485348
constexpr uint32_t kHidlDescriptor = 256136003u;         // 0x0f445343
constexpr uint32_t kHidlPing = 256921159u;               // 0x0f504e47
constexpr uint32_t kHidlSyspropsChanged = 257120595u;    // 0x0f535953

// ISehBluetoothAudioProvidersFactory@2.0/@2.1 methods (1-based)
constexpr uint32_t kFactorySehOpenProvider = 1;
constexpr uint32_t kFactorySehGetCapabilities = 2;
constexpr uint32_t kFactorySehOpenProvider21 = 3;

// ISehBluetoothAudioProvider@2.0/@2.1 methods (1-based)
constexpr uint32_t kProviderSehStartSession = 1;
constexpr uint32_t kProviderSehStreamStarted = 2;
constexpr uint32_t kProviderSehStreamSuspended = 3;
constexpr uint32_t kProviderSehEndSession = 4;
constexpr uint32_t kProviderSehStartSession21 = 5;
constexpr uint32_t kProviderSehUpdateAudioConfiguration21 = 6;

// ISehBluetoothAudioPort@2.0/@2.1 methods (1-based)
constexpr uint32_t kPortSehStartStream = 1;
constexpr uint32_t kPortSehSuspendStream = 2;
constexpr uint32_t kPortSehStopStream = 3;
constexpr uint32_t kPortSehGetPresentationPosition = 4;
constexpr uint32_t kPortSehUpdateMetadata = 5;
constexpr uint32_t kPortSehUpdateSinkMetadata = 6;
constexpr uint32_t kPortSehGetAudioConfiguration = 7;

// AOSP SessionType / ChannelMode / BitsPerSample <-> Samsung HIDL @2.0/@2.1 masks.
constexpr int32_t kSessionA2dpSw = 1;
constexpr int32_t kSessionHearingAidSw = 3;

struct HidlString {
    uint64_t ptr;
    uint32_t size;
    uint32_t owns;
};
static_assert(sizeof(HidlString) == 16, "HidlString size");

struct HidlVec {
    uint64_t ptr;
    uint32_t size;
    uint32_t owns;
};
static_assert(sizeof(HidlVec) == 16, "HidlVec size");

struct HidlPcmParameters {
    uint32_t sampleRateMask;
    uint8_t channelModeMask;
    uint8_t bitsPerSampleMask;
    uint16_t pad;
    uint32_t dataIntervalUs;
};
static_assert(sizeof(HidlPcmParameters) == 12, "HidlPcmParameters size");

struct HidlAudioCapabilities20 {
    uint8_t discriminator;  // 0 = pcmCapabilities
    uint8_t pad[3];
    HidlPcmParameters pcm;  // +0x04..+0x0f
    uint8_t codecPad[12];   // sizeof == 0x1c (28 bytes)
};
static_assert(sizeof(HidlAudioCapabilities20) == 28, "SehAudioCapabilities@2.0 size");

struct HidlAudioConfiguration20 {
    uint8_t discriminator;  // 0 = pcmConfig
    uint8_t pad0[3];
    HidlPcmParameters pcm;  // +0x04..+0x0f
    uint8_t codecPad[16];   // sizeof == 0x20 (32 bytes)
};
static_assert(sizeof(HidlAudioConfiguration20) == 32, "SehAudioConfiguration@2.0 size");

struct HidlAudioConfiguration21 {
    uint8_t discriminator;  // 0 = pcmConfig
    uint8_t pad0[7];        // 8-byte aligned union arm at +0x08
    HidlPcmParameters pcm;  // +0x08..+0x13 (12 bytes)
    uint8_t restPad[44];    // sizeof == 0x40 (64 bytes)
};
static_assert(sizeof(HidlAudioConfiguration21) == 64, "SehAudioConfiguration@2.1 size");

struct HidlGrantorDescriptor {
    uint32_t flags;
    uint32_t fdIndex;
    uint32_t offset;
    uint32_t pad;
    uint64_t extent;
};
static_assert(sizeof(HidlGrantorDescriptor) == 24, "Hidl GrantorDescriptor size");

struct HidlMQDescriptor {
    HidlVec grantors;
    uint64_t handlePtr;
    uint32_t quantum;
    uint32_t flags;
};
static_assert(sizeof(HidlMQDescriptor) == 32, "Hidl MQDescriptor size");

struct HidlNativeHandle {
    int32_t version;
    int32_t numFds;
    int32_t numInts;
    int32_t data[0];
};
static_assert(sizeof(HidlNativeHandle) == 12, "native_handle_t header size");

struct HidlTimeSpec {
    uint64_t tvSec;
    uint64_t tvNSec;
};
static_assert(sizeof(HidlTimeSpec) == 16, "SehTimeSpec size");

struct HidlPlaybackTrackMetadata {
    int32_t usage;
    int32_t contentType;
    float gain;
};
static_assert(sizeof(HidlPlaybackTrackMetadata) == 12, "HIDL audio_common@5.0 PlaybackTrackMetadata size");

// ---- Parcel builders and readers for /dev/hwbinder ----

class HidlParcel {
  public:
    void writeInt32(int32_t v) { appendBytes(&v, sizeof(v)); }
    void writeUint32(uint32_t v) { appendBytes(&v, sizeof(v)); }
    void writeUint64(uint64_t v) { appendBytes(&v, sizeof(v)); }
    void writeUint8(uint8_t v) { writeUint32(static_cast<uint32_t>(v)); }

    void writeInterfaceToken(const char* iface) {
        size_t len = strlen(iface) + 1;
        size_t aligned = (len + 3) & ~size_t(3);
        size_t old = data_.size();
        data_.resize(old + aligned, 0);
        memcpy(data_.data() + old, iface, len);
    }

    // `data` MUST remain alive until the transaction/reply ioctl returns!
    size_t writeBuffer(const void* ptr, size_t len, bool hasParent = false, size_t parent = 0, size_t parentOffset = 0) {
        binder_buffer_object obj{};
        obj.hdr.type = BINDER_TYPE_PTR;
        obj.flags = hasParent ? BINDER_BUFFER_FLAG_HAS_PARENT : 0;
        obj.buffer = reinterpret_cast<binder_uintptr_t>(ptr);
        obj.length = len;
        obj.parent = parent;
        obj.parent_offset = parentOffset;

        size_t objPos = data_.size();
        appendBytes(&obj, sizeof(obj));
        size_t idx = offsets_.size();
        if (ptr != nullptr) {
            offsets_.push_back(objPos);
            buffersSize_ += (len + 7) & ~size_t(7);
        }
        return idx;
    }

    // Stores string bytes & HidlString header inside this parcel's backing storage vectors
    // so callers don't need to manage temporary lifetime until transact() completes.
    void writeHidlString(const std::string& s, bool hasParent = false, size_t parent = 0, size_t parentOffset = 0) {
        auto bytes = std::make_unique<std::string>(s);
        auto hdr = std::make_unique<HidlString>(HidlString{reinterpret_cast<uint64_t>(bytes->c_str()),
                                                           static_cast<uint32_t>(bytes->size()), 0});
        size_t idx = writeBuffer(hdr.get(), sizeof(HidlString), hasParent, parent, parentOffset);
        writeBuffer(bytes->c_str(), bytes->size() + 1, true, idx, 0);
        ownedStrings_.push_back(std::move(bytes));
        ownedHdrs_.push_back(std::move(hdr));
    }

    void writeLocalBinder(void* cookie) {
        flat_binder_object obj{};
        obj.hdr.type = BINDER_TYPE_BINDER;
        obj.flags = 0x7f | FLAT_BINDER_FLAG_ACCEPTS_FDS;
        obj.binder = reinterpret_cast<binder_uintptr_t>(cookie);
        obj.cookie = reinterpret_cast<binder_uintptr_t>(cookie);
        size_t objPos = data_.size();
        appendBytes(&obj, sizeof(obj));
        offsets_.push_back(objPos);
    }

    const uint8_t* data() const { return data_.empty() ? nullptr : data_.data(); }
    size_t dataSize() const { return data_.size(); }
    const binder_size_t* offsets() const { return offsets_.empty() ? nullptr : offsets_.data(); }
    size_t offsetsSize() const { return offsets_.size() * sizeof(binder_size_t); }
    binder_size_t buffersSize() const { return buffersSize_; }

    template <typename T>
    T* keepAlive(std::unique_ptr<T> p) {
        T* raw = p.get();
        ownedRaw_.push_back({p.release(), [](void* q) { delete static_cast<T*>(q); }});
        return raw;
    }

  private:
    struct Holder {
        void* ptr;
        void (*deleter)(void*);
    };
    void appendBytes(const void* src, size_t n) {
        const uint8_t* b = static_cast<const uint8_t*>(src);
        data_.insert(data_.end(), b, b + n);
    }

    std::vector<uint8_t> data_;
    std::vector<binder_size_t> offsets_;
    binder_size_t buffersSize_ = 0;
    std::vector<std::unique_ptr<std::string>> ownedStrings_;
    std::vector<std::unique_ptr<HidlString>> ownedHdrs_;
    std::vector<Holder> ownedRaw_;

  public:
    ~HidlParcel() {
        for (auto& h : ownedRaw_) h.deleter(h.ptr);
    }
    HidlParcel() = default;
    HidlParcel(const HidlParcel&) = delete;
    HidlParcel& operator=(const HidlParcel&) = delete;
};

class HidlReader {
  public:
    HidlReader(const binder_transaction_data& tr, uintptr_t mmapBase, size_t mmapSize)
        : buf_(reinterpret_cast<const uint8_t*>(tr.data.ptr.buffer)),
          len_(tr.data_size),
          offsets_(reinterpret_cast<const binder_size_t*>(tr.data.ptr.offsets)),
          numOffsets_(tr.offsets_size / sizeof(binder_size_t)),
          mmapBase_(mmapBase),
          mmapSize_(mmapSize) {}

    bool inMmap(uint64_t addr, size_t bytes) const {
        if (!addr || addr < mmapBase_) return false;
        uint64_t off = addr - mmapBase_;
        return off <= mmapSize_ && bytes <= mmapSize_ - off;
    }

    bool enforceInterface(const char* expected) {
        size_t need = strlen(expected) + 1;
        size_t aligned = (need + 3) & ~size_t(3);
        if (pos_ + aligned > len_) return false;
        if (strncmp(reinterpret_cast<const char*>(buf_ + pos_), expected, need) != 0) return false;
        pos_ += aligned;
        return true;
    }

    bool readInt32(int32_t* out) {
        if (pos_ + 4 > len_) return false;
        memcpy(out, buf_ + pos_, 4);
        pos_ += 4;
        return true;
    }

    bool readUint8(uint8_t* out) {
        int32_t v = 0;
        if (!readInt32(&v)) return false;
        *out = static_cast<uint8_t>(v & 0xff);
        return true;
    }

    bool readBool(bool* out) {
        int32_t v = 0;
        if (!readInt32(&v)) return false;
        *out = (v != 0);
        return true;
    }

    bool readStrongBinder(uint32_t* outHandle, bool* outIsNull = nullptr) {
        if (pos_ + sizeof(flat_binder_object) > len_) return false;
        flat_binder_object obj{};
        memcpy(&obj, buf_ + pos_, sizeof(obj));
        pos_ += sizeof(obj);
        if (obj.hdr.type == BINDER_TYPE_HANDLE) {
            if (outIsNull) *outIsNull = false;
            *outHandle = obj.handle;
            return true;
        }
        if (obj.hdr.type == BINDER_TYPE_BINDER && obj.binder == 0) {
            if (outIsNull) *outIsNull = true;
            *outHandle = 0;
            return true;
        }
        return false;
    }

    bool readBuffer(size_t expectedSize, const void** outPtr) {
        if (pos_ + sizeof(binder_buffer_object) > len_) return false;
        binder_buffer_object obj{};
        memcpy(&obj, buf_ + pos_, sizeof(obj));
        pos_ += sizeof(obj);
        if (obj.hdr.type != BINDER_TYPE_PTR || obj.length != expectedSize) return false;
        if (expectedSize == 0) {
            *outPtr = nullptr;
            return true;
        }
        if (!inMmap(obj.buffer, expectedSize)) return false;
        *outPtr = reinterpret_cast<const void*>(obj.buffer);
        return true;
    }

    // Consume an optional embedded child buffer (if count > 0 / non-null).
    bool readEmbeddedBuffer(size_t expectedSize, const void** outPtr) {
        if (pos_ + sizeof(binder_buffer_object) > len_) return false;
        binder_buffer_object obj{};
        memcpy(&obj, buf_ + pos_, sizeof(obj));
        pos_ += sizeof(obj);
        if (obj.hdr.type != BINDER_TYPE_PTR) return false;
        if (obj.buffer == 0 || expectedSize == 0) {
            *outPtr = nullptr;
            return expectedSize == 0 || obj.length == 0;
        }
        if (obj.length < expectedSize || !inMmap(obj.buffer, expectedSize)) return false;
        *outPtr = reinterpret_cast<const void*>(obj.buffer);
        return true;
    }

    // Scan remaining objects or next embedded binder_buffer_object for NativeHandle.
    const HidlNativeHandle* findEmbeddedNativeHandle(uint64_t ptrVal) const {
        if (!ptrVal) return nullptr;
        for (size_t i = 0; i < numOffsets_; i++) {
            size_t off = offsets_[i];
            if (off + sizeof(binder_buffer_object) > len_) continue;
            binder_buffer_object obj{};
            memcpy(&obj, buf_ + off, sizeof(obj));
            if (obj.hdr.type == BINDER_TYPE_PTR && obj.buffer == ptrVal && obj.length >= sizeof(HidlNativeHandle)) {
                if (!inMmap(obj.buffer, obj.length)) return nullptr;
                const auto* h = reinterpret_cast<const HidlNativeHandle*>(obj.buffer);
                if (h->numFds < 0 || h->numInts < 0) return nullptr;
                size_t totalInts = static_cast<size_t>(h->numFds) + static_cast<size_t>(h->numInts);
                if (totalInts > (obj.length - sizeof(HidlNativeHandle)) / 4) return nullptr;
                return h;
            }
        }
        return nullptr;
    }

  private:
    const uint8_t* buf_;
    size_t len_;
    const binder_size_t* offsets_;
    size_t numOffsets_;
    uintptr_t mmapBase_;
    size_t mmapSize_;
    size_t pos_ = 0;
};

// Forward callbacks into AIDL port (provided by oneos_btaudio.cpp)
using LogFn = void (*)(const char* fmt, ...);

class HwBinderBridge {
  public:
    static HwBinderBridge& instance() {
        static HwBinderBridge inst;
        return inst;
    }

    void setLog(LogFn fn) { log_ = fn; }
    uintptr_t mmapBase() const { return mmapBase_; }
    size_t mmapSize() const { return kMmapSize; }

    bool ensureOpen() {
        std::lock_guard<std::mutex> l(initMu_);
        if (fd_ >= 0) return true;
        int fd = open("/dev/hwbinder", O_RDWR | O_CLOEXEC);
        if (fd < 0) return false;
        uint32_t maxThreads = 4;
        ioctl(fd, BINDER_SET_MAX_THREADS, &maxThreads);
        void* map = mmap(nullptr, kMmapSize, PROT_READ, MAP_PRIVATE, fd, 0);
        if (map == MAP_FAILED) {
            close(fd);
            return false;
        }
        fd_ = fd;
        mmapBase_ = reinterpret_cast<uintptr_t>(map);
        return true;
    }

    void startLooperPool() {
        if (!ensureOpen()) return;
        std::lock_guard<std::mutex> l(initMu_);
        if (poolStarted_) return;
        poolStarted_ = true;
        std::thread([this]() { looperThread(true); }).detach();
        std::thread([this]() { looperThread(false); }).detach();
    }

    // Lookup vendor.samsung.hardware.bluetooth.audio@{2.1,2.0}::ISehBluetoothAudioProvidersFactory/default.
    // If outHandle != nullptr, retains strong ref (caller owns inc/acquire and must releaseHandle()).
    bool lookupService(const char* fqName, uint32_t* outHandle) {
        if (!ensureOpen()) return false;
        HidlParcel req;
        req.writeInterfaceToken(kHwMgrFqName);
        req.writeHidlString(fqName);
        req.writeHidlString(kDefaultInstance);

        binder_transaction_data rep{};
        if (!transact(0, 1 /* get */, req, &rep, true)) return false;

        HidlReader r(rep, mmapBase_, kMmapSize);
        int32_t st = -1;
        uint32_t handle = 0;
        bool isNull = true;
        bool ok = r.readInt32(&st) && st == 0 && r.readStrongBinder(&handle, &isNull) && !isNull;
        if (ok && outHandle) {
            acquireHandle(handle);
            *outHandle = handle;
        }
        freeBuffer(rep.data.ptr.buffer);
        return ok;
    }

    bool lookupFactory(uint32_t* outHandle, bool* outIsV21 = nullptr) {
        if (lookupService(kSehHidlFactoryFqName21, outHandle)) {
            if (outIsV21) *outIsV21 = true;
            return true;
        }
        if (lookupService(kSehHidlFactoryFqName, outHandle)) {
            if (outIsV21) *outIsV21 = false;
            return true;
        }
        return false;
    }

    bool factoryExists() {
        uint32_t h = 0;
        if (!lookupFactory(&h)) return false;
        releaseHandle(h);
        return true;
    }

    void acquireHandle(uint32_t handle) {
        if (fd_ < 0 || handle == 0) return;
        struct {
            uint32_t c1;
            uint32_t h1;
            uint32_t c2;
            uint32_t h2;
        } __attribute__((packed)) cmd{BC_INCREFS, handle, BC_ACQUIRE, handle};
        sendWriteOnly(&cmd, sizeof(cmd));
    }

    void releaseHandle(uint32_t handle) {
        if (fd_ < 0 || handle == 0) return;
        struct {
            uint32_t c1;
            uint32_t h1;
            uint32_t c2;
            uint32_t h2;
        } __attribute__((packed)) cmd{BC_RELEASE, handle, BC_DECREFS, handle};
        sendWriteOnly(&cmd, sizeof(cmd));
    }

    void freeBuffer(binder_uintptr_t ptr) {
        if (fd_ < 0 || ptr == 0) return;
        struct {
            uint32_t cmd;
            binder_uintptr_t p;
        } __attribute__((packed)) cmd{BC_FREE_BUFFER, ptr};
        sendWriteOnly(&cmd, sizeof(cmd));
    }

    // Perform a synchronous HIDL transaction. If needReply is true, loops until BR_REPLY
    // (handling nested incoming BR_TRANSACTION / refcount commands inline on this thread).
    // Caller MUST call freeBuffer(outReply->data.ptr.buffer) on success.
    bool transact(uint32_t handle, uint32_t code, const HidlParcel& req, binder_transaction_data* outReply,
                  bool needReply) {
        if (fd_ < 0) return false;
        struct {
            uint32_t cmd;
            binder_transaction_data_sg tx;
        } __attribute__((packed)) wbuf{};
        wbuf.cmd = BC_TRANSACTION_SG;
        wbuf.tx.transaction_data.target.handle = handle;
        wbuf.tx.transaction_data.code = code;
        wbuf.tx.transaction_data.flags = TF_ACCEPT_FDS;
        wbuf.tx.transaction_data.data_size = req.dataSize();
        wbuf.tx.transaction_data.offsets_size = req.offsetsSize();
        wbuf.tx.transaction_data.data.ptr.buffer = reinterpret_cast<binder_uintptr_t>(req.data());
        wbuf.tx.transaction_data.data.ptr.offsets = reinterpret_cast<binder_uintptr_t>(req.offsets());
        wbuf.tx.buffers_size = req.buffersSize();

        uint8_t rbuf[4096];
        binder_write_read bwr{};
        bwr.write_buffer = reinterpret_cast<binder_uintptr_t>(&wbuf);
        bwr.write_size = sizeof(wbuf);
        bwr.read_buffer = reinterpret_cast<binder_uintptr_t>(rbuf);
        bwr.read_size = sizeof(rbuf);

        bool txComplete = false;
        bool gotReply = false;
        bool failed = false;
        while (true) {
            if (ioctl(fd_, BINDER_WRITE_READ, &bwr) < 0) {
                if (errno == EINTR) {
                    bwr.write_buffer += bwr.write_consumed;
                    bwr.write_size -= bwr.write_consumed;
                    bwr.write_consumed = 0;
                    bwr.read_consumed = 0;
                    continue;
                }
                return false;
            }
            bwr.write_buffer += bwr.write_consumed;
            bwr.write_size -= bwr.write_consumed;
            bwr.write_consumed = 0;
            const uint8_t* cur = rbuf;
            const uint8_t* end = rbuf + bwr.read_consumed;
            while (cur < end) {
                uint32_t cmd = *reinterpret_cast<const uint32_t*>(cur);
                cur += 4;
                switch (cmd) {
                    case BR_NOOP:
                        break;
                    case BR_TRANSACTION_COMPLETE:
                        txComplete = true;
                        break;
                    case BR_REPLY: {
                        binder_transaction_data tr{};
                        memcpy(&tr, cur, sizeof(tr));
                        cur += sizeof(tr);
                        if (needReply && outReply) *outReply = tr;
                        gotReply = true;
                        break;
                    }
                    case BR_DEAD_REPLY:
                    case BR_FAILED_REPLY:
                    case BR_FROZEN_REPLY:
                        failed = true;
                        break;
                    case BR_ERROR:
                        cur += 4;
                        failed = true;
                        break;
                    case BR_ACQUIRE_RESULT:
                        cur += 4;
                        break;
                    case BR_SPAWN_LOOPER: {
                        std::thread([this]() { looperThread(false); }).detach();
                        break;
                    }
                    case BR_INCREFS:
                    case BR_ACQUIRE:
                    case BR_RELEASE:
                    case BR_DECREFS: {
                        binder_ptr_cookie pc{};
                        memcpy(&pc, cur, sizeof(pc));
                        cur += sizeof(pc);
                        handleRefCmd(cmd, pc);
                        break;
                    }
                    case BR_ATTEMPT_ACQUIRE: {
                        binder_pri_ptr_cookie ppc{};
                        memcpy(&ppc, cur, sizeof(ppc));
                        cur += sizeof(ppc);
                        struct {
                            uint32_t cmd;
                            int32_t success;
                        } __attribute__((packed)) ack{BC_ACQUIRE_RESULT, 1};
                        sendWriteOnly(&ack, sizeof(ack));
                        break;
                    }
                    case BR_TRANSACTION_SEC_CTX: {
                        binder_transaction_data_secctx tr{};
                        memcpy(&tr, cur, sizeof(tr));
                        cur += sizeof(tr);
                        handleIncomingTransaction(tr.transaction_data);
                        break;
                    }
                    case BR_TRANSACTION: {
                        binder_transaction_data tr{};
                        memcpy(&tr, cur, sizeof(tr));
                        cur += sizeof(tr);
                        handleIncomingTransaction(tr);
                        break;
                    }
                    case BR_DEAD_BINDER:
                    case BR_CLEAR_DEATH_NOTIFICATION_DONE:
                    case BR_CLEAR_FREEZE_NOTIFICATION_DONE:
                        cur += sizeof(binder_uintptr_t);
                        break;
                    case BR_FROZEN_BINDER:
                        cur += sizeof(binder_frozen_state_info);
                        break;
                    case BR_FINISHED:
                    case BR_ONEWAY_SPAM_SUSPECT:
                    case BR_TRANSACTION_PENDING_FROZEN:
                        break;
                    default:
                        if (log_) log_("hwbinder: unknown BR cmd 0x%x (txComplete=%d)", cmd, txComplete);
                        failed = true;
                        cur = end;
                        break;
                }
            }
            bwr.read_consumed = 0;
            if (failed) return false;
            if (needReply ? gotReply : txComplete) return true;
        }
    }

    // Active port entry bridging HIDL ISehBluetoothAudioPort@{2.0,2.1} callbacks -> guest AIDL IBluetoothAudioPort.
    struct PortEntry {
        uint64_t cookie = 0;
        AIBinder* guestPort = nullptr;  // strong ref held while active
        bool active = true;
        int vendorStrong = 0;
        HidlAudioConfiguration21 audioCfg{};
        bool hasAudioCfg = false;
    };

    std::shared_ptr<PortEntry> registerPort(AIBinder* guestPort) {
        std::lock_guard<std::mutex> l(portsMu_);
        // Prune any orphaned inactive entries with no vendor refs.
        for (auto it = ports_.begin(); it != ports_.end();) {
            if (!it->second->active && it->second->vendorStrong <= 0) {
                it = ports_.erase(it);
            } else {
                ++it;
            }
        }
        auto entry = std::make_shared<PortEntry>();
        entry->cookie = nextCookie_.fetch_add(1);
        AIBinder_incStrong(guestPort);
        entry->guestPort = guestPort;
        ports_[entry->cookie] = entry;
        return entry;
    }

    void deactivatePort(const std::shared_ptr<PortEntry>& entry) {
        if (!entry) return;
        AIBinder* toDrop = nullptr;
        {
            std::lock_guard<std::mutex> l(portsMu_);
            entry->active = false;
            toDrop = entry->guestPort;
            entry->guestPort = nullptr;
            if (entry->vendorStrong <= 0) {
                ports_.erase(entry->cookie);
            }
        }
        if (toDrop) AIBinder_decStrong(toDrop);
    }

    void setPortAudioConfig(uint64_t cookie, const HidlAudioConfiguration21& cfg) {
        std::lock_guard<std::mutex> l(portsMu_);
        auto it = ports_.find(cookie);
        if (it != ports_.end()) {
            it->second->audioCfg = cfg;
            it->second->hasAudioCfg = true;
        }
    }

    bool getPortAudioConfig(uint64_t cookie, HidlAudioConfiguration21* outCfg) {
        std::lock_guard<std::mutex> l(portsMu_);
        auto it = ports_.find(cookie);
        if (it == ports_.end() || !it->second->hasAudioCfg) return false;
        *outCfg = it->second->audioCfg;
        return true;
    }

    AIBinder* getGuestPortRef(uint64_t cookie) {
        std::lock_guard<std::mutex> l(portsMu_);
        auto it = ports_.find(cookie);
        if (it == ports_.end() || !it->second->active || !it->second->guestPort) return nullptr;
        AIBinder_incStrong(it->second->guestPort);
        return it->second->guestPort;
    }

    // Cached vendor factory handle (re-fetched if HAL process died)
    uint32_t getFactoryHandle(bool* outIsV21 = nullptr) {
        std::lock_guard<std::mutex> l(facMu_);
        if (factoryHandle_ != 0) {
            // Ping to check liveness
            HidlParcel req;
            req.writeInterfaceToken(kHidlBaseFqName);
            binder_transaction_data rep{};
            if (transact(factoryHandle_, kHidlPing, req, &rep, true)) {
                freeBuffer(rep.data.ptr.buffer);
                if (outIsV21) *outIsV21 = factoryIsV21_;
                return factoryHandle_;
            }
            releaseHandle(factoryHandle_);
            factoryHandle_ = 0;
            factoryIsV21_ = false;
        }
        uint32_t h = 0;
        bool isV21 = false;
        if (lookupFactory(&h, &isV21)) {
            factoryHandle_ = h;
            factoryIsV21_ = isV21;
            if (outIsV21) *outIsV21 = isV21;
            if (log_) log_("HIDL Samsung @2.%d factory fetched (handle %u)", isV21 ? 1 : 0, h);
        }
        return factoryHandle_;
    }

    static uint32_t sampleRateToMask(int32_t hz) {
        switch (hz) {
            case 44100: return 0x01;
            case 48000: return 0x02;
            case 88200: return 0x04;
            case 96000: return 0x08;
            case 176400: return 0x10;
            case 192000: return 0x20;
            case 24000: return 0x40;
            case 16000: return 0x80;
            default: return 0;
        }
    }

    static std::vector<int32_t> maskToSampleRates(uint32_t mask) {
        struct Map { uint32_t bit; int32_t hz; };
        static const Map kRates[] = {
            {0x01, 44100}, {0x02, 48000}, {0x04, 88200}, {0x08, 96000},
            {0x10, 176400}, {0x20, 192000}, {0x40, 24000}, {0x80, 16000},
        };
        std::vector<int32_t> out;
        for (const auto& m : kRates) {
            if (mask & m.bit) out.push_back(m.hz);
        }
        return out;
    }

    static std::vector<int8_t> maskToChannelModes(uint8_t mask) {
        std::vector<int8_t> out;
        if (mask & 0x01) out.push_back(1);  // MONO
        if (mask & 0x02) out.push_back(2);  // STEREO
        return out;
    }

    static uint8_t aidlBitsToMask(int8_t bps) {
        switch (bps) {
            case 16: return 0x01;
            case 24: return 0x02;
            case 32: return 0x04;
            default: return 0;
        }
    }

    static std::vector<int8_t> maskToBitsPerSample(uint8_t mask) {
        std::vector<int8_t> out;
        if (mask & 0x01) out.push_back(16);
        if (mask & 0x02) out.push_back(24);
        if (mask & 0x04) out.push_back(32);
        return out;
    }

    static uint8_t aidlStatusToHidl(int32_t aidlStatus) {
        switch (aidlStatus) {
            case 1: return 0;  // SUCCESS
            case 2: return 1;  // UNSUPPORTED_CODEC_CONFIGURATION
            case 3: return 2;  // FAILURE
            case 4: return 3;  // RECONFIGURATION
            default: return 2; // FAILURE
        }
    }

    // Safe read-only diagnostic probe for --hidl-probe
    int runProbe() {
        uint32_t fac = getFactoryHandle();
        if (!fac) {
            printf("hidl-probe: lookup %s/%s failed\n", kSehHidlFactoryFqName, kDefaultInstance);
            return 1;
        }
        printf("hidl-probe: factory handle=%u\n", fac);

        for (uint8_t stype : {uint8_t(kSessionA2dpSw), uint8_t(kSessionHearingAidSw)}) {
            HidlParcel req;
            req.writeInterfaceToken(kSehHidlFactoryFqName);
            req.writeUint8(stype);
            binder_transaction_data rep{};
            if (!transact(fac, kFactorySehGetCapabilities, req, &rep, true)) {
                printf("hidl-probe: getCapabilities(%u) transact failed\n", stype);
                continue;
            }
            HidlReader r(rep, mmapBase_, kMmapSize);
            int32_t hst = -1;
            const void* vecPtr = nullptr;
            const void* elemPtr = nullptr;
            if (r.readInt32(&hst) && hst == 0 && r.readBuffer(sizeof(HidlVec), &vecPtr) && vecPtr) {
                HidlVec hv{};
                memcpy(&hv, vecPtr, sizeof(hv));
                printf("hidl-probe: capabilities(%u) count=%u\n", stype, hv.size);
                if (hv.size > 0 && r.readEmbeddedBuffer(hv.size * sizeof(HidlAudioCapabilities20), &elemPtr) && elemPtr) {
                    const auto* elems = static_cast<const HidlAudioCapabilities20*>(elemPtr);
                    for (uint32_t i = 0; i < hv.size; i++) {
                        if (elems[i].discriminator == 0) {
                            printf("  [%u] pcm: rateMask=0x%x chMask=0x%x bpsMask=0x%x\n", i,
                                   elems[i].pcm.sampleRateMask, elems[i].pcm.channelModeMask,
                                   elems[i].pcm.bitsPerSampleMask);
                        } else {
                            printf("  [%u] disc=%u\n", i, elems[i].discriminator);
                        }
                    }
                }
            } else {
                printf("hidl-probe: getCapabilities(%u) parse failed (hst=%d)\n", stype, hst);
            }
            freeBuffer(rep.data.ptr.buffer);

            HidlParcel oreq;
            oreq.writeInterfaceToken(kSehHidlFactoryFqName);
            oreq.writeUint8(stype);
            binder_transaction_data orep{};
            if (!transact(fac, kFactorySehOpenProvider, oreq, &orep, true)) {
                printf("hidl-probe: openProvider(%u) transact failed\n", stype);
                continue;
            }
            HidlReader orr(orep, mmapBase_, kMmapSize);
            int32_t ohst = -1;
            uint8_t sehSt = 255;
            uint32_t provHandle = 0;
            bool isNull = true;
            if (orr.readInt32(&ohst) && ohst == 0 && orr.readUint8(&sehSt) &&
                orr.readStrongBinder(&provHandle, &isNull)) {
                printf("hidl-probe: openProvider(%u) sehStatus=%u provHandle=%u null=%d\n",
                       stype, sehSt, provHandle, isNull ? 1 : 0);
                if (!isNull && provHandle) {
                    // Ping provider before releasing (acquire first so kernel knows ref, then release)
                    acquireHandle(provHandle);
                    HidlParcel preq;
                    preq.writeInterfaceToken(kHidlBaseFqName);
                    binder_transaction_data prep{};
                    if (transact(provHandle, kHidlPing, preq, &prep, true)) {
                        printf("hidl-probe: provider(%u) ping ok\n", stype);
                        freeBuffer(prep.data.ptr.buffer);
                    }
                    releaseHandle(provHandle);
                }
            } else {
                printf("hidl-probe: openProvider(%u) parse failed (ohst=%d)\n", stype, ohst);
            }
            freeBuffer(orep.data.ptr.buffer);
        }
        return 0;
    }

  private:
    HwBinderBridge() = default;
    static constexpr size_t kMmapSize = 1024 * 1024;

    void sendWriteOnly(const void* data, size_t len) {
        binder_write_read bwr{};
        bwr.write_buffer = reinterpret_cast<binder_uintptr_t>(data);
        bwr.write_size = len;
        while (bwr.write_size > 0) {
            if (ioctl(fd_, BINDER_WRITE_READ, &bwr) < 0) {
                if (errno == EINTR) {
                    bwr.write_buffer += bwr.write_consumed;
                    bwr.write_size -= bwr.write_consumed;
                    bwr.write_consumed = 0;
                    continue;
                }
                break;
            }
            if (bwr.write_consumed == 0) break;
            bwr.write_buffer += bwr.write_consumed;
            bwr.write_size -= bwr.write_consumed;
            bwr.write_consumed = 0;
        }
    }

    void handleRefCmd(uint32_t cmd, const binder_ptr_cookie& pc) {
        if (cmd == BR_INCREFS || cmd == BR_ACQUIRE) {
            if (cmd == BR_ACQUIRE) {
                std::lock_guard<std::mutex> l(portsMu_);
                auto it = ports_.find(static_cast<uint64_t>(pc.cookie));
                if (it != ports_.end()) it->second->vendorStrong++;
            }
            struct {
                uint32_t doneCmd;
                binder_ptr_cookie pc;
            } __attribute__((packed)) ack{
                cmd == BR_INCREFS ? uint32_t(BC_INCREFS_DONE) : uint32_t(BC_ACQUIRE_DONE),
                pc,
            };
            sendWriteOnly(&ack, sizeof(ack));
        } else if (cmd == BR_RELEASE) {
            std::lock_guard<std::mutex> l(portsMu_);
            auto it = ports_.find(static_cast<uint64_t>(pc.cookie));
            if (it != ports_.end()) {
                if (it->second->vendorStrong > 0) it->second->vendorStrong--;
                if (!it->second->active && it->second->vendorStrong <= 0) {
                    ports_.erase(it);
                }
            }
        }
    }

    void sendReply(const HidlParcel& rep, bool oneway) {
        if (oneway) return;
        if (rep.buffersSize() > 0 || rep.offsetsSize() > 0) {
            struct {
                uint32_t cmd;
                binder_transaction_data_sg tx;
            } __attribute__((packed)) w{};
            w.cmd = BC_REPLY_SG;
            w.tx.transaction_data.flags = TF_ACCEPT_FDS;
            w.tx.transaction_data.data_size = rep.dataSize();
            w.tx.transaction_data.offsets_size = rep.offsetsSize();
            w.tx.transaction_data.data.ptr.buffer = reinterpret_cast<binder_uintptr_t>(rep.data());
            w.tx.transaction_data.data.ptr.offsets = reinterpret_cast<binder_uintptr_t>(rep.offsets());
            w.tx.buffers_size = rep.buffersSize();
            sendWriteOnly(&w, sizeof(w));
        } else {
            struct {
                uint32_t cmd;
                binder_transaction_data tx;
            } __attribute__((packed)) w{};
            w.cmd = BC_REPLY;
            w.tx.flags = TF_ACCEPT_FDS;
            w.tx.data_size = rep.dataSize();
            w.tx.data.ptr.buffer = reinterpret_cast<binder_uintptr_t>(rep.data());
            sendWriteOnly(&w, sizeof(w));
        }
    }

    void handleIncomingTransaction(const binder_transaction_data& tr) {
        bool oneway = (tr.flags & TF_ONE_WAY) != 0;
        HidlReader r(tr, mmapBase_, kMmapSize);
        HidlParcel rep;

        switch (tr.code) {
            case kHidlInterfaceChain: {
                rep.writeInt32(0);  // Status::ok()
                auto vHdr = std::make_unique<HidlVec>(HidlVec{0, 3, 0});
                auto arr = std::make_unique<std::vector<HidlString>>(3);
                auto s0 = std::make_unique<std::string>(kSehHidlPortFqName21);
                auto s1 = std::make_unique<std::string>(kSehHidlPortFqName);
                auto s2 = std::make_unique<std::string>(kHidlBaseFqName);
                (*arr)[0] = HidlString{reinterpret_cast<uint64_t>(s0->c_str()), static_cast<uint32_t>(s0->size()), 0};
                (*arr)[1] = HidlString{reinterpret_cast<uint64_t>(s1->c_str()), static_cast<uint32_t>(s1->size()), 0};
                (*arr)[2] = HidlString{reinterpret_cast<uint64_t>(s2->c_str()), static_cast<uint32_t>(s2->size()), 0};
                size_t vIdx = rep.writeBuffer(vHdr.get(), sizeof(HidlVec));
                size_t aIdx = rep.writeBuffer(arr->data(), 3 * sizeof(HidlString), true, vIdx, 0);
                rep.writeBuffer(s0->c_str(), s0->size() + 1, true, aIdx, 0);
                rep.writeBuffer(s1->c_str(), s1->size() + 1, true, aIdx, sizeof(HidlString));
                rep.writeBuffer(s2->c_str(), s2->size() + 1, true, aIdx, 2 * sizeof(HidlString));
                sendReply(rep, oneway);
                freeBuffer(tr.data.ptr.buffer);
                return;
            }
            case kHidlDescriptor: {
                rep.writeInt32(0);
                rep.writeHidlString(kSehHidlPortFqName21);
                sendReply(rep, oneway);
                freeBuffer(tr.data.ptr.buffer);
                return;
            }
            case kHidlGetHashChain: {
                rep.writeInt32(0);
                auto vHdr = std::make_unique<HidlVec>(HidlVec{0, 0, 0});
                rep.writeBuffer(vHdr.get(), sizeof(HidlVec));
                sendReply(rep, oneway);
                freeBuffer(tr.data.ptr.buffer);
                return;
            }
            case kHidlPing:
            case kHidlSyspropsChanged: {
                rep.writeInt32(0);
                sendReply(rep, oneway);
                freeBuffer(tr.data.ptr.buffer);
                return;
            }
            default:
                break;
        }

        const bool isV21Method =
            (tr.code == kPortSehUpdateSinkMetadata || tr.code == kPortSehGetAudioConfiguration);
        if (!(isV21Method ? r.enforceInterface(kSehHidlPortFqName21)
                          : (r.enforceInterface(kSehHidlPortFqName) || r.enforceInterface(kSehHidlPortFqName21)))) {
            freeBuffer(tr.data.ptr.buffer);
            rep.writeInt32(-22);
            sendReply(rep, oneway);
            return;
        }

        AIBinder* guestPort = getGuestPortRef(static_cast<uint64_t>(tr.target.ptr));
        auto forwardVoidPort = [&](transaction_code_t aidlCode, bool withFalseBool = false) {
            if (guestPort) {
                AParcel* areq = nullptr;
                AParcel* arep = nullptr;
                if (AIBinder_prepareTransaction(guestPort, &areq) == STATUS_OK) {
                    if (withFalseBool) AParcel_writeBool(areq, false);
                    if (AIBinder_transact(guestPort, aidlCode, &areq, &arep, 0) == STATUS_OK && arep) {
                        AParcel_delete(arep);
                    }
                }
            }
            rep.writeInt32(0);
        };

        switch (tr.code) {
            case kPortSehStartStream:
                forwardVoidPort(FIRST_CALL_TRANSACTION + 1, true /* isLowLatency = false */);
                break;
            case kPortSehStopStream:
                forwardVoidPort(FIRST_CALL_TRANSACTION + 2);
                break;
            case kPortSehSuspendStream:
                forwardVoidPort(FIRST_CALL_TRANSACTION + 3);
                break;
            case kPortSehGetPresentationPosition: {
                uint8_t sehSt = 2;  // FAILURE
                uint64_t delayNs = 0;
                uint64_t octets = 0;
                HidlTimeSpec ts{0, 0};
                if (guestPort) {
                    AParcel* areq = nullptr;
                    AParcel* arep = nullptr;
                    if (AIBinder_prepareTransaction(guestPort, &areq) == STATUS_OK &&
                        AIBinder_transact(guestPort, FIRST_CALL_TRANSACTION + 0, &areq, &arep, 0) == STATUS_OK &&
                        arep) {
                        AStatus* ast = nullptr;
                        if (AParcel_readStatusHeader(arep, &ast) == STATUS_OK && AStatus_isOk(ast)) {
                            int32_t nonNull = 0, psize = 0, tsNull = 0, tsSize = 0;
                            int64_t aDelay = 0, aOctets = 0, aSec = 0, aNSec = 0;
                            if (AParcel_readInt32(arep, &nonNull) == STATUS_OK && nonNull != 0 &&
                                AParcel_readInt32(arep, &psize) == STATUS_OK && psize >= 24 &&
                                AParcel_readInt64(arep, &aDelay) == STATUS_OK &&
                                AParcel_readInt64(arep, &aOctets) == STATUS_OK &&
                                AParcel_readInt32(arep, &tsNull) == STATUS_OK && tsNull != 0 &&
                                AParcel_readInt32(arep, &tsSize) == STATUS_OK && tsSize >= 20 &&
                                AParcel_readInt64(arep, &aSec) == STATUS_OK &&
                                AParcel_readInt64(arep, &aNSec) == STATUS_OK) {
                                sehSt = 0;
                                delayNs = aDelay >= 0 ? static_cast<uint64_t>(aDelay) : 0;
                                octets = aOctets >= 0 ? static_cast<uint64_t>(aOctets) : 0;
                                ts.tvSec = aSec >= 0 ? static_cast<uint64_t>(aSec) : 0;
                                ts.tvNSec = aNSec >= 0 ? static_cast<uint64_t>(aNSec) : 0;
                            }
                        }
                        if (ast) AStatus_delete(ast);
                        AParcel_delete(arep);
                    }
                }
                rep.writeInt32(0);  // hidlStatus
                rep.writeUint8(sehSt);
                rep.writeUint64(delayNs);
                rep.writeUint64(octets);
                rep.writeBuffer(&ts, sizeof(ts));
                sendReply(rep, oneway);
                if (guestPort) AIBinder_decStrong(guestPort);
                freeBuffer(tr.data.ptr.buffer);
                return;
            }
            case kPortSehUpdateMetadata: {
                const void* vecPtr = nullptr;
                const void* tracksPtr = nullptr;
                HidlVec hv{0, 0, 0};
                if (r.readBuffer(sizeof(HidlVec), &vecPtr) && vecPtr) {
                    memcpy(&hv, vecPtr, sizeof(hv));
                    if (hv.size > 0 && hv.size <= 256) {
                        if (!r.readEmbeddedBuffer(hv.size * sizeof(HidlPlaybackTrackMetadata), &tracksPtr)) {
                            tracksPtr = nullptr;
                            hv.size = 0;
                        }
                    } else {
                        hv.size = 0;
                    }
                }
                if (guestPort && tracksPtr && hv.size > 0) {
                    AParcel* areq = nullptr;
                    AParcel* arep = nullptr;
                    if (AIBinder_prepareTransaction(guestPort, &areq) == STATUS_OK) {
                        AParcel_writeInt32(areq, 1);  // non-null SourceMetadata
                        int32_t smStart = AParcel_getDataPosition(areq);
                        AParcel_writeInt32(areq, 0);
                        AParcel_writeInt32(areq, static_cast<int32_t>(hv.size));
                        const auto* tracks = static_cast<const HidlPlaybackTrackMetadata*>(tracksPtr);
                        for (uint32_t i = 0; i < hv.size; i++) {
                            AParcel_writeInt32(areq, 1);  // non-null PlaybackTrackMetadata
                            AParcel_writeInt32(areq, 16); // size + usage + contentType + gain (v1 layout)
                            AParcel_writeInt32(areq, tracks[i].usage);
                            AParcel_writeInt32(areq, tracks[i].contentType);
                            AParcel_writeFloat(areq, tracks[i].gain);
                        }
                        int32_t smEnd = AParcel_getDataPosition(areq);
                        AParcel_setDataPosition(areq, smStart);
                        AParcel_writeInt32(areq, smEnd - smStart);
                        AParcel_setDataPosition(areq, smEnd);
                        if (AIBinder_transact(guestPort, FIRST_CALL_TRANSACTION + 4, &areq, &arep, 0) == STATUS_OK &&
                            arep) {
                            AParcel_delete(arep);
                        }
                    }
                }
                rep.writeInt32(0);
                break;
            }
            case kPortSehUpdateSinkMetadata: {
                rep.writeInt32(0);
                break;
            }
            case kPortSehGetAudioConfiguration: {
                HidlAudioConfiguration21 cfg{};
                bool found = getPortAudioConfig(static_cast<uint64_t>(tr.target.ptr), &cfg);
                rep.writeInt32(0);                   // hidl Status::ok()
                rep.writeUint8(found ? 0 : 2);       // SehStatus::SUCCESS / FAILURE
                rep.writeBuffer(&cfg, sizeof(cfg));  // 64-byte @2.1::SehAudioConfiguration
                sendReply(rep, oneway);
                if (guestPort) AIBinder_decStrong(guestPort);
                freeBuffer(tr.data.ptr.buffer);
                return;
            }
            default:
                rep.writeInt32(-22);
                break;
        }
        if (guestPort) AIBinder_decStrong(guestPort);
        sendReply(rep, oneway);
        freeBuffer(tr.data.ptr.buffer);
    }

    void looperThread(bool mainLooper) {
        uint32_t enter = mainLooper ? BC_ENTER_LOOPER : BC_REGISTER_LOOPER;
        uint8_t rbuf[4096];
        binder_write_read bwr{};
        bwr.write_buffer = reinterpret_cast<binder_uintptr_t>(&enter);
        bwr.write_size = sizeof(enter);
        bwr.read_buffer = reinterpret_cast<binder_uintptr_t>(rbuf);
        bwr.read_size = sizeof(rbuf);
        while (true) {
            if (ioctl(fd_, BINDER_WRITE_READ, &bwr) < 0) {
                if (errno == EINTR) {
                    bwr.write_size -= bwr.write_consumed;
                    bwr.write_buffer += bwr.write_consumed;
                    bwr.write_consumed = 0;
                    bwr.read_consumed = 0;
                    continue;
                }
                if (log_) log_("hwbinder looper stopped: %d", errno);
                return;
            }
            const uint8_t* cur = rbuf;
            const uint8_t* end = rbuf + bwr.read_consumed;
            while (cur < end) {
                uint32_t cmd = *reinterpret_cast<const uint32_t*>(cur);
                cur += 4;
                switch (cmd) {
                    case BR_NOOP:
                    case BR_FINISHED:
                    case BR_TRANSACTION_COMPLETE:
                    case BR_ONEWAY_SPAM_SUSPECT:
                    case BR_TRANSACTION_PENDING_FROZEN:
                        break;
                    case BR_SPAWN_LOOPER:
                        std::thread([this]() { looperThread(false); }).detach();
                        break;
                    case BR_ERROR:
                    case BR_ACQUIRE_RESULT:
                        cur += 4;
                        break;
                    case BR_INCREFS:
                    case BR_ACQUIRE:
                    case BR_RELEASE:
                    case BR_DECREFS: {
                        binder_ptr_cookie pc{};
                        memcpy(&pc, cur, sizeof(pc));
                        cur += sizeof(pc);
                        handleRefCmd(cmd, pc);
                        break;
                    }
                    case BR_ATTEMPT_ACQUIRE: {
                        binder_pri_ptr_cookie ppc{};
                        memcpy(&ppc, cur, sizeof(ppc));
                        cur += sizeof(ppc);
                        struct {
                            uint32_t cmd;
                            int32_t success;
                        } __attribute__((packed)) ack{BC_ACQUIRE_RESULT, 1};
                        sendWriteOnly(&ack, sizeof(ack));
                        break;
                    }
                    case BR_TRANSACTION_SEC_CTX: {
                        binder_transaction_data_secctx tr{};
                        memcpy(&tr, cur, sizeof(tr));
                        cur += sizeof(tr);
                        handleIncomingTransaction(tr.transaction_data);
                        break;
                    }
                    case BR_TRANSACTION: {
                        binder_transaction_data tr{};
                        memcpy(&tr, cur, sizeof(tr));
                        cur += sizeof(tr);
                        handleIncomingTransaction(tr);
                        break;
                    }
                    case BR_DEAD_BINDER:
                    case BR_CLEAR_DEATH_NOTIFICATION_DONE:
                    case BR_CLEAR_FREEZE_NOTIFICATION_DONE:
                        cur += sizeof(binder_uintptr_t);
                        break;
                    case BR_FROZEN_BINDER:
                        cur += sizeof(binder_frozen_state_info);
                        break;
                    default:
                        break;
                }
            }
            bwr.write_size = 0;
            bwr.write_consumed = 0;
            bwr.read_consumed = 0;
        }
    }

    int fd_ = -1;
    uintptr_t mmapBase_ = 0;
    bool poolStarted_ = false;
    std::mutex initMu_;
    std::mutex facMu_;
    uint32_t factoryHandle_ = 0;
    bool factoryIsV21_ = false;
    std::mutex portsMu_;
    std::map<uint64_t, std::shared_ptr<PortEntry>> ports_;
    std::atomic<uint64_t> nextCookie_{1};
    LogFn log_ = nullptr;
};

}  // namespace hidl_bridge
