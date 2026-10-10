#!/usr/bin/env python3
"""Summarize JaCoCo production coverage without external Python dependencies."""
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET


def counters(element):
    return {c.attrib['type']: (int(c.attrib['covered']), int(c.attrib['missed']))
            for c in element.findall('counter')}


def ratio(pair):
    covered, missed = pair
    return f'{100 * covered / (covered + missed):.1f}%' if covered + missed else 'n/a'


def summarize(path):
    root = ET.parse(path).getroot()
    total = counters(root)
    if sum(total.get('LINE', (0, 0))) == 0:
        raise ValueError('No production lines in coverage report; check class directories')
    rows = [('All production code', total)]
    for label, prefix in [('Core', 'de/kaipressmar/a52srepair/core/'),
                          ('Android app', 'de/kaipressmar/a52srepair/')]:
        sums = {}
        for package in root.findall('package'):
            name = package.attrib['name']
            if not name.startswith(prefix) or (label == 'Android app' and '/core/' in name):
                continue
            for kind, (covered, missed) in counters(package).items():
                old = sums.get(kind, (0, 0))
                sums[kind] = (old[0] + covered, old[1] + missed)
        if sum(sums.get('LINE', (0, 0))) == 0:
            raise ValueError(f'No production lines for {label}; coverage is incomplete')
        rows.append((label, sums))
    lines = ['## Test coverage', '', '| Scope | Lines | Branches |', '| --- | --- | --- |']
    for label, data in rows:
        lines.append(f'| {label} | {ratio(data.get("LINE", (0, 0)))} | {ratio(data.get("BRANCH", (0, 0)))} |')
    lines += ['', 'Production classes only; generated resources, BuildConfig and view bindings are excluded.',
              'Robolectric coverage verifies Android integration, not physical Bluetooth audio recovery.', '']
    return '\n'.join(lines)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('xml', type=Path)
    parser.add_argument('--markdown', type=Path)
    args = parser.parse_args()
    output = summarize(args.xml)
    if args.markdown:
        args.markdown.parent.mkdir(parents=True, exist_ok=True)
        args.markdown.write_text(output, encoding='utf-8')
    print(output)
