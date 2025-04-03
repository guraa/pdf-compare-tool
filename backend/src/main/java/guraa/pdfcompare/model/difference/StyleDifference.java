package guraa.pdfcompare.model.difference;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StyleDifference extends Difference {

    // The text that has style differences
    private String text;

    // Style properties
    private String baseColor;
    private String compareColor;

    private String baseBackgroundColor;
    private String compareBackgroundColor;

    private Float baseOpacity;
    private Float compareOpacity;

    private Float baseLineHeight;
    private Float compareLineHeight;

    private Float baseCharacterSpacing;
    private Float compareCharacterSpacing;

    private String baseAlignment;
    private String compareAlignment;

    // Store other style properties that may be different
    private Map<String, Object> baseStyles = new HashMap<>();
    private Map<String, Object> compareStyles = new HashMap<>();

    // For tracking the source element IDs in the PDFs
    private String baseElementId;
    private String compareElementId;
}