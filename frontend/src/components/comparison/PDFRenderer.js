import React, { useState, useEffect, useRef, useCallback } from 'react';
import { usePreferences } from '../../context/PreferencesContext';
import './PDFRenderer.css';

const PDFRenderer = ({ 
  fileId, 
  page, 
  zoom = 1, 
  highlightMode = 'all',
  differences = [],
  selectedDifference = null,
  onDifferenceSelect,
  loading = false,
  interactive = true,
  opacity = 1,
  onImageLoaded = null // Callback for parent components to know when image is loaded
}) => {
  const [pdfData, setPdfData] = useState(null);
  const [rendered, setRendered] = useState(false);
  const [renderError, setRenderError] = useState(null);
  const [dimensions, setDimensions] = useState({ width: 0, height: 0 });
  const [isImage, setIsImage] = useState(false);
  const [hoveredDiffId, setHoveredDiffId] = useState(null);
  const [hasNotifiedImageLoaded, setHasNotifiedImageLoaded] = useState(false);
  
  const { preferences } = usePreferences();
  const canvasRef = useRef(null);
  const highlightLayerRef = useRef(null);
  const imageRef = useRef(null);
  const tooltipRef = useRef(null);
  
  // Debug logs (only on initial render to avoid spam)
  console.log(`PDFRenderer rendering - fileId: ${fileId}, page: ${page}, zoom: ${zoom}`);
  
  // Reset states when page or fileId changes
  useEffect(() => {
    setPdfData(null);
    setRendered(false);
    setRenderError(null);
    setHasNotifiedImageLoaded(false);
    // Don't reset dimensions here as it causes flickering
  }, [fileId, page]);
  
  // Fetch PDF page data
  useEffect(() => {
    const fetchPdfPage = async () => {
      // Don't refetch if we already have data and nothing has changed
      if (pdfData && rendered) {
        return;
      }
      
      // Ensure we have a valid fileId and page number
      if (!fileId || page === undefined || page === null) {
        console.warn("Missing fileId or page number:", { fileId, page });
        // Set a fallback empty image to prevent UI from being stuck
        setIsImage(true);
        setDimensions({ width: 800, height: 1000 });
        setRendered(true);
        return;
      }
      
      // Convert page numbers starting at 0 to start at 1 for the API
      const apiPage = page < 1 ? 1 : page;
      
      try {
        console.log(`Fetching PDF page for fileId: ${fileId}, page: ${apiPage}`);
        
        // Get the PDF page as a blob
        const response = await fetch(`/api/pdfs/document/${fileId}/page/${apiPage}`);
        
        if (!response.ok) {
          throw new Error(`Failed to fetch page: ${response.status} ${response.statusText}`);
        }
        
        const contentType = response.headers.get('content-type');
        console.log(`Content type for page: ${contentType}`);
        
        const isImageType = contentType && contentType.startsWith('image/');
        setIsImage(isImageType);
        
        // Create a URL for the blob
        const blob = await response.blob();
        console.log(`Received blob of size ${blob.size} bytes`);
        
        const pageUrl = URL.createObjectURL(blob);
        
        setPdfData(pageUrl);
        setRenderError(null);
      } catch (err) {
        console.error('Error fetching PDF page:', err);
        setRenderError('Failed to load page: ' + err.message);
      }
    };
    
    fetchPdfPage();
    
    // Cleanup function to revoke the blob URL
    return () => {
      if (pdfData) {
        URL.revokeObjectURL(pdfData);
      }
    };
  }, [fileId, page, pdfData, rendered]);
  
  // Memoized image load handler to prevent recreation on each render
  const handleImageLoad = useCallback(() => {
    if (imageRef.current) {
      const image = imageRef.current;
      const newWidth = image.naturalWidth * zoom;
      const newHeight = image.naturalHeight * zoom;
      
      console.log(`Image loaded with dimensions: ${image.naturalWidth}x${image.naturalHeight}`);
      
      setDimensions({
        width: newWidth,
        height: newHeight
      });
      
      setRendered(true);
      setRenderError(null);
      
      // Call the onImageLoaded callback if provided and not already called
      if (onImageLoaded && typeof onImageLoaded === 'function' && !hasNotifiedImageLoaded) {
        onImageLoaded(newWidth, newHeight);
        setHasNotifiedImageLoaded(true);
      }
    }
  }, [zoom, onImageLoaded, hasNotifiedImageLoaded]);
  
  // Handle image rendering - now using the event handler correctly
  useEffect(() => {
    if (isImage && pdfData && imageRef.current) {
      const image = imageRef.current;
      
      // Set up event handlers
      image.onload = handleImageLoad;
      
      image.onerror = (err) => {
        console.error('Error loading image:', err);
        setRenderError('Failed to load image');
        setRendered(false);
      };
      
      // Only set src if it's changed
      if (image.src !== pdfData) {
        image.src = pdfData;
      }
    }
  }, [pdfData, isImage, handleImageLoad]);

  // Handle drawing highlights
  useEffect(() => {
    const drawHighlights = () => {
      if (!highlightLayerRef.current || !rendered) return;
      
      const canvas = highlightLayerRef.current;
      const ctx = canvas.getContext('2d');
      
      // Clear the canvas
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      
      // Set canvas size to match image/PDF
      canvas.width = dimensions.width;
      canvas.height = dimensions.height;
      
      // Draw highlights based on mode
      differences.forEach(diff => {
        // Skip if the type doesn't match the highlight mode
        if (highlightMode !== 'all' && diff.type !== highlightMode) {
          return;
        }
        
        // Get highlight color
        const color = getHighlightColor(diff.type, diff.changeType);
        
        // Check if this is the selected difference
        const isSelected = selectedDifference && selectedDifference.id === diff.id;
        
        // Check if this is being hovered
        const isHovered = hoveredDiffId === diff.id;
        
        // Draw the highlight if we have position/bounds
        if (diff.position && diff.bounds) {
          const { x, y } = diff.position;
          const { width, height } = diff.bounds;
          
          // Set highlight style
          ctx.fillStyle = color;
          
          if (isSelected) {
            ctx.strokeStyle = 'rgba(255, 255, 0, 0.8)';
            ctx.lineWidth = 3;
          } else if (isHovered) {
            ctx.strokeStyle = 'rgba(255, 255, 255, 0.8)';
            ctx.lineWidth = 2;
          } else {
            ctx.strokeStyle = color.replace('0.3', '0.8');
            ctx.lineWidth = 1;
          }
          
          // Draw the highlight
          ctx.fillRect(x, y, width, height);
          ctx.strokeRect(x, y, width, height);
        } else {
          console.warn(`Difference ${diff.id} has no position/bounds, cannot highlight`, diff);
        }
      });
    };
    
    // Draw highlights if we have differences
    if (rendered && differences.length > 0) {
      console.log(`Drawing ${differences.length} highlights in mode: ${highlightMode}`);
      drawHighlights();
    }
  }, [rendered, differences, highlightMode, selectedDifference, hoveredDiffId, dimensions]);
  
  // Get color for highlight based on difference type and change type
  const getHighlightColor = (type, changeType) => {
    // Use colors from preferences if available
    if (preferences?.differenceColors && preferences.differenceColors[type]) {
      let baseColor = preferences.differenceColors[type];
      
      // Modify alpha based on change type
      return baseColor.replace(')', ', 0.3)').replace('rgb', 'rgba');
    }
    
    // Default colors
    switch (type) {
      case 'text':
        return 'rgba(255, 82, 82, 0.3)';
      case 'image':
        return 'rgba(33, 150, 243, 0.3)';
      case 'font':
        return 'rgba(156, 39, 176, 0.3)';
      case 'style':
        return 'rgba(255, 152, 0, 0.3)';
      default:
        return 'rgba(128, 128, 128, 0.3)';
    }
  };
  
  const handleHighlightClick = (e) => {
    if (!interactive || !differences.length || !onDifferenceSelect) return;
    
    // Get click coordinates
    const canvas = highlightLayerRef.current;
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    // Scale coordinates for canvas
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    const scaledX = x * scaleX;
    const scaledY = y * scaleY;
    
    // Find the difference that was clicked
    let clickedDiff = null;
    for (const diff of differences) {
      if (diff.position && diff.bounds) {
        const { x: diffX, y: diffY } = diff.position;
        const { width, height } = diff.bounds;
        
        if (
          scaledX >= diffX && 
          scaledX <= diffX + width && 
          scaledY >= diffY && 
          scaledY <= diffY + height
        ) {
          clickedDiff = diff;
          break;
        }
      }
    }
    
    if (clickedDiff) {
      console.log("Clicked difference:", clickedDiff);
      onDifferenceSelect(clickedDiff);
    }
  };
  
  // Handle mouse movement over highlight layer
  const handleMouseMove = (e) => {
    if (!interactive || !differences.length) return;
    
    // Get mouse coordinates
    const canvas = highlightLayerRef.current;
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    // Scale coordinates for canvas
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    const scaledX = x * scaleX;
    const scaledY = y * scaleY;
    
    // Check if mouse is over any difference
    let foundDiff = null;
    for (const diff of differences) {
      if (diff.position && diff.bounds) {
        const { x: diffX, y: diffY } = diff.position;
        const { width, height } = diff.bounds;
        
        if (
          scaledX >= diffX && 
          scaledX <= diffX + width && 
          scaledY >= diffY && 
          scaledY <= diffY + height
        ) {
          foundDiff = diff;
          break;
        }
      }
    }
    
    // Update hovered difference ID
    setHoveredDiffId(foundDiff ? foundDiff.id : null);
    
    // Update tooltip
    if (tooltipRef.current) {
      if (foundDiff) {
        tooltipRef.current.style.display = 'block';
        tooltipRef.current.style.left = `${e.clientX}px`;
        tooltipRef.current.style.top = `${e.clientY - 30}px`;
        tooltipRef.current.textContent = foundDiff.description || `${foundDiff.type} ${foundDiff.changeType}`;
      } else {
        tooltipRef.current.style.display = 'none';
      }
    }
    
    // Update cursor style
    if (canvas) {
      canvas.style.cursor = foundDiff ? 'pointer' : 'default';
    }
  };
  
  // Handle mouse leaving the highlight layer
  const handleMouseLeave = () => {
    setHoveredDiffId(null);
    if (tooltipRef.current) {
      tooltipRef.current.style.display = 'none';
    }
  };

  return (
    <div className="pdf-renderer" style={{ opacity }}>
      {renderError && (
        <div className="render-error">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
          </svg>
          <span>{renderError}</span>
        </div>
      )}
      
      {loading && (
        <div className="renderer-loading">
          <div className="spinner"></div>
        </div>
      )}
      
      <div className="canvas-container" style={{ opacity: rendered ? 1 : 0.3 }}>
        {isImage ? (
          <img 
            ref={imageRef}
            className="pdf-image"
            alt="PDF page"
            style={{ 
              width: dimensions.width, 
              height: dimensions.height,
              display: rendered ? 'block' : 'none'
            }}
          />
        ) : (
          <canvas 
            ref={canvasRef} 
            className="pdf-canvas"
            width={dimensions.width}
            height={dimensions.height}
          />
        )}
        
        <canvas 
          ref={highlightLayerRef}
          className="highlight-layer"
          onClick={handleHighlightClick}
          onMouseMove={interactive ? handleMouseMove : undefined}
          onMouseLeave={interactive ? handleMouseLeave : undefined}
          width={dimensions.width}
          height={dimensions.height}
        />
        
        {interactive && (
          <div 
            ref={tooltipRef} 
            className="diff-tooltip"
            style={{ display: 'none' }}
          >
            Difference
          </div>
        )}
      </div>
      
      {/* Difference count badge */}
      {differences.length > 0 && (
        <div className="diff-count-badge">
          {differences.length}
        </div>
      )}
    </div>
  );
};

export default PDFRenderer;
