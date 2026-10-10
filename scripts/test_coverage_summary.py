"""Coverage tooling regressions: incomplete reports and coverage drops must fail CI."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('coverage_summary', Path(__file__).with_name('coverage-summary.py'))
coverage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(coverage)


class CoverageSummaryTest(unittest.TestCase):
    def report(self, body):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        path = Path(temp.name) / 'coverage.xml'
        path.write_text('<report>' + body + '</report>')
        return path

    def test_missing_android_or_empty_report_cannot_pass(self):
        for body in ['', '<counter type="LINE" covered="10" missed="0"/>'
                     '<package name="de/kaipressmar/a52srepair/core/repair">'
                     '<counter type="LINE" covered="10" missed="0"/></package>']:
            with self.assertRaises(ValueError):
                coverage.summarize(self.report(body))

    def test_thresholds_enforce_each_module_and_both_counter_types(self):
        passing = [('Core', {'LINE': (95, 5), 'BRANCH': (85, 15)}),
                   ('Android app', {'LINE': (75, 25), 'BRANCH': (60, 40)})]
        coverage.verify(passing)
        for scope, kind in [('Core', 'LINE'), ('Core', 'BRANCH'),
                            ('Android app', 'LINE'), ('Android app', 'BRANCH')]:
            rows = [(name, {key: tuple(value) for key, value in data.items()}) for name, data in passing]
            for name, data in rows:
                if name == scope:
                    covered, missed = data[kind]
                    data[kind] = (covered - 1, missed + 1)
            with self.assertRaises(ValueError):
                coverage.verify(rows)

    def test_zero_branches_cannot_be_mistaken_for_complete_coverage(self):
        with self.assertRaises(ValueError):
            coverage.verify([('Core', {'LINE': (100, 0)})])


if __name__ == '__main__':
    unittest.main()
