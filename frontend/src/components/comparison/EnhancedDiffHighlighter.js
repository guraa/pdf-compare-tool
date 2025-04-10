// EnhancedDiffHighlighter.js - Refactored for better positioning
import React, { useState, useEffect, useRef } from 'react';
import './EnhancedDiffHighlighter.css';

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
  const baseScrollContainerRef = useRef(null); // Ref for base scroll container
  const compareScrollContainerRef = useRef(null); // Ref for compare scroll container
  const isSyncingScroll = useRef(false); // Flag to prevent scroll loops
  
  const [baseImageLoaded, setBaseImageLoaded] = useState(false);
  const [compareImageLoaded, setCompareImageLoaded] = useState(false);
  // Store both natural and rendered dimensions + scale
  const [baseDimensions, setBaseDimensions] = useState({ naturalWidth: 0, naturalHeight: 0, renderedWidth: 0, renderedHeight: 0, scaleX: 1, scaleY: 1 });
  const [compareDimensions, setCompareDimensions] = useState({ naturalWidth: 0, naturalHeight: 0, renderedWidth: 0, renderedHeight: 0, scaleX: 1, scaleY: 1 });
  const [tooltipInfo, setTooltipInfo] = useState(null);

  // Function to update dimensions and scale
  const updateDimensions = (imageRef, setDimensions) => {
    if (imageRef.current) {
      const naturalWidth = imageRef.current.naturalWidth;
      const naturalHeight = imageRef.current.naturalHeight;
      const renderedWidth = imageRef.current.offsetWidth;
      const renderedHeight = imageRef.current.offsetHeight;
      
      // Avoid division by zero if image hasn't rendered fully yet
      const scaleX = naturalWidth > 0 ? renderedWidth / naturalWidth : 1;
      const scaleY = naturalHeight > 0 ? renderedHeight / naturalHeight : 1;

      console.log(`Updating dimensions: Natural ${naturalWidth}x${naturalHeight}, Rendered ${renderedWidth}x${renderedHeight}, Scale ${scaleX.toFixed(2)}x${scaleY.toFixed(2)}`);

      setDimensions({
        naturalWidth,
        naturalHeight,
        renderedWidth,
        renderedHeight,
        scaleX,
        scaleY
      });
      return true; // Indicate success
    }
    return false; // Indicate failure
  };
  
  // Handle image load events
  const handleBaseImageLoad = () => {
    if (baseImageRef.current) {
      console.log('Base image loaded:', baseImageRef.current.src);
      if (updateDimensions(baseImageRef, setBaseDimensions)) {
        setBaseImageLoaded(true);
      } else {
         // Retry dimension update shortly after load, sometimes offsetWidth isn't ready immediately
         setTimeout(() => {
            if(updateDimensions(baseImageRef, setBaseDimensions)) {
              setBaseImageLoaded(true);
            } else {
              console.error("Failed to get base image dimensions even after delay.");
            }
         }, 100); // 100ms delay
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
  
  // Function to draw highlights on canvas - simplified for accuracy
  const drawHighlights = (canvas, differences, dimensions, source, highlightMode, selectedDiff, zoom) => {
    const ctx = canvas.getContext('2d');
    const { renderedWidth, renderedHeight, scaleX, scaleY } = dimensions;

    // Set canvas dimensions based on the RENDERED image size, scaled by zoom
    canvas.width = renderedWidth * zoom;
    canvas.height = renderedHeight * zoom;
    
    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // If no differences, don't try to draw anything
    if (!differences || differences.length === 0) {
      return;
    }
    
    // Draw each difference highlight - only if it has position data
    differences.forEach(diff => {
      // Skip if highlight doesn't match the selected mode
      if (highlightMode !== 'all' && diff.type !== highlightMode) {
        return;
      }
      
      // Skip if the difference doesn't have position and bounds data
      // Use direct properties: x, y, width, height
      if (diff.x === undefined || diff.y === undefined || diff.width === undefined || diff.height === undefined) {
        console.warn('Difference skipped due to missing coordinates:', diff);
        return;
      }
      
      // Determine if this is the selected difference
      const isSelected = selectedDiff && selectedDiff.id === diff.id;
      
      // Get position and bounds, apply rendering scale AND zoom
      const x = (diff.x || 0) * scaleX * zoom;
      const y = (diff.y || 0) * scaleY * zoom;
      const width = (diff.width || 0) * scaleX * zoom;
      const height = (diff.height || 0) * scaleY * zoom;
      
      // Determine colors based on change type
      let fillColor, strokeColor;
      
      switch (diff.changeType) {
        case 'added':
          fillColor = 'rgba(76, 175, 80, 0.3)';  // Green for added
          strokeColor = 'rgba(76, 175, 80, 0.8)';
          break;
        case 'deleted':
          fillColor = 'rgba(244, 67, 54, 0.3)';  // Red for deleted
          strokeColor = 'rgba(244, 67, 54, 0.8)';
          break;
        case 'modified':
          fillColor = 'rgba(255, 152, 0, 0.3)';  // Orange for modified
          strokeColor = 'rgba(255, 152, 0, 0.8)';
          break;
        default:
          // Default colors by difference type
          switch (diff.type) {
            case 'text':
              fillColor = 'rgba(255, 82, 82, 0.3)';
              strokeColor = 'rgba(255, 82, 82, 0.8)';
              break;
            case 'image':
              fillColor = 'rgba(33, 150, 243, 0.3)';
              strokeColor = 'rgba(33, 150, 243, 0.8)';
              break;
            case 'font':
              fillColor = 'rgba(156, 39, 176, 0.3)';
              strokeColor = 'rgba(156, 39, 176, 0.8)';
              break;
            case 'style':
              fillColor = 'rgba(255, 152, 0, 0.3)';
              strokeColor = 'rgba(255, 152, 0, 0.8)';
              break;
            default:
              fillColor = 'rgba(128, 128, 128, 0.3)';
              strokeColor = 'rgba(128, 128, 128, 0.8)';
          }
      }
      
      // Enhance selected difference
      if (isSelected) {
        fillColor = fillColor.replace('0.3', '0.5');
        strokeColor = strokeColor.replace('0.8', '1.0');
        ctx.lineWidth = 2 * zoom;
      } else {
        ctx.lineWidth = 1 * zoom;
      }
      
      // Draw the highlight rectangle
      ctx.fillStyle = fillColor;
      ctx.strokeStyle = strokeColor;
      ctx.fillRect(x, y, width, height);
      ctx.strokeRect(x, y, width, height);
      
      // Add indicator for change type (only for visible differences)
      if (width > 10 && height > 10) {
        let indicator;
        switch (diff.changeType) {
          case 'added':
            indicator = '+';
            break;
          case 'deleted':
            indicator = '-';
            break;
          case 'modified':
            indicator = '~';
            break;
          default:
            indicator = '';
        }
        
        if (indicator) {
          // Position the indicator in the top-left of the highlight box
          const fontSize = Math.max(10, 12 * zoom);
          ctx.font = `bold ${fontSize}px sans-serif`;
          ctx.fillStyle = strokeColor;
          ctx.fillText(indicator, x + 2, y + fontSize);
        }
      }
    });
  };
   
  // Handle clicking on highlights
  const handleCanvasClick = (e, source, differences) => {
    if (!differences || !onDifferenceSelect) return;
    
    const canvas = e.target;
    const rect = canvas.getBoundingClientRect();
    const dimensions = source === 'base' ? baseDimensions : compareDimensions;
    const { scaleX: renderScaleX, scaleY: renderScaleY } = dimensions;

    // Calculate click position relative to the canvas
    const clickXRel = e.clientX - rect.left;
    const clickYRel = e.clientY - rect.top;

    // Scale click coordinates from display size to canvas size
    const canvasScaleX = canvas.width / rect.width;
    const canvasScaleY = canvas.height / rect.height;
    const clickXCanvas = clickXRel * canvasScaleX;
    const clickYCanvas = clickYRel * canvasScaleY;
    
    // Find the clicked difference
    const zoom = viewSettings?.zoom ?? 1;
    
    // Only consider differences with position data (using direct properties)
    const positionedDifferences = differences.filter(diff => 
      diff.x !== undefined && diff.y !== undefined && diff.width !== undefined && diff.height !== undefined
    );
    
    for (const diff of positionedDifferences) {
      // Calculate diff bounds on the canvas (applying render scale and zoom)
      const diffX = (diff.x || 0) * renderScaleX * zoom;
      const diffY = (diff.y || 0) * renderScaleY * zoom;
      const diffWidth = (diff.width || 0) * renderScaleX * zoom;
      const diffHeight = (diff.height || 0) * renderScaleY * zoom;
      
      // Check if the canvas click coordinates fall within the calculated diff bounds
      if (clickXCanvas >= diffX && clickXCanvas <= diffX + diffWidth &&
          clickYCanvas >= diffY && clickYCanvas <= diffY + diffHeight) {
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
  
  // Handle mouse movement for hover effects
  const handleMouseMove = (e, source, differences) => {
    if (!differences) return;
    
    const canvas = e.target;
    const rect = canvas.getBoundingClientRect();
    const dimensions = source === 'base' ? baseDimensions : compareDimensions;
    const { scaleX: renderScaleX, scaleY: renderScaleY } = dimensions;

    // Calculate mouse position relative to the canvas
    const mouseXRel = e.clientX - rect.left;
    const mouseYRel = e.clientY - rect.top;

    // Scale mouse coordinates from display size to canvas size
    const canvasScaleX = canvas.width / rect.width;
    const canvasScaleY = canvas.height / rect.height;
    const mouseXCanvas = mouseXRel * canvasScaleX;
    const mouseYCanvas = mouseYRel * canvasScaleY;
    
    // Check if mouse is over a difference
    let isOverDifference = false;
    const zoom = viewSettings?.zoom ?? 1;
    
    // Only consider differences with position data (using direct properties)
    const positionedDifferences = differences.filter(diff => 
      diff.x !== undefined && diff.y !== undefined && diff.width !== undefined && diff.height !== undefined
    );
    
    for (const diff of positionedDifferences) {
       // Calculate diff bounds on the canvas (applying render scale and zoom)
      const diffX = (diff.x || 0) * renderScaleX * zoom;
      const diffY = (diff.y || 0) * renderScaleY * zoom;
      const diffWidth = (diff.width || 0) * renderScaleX * zoom;
      const diffHeight = (diff.height || 0) * renderScaleY * zoom;
      
      // Check if the canvas mouse coordinates fall within the calculated diff bounds
      if (mouseXCanvas >= diffX && mouseXCanvas <= diffX + diffWidth &&
          mouseYCanvas >= diffY && mouseYCanvas <= diffY + diffHeight) {
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
        // Use requestAnimationFrame to release the lock after the browser has processed the scroll update
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
  }, [baseImageLoaded, compareImageLoaded]); // Re-run if images reload, ensuring refs are current

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
        {/* Attach ref to the scrollable container */}
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
         {/* Attach ref to the scrollable container */}
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
