#!/usr/bin/env python3
"""Run real pinned swapchain/handler C with captured Vulkan driver commands."""
import argparse, os, pathlib, re, shutil, subprocess, tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser()
p.add_argument('--source', default=os.environ.get('VORTEK_SERVER_SOURCE'))
p.add_argument('--patched', action='store_true')
p.add_argument('--baseline', action='store_true')
p.add_argument('--layout-only', action='store_true')
a=p.parse_args()
if not a.source:
    p.error('--source (or VORTEK_SERVER_SOURCE) must point to pinned Vortek sources')
source=pathlib.Path(a.source).resolve()
with tempfile.TemporaryDirectory(prefix='rb4dx-foreign-sync-') as td:
    work=pathlib.Path(td); server=work/'server'
    shutil.copytree(source,server,ignore=shutil.ignore_patterns('.git'))
    if not a.patched:
        patches=['rb4dx-vortek-frame-index.patch']
        if not a.baseline: patches+=['rb4dx-vortek-foreign-sync.patch']
        for patch in patches:
            subprocess.run(['git','apply','--check',str(ROOT/'native/patches'/patch)],cwd=server,check=True)
            subprocess.run(['git','apply',str(ROOT/'native/patches'/patch)],cwd=server,check=True)
    handlers=(server/'src/request_handler.c').read_text()
    match=re.search(r'void vt_handle_vkQueuePresentKHR\(VkContext\* context\) \{.*?\n\}',handlers,re.S)
    assert match, 'production present handler not found'
    (work/'present_handler_under_test.c').write_text(match.group())
    layout=''
    if (server/'include/rb4dx_present_layout.h').exists():
        layout='#include \"rb4dx_present_layout.h\"\n'
    for name in ['vkCmdPipelineBarrier','vkCmdPipelineBarrier2']:
        m=re.search(r'void vt_handle_'+name+r'\(VkContext\* context\) \{.*?\n\}',handlers,re.S)
        assert m
        layout+=m.group()+'\n'
    (work/'layout_handlers_under_test.c').write_text(layout)
    cmd=['cc','-std=c11','-Wall','-Wextra','-Werror','-Wno-sign-compare',
      '-include',str(ROOT/'tests/vortek_host/swapchain_host.h'),
      '-I'+str(ROOT/'tests/vortek_host'),'-I'+str(server/'include'),'-I'+str(work),
    ]
    if not a.layout_only:
        subprocess.run(cmd+[str(ROOT/'tests/VortekForeignSyncTest.c'),str(server/'src/xwindow_swapchain.c'),'-o',str(work/'test')],check=True)
        subprocess.run([str(work/'test')],check=True)
    subprocess.run(cmd+[str(ROOT/'tests/VortekPresentLayoutTest.c'),'-o',str(work/'layouts')],check=True)
    subprocess.run([str(work/'layouts')],check=True)
