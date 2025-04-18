import React, { useState, useEffect, useRef } from 'react';
import { getDocumentPage } from '../../services/api';
import Spinner from '../common/Spinner';
import DifferenceTooltip from './DifferenceTooltip';
import './PDFRenderer.css';

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
  debugMode = false
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
  const highlightsContainerRef = useRef(null);

  console.log("%c PDFRenderer mounting", "background:#336699;color:white;padding:4px", {
    page,
    fileId,
    differencesCount: differences.length,
    highlightMode,
    isBaseDocument
  });
  
  // Debug logging for differences
  useEffect(() => {
    if (differences.length > 0 && debugMode) {
      console.log("%c PDF Renderer Differences", "background:purple;color:white;padding:4px");
      console.log("Page:", page);
      console.log("Total differences:", differences.length);
      console.log("Highlight mode:", highlightMode);
      console.log("isBaseDocument:", isBaseDocument);
      
      // Log a sample difference for debugging
      console.log("Sample difference:", differences[0]);
    }
  }, [differences, page, highlightMode, isBaseDocument, debugMode]);
  
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
    if (imageLoaded && imageRef.current) {
      const image = imageRef.current;
      
      // Get the natural dimensions of the loaded image
      const imageNaturalWidth = image.naturalWidth;
      const imageNaturalHeight = image.naturalHeight;
      
      // Get the actual PDF dimensions from the metadata or use defaults
      const pdfWidth = 612; // Standard letter width in points
      const pdfHeight = 792; // Standard letter height in points
      
      // Calculate the scale factor between the rendered image and actual PDF
      const xRatio = imageNaturalWidth / pdfWidth;
      const yRatio = imageNaturalHeight / pdfHeight;
      const calculatedScaleFactor = xRatio * scaleAdjustment;
      
      if (debugMode) {
        console.log(`PDFRenderer: PDF dimensions: ${pdfWidth}x${pdfHeight}`);
        console.log(`PDFRenderer: Image dimensions: ${imageNaturalWidth}x${imageNaturalHeight}`);
        console.log(`PDFRenderer: Scale factor: ${calculatedScaleFactor}, X ratio: ${xRatio}, Y ratio: ${yRatio}`);
      }
      
      setScaleFactor(calculatedScaleFactor);
      
      // Measure the container to get offsets
      if (containerRef.current) {
        const containerRect = containerRef.current.getBoundingClientRect();
        const imageRect = image.getBoundingClientRect();
        
        // Calculate the actual offset of the image within the container
        const offsetX = imageRect.left - containerRect.left + xOffsetAdjustment;
        const offsetY = imageRect.top - containerRect.top + yOffsetAdjustment;
        
        if (debugMode) {
          console.log(`PDFRenderer: Container offsets: (${offsetX}, ${offsetY})`);
        }
        
        setContainerOffset({ x: offsetX, y: offsetY });
      }
    }
  }, [imageLoaded, scaleAdjustment, xOffsetAdjustment, yOffsetAdjustment, debugMode]);

  // Transform difference coordinates for display
  useEffect(() => {
    if (!differences || !scaleFactor || !imageRef.current) return;
    
    if (debugMode && differences.length > 0) {
      console.log(`Processing ${differences.length} differences for page ${page}`);
    }
    
    const image = imageRef.current;
    const imageWidth = image.naturalWidth;
    const imageHeight = image.naturalHeight;
    
    // Standard PDF dimensions
    const pdfWidth = 612;  // Letter width in points
    const pdfHeight = 792; // Letter height in points
    
    // Calculate ratios
    const xRatio = imageWidth / pdfWidth;
    const yRatio = imageHeight / pdfHeight;
    
    // Transform all difference coordinates
    const transformed = differences.map(diff => {
      // Skip if missing key properties
      if (!diff) return null;
      
      // Create an ID if one doesn't exist
      const id = diff.id || `diff-${Math.random().toString(36).substr(2, 9)}`;
      
      // Get coordinates based on the format
      let originalX, originalY, originalWidth, originalHeight;
      let text, baseText, compareText, type, changeType;
      
      // Handle the new format
      if (diff.baseX !== undefined || diff.compareX !== undefined) {
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
      
      // Handle Y coordinate flip if needed
      let displayY;
      if (flipY) {
        // Calculate the pixel height of the PDF
        const pixelPdfHeight = pdfHeight * yRatio * zoom;
        
        // Flip the Y coordinate, accounting for the height
        displayY = pixelPdfHeight - scaledY - scaledHeight + containerOffset.y;
      } else {
        displayY = scaledY + containerOffset.y;
      }
      
      // Create the transformed coordinates object
      return {
        id,
        type,
        changeType,
        text,
        baseText,
        compareText,
        // Display coordinates for rendering
        x: displayX,
        y: displayY,
        width: scaledWidth,
        height: scaledHeight,
        // Original coordinates for reference
        originalX,
        originalY,
        originalWidth,
        originalHeight,
        // Original difference object
        originalDiff: diff
      };
    }).filter(Boolean); // Remove any null items
    
    if (debugMode && transformed.length > 0) {
      console.log('Transformed coordinates for first difference:', transformed[0]);
    }
    
    setAdjustedCoordinates(transformed);
  }, [differences, scaleFactor, zoom, containerOffset, flipY, dimensions, isBaseDocument, 
      xOffsetAdjustment, yOffsetAdjustment, debugMode, page]);

  // Create DOM-based highlights (instead of canvas-based)
  useEffect(() => {
    // Log the current state
    console.log("%c Attempting to create highlights", "background:orange;color:black;", {
      highlightMode,
      adjustedCoordinatesCount: adjustedCoordinates?.length,
      containerExists: !!highlightsContainerRef.current,
      imageLoaded
    });
  
    // Skip if highlight mode is none or no coordinates are available
    if (highlightMode === 'none') {
      console.log("Highlights disabled - highlight mode is 'none'");
      return;
    }
    
    if (!adjustedCoordinates || adjustedCoordinates.length === 0) {
      console.log("No adjusted coordinates available for highlighting");
      return;
    }
    
    if (!highlightsContainerRef.current) {
      console.error("Highlights container ref is not available!");
      return;
    }
    
    // Get the container
    const container = highlightsContainerRef.current;
    
    // Remove existing highlights
    const oldHighlightCount = container.childNodes.length;
    console.log(`Removing ${oldHighlightCount} existing highlights`);
    
    while (container.firstChild) {
      container.removeChild(container.firstChild);
    }
    
    console.log(`Creating ${adjustedCoordinates.length} highlight elements`);
    
    // Create new highlight elements for each difference
    let createdCount = 0;
    
    adjustedCoordinates.forEach((diff, index) => {
      // Skip if type doesn't match highlight mode (unless mode is 'all')
      if (highlightMode !== 'all' && diff.type !== highlightMode) {
        return;
      }
      
      try {
        // Create highlight element
        const highlight = document.createElement('div');
        highlight.className = `difference-highlight ${diff.type}`;
        
        // Add change type class if present
        if (diff.changeType) {
          highlight.classList.add(diff.changeType);
        }
        
        // Add selected class if this is the selected difference
        if (selectedDifference && selectedDifference.id === diff.id) {
          highlight.classList.add('selected');
        }
        
        // Position and size the highlight
        highlight.style.left = `${diff.x}px`;
        highlight.style.top = `${diff.y}px`;
        highlight.style.width = `${diff.width}px`;
        highlight.style.height = `${diff.height}px`;
        
        // Debug style to make it extremely visible
        highlight.style.backgroundColor = 'rgba(255, 0, 0, 0.7)';  // Bright red
        highlight.style.border = '3px solid red';
        highlight.style.opacity = '0.8';
        highlight.style.zIndex = '1000';
        
        // Add a text label
        highlight.textContent = `${diff.type} ${index}`;
        highlight.style.color = 'white';
        highlight.style.fontSize = '10px';
        highlight.style.display = 'flex';
        highlight.style.alignItems = 'center';
        highlight.style.justifyContent = 'center';
        
        // Add additional data for interaction
        highlight.dataset.diffId = diff.id;
        
        // Add event listeners
        highlight.addEventListener('click', () => {
          if (onDifferenceSelect) {
            console.log("Difference clicked:", diff);
            onDifferenceSelect(diff.originalDiff);
          }
        });
        
        highlight.addEventListener('mouseover', (e) => {
          console.log("Difference hover:", diff);
          setHoverDifference(diff.originalDiff);
          setTooltipPosition({ x: e.clientX, y: e.clientY });
        });
        
        highlight.addEventListener('mouseout', () => {
          setHoverDifference(null);
        });
        
        // Add to container
        container.appendChild(highlight);
        createdCount++;
        
        // Log every 50th highlight created for performance
        if (index % 50 === 0) {
          console.log(`Created highlight ${index} of ${adjustedCoordinates.length}`);
        }
      } catch (err) {
        console.error("Error creating highlight:", err, diff);
      }
    });
    
    console.log(`Successfully created ${createdCount} highlight elements`);
    
    // Force display of the highlights container
    container.style.display = 'block';
    container.style.zIndex = '1000';
    container.style.pointerEvents = 'all';
    
  }, [adjustedCoordinates, highlightMode, selectedDifference, onDifferenceSelect]);
  // Handle image load
  const handleImageLoad = (e) => {
    if (!imageRef.current) return;
    
    const image = imageRef.current;
    const naturalWidth = image.naturalWidth;
    const naturalHeight = image.naturalHeight;
    
    const scaledWidth = naturalWidth * zoom;
    const scaledHeight = naturalHeight * zoom;
    
    console.log(`Image loaded with dimensions: ${naturalWidth}x${naturalHeight}, scaled to ${scaledWidth}x${scaledHeight}`);
    
    // Log container dimensions
    if (containerRef.current) {
      const containerRect = containerRef.current.getBoundingClientRect();
      console.log("Container dimensions:", {
        width: containerRect.width,
        height: containerRect.height,
        top: containerRect.top,
        left: containerRect.left
      });
    }
    
    setDimensions({
      width: scaledWidth,
      height: scaledHeight,
      naturalWidth,
      naturalHeight
    });
    
    setImageLoaded(true);
    setIsLoading(false);
    
    // Log highlight container info
    setTimeout(() => {
      if (highlightsContainerRef.current) {
        const highlightContainer = highlightsContainerRef.current;
        console.log("Highlight container:", {
          width: highlightContainer.offsetWidth,
          height: highlightContainer.offsetHeight,
          childCount: highlightContainer.childNodes.length,
          display: window.getComputedStyle(highlightContainer).display,
          zIndex: window.getComputedStyle(highlightContainer).zIndex,
          position: window.getComputedStyle(highlightContainer).position,
        });
      }
    }, 500);
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
    <div className="pdf-renderer" ref={containerRef} style={{ position: 'relative', border: debugMode ? '2px solid red' : 'none' }}>
      {/* Debug mode information */}
      {debugMode && (
        <div className="debug-info" style={{
          position: 'absolute',
          top: '5px',
          right: '5px',
          background: 'rgba(0,0,0,0.6)',
          color: 'white',
          padding: '5px',
          fontSize: '10px',
          zIndex: 20
        }}>
          <div>Page {page}</div>
          <div>Differences: {differences.length}</div>
          <div>Adjusted: {adjustedCoordinates.length}</div>
          <div>Scale: {scaleFactor.toFixed(3)}</div>
          <div>Highlight Mode: {highlightMode}</div>
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
          opacity: imageLoaded ? 1 : 0.3,
          position: 'relative',
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
              height: dimensions.height,
              position: 'absolute',
              top: 0,
              left: 0
            }}
            onLoad={handleImageLoad}
            onError={handleImageError}
          />
        )}
        
        {/* DOM-based highlights container */}
        <div 
          ref={highlightsContainerRef}
          className="highlights-container"
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            pointerEvents: 'none'
          }}
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
    </div>
  );
};

export default PDFRenderer;