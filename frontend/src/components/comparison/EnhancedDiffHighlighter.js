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
  
  const [baseImageLoaded, setBaseImageLoaded] = useState(false);
  const [compareImageLoaded, setCompareImageLoaded] = useState(false);
  const [baseDimensions, setBaseDimensions] = useState({ width: 0, height: 0 });
  const [compareDimensions, setCompareDimensions] = useState({ width: 0, height: 0 });
  const [tooltipInfo, setTooltipInfo] = useState(null);
  
  // Handle image load events
  const handleBaseImageLoad = () => {
    if (baseImageRef.current) {
      console.log('Base image loaded successfully with dimensions:', 
        baseImageRef.current.naturalWidth, 'x', baseImageRef.current.naturalHeight);
      
      setBaseDimensions({
        width: baseImageRef.current.naturalWidth,
        height: baseImageRef.current.naturalHeight,
        originalWidth: baseImageRef.current.naturalWidth,
        originalHeight: baseImageRef.current.naturalHeight
      });
      setBaseImageLoaded(true);
    }
  };
  
  const handleCompareImageLoad = () => {
    if (compareImageRef.current) {
      console.log('Compare image loaded successfully with dimensions:', 
        compareImageRef.current.naturalWidth, 'x', compareImageRef.current.naturalHeight);
      
      setCompareDimensions({
        width: compareImageRef.current.naturalWidth,
        height: compareImageRef.current.naturalHeight,
        originalWidth: compareImageRef.current.naturalWidth,
        originalHeight: compareImageRef.current.naturalHeight
      });
      setCompareImageLoaded(true);
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
    
    // Set canvas dimensions scaled by zoom
    canvas.width = dimensions.width * zoom;
    canvas.height = dimensions.height * zoom;
    
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
      if (!diff.position || !diff.bounds) {
        return;
      }
      
      // Determine if this is the selected difference
      const isSelected = selectedDiff && selectedDiff.id === diff.id;
      
      // Get position and bounds, scaled by zoom
      const x = (diff.position.x || 0) * zoom;
      const y = (diff.position.y || 0) * zoom;
      
      // Use the bounds directly from the diff data, scaled by zoom
      // Don't apply extra scaling that might make boxes too large
      const width = (diff.bounds.width || 0) * zoom;
      const height = (diff.bounds.height || 0) * zoom;
      
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
    
    // Calculate click position relative to the canvas
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    // Scale coordinates correctly
    const scaleX = canvas.width / rect.width; 
    const scaleY = canvas.height / rect.height;
    const clickX = x * scaleX; 
    const clickY = y * scaleY;
    
    // Find the clicked difference
    const zoom = viewSettings?.zoom ?? 1;
    
    // Only consider differences with position data
    const positionedDifferences = differences.filter(diff => diff.position && diff.bounds);
    
    for (const diff of positionedDifferences) {
      const diffX = (diff.position.x || 0) * zoom;
      const diffY = (diff.position.y || 0) * zoom;
      const diffWidth = (diff.bounds.width || 0) * zoom;
      const diffHeight = (diff.bounds.height || 0) * zoom;
      
      if (clickX >= diffX && clickX <= diffX + diffWidth &&
          clickY >= diffY && clickY <= diffY + diffHeight) {
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
    
    // Calculate mouse position relative to the canvas
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    // Scale coordinates correctly
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    const mouseX = x * scaleX;
    const mouseY = y * scaleY;
    
    // Check if mouse is over a difference
    let isOverDifference = false;
    const zoom = viewSettings?.zoom ?? 1;
    
    // Only consider differences with position data
    const positionedDifferences = differences.filter(diff => diff.position && diff.bounds);
    
    for (const diff of positionedDifferences) {
      const diffX = (diff.position.x || 0) * zoom;
      const diffY = (diff.position.y || 0) * zoom;
      const diffWidth = (diff.bounds.width || 0) * zoom;
      const diffHeight = (diff.bounds.height || 0) * zoom;
      
      if (mouseX >= diffX && mouseX <= diffX + diffWidth &&
          mouseY >= diffY && mouseY <= diffY + diffHeight) {
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
        <div className="image-container">
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
        <div className="image-container">
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