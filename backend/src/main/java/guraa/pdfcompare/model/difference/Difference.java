package guraa.pdfcompare.model.difference;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.NoArgsConstructor;

/**
 * Base class for all difference types.
 * Includes coordinate information for positioning differences on the page.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public abstract class Difference {

    private String id;
    private String type;
    private String changeType; // "added", "deleted", "modified"
    private String severity;   // "minor", "major", "critical"
    private String description;

    // Coordinates for positioning the difference on the page
    private double x;
    private double y;
    private double width;
    private double height;

    // Bounds for highlighting the difference
    private double left;
    private double top;
    private double right;
    private double bottom;
}