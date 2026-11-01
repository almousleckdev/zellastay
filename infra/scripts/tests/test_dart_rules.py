import unittest

from tests.support import WorkspaceTest

LIB = "apps/customer-mobile/lib/"
SCREEN = LIB + "features/booking/presentation/booking_screen.dart"
PUBSPEC = ("apps/customer-mobile/pubspec.yaml", "name: zellastay_mobile\n")


class NativePlatformUiTest(WorkspaceTest):
    def test_features_may_not_import_material_or_cupertino(self):
        for toolkit in ("material", "cupertino"):
            with self.subTest(toolkit=toolkit):
                self.write(SCREEN, f"import 'package:flutter/{toolkit}.dart';\n")
                self.assertViolates("native-platform-ui")

    def test_features_use_widgets_and_platform_ui_abstraction(self):
        self.write(
            SCREEN,
            "import 'package:flutter/widgets.dart';\n"
            "import 'package:zellastay_mobile/core/platform_ui/platform_button.dart';\n",
        )
        self.assertClean()

    def test_adaptive_switcher_may_import_both_toolkits(self):
        self.write(
            LIB + "core/platform_ui/platform_button.dart",
            "import 'package:flutter/cupertino.dart';\nimport 'package:flutter/material.dart';\n",
        )
        self.assertClean()

    def test_ios_implementation_cannot_import_material(self):
        self.write(LIB + "core/platform_ui/ios/ios_button.dart", "import 'package:flutter/material.dart';\n")
        self.assertViolates("native-platform-ui")

    def test_android_implementation_cannot_import_cupertino(self):
        self.write(LIB + "core/platform_ui/android/android_button.dart", "import 'package:flutter/cupertino.dart';\n")
        self.assertViolates("native-platform-ui")

    def test_platform_branching_is_confined_to_platform_ui(self):
        self.write(SCREEN, "final ios = Platform.isIOS;\n")
        self.assertViolates("platform-abstraction")
        self.write(SCREEN, "final p = defaultTargetPlatform;\n")
        self.assertViolates("platform-abstraction")
        self.write(LIB + "core/platform_ui/platform_kind.dart", "final ios = Platform.isIOS;\n")
        (self.root / SCREEN).unlink()
        self.assertClean()


class CleanArchitectureTest(WorkspaceTest):
    DOMAIN = LIB + "features/booking/domain/booking.dart"
    DATA = LIB + "features/booking/data/booking_repository_impl.dart"

    def test_domain_must_be_pure_dart(self):
        for package in ("package:flutter/widgets.dart", "package:flutter_riverpod/flutter_riverpod.dart", "package:dio/dio.dart"):
            with self.subTest(package=package):
                self.write(self.DOMAIN, f"import '{package}';\n")
                self.assertViolates("clean-architecture")

    def test_domain_cannot_import_data_or_presentation(self):
        self.write(self.DOMAIN, "import 'package:zellastay_mobile/features/booking/data/x.dart';\n")
        self.assertViolates("clean-architecture")
        self.write(self.DOMAIN, "import '../presentation/x.dart';\n")
        self.assertViolates("clean-architecture")

    def test_data_cannot_import_presentation(self):
        self.write(self.DATA, "import '../presentation/booking_screen.dart';\n")
        self.assertViolates("clean-architecture")

    def test_presentation_cannot_import_data_directly(self):
        self.write(SCREEN, "import '../data/booking_repository_impl.dart';\n")
        self.assertViolates("clean-architecture")

    def test_di_layer_may_wire_data_and_domain(self):
        self.write(
            LIB + "features/booking/di/booking_providers.dart",
            "import '../data/booking_repository_impl.dart';\nimport '../domain/booking.dart';\n",
        )
        self.assertClean()

    def test_features_cannot_import_each_other(self):
        self.write(SCREEN, "import 'package:zellastay_mobile/features/payment/domain/payment.dart';\n")
        self.assertViolates("no-feature-coupling")

    def test_core_and_shared_cannot_import_features(self):
        self.write(LIB + "shared/widget.dart", "import '../features/booking/domain/booking.dart';\n")
        self.assertViolates("no-feature-coupling")

    def test_domain_may_import_its_own_feature_domain(self):
        self.write(self.DOMAIN, "import 'booking_status.dart';\n")
        self.assertClean()


class DartHygieneTest(WorkspaceTest):
    def test_dynamic_is_forbidden(self):
        self.write(SCREEN, "dynamic value = 1;\n")
        self.assertViolates("no-dynamic")

    def test_network_exceptions_are_translated_only_in_core_network(self):
        self.write(SCREEN, "try {} on DioException catch (e) {}\n")
        self.assertViolates("central-error-handling")
        self.write(SCREEN, "export 'x.dart';\n")
        self.write(LIB + "core/network/error_mapper.dart", "Failure map(DioException e) => throw e;\n")
        self.assertClean()

    def test_http_clients_not_allowed_in_presentation(self):
        self.write(SCREEN, "import 'package:dio/dio.dart';\n")
        self.assertViolates("central-error-handling")

    def test_oversized_dart_files_and_generated_files(self):
        self.write(SCREEN, "final a = 1;\n" * 301)
        self.assertViolates("file-size")
        self.write(SCREEN, "final a = 1;\n")
        self.write(LIB + "features/booking/data/model.g.dart", "dynamic a;\n" * 500)
        self.assertClean()


if __name__ == "__main__":
    unittest.main()
