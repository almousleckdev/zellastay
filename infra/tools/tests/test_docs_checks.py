import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from docs_checks import attribution, links, references  # noqa: E402

TRAILER = "Co-" + "Authored-By"


class DocsCheckCase(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.root = Path(self.dir.name)

    def tearDown(self):
        self.dir.cleanup()

    def write(self, name, text):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)


class LinksTest(DocsCheckCase):
    def test_accepts_existing_relative_link_and_ignores_external(self):
        self.write("a.md", "[b](docs/b.md#top) [x](https://example.com) [m](mailto:a@b.c)")
        self.write("docs/b.md", "b")
        self.assertEqual(links.check(self.root), [])

    def test_reports_a_broken_link(self):
        self.write("a.md", "[gone](missing.md)")
        self.assertEqual(links.check(self.root), ["broken link in a.md: missing.md"])

    def test_skips_build_directories(self):
        self.write("build/x.md", "[gone](missing.md)")
        self.assertEqual(links.check(self.root), [])


class ReferencesTest(DocsCheckCase):
    def register(self, ods="| OD-1 |", adrs="| ADR-1 |"):
        self.write("docs/OPEN_DECISIONS.md", ods)
        self.write("docs/DECISIONS.md", adrs)

    def test_accepts_defined_references(self):
        self.register(ods="| OD-6a |", adrs="| ADR-20 |")
        self.write("docs/x.md", "see OD-6a and ADR-20")
        self.assertEqual(references.check(self.root), [])

    def test_reports_undefined_references(self):
        self.register()
        self.write("docs/x.md", "see OD-9 and ADR-99")
        self.assertEqual(references.check(self.root), ["undefined open decision OD-9", "undefined ADR ADR-99"])


class AttributionTest(DocsCheckCase):
    def test_flags_attribution_in_text_files(self):
        self.write("src/A.java", "// " + TRAILER + ": someone")
        errors = attribution.check(self.root)
        self.assertEqual(len(errors), 1)
        self.assertIn("src/A.java", errors[0])

    def test_ignores_exempt_files_and_binary_suffixes(self):
        self.write("CLAUDE.md", TRAILER)
        self.write("img.png", TRAILER)
        self.assertEqual(attribution.check(self.root), [])

    def test_clean_tree_passes(self):
        self.write("README.md", "hello")
        self.assertEqual(attribution.check(self.root), [])


if __name__ == "__main__":
    unittest.main()
