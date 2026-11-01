import unittest

from tests.support import WorkspaceTest

PAGE = "apps/public-web/src/features/booking/ui/booking-page.tsx"


class TypeScriptRulesTest(WorkspaceTest):
    def test_clean_component_passes(self):
        self.write(PAGE, "export function BookingPage(): string {\n  return 'ok';\n}\n")
        self.assertClean()

    def test_any_is_forbidden(self):
        for snippet in ("const a: any = 1;", "const b = x as any;", "type C = Promise<any>;", "let d: any[] = [];"):
            with self.subTest(snippet=snippet):
                self.write(PAGE, snippet + "\n")
                self.assertViolates("no-any")

    def test_any_inside_comments_is_ignored(self):
        self.write(PAGE, "// we avoid : any here\nexport const x = 1;\n")
        self.assertClean()

    def test_ts_suppression_is_forbidden_but_expect_error_is_allowed(self):
        self.write(PAGE, "// @ts-ignore\nconst a = 1;\n")
        self.assertClean()  # comment lines are skipped by design
        self.write(PAGE, "const a = 1; /* x */ // ok\nconst b = 2; // @ts-nocheck\n")
        self.assertViolates("no-ts-suppression")

    def test_raw_html_is_forbidden(self):
        self.write(PAGE, "export const A = () => <div dangerouslySetInnerHTML={{ __html: h }} />;\n")
        self.assertViolates("no-raw-html")

    def test_raw_fetch_is_forbidden_outside_the_api_client(self):
        self.write(PAGE, "export const load = () => fetch('/api/x');\n")
        self.assertViolates("central-api-client")

    def test_fetch_is_allowed_in_the_shared_api_client_and_tests(self):
        self.write("packages/api-client/src/http.ts", "export const send = () => fetch('/x');\n")
        self.write("apps/public-web/src/a.test.ts", "global.fetch = vi.fn();\nfetch('/y');\n")
        self.assertClean()

    def test_importing_another_app_is_forbidden(self):
        self.write(PAGE, "import { x } from '../../../../../vendor-dashboard/src/x';\n")
        self.assertViolates("no-cross-app-import")
        self.write(PAGE, "import { x } from 'apps/admin-dashboard/src/x';\n")
        self.assertViolates("no-cross-app-import")

    def test_importing_shared_packages_is_allowed(self):
        self.write(PAGE, "import { Button } from '@zellastay/ui';\nimport { y } from './local';\n")
        self.assertClean()

    def test_oversized_files_are_flagged(self):
        self.write(PAGE, "export const x = 1;\n" * 301)
        self.assertViolates("file-size")

    def test_generated_and_dependency_directories_are_skipped(self):
        self.write("apps/public-web/node_modules/pkg/index.ts", "const a: any = 1;\n")
        self.write("apps/public-web/src/generated/api.ts", "const a: any = 1;\n" * 400)
        self.assertClean()


class SecretRulesTest(WorkspaceTest):
    def test_cloudinary_secret_names_are_forbidden_in_frontends(self):
        for name in ("CLOUDINARY_API_SECRET", "api_secret", "CLOUDINARY_URL"):
            with self.subTest(name=name):
                self.write("apps/vendor-dashboard/src/media.ts", f"const s = process.env.{name};\n")
                self.assertViolates("no-secrets-in-frontend")

    def test_secret_like_public_variables_are_forbidden(self):
        self.write("apps/public-web/src/c.ts", "const k = process.env.NEXT_PUBLIC_UPLOAD_SECRET;\n")
        self.assertViolates("no-secrets-in-frontend")

    def test_cloud_name_is_not_a_secret(self):
        self.write("apps/public-web/src/c.ts", "const n = process.env.NEXT_PUBLIC_CLOUDINARY_CLOUD_NAME;\n")
        self.assertClean()

    def test_env_files_are_forbidden_but_examples_are_fine(self):
        self.write("apps/public-web/.env.local", "A=1\n")
        self.assertViolates("no-env-files")
        (self.root / "apps/public-web/.env.local").unlink()
        self.write("apps/public-web/.env.example", "A=\n")
        self.assertClean()


if __name__ == "__main__":
    unittest.main()
