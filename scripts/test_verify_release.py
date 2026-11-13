"""Regression tests for malformed or misaligned native artifact rejection."""

import struct
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile, ZipInfo

from verify_release import elf_alignment, inspect_archive


def elf(alignment=16_384):
    data = bytearray(128)
    data[:6] = b"\x7fELF\x02\x01"
    struct.pack_into("<Q", data, 32, 64)
    struct.pack_into("<HH", data, 54, 56, 1)
    struct.pack_into("<I", data, 64, 1)
    struct.pack_into("<Q", data, 112, alignment)
    return data


class NativeAlignmentTest(unittest.TestCase):
    def test_32bit_program_header_layout_is_parsed_separately(self):
        data = bytearray(96)
        data[:6] = b"\x7fELF\x01\x01"
        struct.pack_into("<I", data, 28, 52)
        struct.pack_into("<HH", data, 42, 32, 1)
        struct.pack_into("<I", data, 52, 1)
        struct.pack_into("<I", data, 80, 4_096)
        self.assertEqual(elf_alignment(data), {"bits": 32, "load_alignments": [4_096]})

    def test_accepts_16k_and_64k_load_alignment(self):
        for alignment in (16_384, 65_536):
            self.assertEqual(elf_alignment(elf(alignment))["load_alignments"], [alignment])

    def test_rejects_4k_only_64bit_binary(self):
        with self.assertRaisesRegex(ValueError, "below 16 KB"):
            elf_alignment(elf(4_096))

    def test_rejects_truncated_or_invalid_headers(self):
        for data in (b"", b"not an ELF", elf()[:80]):
            with self.assertRaises(ValueError):
                elf_alignment(data)

    def test_rejects_invalid_power_of_two_and_missing_load_segment(self):
        with self.assertRaisesRegex(ValueError, "Invalid ELF segment alignment"):
            elf_alignment(elf(20_000))
        data = elf()
        struct.pack_into("<I", data, 64, 0)
        with self.assertRaisesRegex(ValueError, "no loadable"):
            elf_alignment(data)

    def test_rejects_incongruent_or_out_of_bounds_segment(self):
        data = elf()
        struct.pack_into("<Q", data, 80, 1)
        with self.assertRaisesRegex(ValueError, "incongruent"):
            elf_alignment(data)
        data = elf()
        struct.pack_into("<Q", data, 96, 1_024)
        with self.assertRaisesRegex(ValueError, "exceeds file"):
            elf_alignment(data)


class NativeArchiveTest(unittest.TestCase):
    def test_unaligned_uncompressed_apk_native_entry_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.apk"
            with ZipFile(path, "w") as archive:
                archive.writestr("lib/arm64-v8a/test.so", elf())
            with self.assertRaisesRegex(ValueError, "not 16 KB ZIP aligned"):
                inspect_archive(path, True)

    def test_aligned_uncompressed_apk_entry_is_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.apk"
            info = ZipInfo("lib/arm64-v8a/test.so")
            padding = 16_384 - 30 - len(info.filename.encode()) - 4
            info.extra = struct.pack("<HH", 0xCAFE, padding) + bytes(padding)
            with ZipFile(path, "w") as archive:
                archive.writestr(info, elf())
            result = inspect_archive(path, True)
            self.assertEqual(result["native"][info.filename]["zip_data_offset"], 16_384)

    def test_benchmark_code_in_runtime_dex_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.apk"
            with ZipFile(path, "w") as archive:
                archive.writestr("classes.dex", b"Landroidx/benchmark/macro/MacrobenchmarkScope;")
            with self.assertRaisesRegex(ValueError, "Debug/benchmark code"):
                inspect_archive(path, True)


if __name__ == "__main__":
    unittest.main()
