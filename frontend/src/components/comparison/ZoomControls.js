import React from 'react';
import './ZoomControls.css';

/**
 * ZoomControls component for PDF comparison
 * @param {object} props Component props
 * @param {number} props.zoom Current zoom level (e.g., 1.0 = 100%)
 * @param {function} props.onZoomIn Callback for zoom in
 * @param {function} props.onZoomOut Callback for zoom out
 * @param {function} props.onZoomReset Callback to reset zoom to 100%
 * @param {string} props.className Additional CSS class names
 */
const ZoomControls = ({ 
  zoom = 1.0, 
  onZoomIn, 
  onZoomOut, 
  onZoomReset,
  className = ''
}) => {
  // Default handlers if none provided
  const handleZoomIn = () => {
    if (onZoomIn) {
      onZoomIn();
    }
  };

  const handleZoomOut = () => {
    if (onZoomOut) {
      onZoomOut();
    }
  };

  const handleZoomReset = () => {
    if (onZoomReset) {
      onZoomReset();
    }
  };

  return (
    <div className={`zoom-controls ${className}`}>
      <button 
        onClick={handleZoomOut} 
        title="Zoom Out"
        className="zoom-button zoom-out"
      >
        <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14zM7 9h5v1H7z"/>
        </svg>
      </button>
      
      <button 
        onClick={handleZoomReset} 
        title="Reset Zoom (100%)"
        className="zoom-value"
      > 
        {Math.round(zoom * 100)}%
      </button>
      
      <button 
        onClick={handleZoomIn} 
        title="Zoom In"
        className="zoom-button zoom-in"
      >
        <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
          <path d="M12 10h-2v-2h-1v2h-2v1h2v2h1v-2h2z"/>
        </svg>
      </button>
    </div>
  );
};

export default ZoomControls;