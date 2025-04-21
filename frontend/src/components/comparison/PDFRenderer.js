import React, { useState, useEffect, useRef } from 'react';
import { getDocumentPage } from '../../services/api';
import Spinner from '../common/Spinner';
import DifferenceTooltip from './DifferenceTooltip';
import './PDFRenderer.css';

/**
 * Simplified PDFRenderer with fixed coordinate adjustments
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
  pageMetadata = null
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
      const calculatedScaleFactor = xRatio * 0.8; // Fixed scale adjustment
      
      console.log(`Image loaded with dimensions: ${imageNaturalWidth}x${imageNaturalHeight}`);
      console.log(`Scale factor: ${calculatedScaleFactor}`);
      
      setScaleFactor(calculatedScaleFactor);
      
      // Measure the container to get offsets
      if (containerRef.current) {
        const containerRect = containerRef.current.getBoundingClientRect();
        const imageRect = image.getBoundingClientRect();
        
        // Calculate the offset of the image within the container
        const offsetX = imageRect.left - containerRect.left - 80; // Fixed X offset
        const offsetY = imageRect.top - containerRect.top ; // Fixed Y offset
        
        setContainerOffset({ x: offsetX, y: offsetY });
      }
    }
  }, [imageLoaded]);

  // Calculate adjusted coordinates
  useEffect(() => {
    if (!differences || !scaleFactor || !imageRef.current) {
      setAdjustedCoordinates([]);
      return;
    }

    const image = imageRef.current;
    const imageWidth = image.naturalWidth;
    const imageHeight = image.naturalHeight;
    
    // Standard PDF dimensions
    const pdfWidth = 612;
    const pdfHeight = 792;
    
    // Calculate ratios
    const xRatio = imageWidth / pdfWidth;
    const yRatio = imageHeight / pdfHeight;
    
    // Calculate adjusted coordinates
    const adjustedCoords = differences.map(diff => {
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
      let displayY;

      // Handle Y coordinate flip and apply fixed correction
      const pixelPdfHeight = pdfHeight * yRatio * zoom;
      displayY = pixelPdfHeight - scaledY - scaledHeight + containerOffset.y;
      
      // DPI CORRECTION APPROACH:
      // The key insight: PDF coordinates are in points (72 points = 1 inch)
      // But the image is rendered at a specific DPI (200 DPI by default)
      
      // The backend renders the PDF at 150 DPI in fast mode (which is what we're requesting), but the coordinates are in PDF points
      // So we need to apply a DPI correction factor
      const backendDpi = 150; // This should match the DPI we're requesting in getDocumentPage
      const pdfPointsPerInch = 72;
      const dpiCorrectionFactor = backendDpi / pdfPointsPerInch; // = 4.1667
      
      // Apply DPI correction to the coordinates
      // We need to scale the coordinates by the DPI correction factor
      const dpiCorrectedX = originalX * dpiCorrectionFactor;
      const dpiCorrectedY = originalY * dpiCorrectionFactor;
      const dpiCorrectedWidth = originalWidth * dpiCorrectionFactor;
      const dpiCorrectedHeight = originalHeight * dpiCorrectionFactor;
      
      // Now apply the coordinate system flip (PDF Y=0 is bottom, Canvas Y=0 is top)
      // We use the image height (which is the PDF height rendered at the backend DPI)
      const imageHeight = dimensions.naturalHeight;
      displayY = imageHeight - dpiCorrectedY - dpiCorrectedHeight + containerOffset.y;
      
      // Apply zoom factor (since the image is scaled in the frontend)
      displayX = dpiCorrectedX * zoom + containerOffset.x;
      displayY = displayY * zoom;
      
      // Log detailed information about this difference for debugging
      console.log(`Difference Debug [${text?.substring(0, 20) || 'No text'}]:`, {
        originalCoords: { x: originalX, y: originalY, width: originalWidth, height: originalHeight },
        scaledCoords: { x: scaledX, y: scaledY, width: scaledWidth, height: scaledHeight },
        displayCoords: { x: displayX, y: displayY, width: scaledWidth, height: scaledHeight },
        containerOffset,
        pixelPdfHeight,
        normalizedY: originalY / pdfHeight,
        zoom,
        scaleFactor,
        ratios: { xRatio, yRatio }
      });

      return {
        id,
        type,
        changeType,
        text,
        baseText,
        compareText,
        x: displayX,
        y: displayY,
        width: scaledWidth,
        height: scaledHeight,
        originalDiff: diff
      };
    }).filter(Boolean); // Remove any null items

    setAdjustedCoordinates(adjustedCoords);
  }, [differences, scaleFactor, zoom, containerOffset, dimensions, isBaseDocument]);

  // Draw highlights
  useEffect(() => {
    if (!canvasRef.current || !imageLoaded || highlightMode === 'none') {
      if (canvasRef.current) {
        const canvas = canvasRef.current;
        const ctx = canvas.getContext('2d');
        ctx.clearRect(0, 0, canvas.width, canvas.height);
      }
      return;
    }

    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    
    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // Set canvas size to match image dimensions
    canvas.width = dimensions.width;
    canvas.height = dimensions.height;
    
    // Draw each highlight
    adjustedCoordinates.forEach(diff => {
      // Skip if this type is not included in highlight mode
      if (highlightMode !== 'all' && diff.type !== highlightMode) {
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
      
      {/* Difference count badge */}
      {adjustedCoordinates.length > 0 && imageLoaded && highlightMode !== 'none' && (
        <div className="diff-count-badge">{adjustedCoordinates.length}</div>
      )}

      {/* Tooltip for difference hover */}
      <DifferenceTooltip
        difference={hoverDifference}
        visible={!!hoverDifference}
        x={tooltipPosition.x}
        y={tooltipPosition.y}
      />
      
      {/* Debug overlay */}
      {imageLoaded && highlightMode !== 'none' && (
        <div 
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            background: 'rgba(0,0,0,0.7)',
            color: 'white',
            padding: '10px',
            fontSize: '12px',
            maxWidth: '300px',
            maxHeight: '200px',
            overflow: 'auto',
            zIndex: 1000
          }}
        >
          <h4 style={{margin: '0 0 5px 0'}}>Debug Info</h4>
          <div>
            <strong>Container Offset:</strong> x={containerOffset.x.toFixed(1)}, y={containerOffset.y.toFixed(1)}
          </div>
          <div>
            <strong>Scale Factor:</strong> {scaleFactor.toFixed(3)}
          </div>
          <div>
            <strong>Zoom:</strong> {zoom.toFixed(2)}
          </div>
          <div>
            <strong>Image Dimensions:</strong> {dimensions.width}x{dimensions.height}
          </div>
          <div>
            <strong>Differences:</strong> {adjustedCoordinates.length}
          </div>
          {adjustedCoordinates.length > 0 && (
            <div>
              <strong>First Diff:</strong> {adjustedCoordinates[0].text?.substring(0, 15) || 'No text'}
              <br />
              Original: x={adjustedCoordinates[0].originalDiff.baseX || adjustedCoordinates[0].originalDiff.compareX || 0}, 
              y={adjustedCoordinates[0].originalDiff.baseY || adjustedCoordinates[0].originalDiff.compareY || 0}
              <br />
              Display: x={adjustedCoordinates[0].x.toFixed(1)}, y={adjustedCoordinates[0].y.toFixed(1)}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default PDFRenderer;
