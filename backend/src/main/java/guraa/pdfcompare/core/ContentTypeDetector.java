package guraa.pdfcompare.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detect content type of a document segment
 */
public class ContentTypeDetector {
    // Content type patterns and keywords
    private static final Map<String, Set<String>> CONTENT_TYPE_PATTERNS = new HashMap<>() {{
        put("ACADEMIC_PAPER", Set.of(
                "abstract", "introduction", "methodology", "results", "conclusion",
                "references", "citations", "research", "study", "analysis"
        ));

        put("TECHNICAL_REPORT", Set.of(
                "technical", "report", "specification", "standard", "procedure",
                "implementation", "architecture", "design", "system"
        ));

        put("FINANCIAL_DOCUMENT", Set.of(
                "invoice", "statement", "balance", "revenue", "expense", "budget",
                "financial", "accounting", "transaction", "profit", "loss"
        ));

        put("LEGAL_DOCUMENT", Set.of(
                "contract", "agreement", "terms", "conditions", "clause", "legal",
                "party", "liability", "jurisdiction", "signature"
        ));

        put("MARKETING_MATERIAL", Set.of(
                "brochure", "campaign", "product", "marketing", "promotion",
                "strategy", "target", "audience", "brand", "advertisement"
        ));
    }};

    /**
     * Detect content type of a document segment
     *
     * @param segment Document segment
     * @param document Full document model
     * @return Detected content type
     */
    public String detectContentType(
            SmartDocumentMatcher.DocumentSegment segment,
            PDFDocumentModel document) {

        // Extract text from the segment
        String fullText = extractFullText(segment, document).toLowerCase();

        // Score each content type
        Map<String, Double> typeScores = new HashMap<>();

        CONTENT_TYPE_PATTERNS.forEach((type, keywords) -> {
            double score = calculateContentTypeScore(fullText, keywords);
            typeScores.put(type, score);
        });

        // Find the highest scoring type
        return typeScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("GENERIC_DOCUMENT");
    }

    /**
     * Calculate content type score based on keyword presence
     */
    private double calculateContentTypeScore(String text, Set<String> keywords) {
        // Count keyword matches
        long matchCount = keywords.stream()
                .filter(text::contains)
                .count();

        // Score is proportion of matched keywords
        return (double) matchCount / keywords.size();
    }

    /**
     * Extract full text from a document segment
     */
    private String extractFullText(
            SmartDocumentMatcher.DocumentSegment segment,
            PDFDocumentModel document) {

        StringBuilder text = new StringBuilder();
        for (int i = segment.getStartPage(); i <= segment.getEndPage(); i++) {
            PDFPageModel page = document.getPages().get(i);
            text.append(page.getText()).append("\n");
        }
        return text.toString();
    }

    /**
     * Advanced content type detection using more sophisticated techniques
     *
     * @param text Full text of the document
     * @return More detailed content type analysis
     */
    public Map<String, Double> advancedContentTypeDetection(String text) {
        Map<String, Double> detailedScores = new HashMap<>();

        CONTENT_TYPE_PATTERNS.forEach((type, keywords) -> {
            double keywordScore = calculateContentTypeScore(text, keywords);
            double structureScore = analyzeDocumentStructure(text, type);

            // Combine scores
            double finalScore = (keywordScore * 0.6) + (structureScore * 0.4);
            detailedScores.put(type, finalScore);
        });

        return detailedScores;
    }

    /**
     * Analyze document structure for additional content type insights
     */
    private double analyzeDocumentStructure(String text, String contentType) {
        // Implement structure-based scoring
        switch (contentType) {
            case "ACADEMIC_PAPER":
                return detectAcademicPaperStructure(text);
            case "TECHNICAL_REPORT":
                return detectTechnicalReportStructure(text);
            case "FINANCIAL_DOCUMENT":
                return detectFinancialDocumentStructure(text);
            case "LEGAL_DOCUMENT":
                return detectLegalDocumentStructure(text);
            default:
                return 0.0;
        }
    }

    /**
     * Detect academic paper structure
     */
    private double detectAcademicPaperStructure(String text) {
        double score = 0.0;

        // Check for typical academic paper sections
        String[] sections = {"abstract", "introduction", "methodology", "results", "discussion", "conclusion"};

        for (String section : sections) {
            if (Pattern.compile("\\b" + section + "\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                score += 0.15;
            }
        }

        // Check for citations
        long citationCount = Pattern.compile("\\[[0-9]+\\]").matcher(text).results().count();
        score += Math.min(citationCount * 0.02, 0.2);

        return Math.min(score, 1.0);
    }

    /**
     * Detect technical report structure
     */
    private double detectTechnicalReportStructure(String text) {
        double score = 0.0;

        // Check for technical sections
        String[] sections = {"scope", "requirements", "design", "implementation", "testing", "appendix"};

        for (String section : sections) {
            if (Pattern.compile("\\b" + section + "\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                score += 0.15;
            }
        }

        // Check for technical terminology
        long technicalTerms = Pattern.compile("\\b(algorithm|architecture|protocol|interface)\\b", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .results()
                .count();

        score += Math.min(technicalTerms * 0.05, 0.25);

        return Math.min(score, 1.0);
    }

    /**
     * Detect financial document structure
     */
    private double detectFinancialDocumentStructure(String text) {
        double score = 0.0;

        // Check for financial sections
        String[] sections = {"balance sheet", "income statement", "cash flow", "summary", "notes"};

        for (String section : sections) {
            if (Pattern.compile("\\b" + section + "\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                score += 0.2;
            }
        }

        // Check for financial terms
        long financialTerms = Pattern.compile("\\b(revenue|expense|profit|loss|asset|liability|equity)\\b", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .results()
                .count();

        score += Math.min(financialTerms * 0.05, 0.3);

        return Math.min(score, 1.0);
    }

    /**
     * Detect legal document structure
     */
    private double detectLegalDocumentStructure(String text) {
        double score = 0.0;

        // Check for legal sections
        String[] sections = {"whereas", "hereby", "agreement", "terms", "conditions", "signatures"};

        for (String section : sections) {
            if (Pattern.compile("\\b" + section + "\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                score += 0.2;
            }
        }

        // Check for legal terminology
        long legalTerms = Pattern.compile("\\b(party|liability|jurisdiction|covenant|clause)\\b", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .results()
                .count();

        score += Math.min(legalTerms * 0.05, 0.3);

        return Math.min(score, 1.0);
    }
}