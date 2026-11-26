#!/usr/bin/env python3
"""Unit tests for DualOS 2.0.0 guest runtime fixes (ART profman/odrefresh, SELinux
context/label fixes, safe NFC enablement, safe microSD with One UI encryption, and
Exynos Mali-G68 blur/thermal handover)."""
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'switcher/assets'
PREPARE = (ASSETS / 'guest_prepare.sh').read_text()
START = (ASSETS / 'guest_start.sh').read_text()
SWITCH = (ASSETS / 'oneos_switch.sh').read_text()
RETURN = (ASSETS / 'return_to_oneui.sh').read_text()
PROPS = (ASSETS / 'guest_props.sh').read_text()


def slice_between(text, start_marker, end_marker):
    i = text.index(start_marker)
    j = text.index(end_marker, i)
    return text[i:j]


class ArtProfmanOdrefreshTests(unittest.TestCase):
    def test_stage_orig_jar_runs_before_umount_for_all_three_jars(self):
        for jar, prof in (
            ('services.jar', 'services.jar.prof'),
            ('framework.jar', 'boot-image.prof'),
            ('telephony-common.jar', 'boot-image.prof'),
        ):
            pat = re.compile(
                rf'stage_orig_jar_for_prof "\$ROOTFS/system/framework/{re.escape(jar)}".*?\n'
                rf'\s*\$BB umount -l "\$ROOTFS/system/framework/{re.escape(jar)}"',
                re.S,
            )
            self.assertRegex(PREPARE, pat, f'{jar}: stage_orig_jar_for_prof must run BEFORE umount -l')

    def test_stage_orig_jar_rejects_zero_byte_placeholder_and_copies_real_jar(self):
        fn = slice_between(PREPARE, 'stage_orig_jar_for_prof() {', '\nif [ -f "$STAGE/services_patched.jar" ]')
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            orig_dir = base / 'orig'
            real_jar = base / 'services.jar'
            empty_jar = base / 'empty.jar'
            patched_jar = base / 'services_patched.jar'
            prof = base / 'services.jar.prof'
            real_jar.write_bytes(b'PK\x03\x04real-jar-data')
            empty_jar.write_bytes(b'')
            patched_jar.write_bytes(b'PK\x03\x04patched-jar-data')
            prof.write_bytes(b'prof-data')

            script = f'''
set -eu
BB=busybox
PROF_NEED=1
ORIG_DIR="{orig_dir}"
{fn}
stage_orig_jar_for_prof "{empty_jar}" "{patched_jar}" "{prof}"
[ ! -e "{orig_dir}/empty.jar" ]
stage_orig_jar_for_prof "{real_jar}" "{patched_jar}" "{prof}"
[ -s "{orig_dir}/services.jar" ]
cmp -s "{real_jar}" "{orig_dir}/services.jar"
'''
            res = subprocess.run(['sh', '-c', script], capture_output=True, text=True)
            self.assertEqual(res.returncode, 0, res.stdout + res.stderr)

    def test_odrefresh_passes_options_before_action_and_glob_ignores_adservices(self):
        # Every odrefresh call must pass $ODR_EXTRA before --compile / --force-compile.
        bad = re.findall(r'"\$ODR"\s+--(?:force-)?compile\s+\$ODR_EXTRA', START)
        self.assertEqual([], bad, f'Found odrefresh option after action: {bad}')
        good = re.findall(r'"\$ODR"\s+\$ODR_EXTRA\s+--(?:force-)?compile', START)
        self.assertGreaterEqual(len(good), 5)

        # Verify profman remap skips 0-byte ORIG_JARS and odrefresh receives option before action.
        remap_fn = slice_between(START, '    remap_art_prof() {', '    if [ -s "$ORIG_JARS/framework.jar" ]')
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            bin_ = base / 'bin'; bin_.mkdir()
            orig = base / 'orig'; orig.mkdir()
            prof_dir = base / 'prof'; prof_dir.mkdir()
            adc = base / 'adc/dalvik-cache/arm64'; adc.mkdir(parents=True)
            # Plant a 0-byte services.jar in orig and a real jar as target.
            (orig / 'services.jar').write_bytes(b'')
            real_svc = base / 'real_services.jar'; real_svc.write_bytes(b'PK\x03\x04valid')
            src_prof = base / 'services.jar.prof'; src_prof.write_bytes(b'profile')
            # Fake profman that rejects any 0-byte --apk= argument.
            profman = bin_ / 'profman'
            profman.write_text('''#!/bin/sh
for a in "$@"; do
    case "$a" in
        --apk=*) p=${a#--apk=}; [ -s "$p" ] || { echo "zero-byte apk: $p" >&2; exit 2; } ;;
        --reference-profile-file=*) out=${a#--reference-profile-file=} ;;
    esac
done
if [ -n "${out:-}" ]; then echo "remapped" > "$out"; else echo "Lclass;->m()V"; fi
''')
            profman.chmod(0o755)
            # Fake odrefresh enforcing [OPTION...] ACTION (exit 64 if option follows action).
            odr = bin_ / 'odrefresh'
            odr.write_text('''#!/bin/sh
seen_action=0
for a in "$@"; do
    case "$a" in
        --compile|--force-compile) seen_action=1 ;;
        --*) [ "$seen_action" = 0 ] || { echo "EX_USAGE: option after action: $a" >&2; exit 64; } ;;
    esac
done
[ "$seen_action" = 1 ] || exit 64
exit 80
''')
            odr.chmod(0o755)
            # Verify services.jar odex glob matches system@framework@services.jar@classes.odex, not service-adservices.jar.
            (adc / 'system@framework@service-adservices.jar@classes.odex').write_bytes(b'wrong')
            (adc / 'system@framework@services.jar@classes.odex').write_bytes(b'0123456789abcdef')
            script = f'''
set -eu
PROFMAN="{profman}"
ORIG_JARS="{orig}"
PROF_DIR="{prof_dir}"
chcon() {{ return 0; }}
chown() {{ return 0; }}
{remap_fn.replace('/data/oneos_profman.log', str(base / 'profman.log')).replace('/data/oneos_prof_$$.txt', str(base / 'prof.txt'))}
remap_art_prof "{src_prof}" "{prof_dir}/out.prof" app "{real_svc}"
[ -s "{prof_dir}/out.prof" ]
ODR_EXTRA="--system-server-compiler-filter=speed"
"{odr}" $ODR_EXTRA --force-compile || [ $? -eq 80 ]
SVC_ODEX_SZ=0
for _so in "{base}/adc"/dalvik-cache/arm64/*@services.jar@*.odex; do
    [ -f "$_so" ] && {{ SVC_ODEX_SZ=$(wc -c < "$_so"); break; }}
done
[ "$SVC_ODEX_SZ" -eq 16 ]
'''
            res = subprocess.run(['sh', '-c', script], capture_output=True, text=True)
            self.assertEqual(res.returncode, 0, res.stdout + res.stderr)


