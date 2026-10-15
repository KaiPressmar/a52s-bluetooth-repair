"""Evidence and release-channel regression checks using only the Python standard library."""
import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('repository_checks', ROOT / 'scripts/validate-repository.py')
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)


class RepositoryValidationTest(unittest.TestCase):
    def test_success_claim_requires_actual_faults_and_disjoint_manual_counts(self):
        data = json.loads((ROOT / 'docs/compatibility/results.json').read_text())
        checks.validate(data)
        row = data['records'][1]
        row.update(status='FAULT_RECOVERY_OBSERVED', eligible_calls=20, faults=0,
                   automatic_recoveries=0, manual_corrections=0)
        with self.assertRaises(AssertionError):
            checks.validate(data)
        row.update(faults=10, automatic_recoveries=8, manual_corrections=3)
        with self.assertRaises(AssertionError):
            checks.validate(data)
        row.update(manual_corrections=2)
        checks.validate(data)
        row['notes'] = 'AA:BB:CC:DD:EE:FF'
        with self.assertRaises(AssertionError):
            checks.validate(data)

    def test_release_metadata_keeps_experimental_release_out_of_latest(self):
        workflow = (ROOT / '.github/workflows/release.yml').read_text()
        start = workflow.index('          set -euo pipefail')
        end = workflow.index('\n  release:', start)
        shell = '\n'.join(line[10:] for line in workflow[start:end].splitlines())
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', directory], check=True)
            (root / 'RELEASE_VERSION').write_text('0.24.0\n')
            (root / 'CHANGELOG.md').write_text('## 0.24.0 - Test\n')
            for channel, expected in [('experimental', 'false'), ('stable', 'true')]:
                (root / 'RELEASE_CHANNEL').write_text(channel + '\n')
                output = root / 'outputs'
                output.write_text('')
                import os
                env = dict(os.environ, BRANCH='release/0.24', GITHUB_OUTPUT=str(output))
                subprocess.run(['bash', '-c', shell], cwd=root, env=env, check=True,
                               stdout=subprocess.DEVNULL)
                fields = dict(line.split('=', 1) for line in output.read_text().splitlines())
                self.assertEqual(expected, fields['latest'])
                self.assertEqual(str(channel == 'experimental').lower(), fields['prerelease'])


if __name__ == '__main__':
    unittest.main()
