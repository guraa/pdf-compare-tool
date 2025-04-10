// EnhancedDiffHighlighter.js - Fixed coordinate scaling
import React, { useState, useEffect, useRef } from 'react';
import { 
  pdfToCanvasCoordinates, 
  canvasToPdfCoordinates,
  extractCoordinates,
  isPointInDifference,
  getDifferenceColors,
  getChangeTypeIndicator
} from './CoordinateUtils';
import './EnhancedDiffHighlighter.css';

// Set to true to enable debug mode with additional visual indicators
const DEBUG_MODE = false;

const EnhancedDiffHighlighter = ({ 
  pageDetails, 
  baseImageUrl, 
  compareImageUrl,
  viewSettings,
  onDifferenceSelect,
  selectedDifference
}) => {
  const baseImageRef = useRef(null);
  const compareImageRef = useRef(null);
  const baseHighlightRef = useRef(null);
  const compareHighlightRef = useRef(null);
  const baseScrollContainerRef = useRef(null);
  const compareScrollContainerRef = useRef(null);
  const isSyncingScroll = useRef(false);
  
  const [baseImageLoaded, setBaseImageLoaded] = useState(false);
  const [compareImageLoaded, setCompareImageLoaded] = useState(false);
  const [baseDimensions, setBaseDimensions] = useState({ 
    naturalWidth: 0, 
    naturalHeight: 0, 
    renderedWidth: 0, 
    renderedHeight: 0, 
    scaleX: 1, 
    scaleY: 1 
  });
  const [compareDimensions, setCompareDimensions] = useState({ 
    naturalWidth: 0, 
    naturalHeight: 0, 
    renderedWidth: 0, 
    renderedHeight: 0, 
    scaleX: 1, 
    scaleY: 1 
  });
  const [tooltipInfo, setTooltipInfo] = useState(null);

  const updateDimensions = (imageRef, setDimensions) => {
    if (imageRef.current) {
      const naturalWidth = imageRef.current.naturalWidth;
      const naturalHeight = imageRef.current.naturalHeight;
      const renderedWidth = imageRef.current.offsetWidth;
      const renderedHeight = imageRef.current.offsetHeight;
      
      // Avoid division by zero
      const scaleX = naturalWidth > 0 ? renderedWidth / naturalWidth : 1;
      const scaleY = naturalHeight > 0 ? renderedHeight / naturalHeight : 1;
      
      // Get PDF dimensions if available from pageDetails
      const pdfWidth = pageDetails?.pdfWidth;
      const pdfHeight = pageDetails?.pdfHeight;

      console.log(`Updating dimensions: Natural ${naturalWidth}x${naturalHeight}, Rendered ${renderedWidth}x${renderedHeight}, Scale ${scaleX.toFixed(2)}x${scaleY.toFixed(2)}, PDF ${pdfWidth}x${pdfHeight}`);

      setDimensions({
        naturalWidth,
        naturalHeight,
        renderedWidth,
        renderedHeight,
        scaleX,
        scaleY,
        pdfWidth,
        pdfHeight
      });
      return true;
    }
    return false;
  };
  
  // Handle image load events
  const handleBaseImageLoad = () => {
    if (baseImageRef.current) {
      console.log('Base image loaded:', baseImageRef.current.src);
      if (updateDimensions(baseImageRef, setBaseDimensions)) {
        setBaseImageLoaded(true);
      } else {
         // Retry dimension update shortly after load
         setTimeout(() => {
            if(updateDimensions(baseImageRef, setBaseDimensions)) {
              setBaseImageLoaded(true);
            } else {
              console.error("Failed to get base image dimensions even after delay.");
            }
         }, 100);
      }
    }
  };
  
  const handleCompareImageLoad = () => {
    if (compareImageRef.current) {
      console.log('Compare image loaded:', compareImageRef.current.src);
       if (updateDimensions(compareImageRef, setCompareDimensions)) {
        setCompareImageLoaded(true);
      } else {
         // Retry dimension update
         setTimeout(() => {
            if(updateDimensions(compareImageRef, setCompareDimensions)) {
              setCompareImageLoaded(true);
            } else {
              console.error("Failed to get compare image dimensions even after delay.");
            }
         }, 100);
      }
    }
  };
  
  // Handle image load errors
  const handleBaseImageError = (e) => {
    console.error('Failed to load base image:', e.target.src);
    setBaseImageLoaded(false);
  };
  
  const handleCompareImageError = (e) => {
    console.error('Failed to load compare image:', e.target.src);
    setCompareImageLoaded(false);
  };
  
  // Reset loaded state when URLs change
  useEffect(() => {
    if (baseImageUrl) {
      console.log('Setting base image URL:', baseImageUrl);
      setBaseImageLoaded(false);
    }
  }, [baseImageUrl]);
  
  useEffect(() => {
    if (compareImageUrl) {
      console.log('Setting compare image URL:', compareImageUrl);
      setCompareImageLoaded(false);
    }
  }, [compareImageUrl]);
  
  // Draw highlights on canvas when images load or differences change
  useEffect(() => {
    if (baseImageLoaded && baseHighlightRef.current && pageDetails?.baseDifferences) {
      console.log(`Drawing ${pageDetails.baseDifferences.length} base differences`);
      drawHighlights(
        baseHighlightRef.current,
        pageDetails.baseDifferences,
        baseDimensions,
        'base',
        viewSettings?.highlightMode || 'all',
        selectedDifference,
        viewSettings?.zoom ?? 1
      );
    }
    
    if (compareImageLoaded && compareHighlightRef.current && pageDetails?.compareDifferences) {
      console.log(`Drawing ${pageDetails.compareDifferences.length} compare differences`);
      drawHighlights(
        compareHighlightRef.current,
        pageDetails.compareDifferences,
        compareDimensions,
        'compare',
        viewSettings?.highlightMode || 'all',
        selectedDifference,
        viewSettings?.zoom ?? 1
      );
    }
  }, [
    baseImageLoaded, 
    compareImageLoaded, 
    pageDetails, 
    baseDimensions, 
    compareDimensions, 
    viewSettings?.highlightMode,
    selectedDifference,
    viewSettings?.zoom
  ]);
  
  // Fixed function to draw highlights with better scaling and verbose logging
  const drawHighlights = (canvas, differences, dimensions, source, highlightMode, selectedDiff, zoom) => {
    const ctx = canvas.getContext('2d');
    const { renderedWidth, renderedHeight, naturalWidth, naturalHeight, scaleX, scaleY, pdfWidth, pdfHeight } = dimensions;

    // Log all relevant dimensions information
    console.log(`==== DRAWING HIGHLIGHTS (${source}) ====`);
    console.log(`Image dimensions: Natural ${naturalWidth}x${naturalHeight}, Rendered ${renderedWidth}x${renderedHeight}`);
    console.log(`Scale factors: ${scaleX.toFixed(3)}x${scaleY.toFixed(3)}`);
    console.log(`PDF dimensions: ${pdfWidth || 'unknown'}x${pdfHeight || 'unknown'}`);
    console.log(`Zoom level: ${zoom}`);
    console.log(`Total differences: ${differences?.length || 0}`);

    // Set canvas dimensions based on the RENDERED image size
    canvas.width = renderedWidth * zoom;
    canvas.height = renderedHeight * zoom;
    
    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // If no differences, don't try to draw anything
    if (!differences || differences.length === 0) {
      console.log("No differences to draw");
      return;
    }
    
    // Draw each difference highlight
    differences.forEach(diff => {
      // Skip if highlight doesn't match the selected mode
      if (highlightMode !== 'all' && diff.type !== highlightMode) {
        return;
      }
      
      // Log all the available properties for this difference
      console.log(`\n=== DIFFERENCE ${diff.id} (${diff.type}) ===`);
      console.log('Original difference object:', JSON.stringify(diff, null, 2));
      
      // Get raw coordinates from the difference object
      let rawX, rawY, rawWidth, rawHeight;
      
      // Check different possible formats
      if (diff.x !== undefined && diff.y !== undefined && diff.width !== undefined && diff.height !== undefined) {
        rawX = diff.x;
        rawY = diff.y;
        rawWidth = diff.width;
        rawHeight = diff.height;
        console.log(`Found direct coordinates: (${rawX}, ${rawY}) ${rawWidth}x${rawHeight}`);
      } 
      else if (diff.position && diff.bounds) {
        rawX = diff.position.x || 0;
        rawY = diff.position.y || 0;
        rawWidth = diff.bounds.width || 0;
        rawHeight = diff.bounds.height || 0;
        console.log(`Found position/bounds coordinates: (${rawX}, ${rawY}) ${rawWidth}x${rawHeight}`);
      }
      else if (diff.left !== undefined && diff.top !== undefined && 
             diff.right !== undefined && diff.bottom !== undefined) {
        rawX = diff.left;
        rawY = diff.top;
        rawWidth = diff.right - diff.left;
        rawHeight = diff.bottom - diff.top;
        console.log(`Found left/top/right/bottom coordinates: (${rawX}, ${rawY}) ${rawWidth}x${rawHeight}`);
      }
      else {
        console.warn(`No valid coordinates found for difference ${diff.id}`);
        return;
      }
      
      // Get standard coordinates using our utility
      const coords = extractCoordinates(diff);
      console.log('Extracted coordinates:', coords);
      
      // Determine if this is the selected difference
      const isSelected = selectedDiff && selectedDiff.id === diff.id;
      
      // Make adjustment for text differences
      let adjustedCoords = { ...coords };
      if (diff.type === 'text') {
        // For text differences, adjust height for better visibility
        const originalHeight = adjustedCoords.height;
        adjustedCoords.height = Math.max(14, adjustedCoords.height);
        
        // If we increased the height, adjust Y position to center the highlight
        if (adjustedCoords.height > originalHeight) {
          adjustedCoords.y -= (adjustedCoords.height - originalHeight) / 2;
        }
        
        console.log(`Text adjustment applied: (${adjustedCoords.x}, ${adjustedCoords.y}) ${adjustedCoords.width}x${adjustedCoords.height}`);
      }
      
      // Transform PDF coordinates to canvas coordinates
      console.log('Before transformation (PDF coordinates):', adjustedCoords);
      
      const { x: scaledX, y: scaledY, width: scaledWidth, height: scaledHeight } = 
        pdfToCanvasCoordinates(
          adjustedCoords.x, adjustedCoords.y, adjustedCoords.width, adjustedCoords.height, 
          dimensions, zoom
        );
      
      console.log('After transformation (Canvas coordinates):', 
        { x: scaledX, y: scaledY, width: scaledWidth, height: scaledHeight });
      
      // Get colors for this difference
      const { fillColor, strokeColor } = getDifferenceColors(diff.type, diff.changeType, isSelected);
      
      // Set line width based on selection state
      ctx.lineWidth = isSelected ? 2 * zoom : 1 * zoom;
      
      // Draw the highlight rectangle
      ctx.fillStyle = fillColor;
      ctx.strokeStyle = strokeColor;
      ctx.fillRect(scaledX, scaledY, scaledWidth, scaledHeight);
      ctx.strokeRect(scaledX, scaledY, scaledWidth, scaledHeight);
      
      // Add indicator for change type (only for visible differences)
      if (scaledWidth > 10 && scaledHeight > 10) {
        const indicator = getChangeTypeIndicator(diff.changeType);
        
        if (indicator) {
          // Position the indicator in the top-left of the highlight box
          const fontSize = Math.max(10, 12 * zoom);
          ctx.font = `bold ${fontSize}px sans-serif`;
          ctx.fillStyle = strokeColor;
          ctx.fillText(indicator, scaledX + 2, scaledY + fontSize);
        }
      }
      
      // Draw coordinate information directly on canvas for debugging
      ctx.fillStyle = 'rgba(0, 0, 0, 0.7)';
      ctx.fillRect(scaledX, scaledY + scaledHeight, scaledWidth, 60);
      ctx.font = '10px monospace';
      ctx.fillStyle = 'white';
      ctx.fillText(`PDF: (${Math.round(adjustedCoords.x)}, ${Math.round(adjustedCoords.y)}) ${Math.round(adjustedCoords.width)}x${Math.round(adjustedCoords.height)}`, 
        scaledX + 4, scaledY + scaledHeight + 12);
      ctx.fillText(`Canvas: (${Math.round(scaledX)}, ${Math.round(scaledY)}) ${Math.round(scaledWidth)}x${Math.round(scaledHeight)}`, 
        scaledX + 4, scaledY + scaledHeight + 24);
      ctx.fillText(`Type: ${diff.type}, ${diff.changeType || 'unknown'}`, 
        scaledX + 4, scaledY + scaledHeight + 36);
      if (diff.text) {
        ctx.fillText(`Text: "${diff.text.substring(0, 20)}${diff.text.length > 20 ? '...' : ''}"`, 
          scaledX + 4, scaledY + scaledHeight + 48);
      }
    });
  };
   
  // Handle clicking on highlights with coordinate transformation
  const handleCanvasClick = (e, source, differences) => {
    if (!differences || !onDifferenceSelect) return;
    
    const canvas = e.target;
    const rect = canvas.getBoundingClientRect();
    const dimensions = source === 'base' ? baseDimensions : compareDimensions;

    // Calculate click position relative to the canvas
    const clickXRel = e.clientX - rect.left;
    const clickYRel = e.clientY - rect.top;

    // Scale click coordinates from display size to canvas size
    const canvasScaleX = canvas.width / rect.width;
    const canvasScaleY = canvas.height / rect.height;
    const clickXCanvas = clickXRel * canvasScaleX;
    const clickYCanvas = clickYRel * canvasScaleY;
    
    // Get the zoom level
    const zoom = viewSettings?.zoom ?? 1;
    
    // Convert canvas coordinates to PDF coordinates
    const { x: pdfX, y: pdfY } = canvasToPdfCoordinates(
      clickXCanvas, 
      clickYCanvas, 
      dimensions, 
      zoom
    );
    
    // Find the clicked difference
    for (const diff of differences) {
      // Check if click point is within difference bounds
      if (isPointInDifference(pdfX, pdfY, diff)) {
        // Call the callback with the difference object
        onDifferenceSelect(diff);
        
        // Show tooltip
        setTooltipInfo({
          x: e.clientX,
          y: e.clientY,
          diff: diff,
          source: source
        });
        
        // Hide tooltip after 3 seconds
        setTimeout(() => {
          setTooltipInfo(null);
        }, 3000);
        
        break;
      }
    }
  };
  
  // Handle mouse movement for hover effects with coordinate transformation
  const handleMouseMove = (e, source, differences) => {
    if (!differences) return;
    
    const canvas = e.target;
    const rect = canvas.getBoundingClientRect();
    const dimensions = source === 'base' ? baseDimensions : compareDimensions;

    // Calculate mouse position relative to the canvas
    const mouseXRel = e.clientX - rect.left;
    const mouseYRel = e.clientY - rect.top;

    // Scale mouse coordinates from display size to canvas size
    const canvasScaleX = canvas.width / rect.width;
    const canvasScaleY = canvas.height / rect.height;
    const mouseXCanvas = mouseXRel * canvasScaleX;
    const mouseYCanvas = mouseYRel * canvasScaleY;
    
    // Get the zoom level
    const zoom = viewSettings?.zoom ?? 1;
    
    // Convert canvas coordinates to PDF coordinates
    const { x: pdfX, y: pdfY } = canvasToPdfCoordinates(
      mouseXCanvas, 
      mouseYCanvas, 
      dimensions, 
      zoom
    );
    
    // Check if mouse is over any difference
    let isOverDifference = false;
    
    for (const diff of differences) {
      // Check if mouse point is within difference bounds
      if (isPointInDifference(pdfX, pdfY, diff)) {
        canvas.style.cursor = 'pointer';
        isOverDifference = true;
        
        // Show tooltip on hover
        setTooltipInfo({
          x: e.clientX,
          y: e.clientY,
          diff: diff,
          source: source
        });
        
        break;
      }
    }
    
    if (!isOverDifference) {
      canvas.style.cursor = 'default';
      // Clear tooltip when not hovering over a difference
      setTooltipInfo(null);
    }
  };
  
  // Handle mouse leaving canvas
  const handleMouseLeave = () => {
    setTooltipInfo(null);
  };

  // Effect for synchronizing scroll
  useEffect(() => {
    const baseContainer = baseScrollContainerRef.current;
    const compareContainer = compareScrollContainerRef.current;

    const syncScroll = (source, target) => {
      if (!isSyncingScroll.current) {
        isSyncingScroll.current = true;
        target.scrollTop = source.scrollTop;
        target.scrollLeft = source.scrollLeft;
        // Use requestAnimationFrame to release the lock after browser has processed the scroll
        requestAnimationFrame(() => {
          isSyncingScroll.current = false;
        });
      }
    };

    const handleBaseScroll = () => syncScroll(baseContainer, compareContainer);
    const handleCompareScroll = () => syncScroll(compareContainer, baseContainer);

    if (baseContainer && compareContainer) {
      baseContainer.addEventListener('scroll', handleBaseScroll);
      compareContainer.addEventListener('scroll', handleCompareScroll);
      console.log("Scroll sync listeners added.");
    }

    // Cleanup
    return () => {
      if (baseContainer && compareContainer) {
        baseContainer.removeEventListener('scroll', handleBaseScroll);
        compareContainer.removeEventListener('scroll', handleCompareScroll);
        console.log("Scroll sync listeners removed.");
      }
    };
  }, [baseImageLoaded, compareImageLoaded]); // Re-run if images reload

  return (
    <div className="enhanced-diff-container">
      <div className="diff-legend">
        <div className="legend-item">
          <div className="legend-color added"></div>
          <span>Added</span>
        </div>
        <div className="legend-item">
          <div className="legend-color deleted"></div>
          <span>Deleted</span>
        </div>
        <div className="legend-item">
          <div className="legend-color modified"></div>
          <span>Modified</span>
        </div>
      </div>
      
      <div className="diff-document base-document">
        <div className="document-label">Base Document</div>
        <div className="image-container" ref={baseScrollContainerRef}> 
          {baseImageUrl ? (
            <>
              <img
                ref={baseImageRef}
                src={baseImageUrl}
                alt="Base document"
                className="document-image"
                onLoad={handleBaseImageLoad}
                onError={handleBaseImageError}
              />
              <canvas
                ref={baseHighlightRef}
                className="highlight-layer"
                onClick={(e) => handleCanvasClick(e, 'base', pageDetails?.baseDifferences)}
                onMouseMove={(e) => handleMouseMove(e, 'base', pageDetails?.baseDifferences)}
                onMouseLeave={handleMouseLeave}
              />
            </>
          ) : (
            <div className="document-placeholder">
              <p>Base document not available</p>
            </div>
          )}
        </div>
      </div>
      
      <div className="diff-document compare-document">
        <div className="document-label">Compare Document</div>
        <div className="image-container" ref={compareScrollContainerRef}>
          {compareImageUrl ? (
            <>
              <img
                ref={compareImageRef}
                src={compareImageUrl}
                alt="Compare document"
                className="document-image"
                onLoad={handleCompareImageLoad}
                onError={handleCompareImageError}
              />
              <canvas
                ref={compareHighlightRef}
                className="highlight-layer"
                onClick={(e) => handleCanvasClick(e, 'compare', pageDetails?.compareDifferences)}
                onMouseMove={(e) => handleMouseMove(e, 'compare', pageDetails?.compareDifferences)}
                onMouseLeave={handleMouseLeave}
              />
            </>
          ) : (
            <div className="document-placeholder">
              <p>Compare document not available</p>
            </div>
          )}
        </div>
      </div>
      
      {tooltipInfo && (
        <div 
          className="diff-tooltip"
          style={{
            left: tooltipInfo.x + 10,
            top: tooltipInfo.y - 30,
            maxWidth: '300px',
            wordBreak: 'break-word'
          }}
        >
          {tooltipInfo.diff.description || 
           `${tooltipInfo.diff.type || 'Unknown'} ${tooltipInfo.diff.changeType || 'change'}`}
        </div>
      )}
    </div>
  );
};

export default EnhancedDiffHighlighter;