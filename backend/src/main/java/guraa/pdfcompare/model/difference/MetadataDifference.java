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
public class MetadataDifference extends Difference {
    private String key;
    private String baseValue;
    private String compareValue;
    private boolean onlyInBase;
    private boolean onlyInCompare;
    private boolean valueDifferent;
}