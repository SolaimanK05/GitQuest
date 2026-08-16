package com.gitquest.core.codebase;

import java.util.ArrayList;
import java.util.List;

/**
 * Squarified treemap layout (Bruls, Huizing, van Wijk) — recursively
 * subdivides a rectangle among a directory's children, area proportional to
 * {@link FileEntry#size()}, choosing each row so cells stay close to square
 * rather than degrading into thin slivers under skewed file sizes. Pure
 * layout math: no JGit, no I/O, safe to unit test directly. Directories are
 * inset slightly from their parent's rect before laying out their own
 * children, so nested directory boundaries stay visible as a gap once
 * rendered rather than being fully overdrawn by their children.
 */
public final class TreemapLayout {

    private static final double DIRECTORY_INSET = 5;

    public record Rect(double x, double y, double w, double h) {
    }

    public record PlacedEntry(FileEntry entry, Rect rect, int depth) {
    }

    private TreemapLayout() {
    }

    public static List<PlacedEntry> layout(FileEntry root, double x, double y, double w, double h) {
        List<PlacedEntry> out = new ArrayList<>();
        layoutNode(root, new Rect(x, y, Math.max(w, 0), Math.max(h, 0)), 0, out);
        return out;
    }

    private static void layoutNode(FileEntry node, Rect rect, int depth, List<PlacedEntry> out) {
        out.add(new PlacedEntry(node, rect, depth));
        if (!node.directory() || node.children().isEmpty() || rect.w() <= 0 || rect.h() <= 0) {
            return;
        }
        double pad = depth == 0 ? 0 : Math.min(DIRECTORY_INSET, Math.min(rect.w(), rect.h()) / 4);
        Rect inner = new Rect(rect.x() + pad, rect.y() + pad,
                Math.max(rect.w() - 2 * pad, 0), Math.max(rect.h() - 2 * pad, 0));

        List<FileEntry> children = new ArrayList<>(node.children());
        children.sort((a, b) -> Double.compare(weightOf(b), weightOf(a)));
        List<Rect> childRects = squarify(children, inner);
        for (int i = 0; i < children.size(); i++) {
            layoutNode(children.get(i), childRects.get(i), depth + 1, out);
        }
    }

    /** Zero-byte files still get a sliver cell, not nothing — they're still real, clickable files. */
    private static double weightOf(FileEntry entry) {
        return Math.max(entry.size(), 1);
    }

    private static List<Rect> squarify(List<FileEntry> entries, Rect bounds) {
        Rect[] result = new Rect[entries.size()];
        double totalWeight = 0;
        for (FileEntry e : entries) {
            totalWeight += weightOf(e);
        }
        double totalArea = bounds.w() * bounds.h();
        double scale = totalWeight > 0 ? totalArea / totalWeight : 0;

        int start = 0;
        Rect remaining = bounds;
        while (start < entries.size()) {
            double shortSide = Math.min(remaining.w(), remaining.h());
            double firstArea = weightOf(entries.get(start)) * scale;
            int end = start + 1;
            double rowSum = firstArea;
            double rowMax = firstArea;
            double rowMin = firstArea;
            double worst = worstRatio(rowMax, rowMin, rowSum, shortSide);

            while (end < entries.size()) {
                double area = weightOf(entries.get(end)) * scale;
                double newSum = rowSum + area;
                double newMax = Math.max(rowMax, area);
                double newMin = Math.min(rowMin, area);
                double newWorst = worstRatio(newMax, newMin, newSum, shortSide);
                if (newWorst > worst) {
                    break;
                }
                rowSum = newSum;
                rowMax = newMax;
                rowMin = newMin;
                worst = newWorst;
                end++;
            }

            remaining = layoutRow(entries, start, end, scale, rowSum, remaining, result);
            start = end;
        }
        return List.of(result);
    }

    /** Lower is more square; a row of near-equal areas scores close to 1, a row with one huge/tiny outlier scores high. */
    private static double worstRatio(double maxArea, double minArea, double sum, double shortSide) {
        if (sum <= 0 || shortSide <= 0 || minArea <= 0) {
            return Double.MAX_VALUE;
        }
        double s2 = shortSide * shortSide;
        double sum2 = sum * sum;
        return Math.max(s2 * maxArea / sum2, sum2 / (s2 * minArea));
    }

    /** Places entries[start, end) as one strip along {@code bounds}' short side; returns the leftover rect. */
    private static Rect layoutRow(List<FileEntry> entries, int start, int end, double scale, double rowSum,
            Rect bounds, Rect[] result) {
        boolean stripAlongTop = bounds.w() >= bounds.h();
        if (stripAlongTop) {
            double stripHeight = bounds.w() > 0 ? Math.min(rowSum / bounds.w(), bounds.h()) : 0;
            double cursorX = bounds.x();
            for (int i = start; i < end; i++) {
                double area = weightOf(entries.get(i)) * scale;
                double itemWidth = stripHeight > 0 ? area / stripHeight : 0;
                result[i] = new Rect(cursorX, bounds.y(), itemWidth, stripHeight);
                cursorX += itemWidth;
            }
            return new Rect(bounds.x(), bounds.y() + stripHeight, bounds.w(), Math.max(bounds.h() - stripHeight, 0));
        } else {
            double stripWidth = bounds.h() > 0 ? Math.min(rowSum / bounds.h(), bounds.w()) : 0;
            double cursorY = bounds.y();
            for (int i = start; i < end; i++) {
                double area = weightOf(entries.get(i)) * scale;
                double itemHeight = stripWidth > 0 ? area / stripWidth : 0;
                result[i] = new Rect(bounds.x(), cursorY, stripWidth, itemHeight);
                cursorY += itemHeight;
            }
            return new Rect(bounds.x() + stripWidth, bounds.y(), Math.max(bounds.w() - stripWidth, 0), bounds.h());
        }
    }
}
