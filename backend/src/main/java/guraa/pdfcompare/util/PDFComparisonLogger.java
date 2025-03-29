package guraa.pdfcompare.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Utility class for improved logging in PDF comparison operations
 */
public class PDFComparisonLogger {
    private static final Logger logger = LoggerFactory.getLogger(PDFComparisonLogger.class);

    /**
     * Log detailed information about a PDF file
     * @param file The PDF file
     * @return A log-friendly description of the file
     */
    public static String logPdfFileInfo(File file) {
        if (file == null) {
            return "NULL_FILE";
        }

        StringBuilder info = new StringBuilder();
        info.append("PDF File: ").append(file.getName());
        info.append(" (").append(file.length() / 1024).append(" KB)");
        info.append(", Last modified: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(file.lastModified())));
        info.append(", Exists: ").append(file.exists());
        info.append(", Readable: ").append(file.canRead());

        logger.info(info.toString());
        return info.toString();
    }

    /**
     * Log an exception with full stack trace
     * @param logger The logger to use
     * @param message The message to log
     * @param e The exception
     */
    public static void logException(Logger logger, String message, Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        logger.error("{}: {} - Stack trace: {}", message, e.getMessage(), sw.toString());
    }

    /**
     * Log detailed difference statistics
     * @param result The comparison result
     */
    public static void logDifferenceStatistics(guraa.pdfcompare.comparison.PDFComparisonResult result) {
        if (result == null) {
            logger.warn("Cannot log statistics for null result");
            return;
        }

        logger.info("==== PDF Comparison Statistics ====");
        logger.info("Total differences: {}", result.getTotalDifferences());
        logger.info("Text differences: {}", result.getTotalTextDifferences());
        logger.info("Image differences: {}", result.getTotalImageDifferences());
        logger.info("Font differences: {}", result.getTotalFontDifferences());
        logger.info("Style differences: {}", result.getTotalStyleDifferences());
        logger.info("Base page count: {}, Compare page count: {}",
                result.getBasePageCount(), result.getComparePageCount());

        if (result.getPageDifferences() != null) {
            logger.info("Page differences: {}", result.getPageDifferences().size());

            // Log details of first few pages with differences
            int pageCount = 0;
            for (guraa.pdfcompare.comparison.PageComparisonResult page : result.getPageDifferences()) {
                if (pageCount++ >= 5) break; // Limit to first 5 pages

                int pageDiffs = 0;
                if (page.getTextDifferences() != null && page.getTextDifferences().getDifferences() != null) {
                    pageDiffs += page.getTextDifferences().getDifferences().size();
                }
                if (page.getTextElementDifferences() != null) {
                    pageDiffs += page.getTextElementDifferences().size();
                }
                if (page.getImageDifferences() != null) {
                    pageDiffs += page.getImageDifferences().size();
                }
                if (page.getFontDifferences() != null) {
                    pageDiffs += page.getFontDifferences().size();
                }

                logger.info("Page {}: {} differences", page.getPageNumber(), pageDiffs);
            }
        }
        logger.info("=================================");
    }
}