class SelinuxFixesTests(unittest.TestCase):
    def test_plat_seapp_contexts_filtered_and_system_server_checked(self):
        self.assertIn('$ROOTFS/system/etc/selinux/plat_seapp_contexts', PREPARE)
        self.assertIn('for t in system_server untrusted_app', PREPARE)
        self.assertIn('restorecon -RF /data/tombstones', RETURN)
        # Ensure we never delete /data/tombstones in return_to_oneui.sh.
        self.assertNotIn('rm -rf /data/tombstones', RETURN)
        # Ensure no dead ONEOS_GUEST_SELINUX_REPORT prop was introduced.
        for name, txt in (('guest_prepare.sh', PREPARE), ('guest_start.sh', START),
                          ('oneos_switch.sh', SWITCH), ('return_to_oneui.sh', RETURN), ('guest_props.sh', PROPS)):
            self.assertNotIn('ONEOS_GUEST_SELINUX_REPORT', txt, name)

    def test_network_stack_mac_permissions_merge_and_deduplication(self):
        blk = slice_between(
            PREPARE,
            'GUEST_MAC="$ROOTFS/system/etc/selinux/plat_mac_permissions.xml"',
            '\nif [ -d "$GUEST_SYSTEM_EXT/priv-app/CellBroadcastService" ]',
        )
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            stage = base / 'stage'; stage.mkdir()
            rootfs = base / 'rootfs'
            host = base / 'host'
            for p in (
                rootfs / 'system/etc/selinux',
                rootfs / 'system_ext/etc/selinux',
                rootfs / 'product/etc/selinux',
                rootfs / 'vendor/etc/selinux',
                rootfs / 'odm/etc/selinux',
                host / 'system/etc/selinux',
                host / 'system_ext/etc/selinux',
                host / 'product/etc/selinux',
            ):
                p.mkdir(parents=True)

            (rootfs / 'system/etc/selinux/plat_mac_permissions.xml').write_text(
                '<?xml version="1.0" encoding="utf-8"?>\n<policy>\n'
                '    <signer signature="AAAA1111">\n        <seinfo value="platform"/>\n    </signer>\n'
                '    <signer signature="DUP2222">\n        <seinfo value="network_stack"/>\n    </signer>\n'
                '</policy>\n'
            )
            (rootfs / 'vendor/etc/selinux/vendor_mac_permissions.xml').write_text(
                '<policy><signer signature="VEND3333"><seinfo value="vendor"/></signer></policy>\n'
            )
            (host / 'system/etc/selinux/plat_mac_permissions.xml').write_text(
                '<policy>\n'
                '    <signer signature="DUP2222">\n        <seinfo value="network_stack"/>\n    </signer>\n'
                '    <signer signature="GOOGLE_NS_4444">\n        <seinfo value="network_stack"/>\n    </signer>\n'
                '    <signer signature="OTHER_PLATFORM_5555">\n        <seinfo value="platform"/>\n    </signer>\n'
                '</policy>\n'
            )
            (host / 'product/etc/selinux/product_mac_permissions.xml').write_text(
                '<policy>\n'
                '    <signer signature="GOOGLE_NS_4444"><seinfo value="network_stack"/></signer>\n'
                '    <signer signature="PRODUCT_NS_6666"><seinfo value="network_stack"/></signer>\n'
                '</policy>\n'
            )

            bb_stub = base / 'bb'
            bb_stub.write_text('''#!/bin/sh
cmd="$1"; shift
case "$cmd" in
    mount|umount|chcon) echo "$cmd $*" >> "$LOG"; exit 0 ;;
    *) exec "$cmd" "$@" ;;
esac
''')
            bb_stub.chmod(0o755)

            adapted = blk.replace(
                '/system/etc/selinux/plat_mac_permissions.xml', f'{host}/system/etc/selinux/plat_mac_permissions.xml'
            ).replace(
                '/system_ext/etc/selinux/system_ext_mac_permissions.xml', f'{host}/system_ext/etc/selinux/system_ext_mac_permissions.xml'
            ).replace(
                '/product/etc/selinux/product_mac_permissions.xml', f'{host}/product/etc/selinux/product_mac_permissions.xml'
            )
            # Restore $ROOTFS prefix on the first assignment that was also matched.
            adapted = adapted.replace(
                f'GUEST_MAC="$ROOTFS{host}/system/etc/selinux/plat_mac_permissions.xml"',
                'GUEST_MAC="$ROOTFS/system/etc/selinux/plat_mac_permissions.xml"',
            )
            script = f'''
set -eu
BB="{bb_stub}"
STAGE="{stage}"
ROOTFS="{rootfs}"
GUEST_SYSTEM_EXT="{rootfs}/system_ext"
GUEST_PRODUCT="{rootfs}/product"
export LOG="{base}/bb.log"
ONEOS_GUEST_SEAPP=1
{adapted}
'''
            res = subprocess.run(['sh', '-c', script], capture_output=True, text=True)
            self.assertEqual(res.returncode, 0, res.stdout + res.stderr)
            merged = (stage / 'oneos_plat_mac_permissions.xml').read_text()
            self.assertEqual(merged.count('GOOGLE_NS_4444'), 1)
            self.assertEqual(merged.count('PRODUCT_NS_6666'), 1)
            self.assertEqual(merged.count('DUP2222'), 1)
            self.assertNotIn('OTHER_PLATFORM_5555', merged)
            self.assertLess(merged.index('GOOGLE_NS_4444'), merged.index('</policy>'))
            self.assertIn('mount --bind', (base / 'bb.log').read_text())


