package com.gitquest.core.codebase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gitquest.core.codebase.TreemapLayout.PlacedEntry;
import com.gitquest.core.codebase.TreemapLayout.Rect;

class TreemapLayoutTest {

    @Test
    void flatLeafRectsExactlyTileTheBounds() {
        // No subdirectories here, so no directory-inset padding applies — the
        // top-level squarify pass alone should tile the bounds exactly.
        FileEntry root = FileEntry.directory("root", "", List.of(
                FileEntry.file("a.txt", "a.txt", 100),
                FileEntry.file("b.txt", "b.txt", 300),
                FileEntry.file("c.txt", "c.txt", 50),
                FileEntry.file("d.txt", "d.txt", 550)));

        List<PlacedEntry> placed = TreemapLayout.layout(root, 0, 0, 800, 600);

        double leafArea = 0;
        for (PlacedEntry entry : placed) {
            if (entry.entry().directory()) {
                continue;
            }
            Rect r = entry.rect();
            assertTrue(r.w() >= 0 && r.h() >= 0, "negative-size rect for " + entry.entry().relativePath());
            assertTrue(r.x() >= -0.01 && r.y() >= -0.01, "rect escaped the left/top bound: " + r);
            assertTrue(r.x() + r.w() <= 800.01 && r.y() + r.h() <= 600.01, "rect escaped the right/bottom bound: " + r);
            leafArea += r.w() * r.h();
        }

        assertEquals(800.0 * 600.0, leafArea, 0.5, "leaf cells should tile the full bounds (minus rounding)");
    }

    @Test
    void nestedDirectoryStaysWithinItsParentsRectAndInsetsSlightly() {
        FileEntry root = FileEntry.directory("root", "", List.of(
                FileEntry.file("a.txt", "a.txt", 100),
                FileEntry.directory("sub", "sub", List.of(
                        FileEntry.file("c.txt", "sub/c.txt", 50),
                        FileEntry.file("d.txt", "sub/d.txt", 550)))));

        List<PlacedEntry> placed = TreemapLayout.layout(root, 0, 0, 800, 600);

        Rect subRect = placed.stream()
                .filter(e -> e.entry().relativePath().equals("sub"))
                .findFirst().orElseThrow().rect();
        double subArea = subRect.w() * subRect.h();

        double childArea = 0;
        for (PlacedEntry entry : placed) {
            if (entry.entry().relativePath().startsWith("sub/")) {
                Rect r = entry.rect();
                assertTrue(r.x() >= subRect.x() - 0.01 && r.y() >= subRect.y() - 0.01,
                        "child escaped its directory's left/top bound: " + r);
                assertTrue(r.x() + r.w() <= subRect.x() + subRect.w() + 0.01
                                && r.y() + r.h() <= subRect.y() + subRect.h() + 0.01,
                        "child escaped its directory's right/bottom bound: " + r);
                childArea += r.w() * r.h();
            }
        }

        assertTrue(childArea < subArea, "the directory inset should leave children with strictly less area than their parent");
        assertTrue(childArea > subArea * 0.8, "the inset should be a small visual gap, not a large chunk of the area");
    }

    @Test
    void emptyDirectoryProducesNoLeafCells() {
        FileEntry root = FileEntry.directory("root", "", List.of());
        List<PlacedEntry> placed = TreemapLayout.layout(root, 0, 0, 400, 300);
        assertEquals(1, placed.size());
        assertEquals(root, placed.get(0).entry());
    }

    @Test
    void singleFileFillsTheEntireBounds() {
        FileEntry root = FileEntry.directory("root", "", List.of(FileEntry.file("only.txt", "only.txt", 42)));
        List<PlacedEntry> placed = TreemapLayout.layout(root, 0, 0, 500, 200);

        PlacedEntry file = placed.stream().filter(e -> !e.entry().directory()).findFirst().orElseThrow();
        assertEquals(500.0, file.rect().w(), 0.01);
        assertEquals(200.0, file.rect().h(), 0.01);
    }

    @Test
    void zeroSizeBoundsProduceNoNegativeRects() {
        FileEntry root = FileEntry.directory("root", "", List.of(
                FileEntry.file("a.txt", "a.txt", 10),
                FileEntry.file("b.txt", "b.txt", 20)));
        List<PlacedEntry> placed = TreemapLayout.layout(root, 0, 0, 0, 0);
        for (PlacedEntry entry : placed) {
            assertTrue(entry.rect().w() >= 0 && entry.rect().h() >= 0);
        }
    }
}
