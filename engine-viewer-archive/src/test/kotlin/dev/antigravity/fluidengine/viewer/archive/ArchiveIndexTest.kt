package dev.antigravity.fluidengine.viewer.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ArchiveIndexTest {
  @Test fun rejects_absolute_traversal_and_ambiguous_portable_paths() {
    for (path in listOf("/tmp/file", "../file", "folder/../file", "C:\\file", "\\\\host\\file",
      "folder\\..\\file", "folder/file:stream", "folder/file.", "folder/file ", "bad\u0000name", "\ufffd.txt")) {
      assertNull(path, archiveSafePath(path))
    }
    assertEquals("docs/2026/file.pdf", archiveSafePath("./docs\\2026/./file.pdf"))
  }

  @Test fun named_folders_replace_synthetic_ones_and_links_are_never_exported() {
    val tree = ArchiveTree(listOf(
      file(0, "docs/2026/note.txt"),
      ArchiveItem(1, "docs/2026", ArchiveKind.Folder, null, null),
      ArchiveItem(2, "docs/link", ArchiveKind.Link, null, null),
      file(3, "docs/same.txt"), file(4, "docs/SAME.txt"), file(5, "docs/2026"),
    ))
    assertEquals(listOf("docs"), tree.children("").map { it.name })
    assertEquals(1, tree.children("docs").first { it.kind == ArchiveKind.Folder }.ordinal)
    assertEquals(listOf("docs/2026/note.txt"), tree.extractable.map { it.path })
  }

  @Test fun growth_is_bounded_across_entries_without_multiplication_overflow() {
    val budget = ArchiveBudget(8_000)
    budget.addEntry(800_000)
    budget.checkRead(800_000)
    assertEquals(ArchiveProblem.Limit, assertThrows(ArchiveReadFailure::class.java) { budget.checkRead(800_001) }.problem)
    assertEquals(ArchiveTotalLimit, ArchiveBudget(Long.MAX_VALUE).totalLimit)
    assertThrows(ArchiveReadFailure::class.java) { ArchiveBudget(null).addEntry(ArchiveEntryLimit + 1) }
    assertThrows(ArchiveReadFailure::class.java) { ArchiveBudget(1).checkRead(1024L * 1024 + 1) }
  }

  private fun file(ordinal: Int, path: String) = ArchiveItem(ordinal, path, ArchiveKind.File, 32, null)
}