class NfcEnablementTests(unittest.TestCase):
    def test_nfc_service_prefs_seeding_and_symlink_safety(self):
        blk = slice_between(
            PREPARE,
            'if [ "${ONEOS_GUEST_NFC:-1}" = 1 ]; then\n    for _npbase in "$STAGE/data/user_de/0/com.android.nfc"',
            '\n# Apps uninstalled in the guest',
        )
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            stage = base / 'stage'; (stage / 'data').mkdir(parents=True)
            bb_stub = base / 'bb'
            bb_stub.write_text('#!/bin/sh\n[ "$1" = chcon ] && exit 0\nexec "$@"\n')
            bb_stub.chmod(0o755)
            # 1) Fresh stage creates first_boot=false in both user_de and user
            script = f'''
set -eu
BB="{bb_stub}"
STAGE="{stage}"
ONEOS_GUEST_NFC=1
chown() {{ return 0; }}
{blk}
'''
            res = subprocess.run(['sh', '-c', script], capture_output=True, text=True)
            self.assertEqual(res.returncode, 0, res.stdout + res.stderr)
            for sub in ('user_de/0/com.android.nfc', 'user/0/com.android.nfc'):
                txt = (stage / 'data' / sub / 'shared_prefs/NfcServicePrefs.xml').read_text()
                self.assertIn('name="first_boot" value="false"', txt)

            # 2) Existing XML with first_boot=true is rewritten to false; XML missing it gets it injected
            de_xml = stage / 'data/user_de/0/com.android.nfc/shared_prefs/NfcServicePrefs.xml'
            ce_xml = stage / 'data/user/0/com.android.nfc/shared_prefs/NfcServicePrefs.xml'
            de_xml.write_text('<map>\n    <boolean name="first_boot" value="true" />\n</map>\n')
            ce_xml.write_text('<map>\n    <boolean name="nfc_on" value="true" />\n</map>\n')
            res = subprocess.run(['sh', '-c', script], capture_output=True, text=True)
            self.assertEqual(res.returncode, 0, res.stdout + res.stderr)
            self.assertIn('name="first_boot" value="false"', de_xml.read_text())
            self.assertNotIn('value="true"', de_xml.read_text())
            self.assertIn('name="first_boot" value="false"', ce_xml.read_text())
            self.assertIn('name="nfc_on" value="true"', ce_xml.read_text())

            # 3) Symlinked NfcServicePrefs.xml causes exit 3 without touching target
            victim = base / 'host_victim'; victim.write_text('untouched')
            de_xml.unlink(); de_xml.symlink_to(victim)
            res = subprocess.run(['sh', '-c', script], capture_output=True, text=True)
            self.assertEqual(res.returncode, 3)
            self.assertEqual(victim.read_text(), 'untouched')

    def test_nfc_privapp_need_and_hal_handover(self):
        need_blk = slice_between(PREPARE, 'NFC_PRIVAPP_NEED=0\n', '\nnfc_privapp_into()')
        hal_blk = slice_between(
            SWITCH,
            'if [ "${ONEOS_GUEST_NFC:-1}" = "1" ]; then\n    NHAL=',
            '\n# Bluetooth audio on a phone',
        )
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            stage = base / 'stage'; stage.mkdir()
            rootfs = base / 'rootfs'
            (rootfs / 'system/priv-app').mkdir(parents=True)
            (rootfs / 'system_ext/priv-app').mkdir(parents=True)
            (rootfs / 'product/priv-app').mkdir(parents=True)
            (stage / 'nfcservices_payload.img').write_bytes(b'img')
            apex_xml = base / 'apex-info-list.xml'
            # Host without com.android.nfcservices (A53 One UI 8) -> NFC_PRIVAPP_NEED=1
            apex_xml.write_text('<apex-info-list><apex-info moduleName="com.android.art" isActive="true"/></apex-info-list>\n')
            adapted = need_blk.replace('/apex/apex-info-list.xml', str(apex_xml))
            for env_nfc, xml_txt, expected in (
                ('1', '<apex-info-list><apex-info moduleName="com.android.art" isActive="true"/></apex-info-list>\n', '1'),
                ('1', '<apex-info-list><apex-info moduleName="com.android.nfcservices" isActive="true"/></apex-info-list>\n', '0'),
                ('0', '<apex-info-list><apex-info moduleName="com.android.art" isActive="true"/></apex-info-list>\n', '0'),
            ):
                apex_xml.write_text(xml_txt)
                res = subprocess.run(
                    ['sh', '-c', f'STAGE="{stage}"; ROOTFS="{rootfs}"; GUEST_SYSTEM_EXT="{rootfs}/system_ext"; GUEST_PRODUCT="{rootfs}/product"; ONEOS_GUEST_NFC={env_nfc}\n{adapted}\necho "$NFC_PRIVAPP_NEED"'],
                    capture_output=True, text=True, check=True,
                )
                self.assertEqual(res.stdout.strip(), expected)

            # HAL discovery on A53 restarts sec.android.hardware.nfc@1.2-service only
            log = base / 'hal.log'
            script = f'''
set -eu
STAGE="{stage}"
ONEOS_GUEST_NFC=1
sleep() {{ return 0; }}
getprop() {{
    printf '%s\n' \
        '[init.svc.sec.android.hardware.nfc@1.2-service]: [running]' \
        '[init.svc.vendor.audio-hal]: [running]' \
        '[init.svc.vendor.secure_element]: [running]'
}}
setprop() {{ echo "setprop $*" >> "{log}"; }}
{hal_blk}
'''
            res = subprocess.run(['sh', '-c', script], capture_output=True, text=True)
            self.assertEqual(res.returncode, 0, res.stdout + res.stderr)
            self.assertEqual((stage / 'host_nfc_hal_svcs').read_text().strip(), 'sec.android.hardware.nfc@1.2-service')
            hal_ops = log.read_text().splitlines()
            self.assertEqual(hal_ops, [
                'setprop ctl.stop sec.android.hardware.nfc@1.2-service',
                'setprop ctl.start sec.android.hardware.nfc@1.2-service',
            ])
            self.assertIn('host_nfc_hal_svcs', RETURN)


