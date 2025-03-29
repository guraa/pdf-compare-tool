package guraa.pdfcompare.model;

/**
 * Response model for file upload operations
 */
public class FileUploadResponse {
    private String fileId;
    private boolean success;
    private String errorMessage;

    private float fileSize;
    private String fileName;


    public FileUploadResponse() {
        this.success = true;
    }

    public FileUploadResponse(String fileId, String errorMessage) {
        this.fileId = fileId;
        this.success = (errorMessage == null);
        this.errorMessage = errorMessage;
    }

    // Getters and setters

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String filename) {
        this.fileName = filename;
    }

    public float getFileSize() {
        return fileSize;
    }

    public void setFileSize(float fileSize) {
        this.fileSize = fileSize;
    }
    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}