import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from check_standards import scan  # noqa: E402


class WorkspaceTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.root = Path(self._tmp.name)

    def write(self, relative: str, content: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def rules(self) -> set[str]:
        return {violation.rule for violation in scan(self.root)}

    def assertViolates(self, rule: str) -> None:
        self.assertIn(rule, self.rules())

    def assertClean(self) -> None:
        self.assertEqual([str(v) for v in scan(self.root)], [])
