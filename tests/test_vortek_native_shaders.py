#!/usr/bin/env python3
"""Compile the full pinned shader inspector; capture its native driver inputs."""
import argparse, pathlib, shutil, subprocess, tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--source',required=True);p.add_argument('--patched',action='store_true');p.add_argument('--baseline',action='store_true');a=p.parse_args()
with tempfile.TemporaryDirectory(prefix='rb4dx-native-shaders-') as td:
    work=pathlib.Path(td);server=work/'server';shutil.copytree(a.source,server,ignore=shutil.ignore_patterns('.git'))
    if not a.patched and not a.baseline:
        patch=ROOT/'native/patches/rb4dx-vortek-native-shaders.patch'
        subprocess.run(['git','apply','--check',str(patch)],cwd=server,check=True)
        subprocess.run(['git','apply',str(patch)],cwd=server,check=True)
    subprocess.run(['cc','-std=c11','-Wall','-Wextra','-Wno-sign-compare','-Wno-unused-function','-Wno-discarded-qualifiers','-pthread',
        '-include',str(ROOT/'tests/vortek_shader_host/shader_host.h'),'-I'+str(ROOT/'tests/vortek_shader_host'),'-I'+str(server/'include'),
        str(ROOT/'tests/VortekNativeShadersTest.c'),str(server/'src/shader_inspector.c'),'-o',str(work/'test')],check=True)
    subprocess.run([str(work/'test')]+(['--baseline'] if a.baseline else []),check=True)
