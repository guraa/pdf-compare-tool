import React, { useState, useEffect, useRef, useCallback } from 'react';
import Spinner from '../common/Spinner';
import './PDFRenderer.css';

/**
 * PDFRenderer component that renders PDF pages and highlights differences
 * with proper coordinate scaling
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
  
  // Store actual coordinates for debugging
  const [mouseCoords, setMouseCoords] = useState({x: 0, y: 0});
  
  // Fixed scaling factor - observed from coordinates
  // Y-coordinate is flipped in PDF space (0 is bottom) vs screen space (0 is top)
  const PDF_SCALE_X = 0.33;
  const PDF_SCALE_Y = 0.33;
  const Y_FLIP = false;
  
  // Refs
  const imageRef = useRef(null);
  const canvasRef = useRef(null);
  const containerRef = useRef(null);
  
  // URL for the image
  const imageUrl = `/api/pdfs/document/${fileId}/page/${page}`;
  
  // Log important information when differences change
  useEffect(() => {
    console.log(`PDFRenderer: rendering for fileId=${fileId}, page=${page}, zoom=${zoom}`);
    console.log('PDFRenderer: Differences array:', differences);
    
    // Check if differences have valid coordinates
    const validDiffs = differences.filter(diff => 
      diff && 
      diff.x !== undefined &&
      diff.y !== undefined &&
      diff.width !== undefined &&
      diff.height !== undefined
    );
    
    console.log(`PDFRenderer: Valid differences with coordinates: ${validDiffs.length} of ${differences.length}`);
  }, [differences, fileId, page, zoom]);

  // Function to convert PDF coordinates to canvas/screen coordinates
  const pdfToCanvasCoords = useCallback((pdfX, pdfY, pdfWidth, pdfHeight) => {
    if (!canvasRef.current) return { x: 0, y: 0, width: 0, height: 0 };
    
    const canvas = canvasRef.current;
    
    // Apply PDF scaling factor
    const canvasX = pdfX * PDF_SCALE_X;
    const canvasWidth = pdfWidth * PDF_SCALE_X;
    
    // Handle Y coordinate - can be flipped in PDFs
    let canvasY;
    if (Y_FLIP) {
      canvasY = canvas.height - (pdfY * PDF_SCALE_Y) - (pdfHeight * PDF_SCALE_Y);
    } else {
      canvasY = pdfY * PDF_SCALE_Y;
    }
    const canvasHeight = pdfHeight * PDF_SCALE_Y;
    
    return {
      x: canvasX,
      y: canvasY,
      width: canvasWidth,
      height: canvasHeight
    };
  }, []);
  
  // Function to convert canvas/screen coordinates to PDF coordinates
  const canvasToPdfCoords = useCallback((canvasX, canvasY) => {
    if (!canvasRef.current) return { x: 0, y: 0 };
    
    const canvas = canvasRef.current;
    
    // Apply inverse of PDF scaling factor
    const pdfX = canvasX / PDF_SCALE_X;
    
    // Handle Y coordinate - can be flipped in PDFs
    let pdfY;
    if (Y_FLIP) {
      pdfY = (canvas.height - canvasY) / PDF_SCALE_Y;
    } else {
      pdfY = canvasY / PDF_SCALE_Y;
    }
    
    return {
      x: pdfX,
      y: pdfY
    };
  }, []);

  // Handle image load
  const handleImageLoad = () => {
    console.log(`PDFRenderer: Image loaded successfully: page ${page}`);
    
    if (!imageRef.current) {
      console.error('PDFRenderer: imageRef is null after load!');
      return;
    }
    
    const image = imageRef.current;
    const naturalWidth = image.naturalWidth;
    const naturalHeight = image.naturalHeight;
    
    console.log(`PDFRenderer: Natural image dimensions: ${naturalWidth}x${naturalHeight}`);
    
    const scaledWidth = naturalWidth * zoom;
    const scaledHeight = naturalHeight * zoom;
    
    setDimensions({
      width: scaledWidth,
      height: scaledHeight,
      naturalWidth,
      naturalHeight
    });
    
    setImageLoaded(true);
    setIsLoading(false);
    
    // Call zoom change callback if provided
    if (onZoomChange) {
      onZoomChange(zoom);
    }
  };
  
  // Handle image error
  const handleImageError = (error) => {
    console.error(`PDFRenderer: Error loading image for page ${page}:`, error);
    setRenderError(`Failed to load page ${page}. The server may be unavailable or the document format is not supported.`);
    setIsLoading(false);
  };
  
  // Draw highlights on canvas
  useEffect(() => {
    if (!canvasRef.current || !imageLoaded || highlightMode === 'none') {
      return;
    }
    
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    
    // Set canvas dimensions to match the image
    canvas.width = dimensions.width;
    canvas.height = dimensions.height;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    console.log(`PDFRenderer: Canvas dimensions set to ${canvas.width}x${canvas.height}`);
    
    // Skip if no differences
    if (!differences || differences.length === 0) {
      console.log('PDFRenderer: No differences to highlight');
      return;
    }

    console.log(`PDFRenderer: Drawing ${differences.length} differences on canvas`);
    
    // Draw differences on the canvas
    differences.forEach((diff, index) => {
      // Skip if missing coordinate data
      if (diff.x === undefined || diff.y === undefined || diff.width === undefined || diff.height === undefined) {
        return;
      }
      
      // Skip if type doesn't match highlight mode (unless mode is 'all')
      if (highlightMode !== 'all' && diff.type !== highlightMode) {
        return;
      }
      
      // Convert PDF coordinates to canvas coordinates
      const { x, y, width, height } = pdfToCanvasCoords(diff.x, diff.y, diff.width, diff.height);
      
      console.log(`PDFRenderer: Drawing diff #${index} - PDF: (${diff.x}, ${diff.y}) ${diff.width}x${diff.height}, Canvas: (${x}, ${y}) ${width}x${height}`);
      
      // Generate highlight color based on difference type
      let fillColor;
      let strokeColor;
      
      switch (diff.type) {
        case 'text':
          fillColor = 'rgba(255, 82, 82, 0.3)'; // Red
          strokeColor = 'rgba(255, 82, 82, 0.8)';
          break;
        case 'image':
          fillColor = 'rgba(33, 150, 243, 0.3)'; // Blue
          strokeColor = 'rgba(33, 150, 243, 0.8)';
          break;
        case 'font':
          fillColor = 'rgba(156, 39, 176, 0.3)'; // Purple
          strokeColor = 'rgba(156, 39, 176, 0.8)';
          break;
        case 'style':
          fillColor = 'rgba(255, 152, 0, 0.3)'; // Orange
          strokeColor = 'rgba(255, 152, 0, 0.8)';
          break;
        default:
          fillColor = 'rgba(0, 150, 136, 0.3)'; // Teal
          strokeColor = 'rgba(0, 150, 136, 0.8)';
      }
      
      // Draw the highlight with vibrant color
      ctx.fillStyle = fillColor;
      ctx.strokeStyle = strokeColor;
      ctx.lineWidth = 2;
      
      // Draw highlight as rectangle
      ctx.fillRect(x, y, width, height);
      ctx.strokeRect(x, y, width, height);
      
      // If this is the selected difference, add a more visible indicator
      if (selectedDifference && selectedDifference.id === diff.id) {
        ctx.strokeStyle = 'rgba(255, 255, 0, 0.9)';
        ctx.lineWidth = 3;
        ctx.strokeRect(x - 3, y - 3, width + 6, height + 6);
      }
    });
  }, [differences, highlightMode, imageLoaded, dimensions, zoom, selectedDifference, pdfToCanvasCoords]);
  
  // Handle canvas mouseover for better debugging
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    
    const handleMouseMove = (e) => {
      const canvasRect = canvas.getBoundingClientRect();
      
      // Get mouse position relative to canvas in screen coordinates
      const mouseX = (e.clientX - canvasRect.left) * (canvas.width / canvasRect.width);
      const mouseY = (e.clientY - canvasRect.top) * (canvas.height / canvasRect.height);
      
      // Store for debug overlay
      setMouseCoords({x: mouseX, y: mouseY});
      
      // Convert to PDF coordinates
      const pdfCoords = canvasToPdfCoords(mouseX, mouseY);
      
      // Debug in developer console occasionally to avoid spam
      if (Math.random() < 0.01) { // Only log 1% of movements
        console.log(`Mouse coordinates - Canvas: (${mouseX.toFixed(2)}, ${mouseY.toFixed(2)}), PDF: (${pdfCoords.x.toFixed(2)}, ${pdfCoords.y.toFixed(2)})`);
      }
    };
    
    canvas.addEventListener('mousemove', handleMouseMove);
    
    return () => {
      canvas.removeEventListener('mousemove', handleMouseMove);
    };
  }, [imageLoaded, canvasToPdfCoords]);
  
  // Handle click on canvas to select a difference
  const handleCanvasClick = (e) => {
    if (!canvasRef.current || !onDifferenceSelect || !differences || !differences.length) return;
    
    // Get mouse coordinates
    const canvas = canvasRef.current;
    const canvasRect = canvas.getBoundingClientRect();
    
    // Get click position in canvas coordinates 
    const canvasX = (e.clientX - canvasRect.left) * (canvas.width / canvasRect.width);
    const canvasY = (e.clientY - canvasRect.top) * (canvas.height / canvasRect.height);
    
    // Convert to PDF coordinates
    const pdfCoords = canvasToPdfCoords(canvasX, canvasY);
    
    console.log(`PDFRenderer: Canvas clicked at Canvas: (${canvasX.toFixed(2)}, ${canvasY.toFixed(2)}), PDF: (${pdfCoords.x.toFixed(2)}, ${pdfCoords.y.toFixed(2)})`);
    
    // Find if we clicked on a difference
    let clickedDiff = null;
    
    for (const diff of differences) {
      if (diff.x === undefined || diff.y === undefined || diff.width === undefined || diff.height === undefined) continue;
      
      // Check if the click is inside this difference in PDF space
      if (pdfCoords.x >= diff.x && pdfCoords.x <= diff.x + diff.width && 
          pdfCoords.y >= diff.y && pdfCoords.y <= diff.y + diff.height) {
        clickedDiff = diff;
        break;
      }
    }
    
    if (clickedDiff) {
      console.log('PDFRenderer: Clicked on difference:', clickedDiff);
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
      setDimensions(prev => ({
        ...prev,
        width: image.naturalWidth * zoom,
        height: image.naturalHeight * zoom
      }));
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
      style={{ opacity }}
      ref={containerRef}
    >
      {/* Debug overlay */}
      <div 
        style={{
          position: 'absolute',
          top: 5,
          left: 5,
          background: 'rgba(0,0,0,0.7)',
          color: 'white',
          padding: '4px 8px',
          fontSize: '10px',
          borderRadius: '4px',
          pointerEvents: 'none',
          zIndex: 1000
        }}
      >
        <div>Page: {page} | Zoom: {Math.round(zoom * 100)}%</div>
        <div>Cursor: ({mouseCoords.x.toFixed(0)}, {mouseCoords.y.toFixed(0)})</div>
        <div>Scale: {PDF_SCALE_X}x | Y-flip: {Y_FLIP ? 'Yes' : 'No'}</div>
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
          opacity: imageLoaded ? 1 : 0.3
        }}
      >
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
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            pointerEvents: 'auto',
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