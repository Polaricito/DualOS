#!/usr/bin/env python3
"""What libsec-ril-patch.sh logs about a library that has neither SMS queue sequence (describe_lib)."""
from pathlib import Path
import hashlib
import os
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / 'switcher/assets/libsec-ril-patch.sh').read_text()
CODE = ('readn() {' + SOURCE.split('readn() {', 1)[1].split('restart_ril() {', 1)[0]).replace('/system/bin/grep', 'grep') + 'describe_lib\n'
BB = shutil.which('busybox')
NOP = bytes.fromhex('1f2003d5')
MILLION = bytes.fromhex('08488852e801a072')          # mov w8, #0x4240; movk w8, #0xf, lsl #16
MILLION_W9 = bytes.fromhex('09488852e901a072')
CMP = bytes.fromhex('9f03086b')


@unittest.skipUnless(BB, 'no busybox on this machine')
class RilLibDescription(unittest.TestCase):
    def describe(self, data):
        with tempfile.TemporaryDirectory() as d:
            lib = Path(d) / 'libsec-ril.so'; lib.write_bytes(data)
            e = {**os.environ, 'BB': BB, 'LIB': str(lib), 'LC_ALL': 'C'}
            out = subprocess.run(['sh', '-c', CODE], env=e, check=True, capture_output=True, text=True).stdout.splitlines()
            return [l.replace(str(lib), 'LIB') for l in out]

    def test_only_flag_checks_before_the_compare_are_shown(self):
        other = bytes.fromhex('880640f9' '29054439' '49000036')   # ldr x8,[x20,#8]; ldrb w9,[x9,#0x101]; tbz w9,#0
        data = (NOP * 16
                + other + NOP + MILLION_W9 + CMP + NOP            # 64: a flag check with other registers, shown
                + NOP * 4 + MILLION + CMP + NOP                   # 96: the constant with nothing before it, counted only
                + b'\x00' + MILLION + NOP * 2 + b'\x00\x00\x00'   # not on an instruction boundary
                + bytes.fromhex('08488852') + NOP * 3)            # the first word alone
        self.assertEqual(0, len(data) % 4)
        self.assertEqual([
            f'inbound SMS: LIB is {len(data)} bytes, sha256 {hashlib.sha256(data).hexdigest()}',
            'inbound SMS: flag check before a compare with 1000000 at 80: 880640f929054439490000361f2003d5 | 09488852e901a0729f03086b1f2003d5',
            'inbound SMS: 2 loads of 1000000 looked at, 1 with a flag check before them',
        ], self.describe(data))

    def test_a_library_without_the_constant(self):
        data = NOP * 64
        self.assertEqual([f'inbound SMS: LIB is 256 bytes, sha256 {hashlib.sha256(data).hexdigest()}',
                          'inbound SMS: 0 loads of 1000000 looked at, 0 with a flag check before them'], self.describe(data))

    def test_at_most_eight_places_are_shown(self):
        place = bytes.fromhex('08054439' 'a8010034') + NOP * 2 + MILLION
        lines = self.describe(NOP * 4 + place * 12 + NOP * 4)
        self.assertEqual(8, sum('flag check before a compare' in l for l in lines))
        self.assertEqual('inbound SMS: 12 loads of 1000000 looked at, 8 with a flag check before them', lines[-1])


