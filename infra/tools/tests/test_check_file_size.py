import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

TOOL = Path(__file__).resolve().parents[1] / "check-file-size.py"


def load_tool():
    spec = importlib.util.spec_from_file_location("check_file_size", TOOL)
    if spec is None or spec.loader is None:
        raise ImportError(f"cannot load {TOOL}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def write_lines(path, count):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("x\n" * count)


class FileSizeCheckTest(unittest.TestCase):
    def setUp(self):
        self.tool = load_tool()
        self.dir = tempfile.TemporaryDirectory()
        self.root = Path(self.dir.name)
        self.config = {
            "maxLines": 280,
            "roots": ["src"],
            "extensions": [".java", ".ts"],
            "excludeDirs": ["node_modules", "build"],
            "excludeSuffixes": [".g.dart", ".d.ts"],
        }

    def tearDown(self):
        self.dir.cleanup()

    def violations(self):
        return self.tool.find_violations(self.root, self.config)

    def test_file_at_the_limit_passes(self):
        write_lines(self.root / "src/Exact.java", 280)
        self.assertEqual(self.violations(), [])

    def test_file_over_the_limit_is_reported_with_its_size(self):
        write_lines(self.root / "src/Big.java", 281)
        self.assertEqual(self.violations(), [("src/Big.java", 281)])

    def test_blank_lines_and_comments_count(self):
        (self.root / "src").mkdir()
        (self.root / "src/Padded.java").write_text("\n" * 200 + "// c\n" * 81)
        self.assertEqual(self.violations(), [("src/Padded.java", 281)])

    def test_last_line_without_trailing_newline_counts(self):
        (self.root / "src").mkdir()
        (self.root / "src/NoEol.java").write_text("x\n" * 280 + "last")
        self.assertEqual(self.violations(), [("src/NoEol.java", 281)])

    def test_other_extensions_are_ignored(self):
        write_lines(self.root / "src/data.sql", 900)
        self.assertEqual(self.violations(), [])

    def test_excluded_directories_are_ignored(self):
        write_lines(self.root / "src/node_modules/pkg/Big.ts", 900)
        write_lines(self.root / "src/build/Big.java", 900)
        self.assertEqual(self.violations(), [])

    def test_generated_suffixes_are_ignored(self):
        write_lines(self.root / "src/types.d.ts", 900)
        self.assertEqual(self.violations(), [])

    def test_missing_root_is_skipped(self):
        self.config["roots"] = ["src", "client/lib"]
        write_lines(self.root / "src/Small.java", 3)
        self.assertEqual(self.violations(), [])

    def test_violations_are_sorted_by_path(self):
        write_lines(self.root / "src/B.java", 300)
        write_lines(self.root / "src/A.ts", 290)
        self.assertEqual(
            [path for path, _ in self.violations()], ["src/A.ts", "src/B.java"]
        )

    def test_main_reports_failure_and_success_through_exit_code(self):
        config_path = self.root / "config.json"
        config_path.write_text(json.dumps(self.config))
        write_lines(self.root / "src/Big.java", 281)
        self.assertEqual(self.tool.main(["--root", str(self.root), "--config", str(config_path)]), 1)
        (self.root / "src/Big.java").write_text("class Big {}\n")
        self.assertEqual(self.tool.main(["--root", str(self.root), "--config", str(config_path)]), 0)

    def test_repository_configuration_declares_the_adr_limit(self):
        config = json.loads((TOOL.parent / "file-size.json").read_text())
        self.assertEqual(config["maxLines"], 280)
        self.assertIn(".java", config["extensions"])
        self.assertIn(".dart", config["extensions"])


if __name__ == "__main__":
    unittest.main()
