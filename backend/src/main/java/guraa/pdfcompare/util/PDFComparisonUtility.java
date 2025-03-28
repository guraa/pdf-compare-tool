package guraa.pdfcompare.util;

import guraa.pdfcompare.comparison.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class with helper methods for PDF comparison
 */
public class PDFComparisonUtility {

    private static final Logger logger = LoggerFactory.getLogger(PDFComparisonUtility.class);

    /**
     * Private constructor to prevent instantiation
     */
    private PDFComparisonUtility() {
        // Utility class, do not instantiate
    }

    /**
     * Find most significant pages with differences
     * @param pages List of all page comparison results
     * @param maxPages Maximum number of pages to return
     * @return List of most significant pages
     */
    public static List<PageComparisonResult> findMostSignificantPages(List<PageComparisonResult> pages, int maxPages) {
        if (pages == null || pages.isEmpty()) {
            return new ArrayList<>();
        }

        // Sort pages by significance (number and type of differences)
        return pages.stream()
                .filter(page ->
                        page.isOnlyInBase() ||
                                page.isOnlyInCompare() ||
                                page.isDimensionsDifferent() ||
                                (page.getTextDifferences() != null && page.getTextDifferences().getDifferenceCount() > 0) ||
                                (page.getTextElementDifferences() != null && !page.getTextElementDifferences().isEmpty()) ||
                                (page.getImageDifferences() != null && !page.getImageDifferences().isEmpty()) ||
                                (page.getFontDifferences() != null && !page.getFontDifferences().isEmpty())
                )
                .sorted((p1, p2) -> {
                    // Calculate significance scores
                    int score1 = calculatePageSignificance(p1);
                    int score2 = calculatePageSignificance(p2);
                    return Integer.compare(score2, score1); // Descending order
                })
                .limit(maxPages)
                .collect(Collectors.toList());
    }

    /**
     * Calculate a significance score for a page
     * @param page The page comparison result
     * @return Significance score
     */
    public static int calculatePageSignificance(PageComparisonResult page) {
        int score = 0;

        // Pages that exist in only one document are highly significant
        if (page.isOnlyInBase() || page.isOnlyInCompare()) {
            score += 1000;
        }

        // Different dimensions are significant
        if (page.isDimensionsDifferent()) {
            score += 500;
        }

        // Count different types of differences with weights
        if (page.getTextDifferences() != null) {
            score += page.getTextDifferences().getDifferenceCount() * 10;
        }

        if (page.getTextElementDifferences() != null) {
            score += page.getTextElementDifferences().size() * 8;
        }

        if (page.getImageDifferences() != null) {
            score += page.getImageDifferences().size() * 20;
        }

        if (page.getFontDifferences() != null) {
            score += page.getFontDifferences().size() * 5;
        }

        return score;
    }

    /**
     * Calculate summary statistics for comparison result
     * @param result The comparison result to update with statistics
     */
    public static void calculateSummaryStatistics(PDFComparisonResult result) {
        int totalDifferences = 0;
        int textDifferences = 0;
        int imageDifferences = 0;
        int fontDifferences = 0;
        int styleDifferences = 0;

        // Count metadata differences
        if (result.getMetadataDifferences() != null) {
            totalDifferences += result.getMetadataDifferences().size();
        }

        // Count page structure differences
        if (result.isPageCountDifferent()) {
            totalDifferences++;
        }

        // Count differences for each page
        for (PageComparisonResult page : result.getPageDifferences()) {
            // Count page structure differences
            if (page.isOnlyInBase() || page.isOnlyInCompare()) {
                totalDifferences++;
            } else if (page.isDimensionsDifferent()) {
                totalDifferences++;
            }

            // Count text differences
            if (page.getTextDifferences() != null && page.getTextDifferences().getDifferences() != null) {
                int pageDiffs = page.getTextDifferences().getDifferences().size();
                textDifferences += pageDiffs;
                totalDifferences += pageDiffs;
            }

            // Count text element differences
            if (page.getTextElementDifferences() != null) {
                for (TextElementDifference diff : page.getTextElementDifferences()) {
                    if (diff.isStyleDifferent()) {
                        styleDifferences++;
                    } else {
                        textDifferences++;
                    }
                    totalDifferences++;
                }
            }

            // Count image differences
            if (page.getImageDifferences() != null) {
                int pageDiffs = page.getImageDifferences().size();
                imageDifferences += pageDiffs;
                totalDifferences += pageDiffs;
            }

            // Count font differences
            if (page.getFontDifferences() != null) {
                int pageDiffs = page.getFontDifferences().size();
                fontDifferences += pageDiffs;
                totalDifferences += pageDiffs;
            }
        }

        // Set statistics
        result.setTotalDifferences(totalDifferences);
        result.setTotalTextDifferences(textDifferences);
        result.setTotalImageDifferences(imageDifferences);
        result.setTotalFontDifferences(fontDifferences);
        result.setTotalStyleDifferences(styleDifferences);

        logger.info("Comparison statistics: total={}, text={}, image={}, font={}, style={}",
                totalDifferences, textDifferences, imageDifferences, fontDifferences, styleDifferences);
    }

