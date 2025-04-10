package guraa.pdfcompare.model.difference;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class FontDifference extends Difference {
    private String fontName;
    private String baseFont;
    private String compareFont;
    private String baseFontFamily;
    private String compareFontFamily;
    private boolean isBaseEmbedded;
    private boolean isCompareEmbedded;
    private boolean isBaseBold;
    private boolean isCompareBold;
    private boolean isBaseItalic;
    private boolean isCompareItalic;
}