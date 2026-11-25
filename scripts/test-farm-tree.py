#!/usr/bin/env python3
"""farm_dir / farm_tree / unfarm_tree overlayfs fallback and private mount tests (guest_prepare.sh, guest_google.sh)."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
PREPARE = (ROOT / 'switcher/assets/guest_prepare.sh').read_text()
GOOGLE = (ROOT / 'switcher/assets/guest_google.sh').read_text()

FARM_FUNCS = (
    PREPARE[
        PREPARE.index('\nfarm_dir() {\n') + 1:
        PREPARE.index('\n# Add the missing guest su mount point', PREPARE.index('\nfarm_dir() {\n'))
    ]
)

GOOGLE_FARM = GOOGLE[
    GOOGLE.index('\nfarm_dir() {'):
    GOOGLE.index('\nunfarm() {', GOOGLE.index('\nfarm_dir() {'))
]

INJ_CHECK_SNIPPET = PREPARE[
    PREPARE.index('\nif [ $INJ = 1 ]; then\n    INJ_RO=0\n') + 1:
    PREPARE.index('\n# Display configs for the phone\'s two panels')
]


MOCK_BB_PY = f"""#!{sys.executable}
import os, pathlib, shutil, sys

args = sys.argv[1:]
if not args:
    sys.exit(1)
cmd = args[0]
rest = args[1:]
calls_log = os.environ['TEST_CALLS']
mounts_file = os.environ['TEST_MOUNTS']

def log_call(line):
    with open(calls_log, 'a') as f:
        f.write(line + '\\n')

def read_mounts():
    p = pathlib.Path(mounts_file)
    return p.read_text().splitlines() if p.exists() else []

def write_mounts(lines):
    pathlib.Path(mounts_file).write_text('\\n'.join(lines) + ('\\n' if lines else ''))

if cmd == 'mount':
    log_call('mount ' + ' '.join(rest))
    if os.environ.get('FAIL_BIND_ON') and '--bind' in rest and os.environ['FAIL_BIND_ON'] in ' '.join(rest):
        sys.exit(1)
    if rest and rest[0] == '--make-private':
        sys.exit(0)
    if len(rest) >= 4 and rest[0] == '-t' and rest[1] == 'tmpfs':
        target = rest[-1]
        lines = read_mounts()
        lines.append(f'tmpfs {{target}} tmpfs rw 0 0')
        write_mounts(lines)
        sys.exit(0)
    if len(rest) >= 3 and rest[0] == '-o':
        opts = rest[1]
        target = rest[-1]
        if 'remount' in opts and 'ro' in opts:
            lines = read_mounts()
            out = []
            for l in lines:
                parts = l.split()
                if len(parts) >= 4 and parts[1] == target and parts[3].startswith('rw'):
                    parts[3] = 'ro' + parts[3][2:]
                    out.append(' '.join(parts))
                else:
                    out.append(l)
            write_mounts(out)
        sys.exit(0)
    if len(rest) == 3 and rest[0] == '--bind':
        src, dst = pathlib.Path(rest[1]), pathlib.Path(rest[2])
        if src.is_dir() and not src.is_symlink():
            if dst.is_dir() and not dst.is_symlink():
                for ch in list(dst.iterdir()):
                    if ch.is_symlink() or not ch.is_dir():
                        ch.unlink()
                    else:
                        shutil.rmtree(ch)
            elif dst.exists() or dst.is_symlink():
                dst.unlink()
                dst.mkdir(parents=True, exist_ok=True)
            else:
                dst.mkdir(parents=True, exist_ok=True)
            for ch in src.iterdir():
                target_ch = dst / ch.name
                if ch.is_symlink():
                    os.symlink(os.readlink(ch), target_ch)
                elif ch.is_dir():
                    shutil.copytree(ch, target_ch, symlinks=True)
                else:
                    shutil.copy2(ch, target_ch)
            fstype = 'tmpfs' if 'farm' in src.name or (len(src.parts) >= 2 and src.name == 'farm') else 'ext4'
            lines = read_mounts()
            lines.append(f'bind {{dst}} {{fstype}} rw 0 0')
            write_mounts(lines)
        else:
            dst.parent.mkdir(parents=True, exist_ok=True)
            if dst.is_symlink() or dst.exists():
                dst.unlink()
            shutil.copy2(src, dst)
            lines = read_mounts()
            lines.append(f'bind {{dst}} ext4 ro 0 0')
            write_mounts(lines)
        sys.exit(0)
    sys.exit(0)
