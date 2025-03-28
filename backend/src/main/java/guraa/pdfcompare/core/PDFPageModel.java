package guraa.pdfcompare.core;

import java.util.List;

/**
 * Model class representing a page in a PDF document
 */
class PDFPageModel {
    private int pageNumber;
    private float width;
    private float height;
    private String text;
    private List<TextElement> textElements;
    private List<ImageElement> images;
    private List<FontInfo> fonts;

    // Getters and setters
    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<TextElement> getTextElements() {
        return textElements;
    }

    public void setTextElements(List<TextElement> textElements) {
        this.textElements = textElements;
    }

    public List<ImageElement> getImages() {
        return images;
    }

    public void setImages(List<ImageElement> images) {
        this.images = images;
    }

    public List<FontInfo> getFonts() {
        return fonts;
    }

    public void setFonts(List<FontInfo> fonts) {
        this.fonts = fonts;
    }
}