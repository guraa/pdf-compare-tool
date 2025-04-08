import React, { useState, useEffect, useRef } from 'react';
import './EnhancedDiffHighlighter.css';

/**
 * Enhanced PDF difference highlighter component
 * Provides more precise difference highlighting similar to i-net PDFC
 */
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
  
  // Handle image load events
  const handleBaseImageLoad = () => {
    if (baseImageRef.current) {
      setBaseDimensions({
        width: baseImageRef.current.naturalWidth,
        height: baseImageRef.current.naturalHeight
      });
      setBaseImageLoaded(true);
    }
  };
  
  const handleCompareImageLoad = () => {
    if (compareImageRef.current) {
      setCompareDimensions({
        width: compareImageRef.current.naturalWidth,
        height: compareImageRef.current.naturalHeight
      });
      setCompareImageLoaded(true);
    }
  };
  
  // Draw highlights on canvas when images load or differences change
  useEffect(() => {
    if (baseImageLoaded && baseHighlightRef.current && pageDetails?.baseDifferences) {
      drawHighlights(
        baseHighlightRef.current, 
        pageDetails.baseDifferences, 
        baseDimensions,
        'base', 
        viewSettings?.highlightMode || 'all',
        selectedDifference
      );
    }
    
    if (compareImageLoaded && compareHighlightRef.current && pageDetails?.compareDifferences) {
      drawHighlights(
        compareHighlightRef.current, 
        pageDetails.compareDifferences, 
        compareDimensions,
        'compare', 
        viewSettings?.highlightMode || 'all',
        selectedDifference
      );
    }
  }, [
    baseImageLoaded, 
    compareImageLoaded, 
    pageDetails, 
    baseDimensions, 
    compareDimensions, 
    viewSettings?.highlightMode,
    selectedDifference
  ]);
  
  // Function to draw highlights on canvas
  const drawHighlights = (canvas, differences, dimensions, source, highlightMode, selectedDiff) => {
    const ctx = canvas.getContext('2d');
    
    // Set canvas dimensions to match the image
    canvas.width = dimensions.width;
    canvas.height = dimensions.height;
    
    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // Draw each difference highlight
    differences.forEach(diff => {
      // Skip if doesn't match the highlight mode
      if (highlightMode !== 'all' && diff.type !== highlightMode) {
        return;
      }
      
      // Determine if this is the selected difference
      const isSelected = selectedDiff && selectedDiff.id === diff.id;
      
      // Get position and bounds (add fallbacks for safety)
      const x = diff.position?.x || 0;
      const y = diff.position?.y || 0;
      const width = diff.bounds?.width || 100;
      const height = diff.bounds?.height || 20;
      
      // Determine colors based on difference type and change type
      let fillColor, strokeColor;
      
      switch (diff.changeType) {
        case 'added':
          fillColor = 'rgba(0, 255, 0, 0.2)';  // Light green for added
          strokeColor = 'rgba(0, 200, 0, 0.8)';
          break;
        case 'deleted':
          fillColor = 'rgba(255, 0, 0, 0.2)';  // Light red for deleted
          strokeColor = 'rgba(200, 0, 0, 0.8)';
          break;
        case 'modified':
          fillColor = 'rgba(255, 165, 0, 0.2)'; // Light orange for modified
          strokeColor = 'rgba(255, 140, 0, 0.8)';
          break;
        default:
          fillColor = 'rgba(0, 0, 255, 0.2)';  // Light blue for other changes
          strokeColor = 'rgba(0, 0, 200, 0.8)';
      }
      
      // Enhance selected difference
      if (isSelected) {
        fillColor = fillColor.replace('0.2', '0.4');
        strokeColor = strokeColor.replace('0.8', '1.0');
        ctx.lineWidth = 3;
      } else {
        ctx.lineWidth = 1;
      }
      
      // Draw the highlight rectangle
      ctx.fillStyle = fillColor;
      ctx.strokeStyle = strokeColor;
      ctx.fillRect(x, y, width, height);
      ctx.strokeRect(x, y, width, height);
      
      // Add change type indicator in top-left corner for larger differences
      if (width > 50 && height > 20) {
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
            indicator = '?';
        }
        
        ctx.font = '12px Arial';
        ctx.fillStyle = strokeColor;
        ctx.fillText(indicator, x + 5, y + 15);
      }
    });
  };
  
  // Handle clicking on highlights
  const handleCanvasClick = (e, source, differences) => {
    if (!differences || !onDifferenceSelect) return;
    
    const canvas = e.target;
    const rect = canvas.getBoundingClientRect();
    
    // Calculate click position relative to the canvas
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    // Scale to account for canvas vs display size
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    const clickX = x * scaleX;
    const clickY = y * scaleY;
    
    // Find the clicked difference
    for (const diff of differences) {
      const diffX = diff.position?.x || 0;
      const diffY = diff.position?.y || 0;
      const diffWidth = diff.bounds?.width || 0;
      const diffHeight = diff.bounds?.height || 0;
      
      if (clickX >= diffX && clickX <= diffX + diffWidth &&
          clickY >= diffY && clickY <= diffY + diffHeight) {
        // Call the callback with the clicked difference
        onDifferenceSelect(diff);
        break;
      }
    }
  };
  
  return (
    <div className="enhanced-diff-container">
      <div className="diff-document base-document">
        <div className="image-container">
          {baseImageUrl && (
            <>
              <img
                ref={baseImageRef}
                src={baseImageUrl}
                alt="Base document"
                className="document-image"
                onLoad={handleBaseImageLoad}
              />
              <canvas
                ref={baseHighlightRef}
                className="highlight-layer"
                onClick={(e) => handleCanvasClick(e, 'base', pageDetails?.baseDifferences)}
              />
            </>
          )}
        </div>
      </div>
      
      <div className="diff-document compare-document">
        <div className="image-container">
          {compareImageUrl && (
            <>
              <img
                ref={compareImageRef}
                src={compareImageUrl}
                alt="Compare document"
                className="document-image"
                onLoad={handleCompareImageLoad}
              />
              <canvas
                ref={compareHighlightRef}
                className="highlight-layer"
                onClick={(e) => handleCanvasClick(e, 'compare', pageDetails?.compareDifferences)}
              />
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default EnhancedDiffHighlighter;