elif cmd == 'umount':
    log_call('umount ' + ' '.join(rest))
    target = rest[-1]
    lines = read_mounts()
    for i in range(len(lines) - 1, -1, -1):
        parts = lines[i].split()
        if len(parts) >= 2 and parts[1] == target:
            lines.pop(i)
            break
    write_mounts(lines)
    sys.exit(0)
elif cmd == 'chcon':
    log_call('chcon ' + ' '.join(rest))
    sys.exit(0)
elif cmd == 'xargs' and os.environ.get('NO_XARGS_P') == '1' and '-P' in rest:
    sys.exit(1)
else:
    os.execvp(cmd, [cmd] + rest)
"""


class FarmTreeTest(unittest.TestCase):
    def make_env(self, root):
        bb = root / 'busybox'
        bb.write_text(MOCK_BB_PY)
        bb.chmod(0o755)
        bin_dir = root / 'bin'
        bin_dir.mkdir(exist_ok=True)
        ls_wrap = bin_dir / 'ls'
        ls_wrap.write_text(
            '#!/bin/sh\n'
            'if [ "$1" = "-dZ" ]; then\n'
            '    case "$2" in */hwservicemanager) echo "u:object_r:hwservicemanager_exec:s0 $2" ;; *) echo "u:object_r:system_file:s0 $2" ;; esac\n'
            '    exit 0\n'
            'fi\n'
            'exec /bin/ls "$@"\n'
        )
        ls_wrap.chmod(0o755)
        chcon_wrap = bin_dir / 'chcon'
        chcon_wrap.write_text('#!/bin/sh\nexit 0\n')
        chcon_wrap.chmod(0o755)
        calls = root / 'calls.log'
        mounts = root / 'proc_mounts'
        mounts.write_text('')
        env = {
            **os.environ,
            'PATH': str(bin_dir) + ':' + os.environ['PATH'],
            'BB': str(bb),
            'SAMCAM_SH': '/bin/sh',
            'TEST_CALLS': str(calls),
            'TEST_MOUNTS': str(mounts),
        }
        return bb, calls, mounts, env

    def run_funcs(self, root, extra_sh, env):
        script = FARM_FUNCS.replace('/proc/mounts', '"$TEST_MOUNTS"') + '\n' + extra_sh
        return subprocess.run(['/bin/sh', '-ec', script], env=env, capture_output=True, text=True)

    def test_farm_dir_is_private_and_remounted_ro_in_both_scripts(self):
        for name, fn in (('guest_prepare', FARM_FUNCS), ('guest_google', GOOGLE_FARM)):
            with self.subTest(name), tempfile.TemporaryDirectory() as d:
                root = Path(d)
                _, calls, mounts, env = self.make_env(root)
                tgt = root / 'target'
                tgt.mkdir()
                (tgt / 'existing.txt').write_text('keep')
                work = root / 'work'
                script = fn.replace('/proc/mounts', '"$TEST_MOUNTS"') + f'\nfarm_dir "{tgt}" "{work}" apex/\n'
                subprocess.run(['/bin/sh', '-ec', script], env=env, check=True)
                self.assertEqual('keep', (tgt / 'existing.txt').read_text())
                self.assertTrue((tgt / 'apex').is_dir())
                call_lines = calls.read_text().splitlines()
                self.assertIn(f'mount --make-private {work / "farm"}', call_lines)
                self.assertIn(f'mount --make-private {work / "orig"}', call_lines)
                self.assertIn(f'mount --make-private {tgt}', call_lines)
                self.assertTrue(any('remount,ro' in l for l in call_lines))
                if name == 'guest_prepare':
                    self.assertTrue(any('remount,bind,ro' in l for l in call_lines))

    def test_display_config_farms_only_needed_depth(self):
        for has_dc in (False, True):
            with self.subTest(has_displayconfig_dir=has_dc), tempfile.TemporaryDirectory() as d:
                root = Path(d)
                _, calls, mounts, env = self.make_env(root)
                petc = root / 'product/etc'
                petc.mkdir(parents=True)
                (petc / 'untouched.xml').write_text('host-etc')
                os.symlink('untouched.xml', petc / 'link.xml')
                if has_dc:
                    (petc / 'displayconfig').mkdir()
                    (petc / 'displayconfig/existing_panel.xml').write_text('panel-orig')
                up = root / 'dov/up'
                (up / 'displayconfig').mkdir(parents=True)
                (up / 'displayconfig/display_port_147.xml').write_text('<display/>')
                work = root / 'dov/ft'

                r = self.run_funcs(root, f'farm_tree "{petc}" "{work}" "{up}"', env)
                self.assertEqual(0, r.returncode, r.stderr)
                self.assertEqual('host-etc', (petc / 'untouched.xml').read_text())
                self.assertTrue((petc / 'link.xml').is_symlink())
                self.assertEqual('<display/>', (petc / 'displayconfig/display_port_147.xml').read_text())
                call_text = calls.read_text()
                if has_dc:
                    self.assertIn('panel-orig', (petc / 'displayconfig/existing_panel.xml').read_text())
                    # petc itself was never farmed when displayconfig already existed.
                    self.assertNotIn(f'mount --bind {work / "farm"} {petc}\n', call_text)
                    self.assertIn(f'mount --bind {work / "d_1/farm"} {petc / "displayconfig"}', call_text)
                else:
                    self.assertIn(f'mount --bind {work / "farm"} {petc}\n', call_text)

    def test_system_ext_tree_preserves_siblings_permissions_contexts_and_ro(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            _, calls, mounts, env = self.make_env(root)
            gse = root / 'rootfs/system/system_ext'
            (gse / 'framework').mkdir(parents=True)
            (gse / 'framework/ext.jar').write_text('orig-jar')
            (gse / 'bin').mkdir()
            (gse / 'bin/existing_bin').write_text('bin-data')
            (gse / 'etc/permissions').mkdir(parents=True)
            (gse / 'etc/permissions/orig.xml').write_text('orig-perm')
            (gse / 'lib64').mkdir()
            (gse / 'lib64/libexisting.so').write_text('orig-so')
            (gse / 'untouched_dir').mkdir()
            (gse / 'untouched_dir/stay.txt').write_text('untouched')

            up = root / 'ov/up'
            (up / 'apex').mkdir(parents=True)
            (up / 'framework').mkdir()
            (up / 'framework/oneos-telephony.jar').write_bytes(b'telephony-jar')
            (up / 'framework/imsmanager.jar').write_bytes(b'')
            (up / 'bin').mkdir()
            (up / 'bin/hwservicemanager').write_bytes(b'')
            (up / 'etc/permissions').mkdir(parents=True)
            (up / 'etc/permissions/oneos-telephony-ims.xml').write_text('<permissions/>')
            (up / 'lib64').mkdir()
            (up / 'lib64/libsec-ims.so').write_bytes(b'')
            (up / 'priv-app/OneOSSecIms').mkdir(parents=True)

            work = root / 'ov/ft'
            r = self.run_funcs(root, f'farm_tree "{gse}" "{work}" "{up}"', env)
            self.assertEqual(0, r.returncode, r.stderr)

            self.assertTrue((gse / 'apex').is_dir())
            self.assertTrue((gse / 'priv-app/OneOSSecIms').is_dir())
            self.assertEqual(b'telephony-jar', (gse / 'framework/oneos-telephony.jar').read_bytes())
            self.assertEqual('orig-jar', (gse / 'framework/ext.jar').read_text())
            self.assertEqual(b'', (gse / 'framework/imsmanager.jar').read_bytes())
            self.assertTrue((gse / 'bin/hwservicemanager').exists())
            self.assertEqual('bin-data', (gse / 'bin/existing_bin').read_text())
            self.assertEqual('orig-perm', (gse / 'etc/permissions/orig.xml').read_text())
            self.assertEqual('<permissions/>', (gse / 'etc/permissions/oneos-telephony-ims.xml').read_text())
            self.assertEqual('orig-so', (gse / 'lib64/libexisting.so').read_text())
            self.assertTrue((gse / 'lib64/libsec-ims.so').exists())
            self.assertEqual('untouched', (gse / 'untouched_dir/stay.txt').read_text())

            # untouched_dir was not farmed separately.
            call_text = calls.read_text()
            self.assertNotIn('untouched_dir/farm', call_text)
            chcons = [l for l in call_text.splitlines() if l.startswith('chcon ')]
            self.assertTrue(any('u:object_r:hwservicemanager_exec:s0' in l and 'hwservicemanager' in l for l in chcons), chcons)
            self.assertTrue(any('u:object_r:system_lib_file:s0' in l for l in chcons), chcons)
            # Check mount table reflects ro tmpfs on framework.
            mount_lines = mounts.read_text()
            self.assertIn(f' {gse / "framework"} tmpfs ro', mount_lines)

    def test_large_lib64_binds_all_entries_with_and_without_parallel_xargs(self):
        for no_xargs_p in ('0', '1'):
            with self.subTest(no_xargs_p=no_xargs_p), tempfile.TemporaryDirectory() as d:
                root = Path(d)
                _, calls, _, env = self.make_env(root)
                env['NO_XARGS_P'] = no_xargs_p
                lib64 = root / 'system/lib64'
                lib64.mkdir(parents=True)
                for i in range(55):
                    (lib64 / f'liborig_{i:02d}.so').write_text(f'so-{i}')
                up = root / 'sov/up/lib64'
                up.mkdir(parents=True)
                (up / 'libcam_extra.so').write_bytes(b'')
                r = self.run_funcs(root, f'farm_tree "{lib64}" "{root / "ft"}" "{up}"', env)
                self.assertEqual(0, r.returncode, r.stderr)
                for i in range(55):
                    self.assertEqual(f'so-{i}', (lib64 / f'liborig_{i:02d}.so').read_text())
                self.assertTrue((lib64 / 'libcam_extra.so').exists())

    def test_failure_unfarms_previous_mounts_in_reverse_order(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            _, calls, mounts, env = self.make_env(root)
            tgt = root / 'sysext'
            (tgt / 'aaa').mkdir(parents=True)
            (tgt / 'aaa/f1').write_text('1')
            (tgt / 'bbb').mkdir()
            (tgt / 'bbb/f2').write_text('2')
            up = root / 'up'
            (up / 'aaa').mkdir(parents=True)
            (up / 'aaa/add1').write_text('a')
            (up / 'bbb').mkdir()
            (up / 'bbb/add2').write_text('b')
            env['FAIL_BIND_ON'] = f'{tgt / "bbb/f2"}'
            r = self.run_funcs(root, f'if farm_tree "{tgt}" "{root / "ft"}" "{up}"; then exit 99; else exit 0; fi', env)
            self.assertEqual(0, r.returncode, r.stderr)
            self.assertEqual([], mounts.read_text().splitlines())

    def test_inj_ro_check_accepts_farm_tree_and_rejects_rw(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            gse = root / 'gse'
            stage = root / 'stage'
            (gse / 'framework').mkdir(parents=True)
            stage.mkdir()
            (gse / 'framework/oneos-telephony.jar').write_bytes(b'abc')
            (stage / 'oneos-telephony.jar').write_bytes(b'abc')
            cases = (
                (f'overlay {gse} overlay ro,relatime 0 0\n', '0'),
                (f'tmpfs {gse} tmpfs ro,relatime 0 0\n'
                 f'tmpfs {gse}/framework tmpfs ro,relatime 0 0\n', '0'),
                (f'tmpfs {gse}/framework tmpfs ro,relatime 0 0\n', '0'),
                (f'tmpfs {gse} tmpfs ro,relatime 0 0\n'
                 f'tmpfs {gse}/framework tmpfs rw,relatime 0 0\n', '1'),
                (f'overlay {gse} overlay rw,relatime 0 0\n', '1'),
            )
            for table, expected in cases:
                with self.subTest(table=table.strip()):
                    mfile = root / 'mounts'
                    mfile.write_text(table)
                    snippet = (
                        f'INJ=1; STAGE="{stage}"; GUEST_SYSTEM_EXT="{gse}"\n'
                        + INJ_CHECK_SNIPPET.replace('/proc/mounts', str(mfile))
                        + '\n[ "$INJ" = ' + ('1' if expected == '0' else '0') + ' ]\n'
                    )
                    res = subprocess.run(['/bin/sh', '-c', snippet], capture_output=True, text=True)
                    self.assertEqual(0, res.returncode, res.stdout + res.stderr)


if __name__ == '__main__':
    unittest.main()