class MicroSdSafetyTests(unittest.TestCase):
    def test_fstab_strips_only_voldmanaged_sdcard_lines(self):
        blk = slice_between(
            PREPARE,
            'SDCARD_MODE="${ONEOS_GUEST_SDCARD:-ro}"',
            '\n# apexd is a HOST daemon:',
        )
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            stage = base / 'stage'; (stage / 'data').mkdir(parents=True)
            rootfs = base / 'rootfs'
            (rootfs / 'vendor/etc').mkdir(parents=True)
            (rootfs / 'odm/etc').mkdir(parents=True)
            fstab = rootfs / 'vendor/etc/fstab.s5e8825'
            fstab.write_text(
                '# Samsung fstab\n'
                '/dev/block/by-name/userdata /data f2fs noatime wait,fileencryption=aes-256-xts\n'
                '/devices/platform/13550000.dwmmc2/mmc_host* auto vfat defaults voldmanaged=sdcard:auto,encryptable=userdata\n'
                '/devices/platform/13200000.usb* auto auto defaults voldmanaged=usb:auto\n'
            )
            bb_stub = base / 'bb'
            bb_stub.write_text('''#!/bin/sh
cmd="$1"; shift
case "$cmd" in
    mount|umount|chcon) echo "$cmd $*" >> "$LOG"; exit 0 ;;
    *) exec "$cmd" "$@" ;;
esac
''')
            bb_stub.chmod(0o755)
            script = f'''
set -eu
BB="{bb_stub}"
STAGE="{stage}"
ROOTFS="{rootfs}"
export LOG="{base}/bb.log"
ONEOS_GUEST_SDCARD=ro
{blk}
'''
            res = subprocess.run(['sh', '-c', script], capture_output=True, text=True)
            self.assertEqual(res.returncode, 0, res.stdout + res.stderr)
            out_txt = (stage / 'oneos_fstab_vendor_fstab.s5e8825').read_text()
            self.assertIn('# OneOS: sdcard hidden from guest vold:', out_txt)
            self.assertIn('/dev/block/by-name/userdata /data f2fs', out_txt)
            self.assertIn('voldmanaged=usb:auto', out_txt)
            # Active (uncommented) voldmanaged=sdcard lines must be zero.
            active_sd = [l for l in out_txt.splitlines() if not l.lstrip().startswith('#') and 'voldmanaged=sdcard' in l.lower()]
            self.assertEqual([], active_sd)
            self.assertEqual((stage / 'data/oneos_sdcard_mode').read_text().strip(), 'ro')
            bb_ops = (base / 'bb.log').read_text()
            self.assertIn('mount --bind', bb_ops)
            self.assertIn('mount --make-private', bb_ops)
            self.assertIn('remount,bind,ro', bb_ops)

    def run_sd_mount_case(self, mode, proc_mounts_lines, meta_ecfs=False, remount_ro_fails=False, make_private_fails=False, symlink_dst=False):
        blk = slice_between(
            START,
            'SD_MODE=ro\n',
            '\n$BB mount -t tmpfs tmpfs /storage',
        )
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            data_dir = base / 'data'; (data_dir / 'media/0').mkdir(parents=True)
            mnt_dir = base / 'mnt/media_rw'; mnt_dir.mkdir(parents=True)
            if mode is not None:
                (data_dir / 'oneos_sdcard_mode').write_text(mode + '\n')
            if symlink_dst:
                (data_dir / 'media/0/ExternalSD').symlink_to(base)
            # Create dummy mountpoint dirs referenced in proc_mounts_lines
            proc_mounts = base / 'proc_mounts'
            rendered = []
            for dev, rel_mp, fstype in proc_mounts_lines:
                mp = base / rel_mp.lstrip('/')
                mp.mkdir(parents=True, exist_ok=True)
                if meta_ecfs:
                    (mp / '.MetaEcfsFile').write_bytes(b'secret')
                rendered.append(f'{dev} {rel_mp} {fstype} rw,relatime 0 0')
            proc_mounts.write_text('\n'.join(rendered) + '\n')

            log = base / 'mount.log'
            bb_stub = base / 'bb'
            bb_stub.write_text(f'''#!/bin/sh
cmd="$1"; shift
case "$cmd" in
    mount)
        echo "mount $*" >> "{log}"
        if [ "{1 if make_private_fails else 0}" = 1 ]; then
            case "$*" in *--make-private*) exit 1 ;; esac
        fi
        if [ "{1 if remount_ro_fails else 0}" = 1 ]; then
            case "$*" in *remount*ro*) exit 1 ;; esac
        fi
        exit 0 ;;
    umount)
        echo "umount $*" >> "{log}"
        exit 0 ;;
    *) exec "$cmd" "$@" ;;
esac
''')
            bb_stub.chmod(0o755)
            adapted = (
                blk
                .replace('/proc/mounts', str(proc_mounts))
                .replace('/data/oneos_sdcard_mode', str(data_dir / 'oneos_sdcard_mode'))
                .replace('/data/media/0/ExternalSD', str(data_dir / 'media/0/ExternalSD'))
                .replace('[ -d "$_smp" ]', f'[ -d "{base}$_smp" ]')
                .replace('[ -e "$_smp/.MetaEcfsFile" ]', f'[ -e "{base}$_smp/.MetaEcfsFile" ]')
            )
            script = f'set -eu\nBB="{bb_stub}"\nchown() {{ return 0; }}\n{adapted}\n'
            res = subprocess.run(['sh', '-c', script], capture_output=True, text=True)
            self.assertEqual(res.returncode, 0, res.stdout + res.stderr)
            ops = log.read_text().splitlines() if log.exists() else []
            return res.stdout, ops

    def test_ro_exfat_mounts_private_and_remounts_ro(self):
        out, ops = self.run_sd_mount_case('ro', [('/dev/block/vold/public:179,1', '/mnt/media_rw/0DE2-359D', 'exfat')])
        self.assertIn('mounted /mnt/media_rw/0DE2-359D (exfat)', out)
        self.assertTrue(any('--bind /mnt/media_rw/0DE2-359D' in o for o in ops))
        self.assertTrue(any('--make-private' in o for o in ops))
        self.assertTrue(any('remount,bind,ro' in o for o in ops))

    def test_ro_fails_closed_if_remount_ro_fails(self):
        out, ops = self.run_sd_mount_case('ro', [('/dev/block/vold/public:179,1', '/mnt/media_rw/0DE2-359D', 'exfat')], remount_ro_fails=True)
        self.assertIn('unmounting for safety', out)
        self.assertTrue(any(o.startswith('umount -l ') for o in ops))

    def test_bind_fails_closed_if_make_private_fails(self):
        out, ops = self.run_sd_mount_case('ro', [('/dev/block/vold/public:179,1', '/mnt/media_rw/0DE2-359D', 'exfat')], make_private_fails=True)
        self.assertIn('could not make', out)
        self.assertIn('unmounting for safety', out)
        self.assertTrue(any(o.startswith('umount -l ') for o in ops))
        self.assertFalse(any('remount' in o for o in ops))

    def test_fuse_mount_is_strictly_rejected(self):
        out, ops = self.run_sd_mount_case('ro', [('/dev/fuse', '/mnt/media_rw/0DE2-359D', 'fuse')])
        self.assertIn('non-kernel fstype fuse', out)
        self.assertEqual([], ops)

    def test_raw_ciphertext_metaecfs_on_exfat_is_refused(self):
        out, ops = self.run_sd_mount_case('ro', [('/dev/block/vold/public:179,1', '/mnt/media_rw/0DE2-359D', 'exfat')], meta_ecfs=True)
        self.assertIn('raw encrypted ciphertext', out)
        self.assertEqual([], ops)

    def test_stacked_ecryptfs_with_metaecfs_downgrades_rw_to_ro(self):
        out, ops = self.run_sd_mount_case('rw', [
            ('/dev/block/vold/public:179,1', '/mnt/media_rw/0DE2-359D', 'sdfat'),
            ('/mnt/media_rw/0DE2-359D', '/mnt/media_rw/0DE2-359D', 'ecryptfs'),
        ], meta_ecfs=True)
        self.assertIn('downgrading rw -> ro', out)
        self.assertTrue(any('remount,bind,ro' in o for o in ops))

    def test_rw_plain_exfat_without_metaecfs_stays_rw(self):
        out, ops = self.run_sd_mount_case('rw', [('/dev/block/vold/public:179,1', '/mnt/media_rw/0DE2-359D', 'exfat')], meta_ecfs=False)
        self.assertIn('(rw)', out)
        self.assertFalse(any('remount' in o for o in ops))

    def test_off_and_symlink_dst_do_not_mount(self):
        out_off, ops_off = self.run_sd_mount_case('off', [('/dev/block/vold/public:179,1', '/mnt/media_rw/0DE2-359D', 'exfat')])
        self.assertIn('disabled (ONEOS_GUEST_SDCARD=off)', out_off)
        self.assertEqual([], ops_off)
        out_sym, ops_sym = self.run_sd_mount_case('ro', [('/dev/block/vold/public:179,1', '/mnt/media_rw/0DE2-359D', 'exfat')], symlink_dst=True)
        self.assertIn('is a symlink; refused', out_sym)
        self.assertEqual([], ops_sym)