class RilLibAltMatchPatch(unittest.TestCase):
    """apply() patches any unique cmp wRn, w8 register variant (including A53's w26) and rejects invalid/duplicate hits."""

    def run_apply(self, data):
        snippet = SOURCE.split('ORIG_PAT=', 1)[1].split('\nBB=""', 1)[0] + '\n'
        snippet += SOURCE.split('is_mounted() {', 1)[1].split('\nrestore() {', 1)[0]
        # Prepend function header back onto is_mounted
        snippet = 'ORIG_PAT=' + snippet.replace('    "$BB" grep -Fq " $LIB " /proc/1/mountinfo', 'is_mounted() {\n    "$BB" grep -Fq " $LIB " "$MOUNTINFO"', 1)
        snippet = snippet.replace('/proc/1/mountinfo', '"$MOUNTINFO"')
        snippet = snippet.replace('/system/bin/grep', 'grep')
        snippet = snippet.replace('/system/bin/setprop', 'true')
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            stage = root / 'stage'
            session = stage / 'session'
            lib = root / 'libsec-ril.so'
            mountinfo = root / 'mountinfo'
            lib.write_bytes(data)
            mountinfo.write_text('')
            bb = root / 'busybox'
            bb.write_text(
                '#!/bin/sh\n'
                'cmd=$1; shift\n'
                'if [ "$cmd" = mount ] && [ "$1" = --bind ]; then\n'
                '    printf "1 1 0:0 %s %s rw - tmpfs tmpfs rw\\n" "$2" "$3" >> "$MOUNTINFO"\n'
                '    cp -f "$2" "$3"\n'
                '    exit 0\n'
                'fi\n'
                'exec "$cmd" "$@"\n'
            )
            bb.chmod(0o755)
            head = (
                f'STAGE={stage}\nLIB={lib}\nSESSION={session}\n'
                f'PATCHED={session / "libsec-ril.session.so"}\n'
                f'MARKER={session / "ril_patch.active"}\nBB={bb}\nMOUNTINFO={mountinfo}\n'
            )
            env = {**os.environ, 'LC_ALL': 'C', 'MOUNTINFO': str(mountinfo)}
            res = subprocess.run(['/bin/sh', '-c', head + snippet + '\napply\n'], env=env, capture_output=True, text=True)
            patched_bytes = (session / 'libsec-ril.session.so').read_bytes() if (session / 'libsec-ril.session.so').exists() else None
            return res, patched_bytes

    def test_patches_w28_and_a53_w26(self):
        for name, cmp_hex in (('S23 w28', '9f03086b'), ('A53 w26', '5f03086b'), ('w0', '1f00086b'), ('w31', 'ff03086b')):
            with self.subTest(name):
                ldr_x20 = bytes.fromhex('880640f9')
                ldrb_cbz = bytes.fromhex('08054439' 'a8010034')
                data = NOP * 4 + ldr_x20 + ldrb_cbz + MILLION + bytes.fromhex(cmp_hex) + NOP * 4
                cbz_off = 20 + 4  # 4*4 + len(ldr_x20) + len(ldrb)
                res, patched = self.run_apply(data)
                self.assertEqual(0, res.returncode, res.stdout + res.stderr)
                self.assertIsNotNone(patched)
                self.assertEqual(NOP, patched[cbz_off:cbz_off + 4])
                # Everything except the cbz instruction is preserved.
                expected = data[:cbz_off] + NOP + data[cbz_off + 4:]
                self.assertEqual(expected, patched)

    def test_rejects_non_cmp_w8_and_duplicate_matches(self):
        ldrb_cbz = bytes.fromhex('08054439' 'a8010034')
        # Wrong instruction after 1000000 (not cmp wRn, w8)
        bad = NOP * 4 + bytes.fromhex('880640f9') + ldrb_cbz + MILLION + bytes.fromhex('5f03096b') + NOP * 4
        res_bad, patched_bad = self.run_apply(bad)
        self.assertNotEqual(0, res_bad.returncode)
        self.assertIsNone(patched_bad)
        # Duplicate match (m = 2)
        one = bytes.fromhex('880640f9') + ldrb_cbz + MILLION + bytes.fromhex('5f03086b')
        dup = NOP * 4 + one + NOP * 4 + one + NOP * 4
        res_dup, patched_dup = self.run_apply(dup)
        self.assertNotEqual(0, res_dup.returncode)
        self.assertIsNone(patched_dup)


if __name__ == '__main__':
    unittest.main()
