import React, { useState, useEffect, useRef } from 'react';
import { getDocumentPage } from '../../services/api';
import Spinner from '../common/Spinner';
import DifferenceTooltip from './DifferenceTooltip';
import './PDFRenderer.css';

/**
 * PDFRenderer with targeted coordinate adjustments optimized for your specific document structure.
 * Improved zone-based adjustment system to fix alignment issues in the middle sections.
 */
const PDFRenderer = ({
  fileId,
  page,
  zoom = 0.5,
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
  flipY = true,
  debugMode = false,
  // Scaling algorithm parameters
  scalingAlgorithm = 'quadratic',
  maxScalingAdjustment = 15
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
  const [baseCoordinates, setBaseCoordinates] = useState([]); // State for base coordinates before dynamic adjustment
  const [adjustedCoordinates, setAdjustedCoordinates] = useState([]); // State for final coordinates after dynamic adjustment

  // Refs
  const imageRef = useRef(null);
  const containerRef = useRef(null);
  const canvasRef = useRef(null);
  
  // Fetch the page image
  useEffect(() => {
    const fetchPageImage = async () => {
      if (!fileId || !page) return;
      
      try {
        setIsLoading(true);
        setRenderError(null);
        console.log(`Fetching page ${page} for fileId ${fileId.substring(0, 8)}...`);
        
        const blob = await getDocumentPage(fileId, page, { format: 'png', dpi: 150 });
        const url = URL.createObjectURL(blob);
        setImageUrl(url);
        
        console.log(`Successfully fetched page ${page}`);
      } catch (err) {
        console.error(`Error loading PDF page ${page}:`, err);
        setRenderError(`Failed to load page ${page}: ${err.message}`);
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
    if (imageLoaded && imageRef.current) {
      const image = imageRef.current;
      
      // Get the natural dimensions of the loaded image
      const imageNaturalWidth = image.naturalWidth;
      const imageNaturalHeight = image.naturalHeight;
      
      // Standard PDF dimensions (letter size in points)
      const pdfWidth = 612;
      const pdfHeight = 792;
      
      // Calculate the scale factor between the rendered image and actual PDF
      const xRatio = imageNaturalWidth / pdfWidth;
      const yRatio = imageNaturalHeight / pdfHeight;
      const calculatedScaleFactor = xRatio * scaleAdjustment;
      
      console.log(`Image loaded with dimensions: ${imageNaturalWidth}x${imageNaturalHeight}`);
      console.log(`Scale factor: ${calculatedScaleFactor}`);
      
      setScaleFactor(calculatedScaleFactor);
      
      // Measure the container to get offsets
      if (containerRef.current) {
        const containerRect = containerRef.current.getBoundingClientRect();
        const imageRect = image.getBoundingClientRect();
        
        // Calculate the offset of the image within the container
        const offsetX = imageRect.left - containerRect.left + xOffsetAdjustment;
        const offsetY = imageRect.top - containerRect.top + yOffsetAdjustment;
        
        setContainerOffset({ x: offsetX, y: offsetY });
        
        if (debugMode) {
          console.log(`Container offsets: x=${offsetX}, y=${offsetY}`);
        }
      }
    }
  }, [imageLoaded, scaleAdjustment, xOffsetAdjustment, yOffsetAdjustment, debugMode]);

  // Effect 1: Calculate base coordinates (scaling, flipping, offsetting)
  useEffect(() => {
    if (!differences || !scaleFactor || !imageRef.current) {
      setBaseCoordinates([]); // Clear if no input
      return;
    }

    console.log(`[Base Calc] Processing ${differences.length} differences for page ${page}`);
    const image = imageRef.current;
    const imageWidth = image.naturalWidth;
    const imageHeight = image.naturalHeight;
    
    // Standard PDF dimensions
    const pdfWidth = 612;  // Letter width in points
    const pdfHeight = 792; // Letter height in points
    
    // Calculate ratios
    const xRatio = imageWidth / pdfWidth;
    const yRatio = imageHeight / pdfHeight;
    
    // Calculate base transformed coordinates
    const transformedBase = differences.map(diff => {
      // Skip if missing key properties
      if (!diff) return null;

      // Create an ID if one doesn't exist
      const id = diff.id || `diff-${Math.random().toString(36).substr(2, 9)}`;
      
      // Get coordinates based on the format
      let originalX, originalY, originalWidth, originalHeight;
      let text, baseText, compareText, type, changeType;
      
      // Special case: Base coordinates are zero but compare coordinates exist
      if (isBaseDocument && diff.baseX === 0 && diff.baseY === 0 && diff.compareX && diff.compareY) {
        // Use the compare coordinates as a fallback for base document
        originalX = diff.compareX;
        originalY = diff.compareY;
        originalWidth = diff.compareWidth || 50;
        originalHeight = diff.compareHeight || 20;
        text = diff.baseText || diff.compareText || '';
        console.log('Using compare coordinates for base document due to zero base coordinates');
      } 
      // Handle the new format
      else if (diff.baseX !== undefined || diff.compareX !== undefined) {
        // Use base coordinates if this is base document, otherwise use compare
        if (isBaseDocument) {
          originalX = diff.baseX || 0;
          originalY = diff.baseY || 0;
          originalWidth = diff.baseWidth || 50;
          originalHeight = diff.baseHeight || 20;
          text = diff.baseText || '';
        } else {
          originalX = diff.compareX || 0;
          originalY = diff.compareY || 0;
          originalWidth = diff.compareWidth || 50;
          originalHeight = diff.compareHeight || 20;
          text = diff.compareText || '';
        }
        
        baseText = diff.baseText;
        compareText = diff.compareText;
      } 
      // Handle older format
      else if (diff.x !== undefined && diff.y !== undefined) {
        originalX = diff.x;
        originalY = diff.y;
        originalWidth = diff.width || 10;
        originalHeight = diff.height || 10;
        text = diff.text || '';
      } 
      // Handle position/bounds format
      else if (diff.position && diff.bounds) {
        originalX = diff.position.x;
        originalY = diff.position.y;
        originalWidth = diff.bounds.width || 10;
        originalHeight = diff.bounds.height || 10;
        text = diff.text || '';
      } 
      // If we don't have coordinates, create default ones
      else {
        originalX = 50;
        originalY = 50;
        originalWidth = 100;
        originalHeight = 30;
        text = diff.baseText || diff.compareText || diff.text || '';
      }
      
      // Keep other important properties
      type = diff.type || 'text';
      changeType = diff.changeType;
      
      // Apply precise scaling to get pixel dimensions
      const scaledX = originalX * xRatio * zoom;
      const scaledY = originalY * yRatio * zoom;
      const scaledWidth = Math.max(originalWidth * xRatio * zoom, 5);
      const scaledHeight = Math.max(originalHeight * yRatio * zoom, 5);
      
      // Apply container offset
      let displayX = scaledX + containerOffset.x;
      let displayY; // Declare displayY here

      // Handle Y coordinate flip
      if (flipY) {
        // Calculate the standard flipped Y coordinate
        const pixelPdfHeight = pdfHeight * yRatio * zoom;
        displayY = pixelPdfHeight - scaledY - scaledHeight + containerOffset.y;
        
        // Log critical coordinate for debugging
        if (Math.abs(originalY - 427.7) < 1) {
          console.log(`CRITICAL COORDINATE DEBUG for Y=${originalY}:`, {
            originalY,
            scaledY,
            pixelPdfHeight,
            standardFlippedY: displayY
          });
        }
        
        // Apply dynamic scaling adjustments based on the selected algorithm
        if (maxScalingAdjustment > 0) {
          const normalizedY = originalY / pdfHeight; // Normalize Y coordinate (0 at top, 1 at bottom)
          let adjustment = 0;

          switch (scalingAlgorithm) {
            case 'linear':
              // Linear adjustment (more adjustment at the top, less at the bottom)
              adjustment = maxScalingAdjustment * (1 - normalizedY);
              break;
            case 'quadratic':
              // Quadratic adjustment (parabolic - max adjustment in the middle)
              adjustment = 4 * maxScalingAdjustment * normalizedY * (1 - normalizedY);
              break;
            case 'sigmoid':
              // Sigmoid adjustment (S-curve - sharp change in the middle)
              // The factor 10 controls the steepness of the curve
              const sigmoidFactor = 1 / (1 + Math.exp(-10 * (normalizedY - 0.5)));
              adjustment = maxScalingAdjustment * (1 - sigmoidFactor); // Adjusts more towards the top
              break;
            case 'inversed':
              // Inverse quadratic (min adjustment in the middle, max at top/bottom)
              adjustment = maxScalingAdjustment * (1 - 4 * normalizedY * (1 - normalizedY));
              break;
            default:
              // Default to quadratic if algorithm is unknown
              adjustment = 4 * maxScalingAdjustment * normalizedY * (1 - normalizedY);
          }

          // Apply the calculated adjustment, scaled by zoom
          // We subtract because a positive adjustment should move the highlight UP (decrease Y)
          displayY -= adjustment * zoom;

          if (debugMode && Math.abs(originalY - 427.7) < 1) {
             console.log(`[SCALING DEBUG] Y=${originalY}, Algorithm=${scalingAlgorithm}, MaxAdj=${maxScalingAdjustment}, Adjustment=${adjustment.toFixed(2)}`);
          }
        }

      } else {
        // If not flipping Y, just apply the offset
        displayY = scaledY + containerOffset.y; // Assign to displayY
      }

      // Create the base transformed coordinates object
      return {
        id,
        type,
        changeType,
        text,
        baseText,
        compareText,
        // Base display coordinates (before dynamic adjustment)
        x: displayX,
        y: displayY, // Use displayY which is now correctly scoped
        width: scaledWidth,
        height: scaledHeight,
        // Original coordinates needed for adjustment
        originalX,
        originalY,
        originalWidth,
        originalHeight,
        // Original difference object
        originalDiff: diff,
        originalY // Pass originalY for dynamic adjustment calculation
      };
    }).filter(Boolean); // Remove any null items

    setBaseCoordinates(transformedBase);

    if (debugMode) {
      console.log(`[Base Calc] Calculated ${transformedBase.length} base coordinates for page ${page}.`);
      if (transformedBase.length > 0) {
         console.log("[Base Calc] Example base coordinate:", transformedBase[0]);
      }
    }
  // This effect should ONLY run when the core difference data or scaling/offset factors change
  }, [differences, scaleFactor, zoom, containerOffset, flipY, dimensions, isBaseDocument, debugMode, page, xOffsetAdjustment, yOffsetAdjustment, scaleAdjustment]);


  // Effect 2: Apply dynamic adjustment to base coordinates
  useEffect(() => {
    if (!baseCoordinates || baseCoordinates.length === 0) {
      setAdjustedCoordinates([]); // Ensure adjusted is cleared if base is empty
      return;
    }

    const pdfHeight = 792; // Standard PDF height

    console.log(`[Adjustment] Applying ${scalingAlgorithm} (max: ${maxScalingAdjustment}) to ${baseCoordinates.length} base coordinates.`);

    const finalCoordinates = baseCoordinates.map(baseCoord => {
      let finalY = baseCoord.y; // Start with base Y

      // Apply dynamic scaling adjustments based on the selected algorithm
      // Only apply if flipping Y (as coordinates are relative to top-left otherwise)
      // and if there's an adjustment value set
      if (flipY && maxScalingAdjustment > 0) {
          const normalizedY = baseCoord.originalY / pdfHeight; // Normalize original Y
          let adjustment = 0;

          switch (scalingAlgorithm) {
            case 'linear':
              adjustment = maxScalingAdjustment * (1 - normalizedY);
              break;
            case 'quadratic':
              adjustment = 4 * maxScalingAdjustment * normalizedY * (1 - normalizedY);
              break;
            case 'sigmoid':
              const sigmoidFactor = 1 / (1 + Math.exp(-10 * (normalizedY - 0.5)));
              adjustment = maxScalingAdjustment * (1 - sigmoidFactor);
              break;
            case 'inversed':
              adjustment = maxScalingAdjustment * (1 - 4 * normalizedY * (1 - normalizedY));
              break;
            default: // Default to quadratic
              adjustment = 4 * maxScalingAdjustment * normalizedY * (1 - normalizedY);
          }
          // Subtract adjustment scaled by zoom
          finalY -= adjustment * zoom;
      }

      return {
        ...baseCoord, // Spread all properties from base coordinate
        y: finalY // Override Y with the final adjusted value
      };
    });

    setAdjustedCoordinates(finalCoordinates);

    // Debugging logs
    if (debugMode) {
      console.log(`[Adjustment] Produced ${finalCoordinates.length} adjusted coordinates.`);
      if (finalCoordinates.length > 0) {
        const example = finalCoordinates[0];
        const baseExample = baseCoordinates.find(bc => bc.id === example.id);
        console.log("[Adjustment] Example adjusted coordinate:", {
           id: example.id,
           originalY: example.originalY,
           baseY: baseExample?.y.toFixed(2),
           adjustedY: example.y.toFixed(2),
           adjustment: (baseExample?.y - example.y).toFixed(2)
        });
      }
    }
  // This effect ONLY runs when base coordinates change or adjustment parameters change
  }, [baseCoordinates, scalingAlgorithm, maxScalingAdjustment, flipY, zoom, debugMode]);


  // Effect 3: Draw highlights using final adjustedCoordinates
  useEffect(() => {
    // Check dependencies needed for drawing itself
    if (!canvasRef.current || !imageLoaded || highlightMode === 'none') {
       // Clear canvas if no highlights or not loaded
       if (canvasRef.current) {
         const canvas = canvasRef.current;
         const ctx = canvas.getContext('2d');
         ctx.clearRect(0, 0, canvas.width, canvas.height);
       }
      return;
    }

    console.log(`[Draw] Drawing ${adjustedCoordinates.length} highlights. Mode: ${highlightMode}, Zoom: ${zoom}`);
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    
    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // Set canvas size to match image dimensions
    canvas.width = dimensions.width;
    canvas.height = dimensions.height;
    
    // Draw each highlight using the final 'adjustedCoordinates'
    adjustedCoordinates.forEach(diff => {
      // Skip if this type is not included in highlight mode
      if (highlightMode !== 'all' && diff.type !== highlightMode && !(diff.changeType && highlightMode === 'changes')) { // Added check for 'changes' mode if implemented
        return;
      }
      
      // Get color based on type and change type
      let fillColor, strokeColor;
      
      if (diff.changeType === 'added') {
        fillColor = 'rgba(76, 175, 80, 0.3)'; // Green
        strokeColor = 'rgba(76, 175, 80, 0.8)';
      } else if (diff.changeType === 'deleted') {
        fillColor = 'rgba(244, 67, 54, 0.3)'; // Red
        strokeColor = 'rgba(244, 67, 54, 0.8)';
      } else if (diff.changeType === 'modified') {
        fillColor = 'rgba(255, 152, 0, 0.3)'; // Orange
        strokeColor = 'rgba(255, 152, 0, 0.8)';
      } else {
        // Default colors based on type
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
            fillColor = 'rgba(158, 158, 158, 0.3)'; // Gray
            strokeColor = 'rgba(158, 158, 158, 0.8)';
        }
      }
      
      // Set styles
      ctx.fillStyle = fillColor;
      ctx.strokeStyle = strokeColor;
      ctx.lineWidth = 2;
      
      // Check if this is the selected difference
      const isSelected = selectedDifference && (
        selectedDifference.id === diff.id || 
        (selectedDifference.originalDiff && selectedDifference.originalDiff.id === diff.id)
      );
      
      // Draw highlight
      ctx.beginPath();
      
      // Use rounded rectangle if available
      if (ctx.roundRect) {
        ctx.roundRect(diff.x, diff.y, diff.width, diff.height, 3);
      } else {
        // Fallback for browsers without roundRect support
        ctx.rect(diff.x, diff.y, diff.width, diff.height);
      }
      
      ctx.fill();
      ctx.stroke();
      
      // Add extra highlight for selected difference
      if (isSelected) {
        ctx.strokeStyle = 'rgba(255, 255, 0, 0.8)'; // Yellow
        ctx.lineWidth = 3;
        ctx.beginPath();
        
        if (ctx.roundRect) {
          ctx.roundRect(diff.x - 2, diff.y - 2, diff.width + 4, diff.height + 4, 5);
        } else {
          ctx.rect(diff.x - 2, diff.y - 2, diff.width + 4, diff.height + 4);
        }
        
        ctx.stroke();
      }
    });

  // This effect ONLY runs when the final coordinates or visual parameters change
  }, [adjustedCoordinates, zoom, highlightMode, selectedDifference, dimensions, imageLoaded]);


  // Handle mouse events for interaction with differences
  const handleCanvasClick = (e) => {
    if (!adjustedCoordinates || adjustedCoordinates.length === 0 || !onDifferenceSelect) {
      return;
    }
    
    const rect = canvasRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    // Find if we clicked on a difference
    const clickedDiff = adjustedCoordinates.find(diff => 
      x >= diff.x && 
      x <= diff.x + diff.width && 
      y >= diff.y && 
      y <= diff.y + diff.height
    );
    
    if (clickedDiff) {
      console.log('Clicked on difference:', clickedDiff);
      onDifferenceSelect(clickedDiff.originalDiff);
    }
  };
  
  const handleCanvasMouseMove = (e) => {
    if (!adjustedCoordinates || adjustedCoordinates.length === 0) {
      return;
    }
    
    const rect = canvasRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    // Find if mouse is over a difference
    const hoverDiff = adjustedCoordinates.find(diff => 
      x >= diff.x && 
      x <= diff.x + diff.width && 
      y >= diff.y && 
      y <= diff.y + diff.height
    );
    
    if (hoverDiff) {
      setHoverDifference(hoverDiff.originalDiff);
      setTooltipPosition({ x: e.clientX, y: e.clientY });
      
      // Change cursor to pointer
      canvasRef.current.style.cursor = 'pointer';
    } else {
      setHoverDifference(null);
      
      // Reset cursor
      canvasRef.current.style.cursor = 'default';
    }
  };
  
  const handleCanvasMouseLeave = () => {
    setHoverDifference(null);
  };

  // Handle image load
  const handleImageLoad = (e) => {
    if (!imageRef.current) return;
    
    const image = imageRef.current;
    const naturalWidth = image.naturalWidth;
    const naturalHeight = image.naturalHeight;
    
    const scaledWidth = naturalWidth * zoom;
    const scaledHeight = naturalHeight * zoom;
    
    console.log(`Image loaded with dimensions: ${naturalWidth}x${naturalHeight}, scaled to ${scaledWidth}x${scaledHeight}`);
    
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
      {/* Loading indicator */}
      {(isLoading || loading) && (
        <div className="renderer-loading">
          <Spinner size="medium" />
          <div className="loading-message">Loading page {page}...</div>
        </div>
      )}
      
      {/* Debug mode information */}
      {debugMode && differences.length > 0 && (
        <div className="debug-info" style={{
          position: 'absolute',
          top: '5px',
          right: '5px',
          background: 'rgba(0, 0, 0, 0.8)',
          color: 'white',
          padding: '5px',
          fontSize: '10px',
          zIndex: 20,
          borderRadius: '3px'
        }}>
          <div>Page {page}</div>
          <div>Differences: {differences.length}</div>
          <div>Base Coords: {baseCoordinates.length}</div>
          <div>Adjusted Coords: {adjustedCoordinates.length}</div>
          <div>Scale Factor: {scaleFactor?.toFixed(3)}</div>
          <div>Algorithm: {scalingAlgorithm}</div>
          <div>Max Adjust: {maxScalingAdjustment}</div>
        </div>
      )}
      
      {/* Image and highlights container */}
      <div
        className="canvas-container"
        style={{
          opacity: imageLoaded ? 1 : 0.3,
          width: dimensions.width,
          height: dimensions.height
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
        
        {/* Canvas for highlights */}
        <canvas
          ref={canvasRef}
          className="highlight-layer"
          style={{
            display: highlightMode === 'none' ? 'none' : 'block'
          }}
          onClick={handleCanvasClick}
          onMouseMove={handleCanvasMouseMove}
          onMouseLeave={handleCanvasMouseLeave}
        />
      </div>
      
      {/* Difference count badge (using final adjusted coordinates) */}
      {adjustedCoordinates.length > 0 && imageLoaded && highlightMode !== 'none' && (
        <div className="diff-count-badge">{adjustedCoordinates.length}</div>
      )}

      {/* Tooltip for difference hover (uses originalDiff from adjustedCoordinates) */}
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
