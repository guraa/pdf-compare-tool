import React, { useState, useEffect, useRef } from 'react';
import Spinner from '../common/Spinner';
import './PDFRenderer.css';

/**
 * Debug PDFRenderer component that logs crucial information and adds visual debugging
 */
const PDFRenderer = ({ 
  fileId, 
  page, 
  zoom = 1,
  highlightMode = 'all',
  differences = [],
  selectedDifference = null,
  onDifferenceSelect,
  onZoomChange,
  isBaseDocument = false,
  loading = false,
  opacity = 1
}) => {
  // State
  const [renderError, setRenderError] = useState(null);
  const [dimensions, setDimensions] = useState({ width: 0, height: 0 });
  const [isLoading, setIsLoading] = useState(true);
  const [imageLoaded, setImageLoaded] = useState(false);
  const [debugInfo, setDebugInfo] = useState({
    differencesCount: 0,
    hasValidDifferences: false,
    canvasReady: false,
    imageSize: null
  });
  
  // Refs
  const imageRef = useRef(null);
  const canvasRef = useRef(null);
  const containerRef = useRef(null);
  
  // URL for the image
  const imageUrl = `/api/pdfs/document/${fileId}/page/${page}`;
  
  // Log important information
  useEffect(() => {
    console.log(`PDFRenderer DEBUG: rendering for fileId=${fileId}, page=${page}, zoom=${zoom}`);
    console.log('PDFRenderer DEBUG: Differences array:', differences);
    
    // Check if differences have valid coordinates
    const validDiffs = differences.filter(diff => 
      diff && diff.position && diff.bounds && 
      typeof diff.position.x === 'number' && 
      typeof diff.position.y === 'number' &&
      typeof diff.bounds.width === 'number' &&
      typeof diff.bounds.height === 'number'
    );
    
    console.log(`PDFRenderer DEBUG: Valid differences with coordinates: ${validDiffs.length} of ${differences.length}`);
    
    setDebugInfo(prev => ({
      ...prev,
      differencesCount: differences.length,
      hasValidDifferences: validDiffs.length > 0
    }));
    
    // If there are no valid differences, log the first few differences to see what we have
    if (validDiffs.length === 0 && differences.length > 0) {
      console.log('PDFRenderer DEBUG: First difference object structure:', 
        JSON.stringify(differences[0], null, 2));
    }
  }, [differences, fileId, page, zoom]);
  
  // Handle image load
  const handleImageLoad = () => {
    console.log(`PDFRenderer DEBUG: Image loaded successfully: page ${page}`);
    
    if (!imageRef.current) {
      console.error('PDFRenderer DEBUG: imageRef is null after load!');
      return;
    }
    
    const image = imageRef.current;
    const naturalWidth = image.naturalWidth;
    const naturalHeight = image.naturalHeight;
    
    const scaledWidth = naturalWidth * zoom;
    const scaledHeight = naturalHeight * zoom;
    
    console.log(`PDFRenderer DEBUG: Image dimensions: ${naturalWidth}x${naturalHeight}, zoomed: ${scaledWidth}x${scaledHeight}`);
    
    setDimensions({
      width: scaledWidth,
      height: scaledHeight
    });
    
    setDebugInfo(prev => ({
      ...prev,
      imageSize: { width: naturalWidth, height: naturalHeight, 
                  scaled: { width: scaledWidth, height: scaledHeight } }
    }));
    
    setImageLoaded(true);
    setIsLoading(false);
    
    // Call zoom change callback if provided
    if (onZoomChange) {
      onZoomChange(zoom);
    }
  };
  
  // Handle image error
  const handleImageError = (error) => {
    console.error(`PDFRenderer DEBUG: Error loading image for page ${page}:`, error);
    setRenderError(`Failed to load page ${page}. The server may be unavailable or the document format is not supported.`);
    setIsLoading(false);
  };
  
  // Draw highlights on canvas - with additional debugging
  useEffect(() => {
    if (!canvasRef.current || !imageLoaded || highlightMode === 'none') {
      console.log(`PDFRenderer DEBUG: Skipping canvas drawing - canvasRef exists: ${!!canvasRef.current}, imageLoaded: ${imageLoaded}, highlightMode: ${highlightMode}`);
      return;
    }
    
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    
    // Set canvas dimensions
    canvas.width = dimensions.width;
    canvas.height = dimensions.height;
    
    console.log(`PDFRenderer DEBUG: Canvas dimensions set to ${canvas.width}x${canvas.height}`);
    
    // Clear canvas with a visible color for debugging
    ctx.fillStyle = 'rgba(255, 0, 0, 0.1)'; // Very light red for debugging
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // Skip if no differences
    if (!differences || differences.length === 0) {
      console.log('PDFRenderer DEBUG: No differences to highlight');
      return;
    }

    console.log(`PDFRenderer DEBUG: Drawing ${differences.length} differences on canvas`);
    
    setDebugInfo(prev => ({
      ...prev,
      canvasReady: true
    }));
    
    // Draw a debug rectangle to ensure canvas is working
    ctx.fillStyle = 'rgba(255, 0, 0, 0.2)';
    ctx.fillRect(10, 10, 50, 50);
    ctx.fillStyle = 'rgba(0, 255, 0, 0.2)';
    ctx.fillRect(canvas.width - 60, 10, 50, 50);
    
    // Loop through differences and draw them
    differences.forEach((diff, index) => {
      // Skip if no position or bounds data
      if (!diff.position || !diff.bounds) {
        console.log(`PDFRenderer DEBUG: Skipping difference #${index} - missing position or bounds`);
        return;
      }
      
      // Skip if type doesn't match highlight mode (unless mode is 'all')
      if (highlightMode !== 'all' && diff.type !== highlightMode) {
        console.log(`PDFRenderer DEBUG: Skipping difference #${index} - type ${diff.type} doesn't match highlight mode ${highlightMode}`);
        return;
      }
      
      // Apply zoom to coordinates
      const x = diff.position.x * zoom;
      const y = diff.position.y * zoom;
      const width = diff.bounds.width * zoom;
      const height = diff.bounds.height * zoom;
      
      console.log(`PDFRenderer DEBUG: Drawing difference #${index} at (${x},${y}) with size ${width}x${height}`);
      
      // Generate vibrant highlight color
      let fillColor;
      let strokeColor;
      
      switch (diff.type) {
        case 'text':
          fillColor = 'rgba(255, 255, 0, 0.5)'; // Yellow
          strokeColor = 'rgba(255, 200, 0, 0.8)';
          break;
        case 'image':
          fillColor = 'rgba(0, 255, 255, 0.4)'; // Cyan
          strokeColor = 'rgba(0, 200, 255, 0.8)';
          break;
        case 'font':
          fillColor = 'rgba(255, 100, 255, 0.4)'; // Pink
          strokeColor = 'rgba(255, 0, 255, 0.8)';
          break;
        case 'style':
          fillColor = 'rgba(100, 255, 100, 0.4)'; // Green
          strokeColor = 'rgba(0, 255, 0, 0.8)';
          break;
        default:
          fillColor = 'rgba(255, 0, 0, 0.4)'; // Red
          strokeColor = 'rgba(255, 0, 0, 0.8)';
      }
      
      // Draw the highlight with vibrant color
      ctx.fillStyle = fillColor;
      ctx.strokeStyle = strokeColor;
      ctx.lineWidth = 2;
      
      // Draw highlight as rectangle
      ctx.fillRect(x, y, width, height);
      ctx.strokeRect(x, y, width, height);
      
      // Add a number label for debugging
      ctx.fillStyle = 'black';
      ctx.font = '12px Arial';
      ctx.fillText(`#${index}`, x + 5, y + 15);
      
      // If this is the selected difference, add a more visible indicator
      if (selectedDifference && selectedDifference.id === diff.id) {
        ctx.strokeStyle = 'rgba(255, 0, 0, 0.9)';
        ctx.lineWidth = 3;
        ctx.strokeRect(x - 3, y - 3, width + 6, height + 6);
        
        // Label it as 'SELECTED' for visibility
        ctx.fillStyle = 'rgba(255, 0, 0, 0.9)';
        ctx.font = 'bold 14px Arial';
        ctx.fillText('SELECTED', x + 5, y - 5);
      }
    });
  }, [differences, highlightMode, imageLoaded, dimensions, zoom, selectedDifference]);
  
  // Handle click on canvas to select a difference
  const handleCanvasClick = (e) => {
    if (!canvasRef.current || !onDifferenceSelect || !differences || !differences.length) return;
    
    // Get mouse coordinates
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;
    
    // Convert to canvas coordinates
    const canvasX = mouseX * (canvas.width / rect.width);
    const canvasY = mouseY * (canvas.height / rect.height);
    
    console.log(`PDFRenderer DEBUG: Canvas clicked at (${canvasX}, ${canvasY})`);
    
    // Find if we clicked on a difference
    let clickedDiff = null;
    
    for (const diff of differences) {
      if (!diff.position || !diff.bounds) continue;
      
      const x = diff.position.x * zoom;
      const y = diff.position.y * zoom;
      const width = diff.bounds.width * zoom;
      const height = diff.bounds.height * zoom;
      
      if (canvasX >= x && canvasX <= x + width && canvasY >= y && canvasY <= y + height) {
        clickedDiff = diff;
        break;
      }
    }
    
    if (clickedDiff) {
      console.log('PDFRenderer DEBUG: Clicked on difference:', clickedDiff);
      onDifferenceSelect(clickedDiff);
    } else {
      console.log('PDFRenderer DEBUG: Click did not hit any difference');
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
    <div 
      className="pdf-renderer" 
      style={{ 
        opacity,
        position: 'relative',
        border: '2px solid #ccc' // Add border for visibility
      }}
      ref={containerRef}
    >
      {/* Debug info overlay */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          background: 'rgba(0,0,0,0.7)',
          color: 'white',
          padding: '5px',
          fontSize: '12px',
          zIndex: 1000,
          maxWidth: '300px',
          overflowWrap: 'break-word'
        }}
      >
        <div>Diffs: {debugInfo.differencesCount}</div>
        <div>Valid coords: {debugInfo.hasValidDifferences ? 'YES' : 'NO'}</div>
        <div>Canvas ready: {debugInfo.canvasReady ? 'YES' : 'NO'}</div>
        <div>Image loaded: {imageLoaded ? 'YES' : 'NO'}</div>
        <div>Highlight mode: {highlightMode}</div>
      </div>
      
      {/* Loading indicator */}
      {(isLoading || loading) && (
        <div className="renderer-loading">
          <Spinner size="medium" />
          <div className="loading-message">Loading page {page}...</div>
        </div>
      )}
      
      {/* Image and highlights container */}
      <div 
        className="canvas-container" 
        style={{ 
          opacity: imageLoaded ? 1 : 0.3,
          position: 'relative',
          border: '1px dashed red' // Add border for debugging
        }}
      >
        <img 
          ref={imageRef}
          className="pdf-image"
          src={imageUrl}
          alt={`PDF page ${page}`}
          style={{ 
            width: dimensions.width,
            height: dimensions.height,
            border: '1px solid blue' // Add border for debugging
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
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            pointerEvents: 'auto',
            border: '1px solid green', // Add border for debugging
            zIndex: 10
          }}
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