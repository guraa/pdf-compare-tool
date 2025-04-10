package guraa.pdfcompare.util;

import guraa.pdfcompare.model.PageDetails;
import guraa.pdfcompare.model.difference.Difference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Utility class to validate that all differences have proper coordinates.
 * Can be used for debugging and testing.
 */
@Slf4j
@Component
public class DifferenceCoordinateValidator {

    /**
     * Validates that all differences in a PageDetails object have proper coordinates.
     *
     * @param pageDetails The page details to validate
     * @return True if all differences have coordinates, false otherwise
     */
    public boolean validatePageDetailsCoordinates(PageDetails pageDetails) {
        if (pageDetails == null) {
            log.error("PageDetails is null");
            return false;
        }

        boolean allValid = true;

        // Check base differences
        List<Difference> baseDiffs = pageDetails.getBaseDifferences();
        if (baseDiffs != null) {
            for (int i = 0; i < baseDiffs.size(); i++) {
                Difference diff = baseDiffs.get(i);
                if (!validateDifferenceCoordinates(diff)) {
                    log.error("Base difference at index {} is missing coordinates: {}", i, diff);
                    allValid = false;
                }
            }
        }

        // Check compare differences
        List<Difference> compareDiffs = pageDetails.getCompareDifferences();
        if (compareDiffs != null) {
            for (int i = 0; i < compareDiffs.size(); i++) {
                Difference diff = compareDiffs.get(i);
                if (!validateDifferenceCoordinates(diff)) {
                    log.error("Compare difference at index {} is missing coordinates: {}", i, diff);
                    allValid = false;
                }
            }
        }

        if (allValid) {
            log.info("All differences for page {} have valid coordinates", pageDetails.getPageNumber());
        } else {
            log.warn("Some differences for page {} are missing coordinates", pageDetails.getPageNumber());
        }

        return allValid;
    }

    /**
     * Validates that a difference has proper coordinates.
     *
     * @param difference The difference to validate
     * @return True if the difference has coordinates, false otherwise
     */
    public boolean validateDifferenceCoordinates(Difference difference) {
        if (difference == null) {
            return false;
        }

        // Check if coordinates and bounds are set
        boolean hasCoordinates = difference.getX() != 0 || difference.getY() != 0;
        boolean hasBounds = difference.getLeft() != 0 || difference.getTop() != 0 ||
                difference.getRight() != 0 || difference.getBottom() != 0;

        // Return true only if either coordinates or bounds are set
        return hasCoordinates || hasBounds;
    }

    /**
     * Process all differences in a page details object.
     *
     * @param pageDetails The page details object
     * @param processor The processor function to apply to each difference
     */
    public void processAllDifferences(PageDetails pageDetails, Consumer<Difference> processor) {
        if (pageDetails == null || processor == null) {
            return;
        }

        // Process base differences
        List<Difference> baseDiffs = pageDetails.getBaseDifferences();
        if (baseDiffs != null) {
            for (Difference diff : baseDiffs) {
                processor.accept(diff);
            }
        }

        // Process compare differences
        List<Difference> compareDiffs = pageDetails.getCompareDifferences();
        if (compareDiffs != null) {
            for (Difference diff : compareDiffs) {
                processor.accept(diff);
            }
        }
    }

    /**
     * Log a summary of coordinates for all differences in a page.
     *
     * @param pageDetails The page details object
     */
    public void logCoordinateSummary(PageDetails pageDetails) {
        if (pageDetails == null) {
            return;
        }

        int totalDiffs = 0;
        int withCoordinates = 0;

        // Process all differences and count those with coordinates
        processAllDifferences(pageDetails, diff -> {
            totalDiffs++;
            if (validateDifferenceCoordinates(diff)) {
                withCoordinates++;
            }
        });

        // Log summary
        if (totalDiffs > 0) {
            double percentage = (double) withCoordinates / totalDiffs * 100;
            log.info("Page {}: {} out of {} differences have coordinates ({:.2f}%)",
                    pageDetails.getPageNumber(), withCoordinates, totalDiffs, percentage);
        } else {
            log.info("Page {}: No differences found", pageDetails.getPageNumber());
        }
    }
}