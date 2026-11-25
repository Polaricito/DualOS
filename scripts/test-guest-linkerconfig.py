#!/usr/bin/env python3
"""Runtime apex_manifest.pb + apex.libraries.config.txt + ld.config.txt patching (guest_prepare.sh)."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
PREPARE = (ROOT / 'switcher/assets/guest_prepare.sh').read_text()
BLOCK = (
    'apex_manifest_libs() {'
    + PREPARE.split('\napex_manifest_libs() {', 1)[1].split('\n# Wi-Fi (run 41):', 1)[0]
)


def encode_varint(v):
    out = bytearray()
    while True:
        b = v & 0x7F
        v >>= 7
        if v:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def pb_str(field_no, text):
    data = text.encode('utf-8')
    return encode_varint((field_no << 3) | 2) + encode_varint(len(data)) + data


def pb_varint(field_no, value):
    return encode_varint((field_no << 3) | 0) + encode_varint(value)


def make_manifest_pb(name, version=360743400, provide=(), require=(), jni=()):
    parts = [pb_str(1, name), pb_varint(2, version)]
    # Include fixed64 (wire type 1) and fixed32 (wire type 5) dummy fields to verify wire skipping.
    parts.append(encode_varint((15 << 3) | 1) + b'\x01\x00\x00\x00\x00\x00\x00\x00')
    parts.append(encode_varint((16 << 3) | 5) + b'\x02\x00\x00\x00')
    for x in provide:
        parts.append(pb_str(7, x))
    for x in require:
        parts.append(pb_str(8, x))
    for x in jni:
        parts.append(pb_str(9, x))
    return b''.join(parts)


MOCK_BB = """#!/bin/sh
cmd="$1"; shift
case "$cmd" in
    mount|umount)
        printf '%s %s\\n' "$cmd" "$*" >> "$TEST_MOUNTS_LOG"
        exit 0
        ;;
    chcon)
        printf '%s\\n' "$*" >> "$TEST_CHCON_LOG"
        exit 0
        ;;
    *)
        exec "$cmd" "$@"
        ;;