class HeatCapAndBlurTests(unittest.TestCase):
    def test_heat_cap_releases_stale_caps_at_hot_start_and_throttles_then_restores_blur(self):
        # Extract the heat_cap inner sh -c body from oneos_switch.sh and unescape it.
        m = re.search(r'\$BB sh -c "\n(\s*: oneos_heat_cap\n.*?)\n" </dev/null', SWITCH, re.S)
        self.assertIsNotNone(m)
        body = m.group(1)
        # Unescape the double-quoted outer shell escapes the same way shell does.
        unesc = re.sub(r'\\(["$`\\])', r'\1', body)
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            stage = base / 'stage'; (stage / 'data').mkdir(parents=True)
            (stage / 'guest_mntns').write_text('mnt:[1]\n')
            sys_root = base / 'sys'
            files = {
                'devices/system/cpu/cpufreq_limit/cpufreq_max_limit': '1200000',
                'devices/system/cpu/cpufreq_limit/over_limit': '1',
                'devices/system/cpu/cpufreq_limit/little_max_limit': '1000000',
                'kernel/gpu/gpu_max_clock': '351',
                'kernel/gpu/gpu_freq_table': '1001 897 650 351',
                'kernel/ems/emstune/req_mode': '2',
                'devices/system/cpu/cpufreq/policy0/cpuinfo_max_freq': '2000000',
                'devices/system/cpu/cpufreq/policy0/scaling_max_freq': '1200000',
                'fs/cgroup/cgroup.procs': '',
            }
            for rel, val in files.items():
                p = sys_root / rel; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(val + '\n')

            temps = base / 'temps.txt'; temps.write_text('46.0\n41.0\n')
            blur_state = base / 'blur_val'; blur_state.write_text('0\n')
            blur_log = base / 'blur.log'
            bb_stub = base / 'bb'
            bb_stub.write_text(f'''#!/bin/sh
# nsenter stub: route dumpsys thermalservice and settings get/put
while [ $# -gt 0 ]; do
    case "$1" in
        /system/bin/dumpsys)
            t=$(head -n 1 "{temps}")
            tail -n +2 "{temps}" > "{temps}.n" && mv "{temps}.n" "{temps}"
            echo "Current temperatures from HAL:"
            echo "Temperature{{mValue=$t, mType=3, mName=SKIN, mStatus=0}}"
            echo "Current cooling devices:"
            exit 0 ;;
        /system/bin/settings)
            shift
            if [ "$1" = get ]; then cat "{blur_state}"
            elif [ "$1" = put ]; then echo "$4" > "{blur_state}"; echo "put $4" >> "{blur_log}"; fi
            exit 0 ;;
    esac
    shift
done
exit 0
''')
            bb_stub.chmod(0o755)
            rendered = (
                unesc
                .replace('$STAGE', str(stage))
                .replace('$BB', str(bb_stub))
                .replace('$HEAT_CAP', '1')
                .replace('$GUEST_THERMAL_MAP', '0')
                .replace('$GUEST_BLUR', '1')
                .replace('${HOST_LOW_POWER:-unknown}', '0')
                .replace('/sys/', str(sys_root) + '/')
            )
            script = f'''
pidof() {{ echo 1234; }}
timeout() {{ shift; "$@"; }}
tick=0
sleep() {{
    tick=$((tick + 1))
    if [ "$tick" -ge 2 ]; then : > "{stage}/data/oneos_rec_stop"; fi
    return 0
}}
{rendered}
'''
            res = subprocess.run(['sh', '-c', script], capture_output=True, text=True)
            self.assertEqual(res.returncode, 0, res.stdout + res.stderr)
            # Stale host limits MUST have been released on the hot tick 1 (46 C >= 45 C).
            self.assertEqual((sys_root / 'kernel/gpu/gpu_max_clock').read_text().strip(), '1001')
            self.assertEqual((sys_root / 'devices/system/cpu/cpufreq_limit/cpufreq_max_limit').read_text().strip(), '-1')
            self.assertEqual((sys_root / 'kernel/ems/emstune/req_mode').read_text().strip(), '0')
            # Blur paused at 46 C and restored at 41 C.
            self.assertEqual(blur_log.read_text().splitlines(), ['put 1', 'put 0'])
            hc_log = (stage / 'heat_cap.log').read_text()
            self.assertIn('stale host cap lifted at hot start', hc_log)
            self.assertIn('window blurs paused', hc_log)
            self.assertIn('window blurs restored', hc_log)

            # Manual user opt-out (disable_window_blurs=1 before hot spell) must NOT be turned back on.
            (stage / 'data/oneos_rec_stop').unlink()
            temps.write_text('46.0\n40.0\n')
            blur_state.write_text('1\n')
            blur_log.unlink()
            res2 = subprocess.run(['sh', '-c', script], capture_output=True, text=True)
            self.assertEqual(res2.returncode, 0, res2.stdout + res2.stderr)
            self.assertFalse(blur_log.exists(), 'Manual disable_window_blurs=1 must not be overwritten')
            self.assertEqual(blur_state.read_text().strip(), '1')

    def test_blur_default_symlink_safe_and_shader_cache_restored(self):
        # Ensure unsafe touch on oneos_blur_default is completely gone.
        self.assertNotIn('touch $STAGE/data/oneos_blur_default', SWITCH)
        self.assertIn('mkdir $STAGE/data/oneos_blur_default', SWITCH)
        self.assertIn('setprop service.sf.cache_dir_available 1', START)
        self.assertIn('service.sf.cache_dir_available', RETURN)

    def test_blur_toggle_round_trip_1_to_0_to_1_and_symlink_protection(self):
        blk = slice_between(
            SWITCH,
            '            if [ -L $STAGE/data/oneos_blur_default ]; then\n',
            '\n            # Cache quotas',
        )
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            stage = base / 'stage'; (stage / 'data').mkdir(parents=True)
            log = base / 'settings.log'
            bb_stub = base / 'bb'
            bb_stub.write_text(f'''#!/bin/sh
while [ $# -gt 0 ]; do
    if [ "$1" = /system/bin/settings ]; then
        shift
        echo "$*" >> "{log}"
        exit 0
    fi
    shift
done
exit 0
''')
            bb_stub.chmod(0o755)
            def run_step(guest_blur):
                res = subprocess.run(
                    ['sh', '-c', f'STAGE="{stage}"\nBB="{bb_stub}"\nPID=123\nGUEST_OPLUS=0\nGUEST_BLUR={guest_blur}\n{blk}\n'],
                    capture_output=True, text=True, check=True,
                )
                return res.stdout

            # Boot 1 (blur=1): enables blurs (0) and creates dir marker
            run_step(1)
            self.assertTrue((stage / 'data/oneos_blur_default').is_dir())
            # Boot 2 (blur=1 again): respects existing marker (no redundant write)
            run_step(1)
            # Boot 3 (blur=0): disables blurs (1) and removes marker so re-enable works
            run_step(0)
            self.assertFalse((stage / 'data/oneos_blur_default').exists())
            # Boot 4 (blur=1): re-enables blurs (0) and recreates dir marker
            run_step(1)
            self.assertTrue((stage / 'data/oneos_blur_default').is_dir())
            self.assertEqual(log.read_text().splitlines(), [
                'put global disable_window_blurs 0',
                'put global disable_window_blurs 1',
                'put global disable_window_blurs 0',
            ])

            # Symlinked marker is never touched or removed in either mode
            victim = base / 'victim_dir'; victim.mkdir()
            (stage / 'data/oneos_blur_default').rmdir()
            (stage / 'data/oneos_blur_default').symlink_to(victim)
            log.unlink()
            out0 = run_step(0)
            out1 = run_step(1)
            self.assertIn('is a link', out0)
            self.assertIn('is a link', out1)
            self.assertTrue(victim.is_dir())
            self.assertFalse(log.exists())


