package guraa.pdfcompare.comparison;


class MetadataDifference {
    private String key;
    private String baseValue;
    private String compareValue;
    private boolean onlyInBase;
    private boolean onlyInCompare;
    private boolean valueDifferent;

    // Getters and setters
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getBaseValue() {
        return baseValue;
    }

    public void setBaseValue(String baseValue) {
        this.baseValue = baseValue;
    }

    public String getCompareValue() {
        return compareValue;
    }

    public void setCompareValue(String compareValue) {
        this.compareValue = compareValue;
    }

    public boolean isOnlyInBase() {
        return onlyInBase;
    }

    public void setOnlyInBase(boolean onlyInBase) {
        this.onlyInBase = onlyInBase;
    }

    public boolean isOnlyInCompare() {
        return onlyInCompare;
    }

    public void setOnlyInCompare(boolean onlyInCompare) {
        this.onlyInCompare = onlyInCompare;
    }

    public boolean isValueDifferent() {
        return valueDifferent;
    }

    public void setValueDifferent(boolean valueDifferent) {
        this.valueDifferent = valueDifferent;
    }
}
