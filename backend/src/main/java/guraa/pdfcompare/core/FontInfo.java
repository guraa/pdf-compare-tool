package guraa.pdfcompare.core;

import java.util.Objects;

/**
 * Class representing font information
 */
class FontInfo {
    private String name;
    private String family;
    private boolean isEmbedded;
    private boolean isSubset;

    // Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public boolean isEmbedded() {
        return isEmbedded;
    }

    public void setEmbedded(boolean embedded) {
        isEmbedded = embedded;
    }

    public boolean isSubset() {
        return isSubset;
    }

    public void setSubset(boolean subset) {
        isSubset = subset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FontInfo fontInfo = (FontInfo) o;
        return isEmbedded == fontInfo.isEmbedded &&
                isSubset == fontInfo.isSubset &&
                Objects.equals(name, fontInfo.name) &&
                Objects.equals(family, fontInfo.family);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, family, isEmbedded, isSubset);
    }
}