class AAudioMmapFixTests(unittest.TestCase):
    SAMPLE_APM_XML = '''<?xml version='1.0' encoding='utf-8'?>
<audioPolicyConfiguration version="1.0">
    <modules>
        <module halVersion="3.0" name="primary">
            <mixPorts>
                <mixPort flags="AUDIO_OUTPUT_FLAG_PRIMARY" name="primary-out" role="source">
                    <profile channelMasks="AUDIO_CHANNEL_OUT_STEREO" format="AUDIO_FORMAT_PCM_16_BIT" samplingRates="48000" />
                </mixPort>
                <mixPort flags="AUDIO_OUTPUT_FLAG_FAST" name="fast" role="source">
                    <profile channelMasks="AUDIO_CHANNEL_OUT_STEREO" format="AUDIO_FORMAT_PCM_16_BIT" samplingRates="48000" />
                </mixPort>
                <mixPort flags="AUDIO_OUTPUT_FLAG_DIRECT|AUDIO_OUTPUT_FLAG_MMAP_NOIRQ" name="mmap_no_irq_out" role="source">
                    <profile channelMasks="AUDIO_CHANNEL_OUT_STEREO" format="AUDIO_FORMAT_PCM_16_BIT" samplingRates="48000" />
                </mixPort>
                <mixPort flags="AUDIO_INPUT_FLAG_MMAP_NOIRQ" name="mmap_no_irq_in" role="sink">
                    <profile channelMasks="AUDIO_CHANNEL_IN_MONO" format="AUDIO_FORMAT_PCM_16_BIT" samplingRates="48000" />
                </mixPort>
            </mixPorts>
            <devicePorts>
                <devicePort role="sink" tagName="Earpiece" type="AUDIO_DEVICE_OUT_EARPIECE" />
                <devicePort role="sink" tagName="Speaker" type="AUDIO_DEVICE_OUT_SPEAKER" />
                <devicePort role="sink" tagName="OneOS BT A2DP Out" type="AUDIO_DEVICE_OUT_BLUETOOTH_A2DP" />
            </devicePorts>
            <routes>
                <route sink="Earpiece" sources="primary-out,fast,mmap_no_irq_out" type="mix" />
                <route sink="Speaker" sources="primary-out,fast,mmap_no_irq_out" type="mix" />
                <route sink="OneOS BT A2DP Out" sources="primary-out,fast" type="mix" />
                <route sink="mmap_no_irq_in" sources="Built-In Mic" type="mix" />
            </routes>
        </module>
    </modules>
</audioPolicyConfiguration>
'''

    def test_apm_no_mmap_strips_mmap_ports_and_routes_cleanly(self):
        fn = slice_between(PREPARE, '# apm_no_mmap <copy>:', '\napm_copy() {')
        awkgens = ['awk']
        if shutil.which('busybox'):
            awkgens.append('busybox awk')
        for awk_cmd in awkgens:
            with self.subTest(awk=awk_cmd), tempfile.TemporaryDirectory() as d:
                base = Path(d)
                bin_ = base / 'bin'; bin_.mkdir()
                if awk_cmd == 'busybox awk':
                    (bin_ / 'awk').write_text('#!/bin/sh\nexec busybox awk "$@"\n')
                    (bin_ / 'awk').chmod(0o755)
                xml_path = base / 'audio_policy_configuration.xml'
                xml_path.write_text(self.SAMPLE_APM_XML)
                script = f'''
set -eu
PATH="{bin_}:$PATH"
BB=""
APM_MMAP=""
APM_ADDED=0
{fn}
apm_no_mmap "{xml_path}"
echo "ADDED=$APM_ADDED|MMAP=$APM_MMAP"
'''
                res = subprocess.run(['sh', '-c', script], capture_output=True, text=True)
                self.assertEqual(res.returncode, 0, res.stdout + res.stderr)
                self.assertIn('ADDED=1|MMAP=MMAP audio ports stripped', res.stdout)
                updated = xml_path.read_text()
                self.assertNotIn('MMAP_NOIRQ', updated)
                self.assertNotIn('mmap_no_irq_out', updated)
                self.assertNotIn('mmap_no_irq_in', updated)
                root = ET.fromstring(updated)
                mix_names = [m.attrib['name'] for m in root.findall('.//mixPort')]
                self.assertEqual(['primary-out', 'fast'], mix_names)
                routes = {r.attrib['sink']: r.attrib['sources'] for r in root.findall('.//route')}
                self.assertEqual({
                    'Earpiece': 'primary-out,fast',
                    'Speaker': 'primary-out,fast',
                    'OneOS BT A2DP Out': 'primary-out,fast',
                }, routes)

        # Opt-in ONEOS_GUEST_AAUDIO_MMAP=1 preserves MMAP ports untouched.
        with tempfile.TemporaryDirectory() as d:
            xml_path = Path(d) / 'audio_policy_configuration.xml'
            xml_path.write_text(self.SAMPLE_APM_XML)
            res = subprocess.run(
                ['sh', '-c', f'set -eu\nBB=""\nONEOS_GUEST_AAUDIO_MMAP=1\nAPM_MMAP=""\nAPM_ADDED=0\n{fn}\napm_no_mmap "{xml_path}"\necho "ADDED=$APM_ADDED"'],
                capture_output=True, text=True, check=True,
            )
            self.assertIn('ADDED=0', res.stdout)
            self.assertIn('MMAP_NOIRQ', xml_path.read_text())

    def test_guest_props_disables_aaudio_mmap_on_hidl_only_and_return_restores(self):
        blk = slice_between(
            PROPS,
            '    if [ "${ONEOS_GUEST_AAUDIO_MMAP:-0}" != 1 ] && [ -d "${ONEOS_VINTF_DIR:-/vendor/etc/vintf}" ]',
            '\n    # The boot animation on the panel that is on.',
        )
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            props = base / 'props'; props.mkdir()
            vintf = base / 'vintf'; vintf.mkdir()
            (vintf / 'manifest.xml').write_text('<hal><name>android.hardware.audio</name><version>6.0</version></hal>\n')
            host = base / 'host'
            new = base / 'new'
            ovr = base / 'ovr'
            rp = base / 'rp'
            rp.write_text('#!/bin/sh\n[ "$1" = -n ] || exit 2\nprintf "%s" "$3" > "$PROPS/$2"\n')
            rp.chmod(0o755)
            prefix = f'export PROPS="{props}"\nRP="{rp}"\nHOST="{host}"\nNEW="{new}"\nOVR="{ovr}"\nONEOS_VINTF_DIR="{vintf}"\ngetprop() {{ [ -f "$PROPS/$1" ] && cat "$PROPS/$1"; }}\n'

            # Case 1: HIDL host holds 2 -> overridden to 1 and backed up in $OVR.
            (props / 'aaudio.mmap_policy').write_text('2')
            (props / 'aaudio.mmap_exclusive_policy').write_text('2')
            host.write_text('aaudio.mmap_policy\naaudio.mmap_exclusive_policy\n')
            new.write_text('aaudio.mmap_policy=2\n')
            ovr.write_text('')
            res = subprocess.run(['sh', '-c', f'set -eu\n{prefix}{blk}\n'], capture_output=True, text=True, check=True)
            self.assertEqual('1', (props / 'aaudio.mmap_policy').read_text())
            self.assertEqual('1', (props / 'aaudio.mmap_exclusive_policy').read_text())
            self.assertEqual(['aaudio.mmap_policy=2', 'aaudio.mmap_exclusive_policy=2'], ovr.read_text().splitlines())
            self.assertIn('AAudio legacy FastMixer on HIDL audio HAL', res.stdout)

            # Case 2: Host lacks keys -> written to $NEW cleanly.
            host.write_text('other.prop\n')
            new.write_text('aaudio.mmap_policy=2\n')
            ovr.write_text('')
            subprocess.run(['sh', '-c', f'set -eu\n{prefix}{blk}\n'], capture_output=True, text=True, check=True)
            self.assertEqual(['aaudio.mmap_policy=1', 'aaudio.mmap_exclusive_policy=1'], new.read_text().splitlines())
            self.assertEqual('', ovr.read_text())

            # Case 3: AIDL audio core present -> left untouched at 2.
            (props / 'aaudio.mmap_policy').write_text('2')
            (props / 'aaudio.mmap_exclusive_policy').write_text('2')
            (vintf / 'manifest_aidl.xml').write_text('<hal><name>android.hardware.audio.core</name></hal>\n')
            host.write_text('aaudio.mmap_policy\naaudio.mmap_exclusive_policy\n')
            subprocess.run(['sh', '-c', f'set -eu\n{prefix}{blk}\n'], capture_output=True, text=True, check=True)
            self.assertEqual('2', (props / 'aaudio.mmap_policy').read_text())
            self.assertEqual('2', (props / 'aaudio.mmap_exclusive_policy').read_text())

        # Restore safety in return_to_oneui.sh + GAPM gate in guest_prepare.sh.
        ret_loop = slice_between(RETURN, 'for k in ro.control_privapp_permissions', 'do\n    put_back "$k"')
        self.assertIn('aaudio.mmap_policy', ret_loop)
        self.assertIn('aaudio.mmap_exclusive_policy', ret_loop)
        self.assertIn('${ONEOS_GUEST_AAUDIO_MMAP:-0}', PREPARE)


if __name__ == '__main__':
    unittest.main()
