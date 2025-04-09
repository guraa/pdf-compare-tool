import React, { useState, useEffect, useRef, useCallback } from 'react';
import { usePreferences } from '../../context/PreferencesContext';
import Spinner from '../common/Spinner';
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
  const [loadingPage, setLoadingPage] = useState(false);
  const [pageExists, setPageExists] = useState(true);
  const [retryCount, setRetryCount] = useState(0);
  const [hasNotifiedImageLoaded, setHasNotifiedImageLoaded] = useState(false);
  
  const { preferences } = usePreferences();
  const canvasRef = useRef(null);
  const highlightLayerRef = useRef(null);
  const imageRef = useRef(null);
  const tooltipRef = useRef(null);
  const loadingTimeoutRef = useRef(null);
  
  // Debug logs with better information
  console.log(`PDFRenderer rendering - fileId: ${fileId}, page: ${page}, zoom: ${zoom}, pageExists: ${pageExists}`);
  
  // Reset states when page or fileId changes
  useEffect(() => {
    setPdfData(null);
    setRendered(false);
    setRenderError(null);
    setHasNotifiedImageLoaded(false);
    setPageExists(true); // Reset assumption that page exists
    setLoadingPage(true);
    
    // Clear any existing timeout
    if (loadingTimeoutRef.current) {
      clearTimeout(loadingTimeoutRef.current);
    }
    
    // Set a timeout to report an error if loading takes too long
    loadingTimeoutRef.current = setTimeout(() => {
      if (!rendered) {
        console.warn(`Loading timed out for fileId: ${fileId}, page: ${page}`);
        setRenderError('Loading timed out. The document may be too large or unavailable.');
        setLoadingPage(false);
      }
    }, 15000); // 15 seconds timeout
    
    return () => {
      if (loadingTimeoutRef.current) {
        clearTimeout(loadingTimeoutRef.current);
      }
    };
  }, [fileId, page]);
  
  // Clean up blob URL when component unmounts
  useEffect(() => {
    return () => {
      if (pdfData) {
        URL.revokeObjectURL(pdfData);
      }
    };
  }, [pdfData]);
  
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
        setRenderError('Missing document ID or page number');
        setLoadingPage(false);
        setPageExists(false);
        return;
      }
      
      // Convert page numbers starting at 0 to start at 1 for the API
      const apiPage = Math.max(1, page);
      
      try {
        console.log(`Fetching PDF page for fileId: ${fileId}, page: ${apiPage}`);
        setLoadingPage(true);
        
        // Get the PDF page as a blob
        const response = await fetch(`/api/pdfs/document/${fileId}/page/${apiPage}`, {
          // Add error handling for non-200 responses
          headers: {
            'Accept': 'image/png, application/json'
          }
        });
        
        if (!response.ok) {
          if (response.status === 404) {
            console.warn(`Page ${apiPage} not found for document ${fileId}`);
            setPageExists(false);
            setRenderError(`Page ${apiPage} does not exist in this document`);
            setLoadingPage(false);
            return;
          }
          
          throw new Error(`Failed to fetch page: ${response.status} ${response.statusText}`);
        }
        
        const contentType = response.headers.get('content-type');
        console.log(`Content type for page: ${contentType}`);
        
        const isImageType = contentType && contentType.startsWith('image/');
        setIsImage(isImageType);
        
        if (!isImageType) {
          console.warn(`Unexpected content type: ${contentType}`);
          setRenderError(`Unexpected document format: ${contentType}`);
          setLoadingPage(false);
          return;
        }
        
        // Create a URL for the blob
        const blob = await response.blob();
        console.log(`Received blob of size ${blob.size} bytes`);
        
        // Check if we received an empty blob or very small blob (might be an error)
        if (blob.size < 100) {
          console.warn(`Received suspiciously small blob: ${blob.size} bytes`);
          setRenderError('Received invalid document data');
          setLoadingPage(false);
          return;
        }
        
        const pageUrl = URL.createObjectURL(blob);
        
        setPdfData(pageUrl);
        setRenderError(null);
        setPageExists(true);
      } catch (err) {
        console.error('Error fetching PDF page:', err);
        setRenderError(`Failed to load page: ${err.message}`);
        setLoadingPage(false);
        
        // Increment retry count for potential retry logic
        setRetryCount(prev => prev + 1);
      }
    };
    
    fetchPdfPage();
  }, [fileId, page, rendered, pdfData, retryCount]);
  
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
      setLoadingPage(false);
      
      // Clear loading timeout since image loaded successfully
      if (loadingTimeoutRef.current) {
        clearTimeout(loadingTimeoutRef.current);
        loadingTimeoutRef.current = null;
      }
      
      // Call the onImageLoaded callback if provided and not already called
      if (onImageLoaded && typeof onImageLoaded === 'function' && !hasNotifiedImageLoaded) {
        onImageLoaded(newWidth, newHeight);
        setHasNotifiedImageLoaded(true);
      }
    }
  }, [zoom, onImageLoaded, hasNotifiedImageLoaded]);
  
  // Handle image error with better error reporting
  const handleImageError = useCallback((err) => {
    console.error('Error loading image:', err);
    setRenderError('Failed to load image - The document may be corrupted or in an unsupported format');
    setRendered(false);
    setLoadingPage(false);
  }, []);
  
  // Handle image rendering - now using the event handler correctly
  useEffect(() => {
    if (isImage && pdfData && imageRef.current) {
      const image = imageRef.current;
      
      // Set up event handlers
      image.onload = handleImageLoad;
      image.onerror = handleImageError;
      
      // Only set src if it's changed
      if (image.src !== pdfData) {
        image.src = pdfData;
      }
    }
  }, [pdfData, isImage, handleImageLoad, handleImageError]);

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
      
      // Only draw highlights if we have differences to show
      if (!differences || differences.length === 0) {
        return;
      }
      
      // Draw highlights based on mode
      differences.forEach(diff => {
        // Skip if the type doesn't match the highlight mode
        if (highlightMode !== 'all' && diff.type !== highlightMode) {
          return;
        }
        
        // Skip if position or bounds are missing
        if (!diff.position || !diff.bounds) {
          console.warn(`Difference ${diff.id} has no position/bounds, cannot highlight`, diff);
          return;
        }
        
        // Get highlight color
        const color = getHighlightColor(diff.type, diff.changeType);
        
        // Check if this is the selected difference
        const isSelected = selectedDifference && selectedDifference.id === diff.id;
        
        // Check if this is being hovered
        const isHovered = hoveredDiffId === diff.id;
        
        // Get position and bounds with safety checks
        const x = diff.position.x || 0;
        const y = diff.position.y || 0;
        const width = diff.bounds.width || 20;
        const height = diff.bounds.height || 20;
        
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
      });
    };
    
    // Draw highlights if we have differences
    if (rendered && differences && differences.length > 0) {
      console.log(`Drawing ${differences.length} highlights in mode: ${highlightMode}`);
      drawHighlights();
    }
  }, [rendered, differences, highlightMode, selectedDifference, hoveredDiffId, dimensions]);
  
  // Get color for highlight based on difference type and change type
  const getHighlightColor = (type, changeType) => {
    // Use colors from preferences if available
    if (preferences?.differenceColors && preferences.differenceColors[type]) {
      let baseColor = preferences.differenceColors[type];
      
      // Convert hex to rgba if needed
      if (baseColor.startsWith('#')) {
        // Convert hex to rgb
        const r = parseInt(baseColor.slice(1, 3), 16);
        const g = parseInt(baseColor.slice(3, 5), 16);
        const b = parseInt(baseColor.slice(5, 7), 16);
        baseColor = `rgba(${r}, ${g}, ${b}, 0.3)`;
      } else if (baseColor.startsWith('rgb(')) {
        // Convert rgb to rgba
        baseColor = baseColor.replace('rgb', 'rgba').replace(')', ', 0.3)');
      }
      
      return baseColor;
    }
    
    // Default colors by change type if available
    if (changeType) {
      switch (changeType) {
        case 'added':
          return 'rgba(76, 175, 80, 0.3)'; // Green for added
        case 'deleted':
          return 'rgba(244, 67, 54, 0.3)'; // Red for deleted
        case 'modified':
          return 'rgba(255, 152, 0, 0.3)'; // Orange for modified
        default:
          break;
      }
    }
    
    // Default colors by difference type
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
    if (!interactive || !differences || !differences.length || !onDifferenceSelect) return;
    
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
    if (!interactive || !differences || !differences.length) return;
    
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
        tooltipRef.current.textContent = foundDiff.description || `${foundDiff.type || 'Unknown'} ${foundDiff.changeType || 'change'}`;
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

  // Render empty placeholder when page doesn't exist
  if (!pageExists) {
    return (
      <div className="pdf-renderer-empty" style={{ opacity }}>
        <div className="pdf-error-message">
          <div className="error-icon">
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M19 5v14H5V5h14m0-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z"/>
              <path d="M14.14 12l3.64-3.64c.29-.29.29-.77 0-1.06-.29-.29-.77-.29-1.06 0L13.08 10.94 9.44 7.3c-.29-.29-.77-.29-1.06 0-.29.29-.29.77 0 1.06L12.02 12l-3.64 3.64c-.29.29-.29.77 0 1.06.15.15.34.22.53.22s.38-.07.53-.22l3.64-3.64 3.64 3.64c.15.15.34.22.53.22s.38-.07.53-.22c.29-.29.29-.77 0-1.06L14.14 12z"/>
            </svg>
          </div>
          <p>Page {page} not found</p>
          <p>This page does not exist in the document</p>
        </div>
      </div>
    );
  }

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
      
      {(loadingPage || loading) && (
        <div className="renderer-loading">
          <Spinner size="medium" />
          <div className="loading-message">Loading page {page}...</div>
        </div>
      )}
      
      <div className="canvas-container" style={{ opacity: rendered ? 1 : 0.3 }}>
        {isImage ? (
          <img 
            ref={imageRef}
            className="pdf-image"
            alt={`PDF page ${page}`}
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
      {differences && differences.length > 0 && (
        <div className="diff-count-badge">
          {differences.length}
        </div>
      )}
    </div>
  );
};

export default PDFRenderer;
