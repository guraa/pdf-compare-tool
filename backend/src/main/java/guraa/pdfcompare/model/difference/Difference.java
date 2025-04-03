package guraa.pdfcompare.model.difference;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextDifference.class, name = "text"),
        @JsonSubTypes.Type(value = ImageDifference.class, name = "image"),
        @JsonSubTypes.Type(value = FontDifference.class, name = "font"),
        @JsonSubTypes.Type(value = StyleDifference.class, name = "style"),
        @JsonSubTypes.Type(value = MetadataDifference.class, name = "metadata")
})
public abstract class Difference {

    private String id;
    private String type;
    private String changeType; // "added", "deleted", "modified"
    private String severity; // "critical", "major", "minor", "info"
    private String description;

    // Position and bounds in the PDF
    private Position position;
    private Bounds bounds;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Position {
        private double x;
        private double y;
        private int pageNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Bounds {
        private double width;
        private double height;
    }
}