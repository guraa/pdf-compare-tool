import React, { useState, useEffect, useRef, useCallback } from 'react';
import { getDocumentPage } from '../../services/api';
import Spinner from '../common/Spinner';
import DifferenceTooltip from './DifferenceTooltip';
import './PDFRenderer.css';

const PDFRenderer = ({
  fileId,
  page,
  zoom = 0.75,
  highlightMode = 'all',
  differences = [],
  selectedDifference = null,
  onDifferenceSelect,
  onZoomChange,
  isBaseDocument = false,
  loading = false,
  pageMetadata = null,
  // Calibration parameters
  xOffsetAdjustment = 0,
  yOffsetAdjustment = 0,
  scaleAdjustment = 1.0,
  flipY = true
}) => {
  // State
  const [imageUrl, setImageUrl] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [imageLoaded, setImageLoaded] = useState(false);
  const [renderError, setRenderError] = useState(null);
  const [dimensions, setDimensions] = useState({ width: 0, height: 0 });
  const [hoverDifference, setHoverDifference] = useState(null);
  const [tooltipPosition, setTooltipPosition] = useState({ x: 0, y: 0 });
  const [scaleFactor, setScaleFactor] = useState(1);
  const [containerOffset, setContainerOffset] = useState({ x: 0, y: 0 });
  const [adjustedCoordinates, setAdjustedCoordinates] = useState([]);
  const [debugMode, setDebugMode] = useState(true); // Set to true to show coordinate values

  // Refs
  const imageRef = useRef(null);
  const canvasRef = useRef(null);
  const containerRef = useRef(null);
  
  // Fetch the page image
  useEffect(() => {
    const fetchPageImage = async () => {
      if (!fileId || !page) return;
      
      try {
        setIsLoading(true);
        const blob = await getDocumentPage(fileId, page, { format: 'png', dpi: 150 });
        const url = URL.createObjectURL(blob);
        setImageUrl(url);
      } catch (err) {
        console.error(`Error loading PDF page ${page}:`, err);
        setRenderError(`Failed to load page ${page}`);
        setIsLoading(false);
      }
    };
    
    fetchPageImage();
    
    // Cleanup
    return () => {
      if (imageUrl) {
        URL.revokeObjectURL(imageUrl);
      }
    };
  }, [fileId, page]);

  // Calculate scale factor once the image is loaded
  useEffect(() => {
    if (imageLoaded && imageRef.current && pageMetadata) {
      const image = imageRef.current;
      
      // Get the natural dimensions of the loaded image
      const imageNaturalWidth = image.naturalWidth;
      const imageNaturalHeight = image.naturalHeight;
      
      // Get the actual PDF dimensions from the metadata
      const pdfWidth = isBaseDocument ? pageMetadata.baseWidth : pageMetadata.compareWidth;
      const pdfHeight = isBaseDocument ? pageMetadata.baseHeight : pageMetadata.compareHeight;
      
      if (pdfWidth && pdfHeight) {
        // Calculate the scale factor between the rendered image and actual PDF
        const calculatedScaleFactor = (imageNaturalWidth / pdfWidth) * scaleAdjustment;
        console.log(`PDFRenderer: PDF dimensions: ${pdfWidth}x${pdfHeight}, Scale factor: ${calculatedScaleFactor}`);
        setScaleFactor(calculatedScaleFactor);
        
        // Measure the container to get offsets
        if (containerRef.current) {
          const containerRect = containerRef.current.getBoundingClientRect();
          const imageRect = image.getBoundingClientRect();
          
          // Calculate the actual offset of the image within the container
          const offsetX = imageRect.left - containerRect.left + xOffsetAdjustment;
          const offsetY = imageRect.top - containerRect.top + yOffsetAdjustment;
          
          console.log(`PDFRenderer: Container offsets: (${offsetX}, ${offsetY})`);
          setContainerOffset({ x: offsetX, y: offsetY });
        }
      } else {
        // Default to a reasonable scale factor if we don't have PDF dimensions
        setScaleFactor(0.75 * scaleAdjustment);
      }
    }
  }, [imageLoaded, isBaseDocument, pageMetadata, scaleAdjustment, xOffsetAdjustment, yOffsetAdjustment]);

  // Pre-calculate and store adjusted coordinates when scale factor changes
  useEffect(() => {
    if (!differences || !scaleFactor || !canvasRef.current) return;
    
    // Log original differences for debugging
    if (differences.length > 0) {
      console.log(`Processing ${differences.length} differences for rendering`);
      console.log('Sample original difference:', differences[0]);
    }
    
    const canvas = canvasRef.current;
    const canvasHeight = canvas.height;
    
    // Transform all difference coordinates
    const transformed = differences.map(diff => {
      // Skip if missing key properties
      if (!diff.id) return null;
      
      // Get coordinates based on the available format
      let x, y, width, height, id, type, changeType, text;
      let originalX, originalY, originalWidth, originalHeight;
      
      if (diff.x !== undefined && diff.y !== undefined) {
        // Format with direct x,y coordinates
        originalX = diff.x;
        originalY = diff.y;
        originalWidth = diff.width || 10;
        originalHeight = diff.height || 10;
      } else if (diff.position && diff.bounds) {
        // Format with position and bounds objects
        originalX = diff.position.x;
        originalY = diff.position.y;
        originalWidth = diff.bounds.width || 10;
        originalHeight = diff.bounds.height || 10;
      } else {
        return null; // Skip if missing coordinate data
      }
      
      // Keep other important properties
      id = diff.id;
      type = diff.type || 'text';
      changeType = diff.changeType;
      text = diff.text || diff.baseText || diff.compareText;
      
      // FIXED COORDINATE TRANSFORMATION:
      
      // 1. Apply scaling to get pixel dimensions
      const scaledX = originalX * scaleFactor * zoom;
      const scaledY = originalY * scaleFactor * zoom;
      const scaledWidth = originalWidth * scaleFactor * zoom;
      const scaledHeight = originalHeight * scaleFactor * zoom;
      
      // 2. Apply container offset to position correctly within the view
      let displayX = scaledX + containerOffset.x;
      
      // 3. Handle Y coordinate flip properly
      // In PDF coordinates, Y=0 is at the bottom. In canvas, Y=0 is at the top.
      let displayY;
      if (flipY) {
        // This is the critical fix - properly flip Y coordinate
        displayY = canvasHeight - scaledY - scaledHeight + containerOffset.y;
      } else {
        displayY = scaledY + containerOffset.y;
      }
      
      // 4. Create the transformed coordinates object with both original and display values
      return {
        id,
        type,
        changeType,
        text,
        // Display coordinates (for rendering)
        x: displayX,
        y: displayY,
        width: scaledWidth,
        height: scaledHeight,
        // Original coordinates (for reference/debugging)
        originalX,
        originalY,
        originalWidth,
        originalHeight,
        // Original difference object
        originalDiff: diff
      };
    }).filter(Boolean); // Remove any null items
    
    if (transformed.length > 0) {
      console.log('Transformed coordinates:', transformed[0]);
    }
    
    setAdjustedCoordinates(transformed);
  }, [differences, scaleFactor, zoom, containerOffset, flipY, dimensions]);

  // Draw highlights on canvas
  useEffect(() => {
    if (!canvasRef.current || !imageLoaded || highlightMode === 'none' || !adjustedCoordinates.length) {
      return;
    }
    
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    
    // Set canvas dimensions to match the image
    canvas.width = dimensions.width;
    canvas.height = dimensions.height;
    
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // Draw differences on the canvas
    adjustedCoordinates.forEach(diff => {
      // Skip if type doesn't match highlight mode (unless mode is 'all')
      if (highlightMode !== 'all' && diff.type !== highlightMode) {
        return;
      }
      
      // Get the transformed coordinates
      const { x, y, width, height, type, originalX, originalY, originalWidth, originalHeight } = diff;
      
      // Generate highlight color based on difference type
      let fillColor;
      let strokeColor;
      
      switch (type) {
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
      
      // Override colors based on change type if available
      if (diff.changeType === 'added') {
        fillColor = 'rgba(76, 175, 80, 0.3)'; // Green for added
        strokeColor = 'rgba(76, 175, 80, 0.8)';
      } else if (diff.changeType === 'deleted') {
        fillColor = 'rgba(244, 67, 54, 0.3)'; // Red for deleted
        strokeColor = 'rgba(244, 67, 54, 0.8)';
      }
      
      // Draw the highlight with vibrant color
      ctx.fillStyle = fillColor;
      ctx.strokeStyle = strokeColor;
      ctx.lineWidth = 2;
      
      // For text differences, use rounded rectangle for better appearance
      if (type === 'text') {
        // Draw rounded rectangle
        const radius = 4;
        ctx.beginPath();
        ctx.moveTo(x + radius, y);
        ctx.lineTo(x + width - radius, y);
        ctx.quadraticCurveTo(x + width, y, x + width, y + radius);
        ctx.lineTo(x + width, y + height - radius);
        ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
        ctx.lineTo(x + radius, y + height);
        ctx.quadraticCurveTo(x, y + height, x, y + height - radius);
        ctx.lineTo(x, y + radius);
        ctx.quadraticCurveTo(x, y, x + radius, y);
        ctx.closePath();
        ctx.fill();
        ctx.stroke();
        
        // Add coordinate values in debug mode
        if (debugMode) {
          ctx.fillStyle = 'rgba(0, 0, 0, 0.7)';
          ctx.font = '10px monospace';
          
          // Format the coordinate values
          const coordText = `PDF: (${Math.round(originalX)},${Math.round(originalY)}) ${Math.round(originalWidth)}x${Math.round(originalHeight)}`;
          const displayText = `Canvas: (${Math.round(x)},${Math.round(y)}) ${Math.round(width)}x${Math.round(height)}`;
          
          // Position the coordinate text
          ctx.fillText(coordText, x + 5, y + height + 12);
          ctx.fillText(displayText, x + 5, y + height + 24);
        }
      } else {
        // For other differences, use standard rectangle
        ctx.fillRect(x, y, width, height);
        ctx.strokeRect(x, y, width, height);
        
        // Add coordinate values in debug mode
        if (debugMode && width > 50 && height > 30) {
          ctx.fillStyle = 'rgba(0, 0, 0, 0.7)';
          ctx.font = '10px monospace';
          
          // Format the coordinate values
          const coordText = `PDF: (${Math.round(originalX)},${Math.round(originalY)}) ${Math.round(originalWidth)}x${Math.round(originalHeight)}`;
          const displayText = `Canvas: (${Math.round(x)},${Math.round(y)}) ${Math.round(width)}x${Math.round(height)}`;
          
          // Position the coordinate text
          ctx.fillText(coordText, x + 5, y + height - 22);
          ctx.fillText(displayText, x + 5, y + height - 10);
        }
      }
      
      // If this is the selected difference, add a more visible indicator
      if (selectedDifference && selectedDifference.id === diff.id) {
        ctx.strokeStyle = 'rgba(255, 255, 0, 0.9)';
        ctx.lineWidth = 3;
        ctx.strokeRect(x - 3, y - 3, width + 6, height + 6);
      }
    });
  }, [adjustedCoordinates, highlightMode, dimensions, selectedDifference, imageLoaded, debugMode]);

  // Handle image load
  const handleImageLoad = (e) => {
    if (!imageRef.current) return;
    
    const image = imageRef.current;
    const naturalWidth = image.naturalWidth;
    const naturalHeight = image.naturalHeight;
    
    const scaledWidth = naturalWidth * zoom;
    const scaledHeight = naturalHeight * zoom;
    
    console.log(`Image loaded with dimensions: ${naturalWidth}x${naturalHeight}`);
    
    setDimensions({
      width: scaledWidth,
      height: scaledHeight,
      naturalWidth,
      naturalHeight
    });
    
    setImageLoaded(true);
    setIsLoading(false);
  };

  // Handle image error
  const handleImageError = (error) => {
    console.error(`Error loading PDF page ${page}:`, error);
    setRenderError(`Failed to load page ${page}`);
    setIsLoading(false);
  };

  // Handle canvas mouseover
  const handleCanvasMouseMove = (e) => {
    if (!canvasRef.current || !adjustedCoordinates?.length) return;
    
    // Get mouse position relative to canvas
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();
    const mouseX = (e.clientX - rect.left) * (canvas.width / rect.width);
    const mouseY = (e.clientY - rect.top) * (canvas.height / rect.height);
    
    // Find if mouse is over a difference
    let hoveredDiff = null;
    
    for (const diff of adjustedCoordinates) {
      // Check if the mouse is inside this difference
      if (
        mouseX >= diff.x &&
        mouseX <= diff.x + diff.width &&
        mouseY >= diff.y &&
        mouseY <= diff.y + diff.height
      ) {
        hoveredDiff = diff.originalDiff;
        break;
      }
    }
    
    // Update hover state and tooltip position
    if (hoveredDiff) {
      setHoverDifference(hoveredDiff);
      setTooltipPosition({ x: e.clientX, y: e.clientY });
    } else {
      setHoverDifference(null);
    }
  };

  // Handle canvas mouseout
  const handleCanvasMouseOut = () => {
    setHoverDifference(null);
  };

  // Handle click on canvas to select a difference
  const handleCanvasClick = (e) => {
    if (!canvasRef.current || !onDifferenceSelect || !adjustedCoordinates?.length) return;
    
    // Get mouse coordinates
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();
    const mouseX = (e.clientX - rect.left) * (canvas.width / rect.width);
    const mouseY = (e.clientY - rect.top) * (canvas.height / rect.height);
    
    // Find if we clicked on a difference
    let clickedDiff = null;
    
    for (const diff of adjustedCoordinates) {
      // Check if the click is inside this difference
      if (
        mouseX >= diff.x &&
        mouseX <= diff.x + diff.width &&
        mouseY >= diff.y &&
        mouseY <= diff.y + diff.height
      ) {
        clickedDiff = diff.originalDiff;
        break;
      }
    }
    
    if (clickedDiff) {
      onDifferenceSelect(clickedDiff);
    }
  };

  // Toggle debug mode
  const toggleDebugMode = () => {
    setDebugMode(!debugMode);
  };

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
      <div className="pdf-renderer-empty">
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
    <div className="pdf-renderer" ref={containerRef}>
      {/* Debug mode toggle */}
      <button 
        className="debug-toggle"
        onClick={toggleDebugMode}
        style={{
          position: 'absolute',
          top: '5px',
          right: '5px',
          zIndex: 20,
          background: debugMode ? 'rgba(76, 175, 80, 0.7)' : 'rgba(0, 0, 0, 0.5)',
          color: 'white',
          border: 'none',
          borderRadius: '4px',
          padding: '4px 8px',
          fontSize: '12px',
          cursor: 'pointer'
        }}
      >
        {debugMode ? 'Debug: ON' : 'Debug: OFF'}
      </button>
      
      {/* Debug info */}
      {debugMode && (
        <div
          style={{
            position: 'absolute',
            top: '30px',
            right: '5px',
            background: 'rgba(0,0,0,0.7)',
            color: 'white',
            padding: '8px',
            borderRadius: '4px',
            fontSize: '10px',
            maxWidth: '200px',
            zIndex: 20
          }}
        >
          <div>Page: {page}</div>
          <div>Scale: {scaleFactor.toFixed(3)}</div>
          <div>Zoom: {zoom.toFixed(2)}</div>
          <div>Offset: ({Math.round(containerOffset.x)}, {Math.round(containerOffset.y)})</div>
          <div>FlipY: {flipY ? 'Yes' : 'No'}</div>
          <div>Differences: {adjustedCoordinates.length}</div>
        </div>
      )}
      
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
        {imageUrl && (
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
        )}
        
        <canvas
          ref={canvasRef}
          className="highlight-layer"
          onClick={handleCanvasClick}
          onMouseMove={handleCanvasMouseMove}
          onMouseOut={handleCanvasMouseOut}
          width={dimensions.width}
          height={dimensions.height}
        />
      </div>
      
      {/* Difference count badge */}
      {adjustedCoordinates.length > 0 && imageLoaded && (
        <div className="diff-count-badge">{adjustedCoordinates.length}</div>
      )}
      
      {/* Tooltip for difference hover */}
      <DifferenceTooltip
        difference={hoverDifference}
        visible={!!hoverDifference}
        x={tooltipPosition.x}
        y={tooltipPosition.y}
      />
    </div>
  );
};

export default PDFRenderer;