    /**
     * Get used memory in MB
     * @return Used memory in MB
     */
    public static long getUsedMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return usedMemory / (1024 * 1024);
    }

    /**
     * Truncate text to a maximum length to prevent memory issues
     * @param text The text to truncate
     * @param maxLength Maximum length
     * @return Truncated text
     */
    public static String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;

        // Add indicator that text was truncated
        return text.substring(0, maxLength) + "... [Text truncated due to length]";
    }

    /**
     * Create a lightweight version of a page comparison result
     * @param page The original page
     * @param textLimit Max text differences
     * @param elementLimit Max element differences
     * @param imageLimit Max image differences
     * @param fontLimit Max font differences
     * @return Lightweight page comparison result
     */
    public static PageComparisonResult createLightweightPage(
            PageComparisonResult page,
            int textLimit,
            int elementLimit,
            int imageLimit,
            int fontLimit) {

        PageComparisonResult lightPage = new PageComparisonResult();

        // Copy basic info
        lightPage.setPageNumber(page.getPageNumber());
        lightPage.setOnlyInBase(page.isOnlyInBase());
        lightPage.setOnlyInCompare(page.isOnlyInCompare());
        lightPage.setDimensionsDifferent(page.isDimensionsDifferent());

        if (page.getBaseDimensions() != null) {
            lightPage.setBaseDimensions(page.getBaseDimensions().clone());
        }

        if (page.getCompareDimensions() != null) {
            lightPage.setCompareDimensions(page.getCompareDimensions().clone());
        }

        // Create limited text differences
        if (page.getTextDifferences() != null && page.getTextDifferences().getDifferences() != null) {
            TextComparisonResult lightTextResult = new TextComparisonResult();
            lightTextResult.setDifferenceCount(page.getTextDifferences().getDifferenceCount());

            // Limit to specified number of text differences
            List<TextDifferenceItem> lightDiffs = new ArrayList<>();
            int actualTextLimit = Math.min(textLimit, page.getTextDifferences().getDifferences().size());

            for (int i = 0; i < actualTextLimit; i++) {
                lightDiffs.add(page.getTextDifferences().getDifferences().get(i));
            }

            lightTextResult.setDifferences(lightDiffs);
            lightPage.setTextDifferences(lightTextResult);
        }

        // Create limited text element differences
        if (page.getTextElementDifferences() != null && !page.getTextElementDifferences().isEmpty()) {
            int actualElemLimit = Math.min(elementLimit, page.getTextElementDifferences().size());
            lightPage.setTextElementDifferences(
                    new ArrayList<>(page.getTextElementDifferences().subList(0, actualElemLimit))
            );
        }

        // Create limited image differences
        if (page.getImageDifferences() != null && !page.getImageDifferences().isEmpty()) {
            int actualImgLimit = Math.min(imageLimit, page.getImageDifferences().size());
            lightPage.setImageDifferences(
                    new ArrayList<>(page.getImageDifferences().subList(0, actualImgLimit))
            );
        }

        // Create limited font differences
        if (page.getFontDifferences() != null && !page.getFontDifferences().isEmpty()) {
            int actualFontLimit = Math.min(fontLimit, page.getFontDifferences().size());
            lightPage.setFontDifferences(
                    new ArrayList<>(page.getFontDifferences().subList(0, actualFontLimit))
            );
        }

        return lightPage;
    }
}