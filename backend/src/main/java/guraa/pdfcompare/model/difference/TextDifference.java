package guraa.pdfcompare.model.difference;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TextDifference extends Difference {

    private String baseText;
    private String compareText;
    private String text; // Used when only one version exists

    // Context information to locate the difference
    private String contextBefore;
    private String contextAfter;

    // Character-level differences
    private int[] baseChanges; // Indices of changed characters in base text
    private int[] compareChanges; // Indices of changed characters in compare text

    // For stylistic differences
    private boolean isStyleChange;

    // For tracking the source element IDs in the PDFs
    private String baseElementId;
    private String compareElementId;
}