package guraa.pdfcompare.service;

import guraa.pdfcompare.PDFComparisonEngine;
import guraa.pdfcompare.comparison.PageComparisonResult;
import guraa.pdfcompare.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Service for comparing PDFs at the page level, enabling comparison of documents
 * where pages have been reordered, inserted, or deleted.
 */
@Service
public class PageLevelComparisonService {
    private static final Logger logger = LoggerFactory.getLogger(PageLevelComparisonService.class);

    // Similarity thresholds
    private static final double HIGH_SIMILARITY_THRESHOLD = 0.8;
    private static final double MEDIUM_SIMILARITY_THRESHOLD = 0.6;
    private static final double LOW_SIMILARITY_THRESHOLD = 0.3;

    // Relative weights for different similarity measures
    private static final double TEXT_SIMILARITY_WEIGHT = 0.7;
    private static final double STRUCTURE_SIMILARITY_WEIGHT = 0.2;
    private static final double VISUAL_SIMILARITY_WEIGHT = 0.1;

    private final ExecutorService executorService = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()));

    @Autowired
    private PDFComparisonEngine comparisonEngine;

    /**
     * Compare PDFs at the page level
     * @param baseDoc Base document
     * @param compareDoc Compare document
     * @return Comparison result
     */
    public PageLevelComparisonResult compareDocuments(PDFDocumentModel baseDoc, PDFDocumentModel compareDoc) {
        logger.info("Starting page-level comparison between documents: {} and {}",
                baseDoc.getFileName(), compareDoc.getFileName());

        // Extract page models
        List<PDFPageModel> basePages = baseDoc.getPages();
        List<PDFPageModel> comparePages = compareDoc.getPages();

        logger.info("Base document has {} pages, compare document has {} pages",
                basePages.size(), comparePages.size());

        // Calculate page fingerprints
        List<PageFingerprint> baseFingerprints = calculatePageFingerprints(basePages, "base");
        List<PageFingerprint> compareFingerprints = calculatePageFingerprints(comparePages, "compare");

        // Create similarity matrix
        double[][] similarityMatrix = calculatePageSimilarityMatrix(baseFingerprints, compareFingerprints);

        // Find optimal page matching using the similarity matrix
        List<PagePair> pagePairs = findOptimalPageMatching(similarityMatrix, baseFingerprints, compareFingerprints);

        // Compare matched pages in detail
        List<guraa.pdfcompare.service.PageComparisonResult> pageResults = compareMatchedPages(pagePairs, baseDoc, compareDoc);

        // Create a summary of the comparison
        PageLevelComparisonSummary summary = createComparisonSummary(pagePairs, pageResults);

        logger.info("Page-level comparison completed. Found {} matched pages, {} unmatched base pages, {} unmatched compare pages",
                summary.getMatchedPageCount(), summary.getUnmatchedBasePageCount(), summary.getUnmatchedComparePageCount());

        return new PageLevelComparisonResult(pagePairs, pageResults, summary);
    }

    /**
     * Calculate fingerprints for all pages
     * @param pages List of PDF pages
     * @param sourceType Source type (base or compare)
     * @return List of page fingerprints
     */
    private List<PageFingerprint> calculatePageFingerprints(List<PDFPageModel> pages, String sourceType) {
        List<PageFingerprint> fingerprints = new ArrayList<>();

        for (int i = 0; i < pages.size(); i++) {
            PDFPageModel page = pages.get(i);
            int pageIndex = i;

            // Create basic fingerprint
            PageFingerprint fingerprint = new PageFingerprint(sourceType, pageIndex);
            fingerprint.setPage(page);

            // Extract text
            String text = page.getText();
            fingerprint.setText(text);

            // Calculate text hash
            int textHash = text != null ? text.hashCode() : 0;
            fingerprint.setTextHash(textHash);

            // Extract significant words
            Set<String> significantWords = extractSignificantWords(text);
            fingerprint.setSignificantWords(significantWords);

            // Extract structural information
            if (page.getTextElements() != null) {
                // Create a rough layout signature
                List<TextElement> elements = page.getTextElements();
                Map<String, Integer> fontCounts = new HashMap<>();

                for (TextElement element : elements) {
                    if (element.getFontName() != null) {
                        fontCounts.merge(element.getFontName(), 1, Integer::sum);
                    }
                }

                fingerprint.setFontDistribution(fontCounts);
                fingerprint.setElementCount(elements.size());
            }

            // Calculate image hash if images are present
            if (page.getImages() != null && !page.getImages().isEmpty()) {
                fingerprint.setHasImages(true);
                fingerprint.setImageCount(page.getImages().size());
            }

            fingerprints.add(fingerprint);
        }

        return fingerprints;
    }

    /**
     * Extract significant words from text (filters out common words)
     * @param text Page text
     * @return Set of significant words
     */
    private Set<String> extractSignificantWords(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptySet();
        }

        // List of common words to filter out
        Set<String> stopWords = new HashSet<>(Arrays.asList(
                "the", "and", "a", "to", "of", "in", "is", "that", "it", "with",
                "for", "as", "was", "on", "are", "be", "by", "this", "have", "or",
                "at", "from", "an", "but", "not", "what", "all", "were", "we", "when",
                "your", "can", "said", "there", "use", "how", "each", "which", "their"));

        // Extract words, convert to lowercase, and filter stop words
        return Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(word -> word.length() > 2)  // Only words with 3+ characters
                .filter(word -> !stopWords.contains(word))
                .collect(Collectors.toSet());
    }

    /**
     * Calculate similarity between each pair of pages
     * @param baseFingerprints Base page fingerprints
     * @param compareFingerprints Compare page fingerprints
     * @return Similarity matrix
     */
    private double[][] calculatePageSimilarityMatrix(List<PageFingerprint> baseFingerprints,
                                                     List<PageFingerprint> compareFingerprints) {
        double[][] matrix = new double[baseFingerprints.size()][compareFingerprints.size()];

        for (int i = 0; i < baseFingerprints.size(); i++) {
            for (int j = 0; j < compareFingerprints.size(); j++) {
                matrix[i][j] = calculatePageSimilarity(baseFingerprints.get(i), compareFingerprints.get(j));
            }
        }

        return matrix;
    }

    /**
     * Calculate similarity between two pages using multiple metrics
     * @param base Base page fingerprint
     * @param compare Compare page fingerprint
     * @return Similarity score (0.0-1.0)
     */
    private double calculatePageSimilarity(PageFingerprint base, PageFingerprint compare) {
        // Quick check for identical text
        if (base.getTextHash() != 0 && base.getTextHash() == compare.getTextHash()) {
            return 1.0;
        }

        // Calculate text similarity
        double textSimilarity = calculateTextSimilarity(base, compare);

        // Calculate structure similarity
        double structureSimilarity = calculateStructureSimilarity(base, compare);

        // Calculate visual similarity (based on images and layout)
        double visualSimilarity = calculateVisualSimilarity(base, compare);

        // Weighted combination of similarities
        return (TEXT_SIMILARITY_WEIGHT * textSimilarity +
                STRUCTURE_SIMILARITY_WEIGHT * structureSimilarity +
                VISUAL_SIMILARITY_WEIGHT * visualSimilarity);
    }

    /**
     * Calculate text similarity between pages
     * @param base Base page fingerprint
     * @param compare Compare page fingerprint
     * @return Text similarity score
     */
    private double calculateTextSimilarity(PageFingerprint base, PageFingerprint compare) {
        // Handle null text
        if (base.getText() == null || compare.getText() == null ||
                base.getText().isEmpty() || compare.getText().isEmpty()) {
            return 0.0;
        }

        // Calculate Jaccard similarity of significant words
        Set<String> baseWords = base.getSignificantWords();
        Set<String> compareWords = compare.getSignificantWords();

        if (baseWords.isEmpty() && compareWords.isEmpty()) {
            return 0.0;
        }

        Set<String> union = new HashSet<>(baseWords);
        union.addAll(compareWords);

        Set<String> intersection = new HashSet<>(baseWords);
        intersection.retainAll(compareWords);

        return (double) intersection.size() / union.size();
    }

    /**
     * Calculate structural similarity between pages
     * @param base Base page fingerprint
     * @param compare Compare page fingerprint
     * @return Structure similarity score
     */
    private double calculateStructureSimilarity(PageFingerprint base, PageFingerprint compare) {
        // Compare font distributions
        double fontSimilarity = 0.0;
        if (base.getFontDistribution() != null && compare.getFontDistribution() != null) {
            Set<String> allFonts = new HashSet<>(base.getFontDistribution().keySet());
            allFonts.addAll(compare.getFontDistribution().keySet());

            int totalDifference = 0;
            int totalElements = 0;

            for (String font : allFonts) {
                int baseCount = base.getFontDistribution().getOrDefault(font, 0);
                int compareCount = compare.getFontDistribution().getOrDefault(font, 0);

                totalDifference += Math.abs(baseCount - compareCount);
                totalElements += Math.max(baseCount, compareCount);
            }

            fontSimilarity = totalElements > 0 ? 1.0 - ((double) totalDifference / totalElements) : 0.0;
        }

        // Compare element counts
        double elementCountSimilarity = 0.0;
        if (base.getElementCount() > 0 && compare.getElementCount() > 0) {
            double ratio = (double) Math.min(base.getElementCount(), compare.getElementCount()) /
                    Math.max(base.getElementCount(), compare.getElementCount());
            elementCountSimilarity = ratio;
        }

        return (fontSimilarity * 0.7) + (elementCountSimilarity * 0.3);
    }

    /**
     * Calculate visual similarity between pages
     * @param base Base page fingerprint
     * @param compare Compare page fingerprint
     * @return Visual similarity score
     */
    private double calculateVisualSimilarity(PageFingerprint base, PageFingerprint compare) {
        // Simple comparison based on presence/absence of images
        double imageSimilarity = 0.0;

        if (base.isHasImages() == compare.isHasImages()) {
            imageSimilarity = 0.5;  // Both have or don't have images

            // If both have images, compare image counts
            if (base.isHasImages()) {
                double ratio = (double) Math.min(base.getImageCount(), compare.getImageCount()) /
                        Math.max(base.getImageCount(), compare.getImageCount());
                imageSimilarity = 0.5 + (ratio * 0.5);  // Scale remaining 0.5 by ratio
            }
        }

        // TODO: Add more sophisticated visual comparison if needed

        return imageSimilarity;
    }

    /**
     * Find optimal page matching using the similarity matrix
     * @param similarityMatrix Similarity matrix
     * @param baseFingerprints Base page fingerprints
     * @param compareFingerprints Compare page fingerprints
     * @return List of matched page pairs
     */
    private List<PagePair> findOptimalPageMatching(double[][] similarityMatrix,
                                                   List<PageFingerprint> baseFingerprints,
                                                   List<PageFingerprint> compareFingerprints) {
        List<PagePair> pagePairs = new ArrayList<>();
        int baseSize = baseFingerprints.size();
        int compareSize = compareFingerprints.size();

        // Track which pages have been matched
        boolean[] baseMatched = new boolean[baseSize];
        boolean[] compareMatched = new boolean[compareSize];

        // First pass: match pages with high similarity
        for (int i = 0; i < baseSize; i++) {
            if (baseMatched[i]) continue;

            double bestSimilarity = HIGH_SIMILARITY_THRESHOLD;
            int bestMatch = -1;

            for (int j = 0; j < compareSize; j++) {
                if (compareMatched[j]) continue;

                if (similarityMatrix[i][j] > bestSimilarity) {
                    bestSimilarity = similarityMatrix[i][j];
                    bestMatch = j;
                }
            }

            if (bestMatch != -1) {
                pagePairs.add(new PagePair(baseFingerprints.get(i), compareFingerprints.get(bestMatch), bestSimilarity));
                baseMatched[i] = true;
                compareMatched[bestMatch] = true;
            }
        }

        // Second pass: match remaining pages with medium similarity
        for (int i = 0; i < baseSize; i++) {
            if (baseMatched[i]) continue;

            double bestSimilarity = MEDIUM_SIMILARITY_THRESHOLD;
            int bestMatch = -1;

            for (int j = 0; j < compareSize; j++) {
                if (compareMatched[j]) continue;

                if (similarityMatrix[i][j] > bestSimilarity) {
                    bestSimilarity = similarityMatrix[i][j];
                    bestMatch = j;
                }
            }

            if (bestMatch != -1) {
                pagePairs.add(new PagePair(baseFingerprints.get(i), compareFingerprints.get(bestMatch), bestSimilarity));
                baseMatched[i] = true;
                compareMatched[bestMatch] = true;
            }
        }

        // Add unmatched base pages
        for (int i = 0; i < baseSize; i++) {
            if (!baseMatched[i]) {
                pagePairs.add(new PagePair(baseFingerprints.get(i), null, 0.0));
            }
        }

        // Add unmatched compare pages
        for (int j = 0; j < compareSize; j++) {
            if (!compareMatched[j]) {
                pagePairs.add(new PagePair(null, compareFingerprints.get(j), 0.0));
            }
        }

        // Sort pairs by base page index, with null base pages at the end
        pagePairs.sort((a, b) -> {
            if (a.getBaseFingerprint() == null && b.getBaseFingerprint() == null) {
                return 0;
            } else if (a.getBaseFingerprint() == null) {
                return 1;
            } else if (b.getBaseFingerprint() == null) {
                return -1;
            } else {
                return Integer.compare(a.getBaseFingerprint().getPageIndex(), b.getBaseFingerprint().getPageIndex());
            }
        });

        return pagePairs;
    }

    /**
     * Compare matched pages in detail
     * @param pagePairs Matched page pairs
     * @param baseDoc Base document
     * @param compareDoc Compare document
     * @return List of page comparison results
     */
    private List<guraa.pdfcompare.service.PageComparisonResult> compareMatchedPages(List<PagePair> pagePairs,
                                                           PDFDocumentModel baseDoc,
                                                           PDFDocumentModel compareDoc) {
        List<CompletableFuture<guraa.pdfcompare.service.PageComparisonResult>> futures = new ArrayList<>();

        for (PagePair pair : pagePairs) {
            final PagePair finalPair = pair;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    guraa.pdfcompare.service.PageComparisonResult result = new guraa.pdfcompare.service.PageComparisonResult();
                    result.setPagePair(finalPair);

                    if (finalPair.isMatched()) {
                        // Get the page models for this pair
                        PDFPageModel basePage = baseDoc.getPages().get(finalPair.getBaseFingerprint().getPageIndex());
                        PDFPageModel comparePage = compareDoc.getPages().get(finalPair.getCompareFingerprint().getPageIndex());

                        // Use the comparison engine to compare pages
                        guraa.pdfcompare.comparison.PageComparisonResult comparisonResult =
                                comparisonEngine.comparePage(basePage, comparePage);

                        // Convert the comparison result to service result using adapter
                        guraa.pdfcompare.service.PageComparisonResult adaptedResult = PageComparisonResultAdapter.toServiceResult(comparisonResult);

                        // Set the page pair and maintain other service-specific properties
                        adaptedResult.setPagePair(finalPair);

                        // Return the adapted result
                        return adaptedResult;
                    } else {
                        // Create a result for an unmatched page
                        result.setHasDifferences(true);
                        result.setTotalDifferences(1);  // Count as one major difference

                        // Create an appropriate page difference
                        PageDifference difference = new PageDifference();
                        if (finalPair.getBaseFingerprint() != null) {
                            difference.setPageNumber(finalPair.getBaseFingerprint().getPageIndex() + 1);
                            difference.setOnlyInBase(true);
                            difference.setOnlyInCompare(false);
                            result.setChangeType("DELETION");
                        } else {
                            difference.setPageNumber(finalPair.getCompareFingerprint().getPageIndex() + 1);
                            difference.setOnlyInBase(false);
                            difference.setOnlyInCompare(true);
                            result.setChangeType("ADDITION");
                        }

                        result.setPageDifference(difference);
                    }

                    return result;
                } catch (Exception e) {
                    logger.error("Error comparing page pair", e);
                    guraa.pdfcompare.service.PageComparisonResult errorResult = new guraa.pdfcompare.service.PageComparisonResult();
                    errorResult.setPagePair(finalPair);
                    errorResult.setError("Error comparing pages: " + e.getMessage());
                    errorResult.setHasDifferences(true);
                    return errorResult;
                }
            }, executorService));
        }

        // Wait for all comparisons to complete
        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    /**
     * Create a summary of the comparison
     * @param pagePairs Matched page pairs
     * @param pageResults Page comparison results
     * @return Comparison summary
     */
    private PageLevelComparisonSummary createComparisonSummary(List<PagePair> pagePairs,
                                                               List<guraa.pdfcompare.service.PageComparisonResult> pageResults) {
        PageLevelComparisonSummary summary = new PageLevelComparisonSummary();

        // Count matched and unmatched pages
        int matchedPages = 0;
        int unmatchedBasePages = 0;
        int unmatchedComparePages = 0;

        for (PagePair pair : pagePairs) {
            if (pair.isMatched()) {
                matchedPages++;
            } else if (pair.getBaseFingerprint() != null) {
                unmatchedBasePages++;
            } else if (pair.getCompareFingerprint() != null) {
                unmatchedComparePages++;
            }
        }

        summary.setMatchedPageCount(matchedPages);
        summary.setUnmatchedBasePageCount(unmatchedBasePages);
        summary.setUnmatchedComparePageCount(unmatchedComparePages);

        // Count differences
        int identicalPages = 0;
        int pagesWithDifferences = 0;
        int totalDifferences = 0;
        int totalTextDifferences = 0;
        int totalImageDifferences = 0;
        int totalFontDifferences = 0;
        int totalStyleDifferences = 0;

        for (guraa.pdfcompare.service.PageComparisonResult result : pageResults) {
            if (result.isHasDifferences()) {
                pagesWithDifferences++;
                totalDifferences += result.getTotalDifferences();

                // Count specific types of differences
                if (result.getTextDifferences() != null && result.getTextDifferences().getDifferenceItems() != null) {
                    totalTextDifferences += result.getTextDifferences().getDifferenceItems().size();
                }

                if (result.getImageDifferences() != null) {
                    totalImageDifferences += result.getImageDifferences().size();
                }

                if (result.getFontDifferences() != null) {
                    totalFontDifferences += result.getFontDifferences().size();
                }

                if (result.getTextElementDifferences() != null) {
                    for (guraa.pdfcompare.comparison.TextElementDifference diff : result.getTextElementDifferences()) {
                        if (diff.isStyleDifferent()) {
                            totalStyleDifferences++;
                        }
                    }
                }

            } else if (result.getPagePair().isMatched()) {
                identicalPages++;
            }
        }

        summary.setIdenticalPageCount(identicalPages);
        summary.setPagesWithDifferencesCount(pagesWithDifferences);
        summary.setTotalDifferences(totalDifferences);
        summary.setTotalTextDifferences(totalTextDifferences);
        summary.setTotalImageDifferences(totalImageDifferences);
        summary.setTotalFontDifferences(totalFontDifferences);
        summary.setTotalStyleDifferences(totalStyleDifferences);

        // Calculate overall similarity score
        double totalSimilarity = pagePairs.stream()
                .filter(PagePair::isMatched)
                .mapToDouble(PagePair::getSimilarityScore)
                .average()
                .orElse(0.0);

        summary.setOverallSimilarityScore(totalSimilarity);

        return summary;
    }

    /**
     * Create a single-page document from a multi-page document
     * @param document Source document
     * @param pageIndex Page index
     * @return Single-page document
     */
    private PDFDocumentModel createSinglePageDocument(PDFDocumentModel document, int pageIndex) {
        PDFDocumentModel singlePage = new PDFDocumentModel();
        singlePage.setFileName(document.getFileName());

        if (document.getMetadata() != null) {
            singlePage.setMetadata(new HashMap<>(document.getMetadata()));
        } else {
            singlePage.setMetadata(new HashMap<>());
        }

        List<PDFPageModel> pages = new ArrayList<>();
        if (pageIndex >= 0 && pageIndex < document.getPages().size()) {
            pages.add(document.getPages().get(pageIndex));
        }

        singlePage.setPages(pages);
        singlePage.setPageCount(pages.size());

        return singlePage;
    }
}
