import React, { useState, useRef } from 'react';
import { uploadPDF } from '../../services/api';
import Spinner from '../common/Spinner';
import './PDFUploader.css';

const PDFUploader = ({ onFileUploaded, file, label = 'Upload PDF' }) => {
  const [dragActive, setDragActive] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState(null);
  const fileInputRef = useRef(null);

  const handleDrag = (e) => {
    e.preventDefault();
    e.stopPropagation();
    
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true);
    } else if (e.type === 'dragleave') {
      setDragActive(false);
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleFile(e.dataTransfer.files[0]);
    }
  };

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      handleFile(e.target.files[0]);
    }
  };

  const [reusedFile, setReusedFile] = useState(false);

  const handleFile = async (selectedFile) => {
    // Validate file is a PDF
    if (selectedFile.type !== 'application/pdf') {
      setError('Please upload a PDF file');
      return;
    }

    try {
      setUploading(true);
      setError(null);
      setReusedFile(false);
      
      // Upload the file to the server
      const result = await uploadPDF(selectedFile);
      
      // Check if this file was reused (already existed in the system)
      if (result.reused) {
        setReusedFile(true);
      }
      
      // Call the callback with file info
      onFileUploaded({
        fileId: result.fileId,
        fileName: selectedFile.name,
        size: selectedFile.size,
        uploadDate: new Date().toISOString(),
        reused: result.reused
      });
      
      setUploading(false);
    } catch (err) {
      console.error('Upload error:', err);
      setError(err.response?.data?.error || 'Upload failed. Please try again.');
      setUploading(false);
    }
  };

  const handleButtonClick = () => {
    fileInputRef.current.click();
  };

  const handleRemoveFile = () => {
    onFileUploaded(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  // Already uploaded state
  if (file) {
    return (
      <div className="pdf-uploader pdf-uploaded">
        <div className="file-info">
          <div className="file-icon">
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M14 2H6C4.9 2 4 2.9 4 4V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V8L14 2Z" />
              <path d="M14 3V8H19" />
              <path d="M9 13V16M9 16V19M9 16H12M9 16H6" />
            </svg>
          </div>
          <div className="file-details">
            <div className="file-name">{file.fileName}</div>
            <div className="file-size">{Math.round(file.size / 1024)} KB</div>
            {file.reused && (
              <div className="file-reused">
                <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" />
                </svg>
                <span>Existing file reused</span>
              </div>
            )}
          </div>
          <button className="remove-button" onClick={handleRemoveFile}>
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" />
            </svg>
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="pdf-uploader">
      <div 
        className={`drop-zone ${dragActive ? 'active' : ''} ${uploading ? 'uploading' : ''}`}
        onDragEnter={handleDrag}
        onDragOver={handleDrag}
        onDragLeave={handleDrag}
        onDrop={handleDrop}
        onClick={handleButtonClick}
      >
        <input 
          ref={fileInputRef}
          type="file" 
          accept=".pdf,application/pdf" 
          onChange={handleFileChange} 
          className="file-input"
        />
        
        {uploading ? (
          <div className="upload-progress">
            <Spinner size="medium" />
            <span>Uploading...</span>
          </div>
        ) : (
          <div className="upload-content">
            <div className="upload-icon">
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM14 13v4h-4v-4H7l5-5 5 5h-3z" />
              </svg>
            </div>
            <div className="upload-text">
              <span className="upload-title">{label}</span>
              <span className="upload-subtitle">Drop file here or click to browse</span>
            </div>
          </div>
        )}
      </div>
      
      {error && (
        <div className="upload-error">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
          </svg>
          <span>{error}</span>
        </div>
      )}
    </div>
  );
};

export default PDFUploader;
