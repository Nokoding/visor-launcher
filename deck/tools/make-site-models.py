#!/usr/bin/env python3
"""Writes site/models-dualsense.json and site/models-xbox.json from the OBJ models the app ships (deck/assets/models),
so the website shows the same shapes. Usage: python3 deck/tools/make-site-models.py"""
import json, os, re
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
for key, name in (('dualsense', 'dualsense'), ('xbox', 'xbox-one')):
    v, t, b, cur = [], [], [], -1
    for line in open(os.path.join(ROOT, 'deck', 'assets', 'models', name + '.obj'), encoding='utf-8'):
        p = line.split()
        if not p or p[0] == '#':
            continue
        if p[0] == 'v':
            v += [float(x) for x in p[1:4]]
        elif p[0] == 'g':
            cur = int(p[1][1:]) if re.fullmatch(r'b\d+', p[1]) else -1
        elif p[0] == 'f':
            t += [int(x) - 1 for x in p[1:4]]
            b.append(cur)
    out = os.path.join(ROOT, 'site', 'models-%s.json' % key)
    json.dump({'v': [round(x, 2) for x in v], 't': t, 'b': b}, open(out, 'w'), separators=(',', ':'))
    print('wrote', out, len(t) // 3, 'triangles')
