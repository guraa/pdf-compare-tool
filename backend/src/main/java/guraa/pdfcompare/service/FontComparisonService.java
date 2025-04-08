package guraa.pdfcompare.service;

import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.FontDifference;
import guraa.pdfcompare.util.FontAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for comparing fonts between PDF documents
 * Optimized for speed and memory efficiency
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FontComparisonService {

    private final FontAnalyzer fontAnalyzer;

    // Cache for quick lookups of already analyzed font differences
    private final Map<String, List<Difference>> fontDifferenceCache = new ConcurrentHashMap<>();

    /**
     * Compare fonts between two pages.
     *
     * @param baseFonts Fonts from base page
     * @param compareFonts Fonts from comparison page
     * @return List of differences
     */
    public List<Difference> compareFonts(
            List<FontAnalyzer.FontInfo> baseFonts,
            List<FontAnalyzer.FontInfo> compareFonts) {

        // Quick validation to avoid NPEs
        if (baseFonts == null) baseFonts = Collections.emptyList();
        if (compareFonts == null) compareFonts = Collections.emptyList();

        // Generate cache key from font lists
        String cacheKey = generateCacheKey(baseFonts, compareFonts);

        // Check cache first
        List<Difference> cachedDiffs = fontDifferenceCache.get(cacheKey);
        if (cachedDiffs != null) {
            return new ArrayList<>(cachedDiffs);
        }

        // Use quick check first to see if we need detailed analysis
        if (!fontAnalyzer.hasFontDifferences(baseFonts, compareFonts)) {
            // If fonts appear identical, return empty list
            fontDifferenceCache.put(cacheKey, Collections.emptyList());
            return Collections.emptyList();
        }

        List<Difference> differences = new ArrayList<>();

        // Match fonts based on name and properties
        Map<FontAnalyzer.FontInfo, FontAnalyzer.FontInfo> matches =
                matchFonts(baseFonts, compareFonts);

        // Track fonts that have been matched
        Set<FontAnalyzer.FontInfo> matchedBaseFonts = new HashSet<>(matches.keySet());
        Set<FontAnalyzer.FontInfo> matchedCompareFonts = new HashSet<>(matches.values());

        // Find differences in matched fonts
        for (Map.Entry<FontAnalyzer.FontInfo, FontAnalyzer.FontInfo> match : matches.entrySet()) {
            FontAnalyzer.FontInfo baseFont = match.getKey();
            FontAnalyzer.FontInfo compareFont = match.getValue();

            // Normalize font names
            String baseFontName = normalizeFontName(baseFont.getFontName());
            String compareFontName = normalizeFontName(compareFont.getFontName());

            String baseFamily = normalizeFamily(baseFont.getFontFamily());
            String compareFamily = normalizeFamily(compareFont.getFontFamily());

            // Comparisons
            boolean nameDifferent = !baseFontName.equals(compareFontName);
            boolean familyDifferent = !baseFamily.equals(compareFamily);
            boolean embeddingDifferent = baseFont.isEmbedded() != compareFont.isEmbedded();
            boolean boldDifferent = baseFont.isBold() != compareFont.isBold();
            boolean italicDifferent = baseFont.isItalic() != compareFont.isItalic();

            if (nameDifferent || familyDifferent || embeddingDifferent ||
                    boldDifferent || italicDifferent) {

                // Create font difference
                String diffId = UUID.randomUUID().toString();

                // Create description
                StringBuilder description = new StringBuilder("Font differs: ");

                if (nameDifferent) {
                    description.append("Name changed from \"")
                            .append(baseFont.getFontName())
                            .append("\" to \"")
                            .append(compareFont.getFontName())
                            .append("\". ");
                }

                if (familyDifferent) {
                    description.append("Family changed from \"")
                            .append(baseFont.getFontFamily())
                            .append("\" to \"")
                            .append(compareFont.getFontFamily())
                            .append("\". ");
                }

                if (embeddingDifferent) {
                    description.append("Font ")
                            .append(baseFont.isEmbedded() ? "was" : "was not")
                            .append(" embedded in base and ")
                            .append(compareFont.isEmbedded() ? "is" : "is not")
                            .append(" embedded in comparison. ");
                }

                if (boldDifferent) {
                    description.append("Font ")
                            .append(baseFont.isBold() ? "was" : "was not")
                            .append(" bold in base and ")
                            .append(compareFont.isBold() ? "is" : "is not")
                            .append(" bold in comparison. ");
                }

                if (italicDifferent) {
                    description.append("Font ")
                            .append(baseFont.isItalic() ? "was" : "was not")
                            .append(" italic in base and ")
                            .append(compareFont.isItalic() ? "is" : "is not")
                            .append(" italic in comparison.");
                }

                // Create font difference
                FontDifference diff = FontDifference.builder()
                        .id(diffId)
                        .type("font")
                        .changeType("modified")
                        .severity("minor")
                        .description(description.toString())
                        .fontName(baseFont.getFontName())
                        .baseFont(baseFont.getFontName())
                        .compareFont(compareFont.getFontName())
                        .baseFontFamily(baseFont.getFontFamily())
                        .compareFontFamily(compareFont.getFontFamily())
                        .isBaseEmbedded(baseFont.isEmbedded())
                        .isCompareEmbedded(compareFont.isEmbedded())
                        .isBaseBold(baseFont.isBold())
                        .isCompareBold(compareFont.isBold())
                        .isBaseItalic(baseFont.isItalic())
                        .isCompareItalic(compareFont.isItalic())
                        .build();

                differences.add(diff);
            }
        }

        // Fonts only in base document (deleted)
        for (FontAnalyzer.FontInfo font : baseFonts) {
            if (!matchedBaseFonts.contains(font)) {
                // Create font difference for deleted font
                String diffId = UUID.randomUUID().toString();

                FontDifference diff = FontDifference.builder()
                        .id(diffId)
                        .type("font")
                        .changeType("deleted")
                        .severity("minor")
                        .description("Font \"" + font.getFontName() + "\" removed")
                        .fontName(font.getFontName())
                        .baseFont(font.getFontName())
                        .baseFontFamily(font.getFontFamily())
                        .isBaseEmbedded(font.isEmbedded())
                        .isBaseBold(font.isBold())
                        .isBaseItalic(font.isItalic())
                        .build();

                differences.add(diff);
            }
        }

        // Fonts only in compare document (added)
        for (FontAnalyzer.FontInfo font : compareFonts) {
            if (!matchedCompareFonts.contains(font)) {
                // Create font difference for added font
                String diffId = UUID.randomUUID().toString();

                FontDifference diff = FontDifference.builder()
                        .id(diffId)
                        .type("font")
                        .changeType("added")
                        .severity("minor")
                        .description("Font \"" + font.getFontName() + "\" added")
                        .fontName(font.getFontName())
                        .compareFont(font.getFontName())
                        .compareFontFamily(font.getFontFamily())
                        .isCompareEmbedded(font.isEmbedded())
                        .isCompareBold(font.isBold())
                        .isCompareItalic(font.isItalic())
                        .build();

                differences.add(diff);
            }
        }

        // Cache the result
        fontDifferenceCache.put(cacheKey, new ArrayList<>(differences));

        return differences;
    }

    /**
     * Generate a cache key from two font lists
     *
     * @param baseFonts Base fonts
     * @param compareFonts Compare fonts
     * @return Cache key string
     */
    private String generateCacheKey(List<FontAnalyzer.FontInfo> baseFonts, List<FontAnalyzer.FontInfo> compareFonts) {
        StringBuilder key = new StringBuilder();

        // Add base font identifiers
        for (FontAnalyzer.FontInfo font : baseFonts) {
            key.append("B:").append(font.getFontName());
            if (font.isBold()) key.append("-B");
            if (font.isItalic()) key.append("-I");
            key.append(";");
        }

        key.append("|");

        // Add compare font identifiers
        for (FontAnalyzer.FontInfo font : compareFonts) {
            key.append("C:").append(font.getFontName());
            if (font.isBold()) key.append("-B");
            if (font.isItalic()) key.append("-I");
            key.append(";");
        }

        // Generate hash code for more compact key
        return String.valueOf(key.toString().hashCode());
    }

    /**
     * Match fonts between base and comparison pages using optimized algorithm.
     *
     * @param baseFonts Fonts from base page
     * @param compareFonts Fonts from comparison page
     * @return Map of matched fonts
     */
    private Map<FontAnalyzer.FontInfo, FontAnalyzer.FontInfo> matchFonts(
            List<FontAnalyzer.FontInfo> baseFonts,
            List<FontAnalyzer.FontInfo> compareFonts) {

        Map<FontAnalyzer.FontInfo, FontAnalyzer.FontInfo> matches = new HashMap<>();

        if (baseFonts.isEmpty() || compareFonts.isEmpty()) {
            return matches;
        }

        // First match by name (exact matches)
        for (FontAnalyzer.FontInfo baseFont : baseFonts) {
            for (FontAnalyzer.FontInfo compareFont : compareFonts) {
                if (baseFont.getFontName() != null && compareFont.getFontName() != null &&
                        baseFont.getFontName().equals(compareFont.getFontName())) {
                    matches.put(baseFont, compareFont);
                    break;
                }
            }
        }

        // Track fonts that have been matched
        Set<FontAnalyzer.FontInfo> matchedBaseFonts = new HashSet<>(matches.keySet());
        Set<FontAnalyzer.FontInfo> matchedCompareFonts = new HashSet<>(matches.values());

        // For unmatched fonts, try matching by family and properties
        for (FontAnalyzer.FontInfo baseFont : baseFonts) {
            if (matchedBaseFonts.contains(baseFont)) {
                continue;
            }

            FontAnalyzer.FontInfo bestMatch = null;
            double bestScore = 0.4; // Minimum threshold for matching

            for (FontAnalyzer.FontInfo compareFont : compareFonts) {
                if (matchedCompareFonts.contains(compareFont)) {
                    continue;
                }

                double score = calculateFontSimilarityScore(baseFont, compareFont);
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = compareFont;
                }
            }

            if (bestMatch != null) {
                matches.put(baseFont, bestMatch);
                matchedBaseFonts.add(baseFont);
                matchedCompareFonts.add(bestMatch);
            }
        }

        return matches;
    }

    /**
     * Calculate a similarity score between two fonts
     *
     * @param font1 First font
     * @param font2 Second font
     * @return Similarity score between 0.0 and 1.0
     */
    private double calculateFontSimilarityScore(FontAnalyzer.FontInfo font1, FontAnalyzer.FontInfo font2) {
        double score = 0.0;

        // Compare font names (normalized)
        String name1 = normalizeFontName(font1.getFontName());
        String name2 = normalizeFontName(font2.getFontName());

        if (name1.equals(name2)) {
            score += 0.5; // 50% of score is font name
        } else {
            // Check for partial name match
            if (name1.contains(name2) || name2.contains(name1)) {
                score += 0.3;
            }
        }

        // Compare family names
        String family1 = normalizeFamily(font1.getFontFamily());
        String family2 = normalizeFamily(font2.getFontFamily());

        if (family1.equals(family2)) {
            score += 0.3; // 30% of score is family
        } else {
            // Check for partial family match
            if (family1.contains(family2) || family2.contains(family1)) {
                score += 0.15;
            }
        }

        // Compare style properties
        if (font1.isBold() == font2.isBold()) {
            score += 0.1;
        }

        if (font1.isItalic() == font2.isItalic()) {
            score += 0.1;
        }

        // Cap maximum score at 1.0
        return Math.min(score, 1.0);
    }

    /**
     * Normalize font name for comparison
     *
     * @param fontName Font name
     * @return Normalized font name
     */
    private String normalizeFontName(String fontName) {
        if (fontName == null) {
            return "";
        }

        // Convert to lowercase for case-insensitive comparison
        fontName = fontName.toLowerCase();

        // Remove PDFM, NTPT and similar prefixes
        if (fontName.contains("+")) {
            fontName = fontName.substring(fontName.indexOf("+") + 1);
        }

        // Remove style suffixes
        String[] stylesToRemove = {",bold", ",italic", ",bolditalic", "-bold", "-italic", "-bolditalic",
                " bold", " italic", " bold italic"};
        for (String style : stylesToRemove) {
            if (fontName.contains(style)) {
                fontName = fontName.replace(style, "");
            }
        }

        return fontName.trim();
    }

    /**
     * Normalize font family for comparison
     *
     * @param family Font family
     * @return Normalized family
     */
    private String normalizeFamily(String family) {
        if (family == null) {
            return "";
        }

        // Convert to lowercase for case-insensitive comparison
        family = family.toLowerCase();

        // Remove common suffixes
        String[] suffixesToRemove = {" mt", " ms", " std", " lt std", " regular"};
        for (String suffix : suffixesToRemove) {
            if (family.endsWith(suffix)) {
                family = family.substring(0, family.length() - suffix.length());
            }
        }

        return family.trim();
    }

    /**
     * Clear the font difference cache to free memory
     */
    public void clearCache() {
        fontDifferenceCache.clear();
        log.info("Font difference cache cleared");
    }

    /**
     * Get the current size of the font difference cache
     *
     * @return Number of entries in the cache
     */
    public int getCacheSize() {
        return fontDifferenceCache.size();
    }

    /**
     * Pre-compare a set of font lists and cache the results
     * This can be used to warm up the cache before intensive comparisons
     *
     * @param baseFontLists List of base font lists
     * @param compareFontLists List of compare font lists
     * @return Number of comparisons pre-computed
     */
    public int preComputeFontDifferences(List<List<FontAnalyzer.FontInfo>> baseFontLists,
                                         List<List<FontAnalyzer.FontInfo>> compareFontLists) {
        int count = 0;

        // Skip if either list is empty
        if (baseFontLists == null || compareFontLists == null ||
                baseFontLists.isEmpty() || compareFontLists.isEmpty()) {
            return 0;
        }

        // Only compare a reasonable number to avoid excessive computation
        int maxComparisons = Math.min(baseFontLists.size(), compareFontLists.size());
        maxComparisons = Math.min(maxComparisons, 20); // Cap at 20 comparisons

        for (int i = 0; i < maxComparisons; i++) {
            List<FontAnalyzer.FontInfo> baseFonts = baseFontLists.get(i);
            List<FontAnalyzer.FontInfo> compareFonts = compareFontLists.get(i);

            // Pre-compute and cache the comparison
            compareFonts(baseFonts, compareFonts);
            count++;
        }

        return count;
    }
}