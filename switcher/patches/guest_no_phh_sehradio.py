"""Remove TrebleDroid's ISehRadio.setResponseFunction() call in RadioNetworkProxy.setHidl().

On Samsung Exynos HIDL RIL (e.g., Galaxy A53), TrebleDroid's PHH-Radio hook in
RadioNetworkProxy.setHidl() fetches vendor.samsung.hardware.radio@2.2::ISehRadio/slot1
and calls setResponseFunction(p1, p2) with local stubs that are immediately eligible for
GC. This triggers BOTH:
1. A bidirectional hwbinder deadlock between rild (calling interfaceChain() back into
   com.android.phone while inside _hidl_setResponseFunctions) and com.android.phone;
2. A forced rild + cpboot modem restart ~10s later ("RIL Restart for Phone Died") when
   those unreferenced local callback stubs are finalized/GC'd.

OneOS already bypasses rild's FW_READY SMS gate via libsec-ril-patch.sh, so stripping the
ISehRadio block and leaving only super.setHidl(halVersion, radio) eliminates the rild/phone
deadlock and unblocks com.sec.imsservice (ISehChannel/imsd).

Usage: guest_no_phh_sehradio.py [--if-present] <smali root dir>
Prints the smali_* directory (dex file) it changed.
"""
import os
import re
import sys

SUFFIX = os.path.join("com", "android", "internal", "telephony", "RadioNetworkProxy.smali")
HEADER = re.compile(
    r"^\.method public (?:blacklist )?setHidl\(Lcom/android/internal/telephony/HalVersion;Landroid/hardware/radio/V1_4/IRadio;\)V$"
)
SUPER_CALL = "invoke-super {p0, p1, p2}, Lcom/android/internal/telephony/RadioServiceProxy;->setHidl(Lcom/android/internal/telephony/HalVersion;Landroid/hardware/radio/V1_4/IRadio;)V"
SEH_CALL = "Lvendor/samsung/hardware/radio/V2_2/ISehRadio;->setResponseFunction("


def main(root, if_present=False):
    hits = [
        os.path.join(d, f)
        for d, _, fs in os.walk(root)
        for f in fs
        if os.path.join(d, f).endswith(os.sep + SUFFIX)
    ]
    if not hits and if_present:
        print("RadioNetworkProxy.smali: not present, skipping ISehRadio patch", file=sys.stderr)
        return
    if len(hits) != 1:
        sys.exit(f"expected one RadioNetworkProxy.smali, found {len(hits)}")
    path = hits[0]
    lines = open(path).read().split("\n")
    heads = [i for i, l in enumerate(lines) if HEADER.match(l)]
    if len(heads) != 1:
        sys.exit(f"{path}: setHidl() found {len(heads)} times, expected once")
    start = heads[0]
    end = next((j for j in range(start + 1, len(lines)) if lines[j] == ".end method"), None)
    if end is None:
        sys.exit(f"{path}: setHidl() missing .end method")
    body = "\n".join(lines[start : end + 1])
    if SUPER_CALL not in body:
        sys.exit(f"{path}: setHidl() missing expected super.setHidl() call")
    if SEH_CALL not in body:
        if if_present:
            print(f"{path}: setHidl() has no ISehRadio.setResponseFunction(), skipping", file=sys.stderr)
            return
        sys.exit(f"{path}: setHidl() does not contain expected ISehRadio.setResponseFunction() call")

    sig_line = lines[start]
    replacement = [
        sig_line,
        "    .locals 0",
        "",
        "    # OneOS: libsec-ril-patch.sh already bypasses the FW_READY SMS queue in",
        "    # libsec-ril.so. TrebleDroid's ISehRadio.setResponseFunction() passes",
        '    # unreferenced local stubs (GC\'d -> "RIL Restart for Phone Died") and',
        "    # deadlocks rild's single hwbinder thread with com.android.phone.",
        f"    {SUPER_CALL}",
        "",
        "    return-void",
        ".end method",
    ]
    lines[start : end + 1] = replacement
    open(path, "w").write("\n".join(lines))
    print("RadioNetworkProxy.setHidl: stripped TrebleDroid ISehRadio.setResponseFunction", file=sys.stderr)
    print(os.path.relpath(path, root).split(os.sep)[0])


if __name__ == "__main__":
    args = sys.argv[1:]
    if_present = False
    if args and args[0] == "--if-present":
        if_present = True
        args = args[1:]
    if len(args) != 1:
        sys.exit("Usage: guest_no_phh_sehradio.py [--if-present] <smali root dir>")
    main(args[0], if_present=if_present)
