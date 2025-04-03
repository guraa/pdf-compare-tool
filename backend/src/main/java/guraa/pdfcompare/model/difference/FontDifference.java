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
public class FontDifference extends Difference {

    private String fontName;
    private String baseFont;
    private String compareFont;

    private String baseFontFamily;
    private String compareFontFamily;

    private Float baseFontSize;
    private Float compareFontSize;

    private Boolean isBaseEmbedded;
    private Boolean isCompareEmbedded;

    private Boolean isBaseBold;
    private Boolean isCompareBold;

    private Boolean isBaseItalic;
    private Boolean isCompareItalic;

    // The text using this font
    private String sampleText;

    // For tracking the source element IDs in the PDFs
    private String baseElementId;
    private String compareElementId;
}