esac
"""


class GuestLinkerConfigTest(unittest.TestCase):
    def run_block(self, stage, rootfs, fake_ls_label=None):
        bb = stage / 'busybox'
        bb.write_text(MOCK_BB)
        bb.chmod(0o755)
        mounts_log = stage / 'mounts.log'
        chcon_log = stage / 'chcon.log'
        bin_dir = stage / 'bin'
        bin_dir.mkdir(exist_ok=True)
        path = str(bin_dir) + ':' + os.environ['PATH']
        if fake_ls_label:
            fake_ls = bin_dir / 'ls'
            fake_ls.write_text(f'#!/bin/sh\necho "{fake_ls_label} $2"\n')
            fake_ls.chmod(0o755)
        env = {
            **os.environ,
            'PATH': path,
            'STAGE': str(stage),
            'ROOTFS': str(rootfs),
            'BB': str(bb),
            'TEST_MOUNTS_LOG': str(mounts_log),
            'TEST_CHCON_LOG': str(chcon_log),
        }
        res = subprocess.run(['/bin/sh', '-c', BLOCK], env=env, capture_output=True, text=True, check=True)
        return (
            res.stdout,
            mounts_log.read_text().splitlines() if mounts_log.exists() else [],
            chcon_log.read_text().splitlines() if chcon_log.exists() else [],
        )

    def test_a53_appsearch_libicing_and_libz_are_wired_without_oneos_apex_requires(self):
        with tempfile.TemporaryDirectory() as d:
            tmp = Path(d)
            stage = tmp / 'stage'
            rootfs = tmp / 'rootfs'
            (stage).mkdir()
            (rootfs / 'linkerconfig').mkdir(parents=True)
            (rootfs / 'apex/com.android.appsearch').mkdir(parents=True)

            # A53 LineageOS 23 payload has libicing.so in jniLibs and libz.so in requireNativeLibs;
            # intentionally omit oneos-apex-requires.txt to verify runtime pb parsing works standalone.
            (rootfs / 'apex/com.android.appsearch/apex_manifest.pb').write_bytes(
                make_manifest_pb('com.android.appsearch', require=['libz.so'], jni=['libicing.so'])
            )
            (stage / 'payload_loops').write_text('com.android.appsearch /dev/block/loop59 ext4\n')

            orig_galc = (
                '# host apex.libraries.config.txt\n'
                'jni com_android_conscrypt libjavacrypto.so\n'
                'jni com_android_appsearch libicing_anywhere.so\n'
            )
            galc = rootfs / 'linkerconfig/apex.libraries.config.txt'
            galc.write_text(orig_galc)

            orig_gld = (
                '# host ld.config.txt\n'
                'dir.system = /system/bin\n'
                '[unrestricted]\n'
                'namespace.default.isolated = false\n'
                'namespace.default.links = com_android_art\n'
                'namespace.default.link.com_android_art.shared_libs = libart.so\n'
                'namespace.com_android_art.links = default\n'
                'namespace.com_android_art.link.default.shared_libs = libc.so:libz.so\n'
                'namespace.com_android_appsearch.isolated = true\n'
                'namespace.com_android_appsearch.visible = false\n'
                '[system]\n'
                'namespace.default.links = com_android_art\n'
                'namespace.default.link.com_android_art.shared_libs = libart.so\n'
                'namespace.com_android_art.links = default\n'
                'namespace.com_android_art.link.default.shared_libs = libc.so:libz.so\n'
                'namespace.com_android_appsearch.isolated = true\n'
                '[isolated]\n'
                'namespace.default.isolated = true\n'
                'namespace.system.links = com_android_art\n'
                'namespace.system.link.com_android_art.shared_libs = libart.so\n'
                'namespace.com_android_art.links = system\n'
                'namespace.com_android_art.link.system.shared_libs = libc.so:libz.so\n'
                'namespace.com_android_appsearch.isolated = true\n'
                'namespace.com_android_appsearch.visible = false\n'
            )
            gld = rootfs / 'linkerconfig/ld.config.txt'
            gld.write_text(orig_gld)

            stdout, mounts, chcons = self.run_block(stage, rootfs, fake_ls_label='u:object_r:linkerconfig_file:s0')

            # Host originals were never modified on disk; only guest copies were bind-mounted.
            self.assertEqual(orig_galc, galc.read_text())
            self.assertEqual(orig_gld, gld.read_text())
            self.assertIn(f'mount --bind {stage / "apex.libraries.config.guest.txt"} {galc}', mounts)
            self.assertIn(f'mount --bind {stage / "ld.config.guest.txt"} {gld}', mounts)
            self.assertEqual(
                [f'u:object_r:linkerconfig_file:s0 {stage / "apex.libraries.config.guest.txt"}',
                 f'u:object_r:linkerconfig_file:s0 {stage / "ld.config.guest.txt"}'],
                chcons,
            )

            guest_galc = (stage / 'apex.libraries.config.guest.txt').read_text()
            self.assertIn('jni com_android_appsearch libicing_anywhere.so:libicing.so\n', guest_galc)
            self.assertIn('jni com_android_conscrypt libjavacrypto.so\n', guest_galc)

            guest_gld = (stage / 'ld.config.guest.txt').read_text()
            # Check section-by-section properties.
            sec = None
            by_sec = {}
            for line in guest_gld.splitlines():
                if line.startswith('[') and line.endswith(']'):
                    sec = line[1:-1]
                elif sec and line.startswith('namespace.'):
                    by_sec.setdefault(sec, []).append(line)

            for s, consumer in (('unrestricted', 'default'), ('system', 'default'), ('isolated', 'system')):
                lines = by_sec[s]
                self.assertIn('namespace.com_android_appsearch.visible = true', lines, s)
                self.assertNotIn('namespace.com_android_appsearch.visible = false', lines, s)
                # Consumer links com_android_appsearch with libicing.so
                self.assertTrue(any(l.startswith(f'namespace.{consumer}.links = ') and 'com_android_appsearch' in l for l in lines), (s, lines))
                self.assertIn(f'namespace.{consumer}.link.com_android_appsearch.shared_libs = libicing.so', lines, s)
                # com_android_appsearch links consumer with libz.so
                self.assertTrue(any(l.startswith('namespace.com_android_appsearch.links = ') and consumer in l for l in lines), (s, lines))
                self.assertIn(f'namespace.com_android_appsearch.link.{consumer}.shared_libs = libz.so', lines, s)

            self.assertIn('JNI entries updated', stdout)
            self.assertIn('Guest linker config:', stdout)

    def test_plus_equals_missing_jni_line_and_provide_native_libs(self):
        with tempfile.TemporaryDirectory() as d:
            tmp = Path(d)
            stage = tmp / 'stage'
            rootfs = tmp / 'rootfs'
            stage.mkdir()
            (rootfs / 'linkerconfig').mkdir(parents=True)
            (rootfs / 'apex/com.android.provider').mkdir(parents=True)
            (rootfs / 'apex/com.android.consumer').mkdir(parents=True)
            (rootfs / 'apex/com.android.legacy').mkdir(parents=True)

            # Provider exports libprov.so and has two jniLibs; consumer requires libprov.so.
            # Legacy has no .pb and falls back to oneos-apex-requires.txt.
            (rootfs / 'apex/com.android.provider/apex_manifest.pb').write_bytes(
                make_manifest_pb('com.android.provider', provide=['libprov.so'], jni=['libfoo_jni.so', 'libbar_jni.so'])
            )
            (rootfs / 'apex/com.android.consumer/apex_manifest.pb').write_bytes(
                make_manifest_pb('com.android.consumer', require=['libprov.so'])
            )
            (stage / 'payload_loops').write_text(
                'com.android.provider /dev/block/loop60 ext4\n'
                'com.android.consumer /dev/block/loop61 ext4\n'
                'com.android.legacy /dev/block/loop62 ext4\n'
            )
            (stage / 'oneos-apex-requires.txt').write_text(
                'com.android.legacy com_android_legacy libbase.so\n'
            )

            galc = rootfs / 'linkerconfig/apex.libraries.config.txt'
            galc.write_text('# header only\n')

            gld = rootfs / 'linkerconfig/ld.config.txt'
            gld.write_text(
                '[system]\n'
                'namespace.default.links = \n'
                'namespace.default.links += com_android_provider,com_android_art\n'
                'namespace.default.link.com_android_provider.shared_libs = \n'
                'namespace.default.link.com_android_art.shared_libs = libbase.so\n'
                'namespace.com_android_provider.isolated = true\n'
                'namespace.com_android_consumer.isolated = true\n'
                'namespace.com_android_legacy.isolated = true\n'
            )

            self.run_block(stage, rootfs)

            guest_galc = (stage / 'apex.libraries.config.guest.txt').read_text()
            self.assertIn('jni com_android_provider libfoo_jni.so:libbar_jni.so\n', guest_galc)

            guest_gld = (stage / 'ld.config.guest.txt').read_text().splitlines()
            self.assertIn('namespace.com_android_provider.visible = true', guest_gld)
            # Empty `shared_libs = ` RHS receives libs with space (not leading colon).
            self.assertIn('namespace.default.link.com_android_provider.shared_libs = libfoo_jni.so:libbar_jni.so:libprov.so', guest_gld)
            # Consumer resolves libprov.so directly from com_android_provider.
            self.assertIn('namespace.com_android_consumer.links = com_android_provider', guest_gld)
            self.assertIn('namespace.com_android_consumer.link.com_android_provider.shared_libs = libprov.so', guest_gld)
            # Legacy payload gets libbase.so from com_android_art via oneos-apex-requires.txt.
            self.assertIn('namespace.com_android_legacy.links = com_android_art', guest_gld)
            self.assertIn('namespace.com_android_legacy.link.com_android_art.shared_libs = libbase.so', guest_gld)
            # Unrelated com_android_consumer is NOT polluted by provider's jniLibs.
            self.assertFalse(any('com_android_consumer' in l and 'libfoo_jni.so' in l for l in guest_gld))

    def test_already_covered_configs_are_not_bind_mounted(self):
        with tempfile.TemporaryDirectory() as d:
            tmp = Path(d)
            stage = tmp / 'stage'
            rootfs = tmp / 'rootfs'
            stage.mkdir()
            (rootfs / 'linkerconfig').mkdir(parents=True)
            (rootfs / 'apex/com.android.appsearch').mkdir(parents=True)
            (rootfs / 'apex/com.android.appsearch/apex_manifest.pb').write_bytes(
                make_manifest_pb('com.android.appsearch', require=['libz.so'], jni=['libicing.so'])
            )
            (stage / 'payload_loops').write_text('com.android.appsearch /dev/block/loop59 ext4\n')
            (rootfs / 'linkerconfig/apex.libraries.config.txt').write_text('jni com_android_appsearch libicing.so\n')
            (rootfs / 'linkerconfig/ld.config.txt').write_text(
                '[system]\n'
                'namespace.default.links = com_android_appsearch,com_android_art\n'
                'namespace.default.link.com_android_art.shared_libs = libz.so\n'
                'namespace.default.link.com_android_appsearch.shared_libs = libicing.so\n'
                'namespace.com_android_appsearch.visible = true\n'
                'namespace.com_android_appsearch.links = default\n'
                'namespace.com_android_appsearch.link.default.shared_libs = libz.so\n'
            )

            stdout, mounts, _ = self.run_block(stage, rootfs)
            self.assertNotIn('mount --bind', '\n'.join(mounts))
            self.assertIn("Guest apex.libraries config: One UI's covers the guest's APEX payloads", stdout)
            self.assertIn("Guest linker config: One UI's covers the guest's APEX payloads", stdout)


if __name__ == '__main__':
    unittest.main()
