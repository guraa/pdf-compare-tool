import React, { useState, useEffect, useRef } from 'react';
import Spinner from '../common/Spinner';
import './PDFRenderer.css';

/**
 * Simplified PDFRenderer that uses direct image URLs
 */
const PDFRenderer = ({ 
  fileId, 
  page, 
  zoom = 1,
  highlightMode = 'all',
  differences = [],
  selectedDifference = null,
  onDifferenceSelect,
  loading = false,
  opacity = 1,
  onImageLoaded = null,
  onZoomChange = null
}) => {
  // State
  const [renderError, setRenderError] = useState(null);
  const [dimensions, setDimensions] = useState({ width: 0, height: 0 });
  const [isLoading, setIsLoading] = useState(true);
  const [imageLoaded, setImageLoaded] = useState(false);
  
  // Refs
  const imageRef = useRef(null);
  const canvasRef = useRef(null);
  
  // URL for the image
  const imageUrl = `/api/pdfs/document/${fileId}/page/${page}`;
  
  // Log rendering
  console.log(`PDFRenderer: rendering for fileId=${fileId}, page=${page}, zoom=${zoom}`);
  
  // Handle image load
  const handleImageLoad = () => {
    console.log(`Image loaded successfully: page ${page}`);
    
    if (!imageRef.current) return;
    
    const image = imageRef.current;
    const naturalWidth = image.naturalWidth;
    const naturalHeight = image.naturalHeight;
    
    const scaledWidth = naturalWidth * zoom;
    const scaledHeight = naturalHeight * zoom;
    
    console.log(`Image dimensions: ${naturalWidth}x${naturalHeight}, zoomed: ${scaledWidth}x${scaledHeight}`);
    
    setDimensions({
      width: scaledWidth,
      height: scaledHeight
    });
    
    setImageLoaded(true);
    setIsLoading(false);
    
    // Call onImageLoaded callback if provided
    if (onImageLoaded && typeof onImageLoaded === 'function') {
      onImageLoaded(scaledWidth, scaledHeight);
    }
  };
  
  // Handle image error
  const handleImageError = (error) => {
    console.error(`Error loading image for page ${page}:`, error);
    setRenderError(`Failed to load page ${page}. The server may be unavailable or the document format is not supported.`);
    setIsLoading(false);
  };
  
  // Draw highlights on canvas
  useEffect(() => {
    if (!canvasRef.current || !imageLoaded || highlightMode === 'none') return;
    
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    
    // Set canvas dimensions
    canvas.width = dimensions.width;
    canvas.height = dimensions.height;
    
    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // Skip if no differences or highlight mode is none
    if (!differences || differences.length === 0) return;
    
    // Draw each difference
    differences.forEach(diff => {
      // Skip if no position or bounds data
      if (!diff.position || !diff.bounds) return;
      
      // Skip if type doesn't match highlight mode (unless mode is 'all')
      if (highlightMode !== 'all' && diff.type !== highlightMode) return;
      
      // Apply zoom to coordinates
      const x = diff.position.x * zoom;
      const y = diff.position.y * zoom;
      const width = diff.bounds.width * zoom;
      const height = diff.bounds.height * zoom;
      
      // Choose color based on difference type
      let fillColor = 'rgba(255, 82, 82, 0.3)'; // Default red for text
      
      switch (diff.type) {
        case 'image':
          fillColor = 'rgba(33, 150, 243, 0.3)'; // Blue for images
          break;
        case 'font':
          fillColor = 'rgba(156, 39, 176, 0.3)'; // Purple for fonts
          break;
        case 'style':
          fillColor = 'rgba(255, 152, 0, 0.3)'; // Orange for styles
          break;
        default:
          // Use default for text
      }
      
      // Check if this is the selected difference
      const isSelected = selectedDifference && selectedDifference.id === diff.id;
      
      // Draw the difference highlight
      ctx.fillStyle = fillColor;
      ctx.fillRect(x, y, width, height);
      
      // Add border
      ctx.strokeStyle = isSelected ? 'rgba(255, 255, 0, 0.8)' : fillColor.replace('0.3', '0.8');
      ctx.lineWidth = isSelected ? 3 : 1;
      ctx.strokeRect(x, y, width, height);
    });
  }, [differences, highlightMode, imageLoaded, dimensions, zoom, selectedDifference]);
  
  // Handle click on highlight
  const handleCanvasClick = (e) => {
    if (!onDifferenceSelect || !differences || !differences.length || !imageLoaded) return;
    
    // Get mouse coordinates
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;
    
    // Convert to canvas coordinates
    const canvasX = mouseX * (canvas.width / rect.width);
    const canvasY = mouseY * (canvas.height / rect.height);
    
    // Find clicked difference
    const clickedDiff = differences.find(diff => {
      if (!diff.position || !diff.bounds) return false;
      
      const diffX = diff.position.x * zoom;
      const diffY = diff.position.y * zoom;
      const diffWidth = diff.bounds.width * zoom;
      const diffHeight = diff.bounds.height * zoom;
      
      return (
        canvasX >= diffX && 
        canvasX <= diffX + diffWidth && 
        canvasY >= diffY && 
        canvasY <= diffY + diffHeight
      );
    });
    
    if (clickedDiff) {
      console.log('Clicked difference:', clickedDiff);
      onDifferenceSelect(clickedDiff);
    }
  };
  
  // Set up the image when fileId or page changes
  useEffect(() => {
    setIsLoading(true);
    setImageLoaded(false);
  }, [fileId, page]);
  
  // Update dimensions when zoom changes
  useEffect(() => {
    if (imageLoaded && imageRef.current) {
      const image = imageRef.current;
      setDimensions({
        width: image.naturalWidth * zoom,
        height: image.naturalHeight * zoom
      });
    }
  }, [zoom, imageLoaded]);

  // Render error state
  if (renderError) {
    return (
      <div className="pdf-renderer-empty" style={{ opacity }}>
        <div className="pdf-error-message">
          <div className="error-icon">
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
            </svg>
          </div>
          <p>{renderError}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="pdf-renderer" style={{ opacity }}>
      {/* Loading indicator */}
      {(isLoading || loading) && (
        <div className="renderer-loading">
          <Spinner size="medium" />
          <div className="loading-message">Loading page {page}...</div>
        </div>
      )}
      
      {/* Image and highlights container */}
      <div className="canvas-container" style={{ opacity: imageLoaded ? 1 : 0.3 }}>
        <img 
          ref={imageRef}
          className="pdf-image"
          src={imageUrl}
          alt={`PDF page ${page}`}
          style={{ 
            width: dimensions.width,
            height: dimensions.height
          }}
          onLoad={handleImageLoad}
          onError={handleImageError}
        />
        
        <canvas 
          ref={canvasRef}
          className="highlight-layer"
          onClick={handleCanvasClick}
          width={dimensions.width}
          height={dimensions.height}
        />
      </div>
      
      {/* Difference count badge */}
      {differences && differences.length > 0 && imageLoaded && (
        <div className="diff-count-badge">
          {differences.length}
        </div>
      )}
      
      {/* Zoom controls */}
      {onZoomChange && (
        <div className="pdf-controls">
          <button 
            title="Zoom Out" 
            onClick={() => onZoomChange(Math.max(zoom - 0.25, 0.5))}
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14zM7 9h5v1H7z"/>
            </svg>
          </button>
          
          <div className="zoom-value" onClick={() => onZoomChange(1.0)}>
            {Math.round(zoom * 100)}%
          </div>
          
          <button 
            title="Zoom In" 
            onClick={() => onZoomChange(Math.min(zoom + 0.25, 3.0))}
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
              <path d="M12 10h-2v-2h-1v2h-2v1h2v2h1v-2h2z"/>
            </svg>
          </button>
          
          <span className="page-indicator">
            {page}
          </span>
        </div>
      )}
    </div>
  );
};

export default PDFRenderer;