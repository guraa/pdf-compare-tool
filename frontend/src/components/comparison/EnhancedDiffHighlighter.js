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
      
      // Group related text differences by position
      const groupedDifferences = groupRelatedTextDifferences(pageDetails.baseDifferences);
      
      drawHighlights(
         baseHighlightRef.current,
         groupedDifferences,
         baseDimensions,
         'base',
         viewSettings?.highlightMode || 'all',
         selectedDifference,
         viewSettings?.zoom ?? 1 // Pass zoom factor
       );
    }
    
    if (compareImageLoaded && compareHighlightRef.current && pageDetails?.compareDifferences) {
      console.log(`Drawing ${pageDetails.compareDifferences.length} compare differences`);
      
      // Group related text differences by position
      const groupedDifferences = groupRelatedTextDifferences(pageDetails.compareDifferences);
      
      drawHighlights(
         compareHighlightRef.current,
         groupedDifferences,
         compareDimensions,
         'compare',
         viewSettings?.highlightMode || 'all',
         selectedDifference,
         viewSettings?.zoom ?? 1 // Pass zoom factor
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
     viewSettings?.zoom // Add zoom to dependency array
   ]);
   
  // Improved function to extract or calculate position and bounds for differences
  const calculatePositionAndBounds = (diff, index, totalDiffs, dimensions) => {
    // If we have the position and bounds data from the API
    if (diff.position && diff.bounds) {
      // For text differences, try to use the complete block rather than letter-by-letter
      if (diff.type === 'text' && diff.text?.length <= 3 && 
          (diff.baseText?.length > 3 || diff.compareText?.length > 3)) {
        // For single characters that are part of a larger text change,
        // scale up significantly to cover the whole text area
        const blockWidth = Math.max(200, dimensions.width * 0.3);  
        const blockHeight = Math.max(40, dimensions.height * 0.02);
        
        return {
          position: { 
            x: diff.position.x || 0, 
            y: diff.position.y || 0 
          },
          bounds: { 
            width: blockWidth, 
            height: blockHeight 
          }
        };
      } else {
        // Handle regular position and bounds, but scale up substantially
        const scaleFactor = 5; // Significantly increased scale
        const adjustedWidth = Math.max((diff.bounds.width || 50) * scaleFactor, 150);
        const adjustedHeight = Math.max((diff.bounds.height || 20) * scaleFactor, 50);
        
        return {
          position: { 
            x: diff.position.x || 0, 
            y: diff.position.y || 0 
          },
          bounds: { 
            width: adjustedWidth, 
            height: adjustedHeight 
          }
        };
      }
    }
  
    // Fallback for differences without position data - use whole page blocks
    const verticalSections = 5; // Divide page into 5 vertical sections
    const section = index % verticalSections;
    
    return {
      position: { 
        x: 50, 
        y: (dimensions.height / verticalSections) * section 
      },
      bounds: { 
        width: dimensions.width - 100, 
        height: (dimensions.height / verticalSections) - 10 
      }
    };
  };
 
  // Function to draw highlights on canvas
  const drawHighlights = (canvas, differences, dimensions, source, highlightMode, selectedDiff, zoom) => {
    const ctx = canvas.getContext('2d');

    canvas.width = dimensions.width * zoom;
    canvas.height = dimensions.height * zoom;
    
    // Set canvas dimensions scaled by zoom
    console.log("Set canvas dimensions scaled by zoom - dimensions.width:12 " + dimensions.width + " zoom: " + zoom + " canvas.height: " + canvas.height + " dimensions.height: " + dimensions.height +" zoom: "+ zoom)

    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // Log differences for debugging
    console.log(`Drawing ${differences?.length || 0} differences for ${source}`);
    
    // If no differences, don't try to draw anything
    if (!differences || differences.length === 0) {
      return;
    }
    
    // Calculate positions for differences that don't have them
    const differencesWithPositions = differences.map((diff, index) => {
      const positionAndBounds = calculatePositionAndBounds(diff, index, differences.length, dimensions);
      return {
        ...diff,
        position: positionAndBounds.position,
        bounds: positionAndBounds.bounds
      };
    });
    
    // Draw each difference highlight
    differencesWithPositions.forEach(diff => {
      // Skip if doesn't match the highlight mode
      if (highlightMode !== 'all' && diff.type !== highlightMode) {
        return;
      }
      
      // Determine if this is the selected difference
      const isSelected = selectedDiff && selectedDiff.id === diff.id;
      
      // Get position and bounds, scaled by zoom
      const x = (diff.position.x || 0) * zoom;
      const y = (diff.position.y || 0) * zoom;
      const width = (diff.bounds.width || 0) * zoom;
      const height = (diff.bounds.height || 0) * zoom;
      
      // Determine colors based on difference type and document
      let fillColor, strokeColor;
      
      // Use custom colors for specific areas of the document based on the image
      if (source === 'base') {
        // Base document uses red highlights
        fillColor = 'rgba(255, 0, 0, 0.5)';  
        strokeColor = 'rgba(200, 0, 0, 1.0)';
      } else {
        // Compare document uses color-coded areas as in the image
        // Yellow for left area
        if (x < dimensions.width * 0.4) {
          fillColor = 'rgba(255, 200, 0, 0.5)';  
          strokeColor = 'rgba(255, 160, 0, 1.0)';
        } 
        // Green for right area
        else {
          fillColor = 'rgba(0, 200, 0, 0.5)';  
          strokeColor = 'rgba(0, 160, 0, 1.0)';
        }
      }
      
      // Enhance selected difference
      if (isSelected) {
        fillColor = fillColor.replace('0.5', '0.7');
        strokeColor = strokeColor.replace('1.0', '1.0'); // Keep stroke opaque
        ctx.lineWidth = Math.max(3, 4 * zoom); // Scale selected line width
      } else {
        ctx.lineWidth = Math.max(2, 2 * zoom); // Scale default line width for better visibility
      }
      
      // Draw the highlight rectangle
      ctx.fillStyle = fillColor;
      ctx.strokeStyle = strokeColor;
      ctx.fillRect(x, y, width, height);
      ctx.strokeRect(x, y, width, height);
      
      // Add change type indicator in top-left corner for larger differences
      if (width > 30 && height > 20) {
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
        
        const indicatorFontSize = Math.max(12, 16 * zoom); // Increased font size for indicators
        ctx.font = `bold ${indicatorFontSize}px Arial`;
        ctx.fillStyle = strokeColor;
        // Adjust text position slightly for better visibility
        ctx.fillText(indicator, x + (8 * zoom), y + indicatorFontSize + (4 * zoom)); 
      }
    });
  };
   
  // Function to group related text differences by position to avoid individual letter highlights
  const groupRelatedTextDifferences = (differences) => {
    if (!differences || differences.length === 0) return [];
    
    // Clone to avoid modifying original array
    const diffs = [...differences];
    
    // First pass: identify single-character text differences that should be grouped
    const singleCharDiffs = diffs.filter(diff => 
      diff.type === 'text' && 
      (diff.text?.length <= 2 || diff.baseText?.length <= 2 || diff.compareText?.length <= 2)
    );
    
    // Skip grouping if we don't have many single character diffs
    if (singleCharDiffs.length <= 3) return diffs;
    
    // Second pass: try to group by position (proximity)
    const groupedIDs = new Set();
    const resultDiffs = [];
    
    // For each diff that hasn't been grouped yet
    for (let i = 0; i < diffs.length; i++) {
      const current = diffs[i];
      
      // Skip if already added to a group
      if (groupedIDs.has(current.id)) continue;
      
      // If this is a small text difference, try to find nearby related diffs
      if (current.type === 'text' && current.text?.length <= 2 && current.position) {
        const relatedDiffs = findRelatedDiffs(current, diffs);
        
        // If we found related diffs, create a grouped diff
        if (relatedDiffs.length > 2) {
          const combinedDiff = createCombinedDiff(current, relatedDiffs);
          
          // Add all related diff IDs to the grouped set
          relatedDiffs.forEach(diff => groupedIDs.add(diff.id));
          groupedIDs.add(current.id);
          
          resultDiffs.push(combinedDiff);
        } else {
          // Not enough related diffs, keep the original
          resultDiffs.push(current);
        }
      } else {
        // Not a candidate for grouping
        resultDiffs.push(current);
      }
    }
    
    return resultDiffs;
  };
  
  // Function to find related differences near the given diff
  const findRelatedDiffs = (sourceDiff, allDiffs) => {
    const sourceX = sourceDiff.position.x;
    const sourceY = sourceDiff.position.y;
    const proximityX = 100; // Horizontal proximity threshold
    const proximityY = 20;  // Vertical proximity threshold
    
    return allDiffs.filter(diff => 
      diff.id !== sourceDiff.id &&
      diff.type === 'text' &&
      diff.position &&
      Math.abs(diff.position.x - sourceX) < proximityX &&
      Math.abs(diff.position.y - sourceY) < proximityY
    );
  };
  
  // Function to create a combined diff object from related diffs
  const createCombinedDiff = (sourceDiff, relatedDiffs) => {
    // Combine all diffs including the source
    const allDiffs = [sourceDiff, ...relatedDiffs];
    
    // Find min/max coordinates to create a bounding box
    let minX = Number.MAX_VALUE;
    let minY = Number.MAX_VALUE;
    let maxX = 0;
    let maxY = 0;
    
    allDiffs.forEach(diff => {
      if (diff.position) {
        minX = Math.min(minX, diff.position.x);
        minY = Math.min(minY, diff.position.y);
        maxX = Math.max(maxX, diff.position.x + (diff.bounds?.width || 10));
        maxY = Math.max(maxY, diff.position.y + (diff.bounds?.height || 10));
      }
    });
    
    // Create a combined text value if available
    const combinedText = allDiffs
      .filter(diff => diff.text || diff.baseText || diff.compareText)
      .map(diff => diff.text || diff.baseText || diff.compareText)
      .join('');
    
    // Create the combined diff object
    return {
      ...sourceDiff,
      id: `combined-${sourceDiff.id}`,
      position: { x: minX, y: minY },
      bounds: { 
        width: maxX - minX + 100, // Add padding
        height: maxY - minY + 20  // Add padding
      },
      text: combinedText || sourceDiff.text,
      baseText: combinedText || sourceDiff.baseText,
      compareText: combinedText || sourceDiff.compareText,
      description: `Combined text differences (${allDiffs.length} changes)`
    };
  };
  
  // Check if two differences are close to each other - helper function for findRelatedDiffs
  const areClose = (diff1, diff2, threshold) => {
    // Extract x and y coordinates safely
    let x1, y1, x2, y2;
    
    if (diff1.position) {
      if (typeof diff1.position.x === 'number') {
        x1 = diff1.position.x;
        y1 = diff1.position.y;
      } else if (diff1.position.pageNumber !== undefined) {
        x1 = diff1.position.x;
        y1 = diff1.position.y;
      } else {
        x1 = 0;
        y1 = 0;
      }
    } else {
      x1 = 0;
      y1 = 0;
    }
    
    if (diff2.position) {
      if (typeof diff2.position.x === 'number') {
        x2 = diff2.position.x;
        y2 = diff2.position.y;
      } else if (diff2.position.pageNumber !== undefined) {
        x2 = diff2.position.x;
        y2 = diff2.position.y;
      } else {
        x2 = 0;
        y2 = 0;
      }
    } else {
      x2 = 0;
      y2 = 0;
    }
    
    const w1 = diff1.bounds?.width || 0;
    const h1 = diff1.bounds?.height || 0;
    const w2 = diff2.bounds?.width || 0;
    const h2 = diff2.bounds?.height || 0;
    
    // Calculate centers
    const cx1 = x1 + w1/2;
    const cy1 = y1 + h1/2;
    const cx2 = x2 + w2/2;
    const cy2 = y2 + h2/2;
    
    // Calculate distance between centers
    const distance = Math.sqrt(Math.pow(cx2 - cx1, 2) + Math.pow(cy2 - cy1, 2));
    
    return distance < threshold;
  };
   
  // Draw annotations for groups of differences
  const drawGroupAnnotations = (ctx, groups, source, zoom) => { // Accept zoom
    groups.forEach(group => {
      // Calculate the bounding box for the group
      const bounds = calculateGroupBounds(group);
      
      // Count differences by type
      const counts = countDifferencesByType(group);
      
      // Draw annotation circle (scaling positions and radius)
      drawAnnotationCircle(ctx, bounds, counts, source, zoom);
    });
  };
  
  // Calculate the bounding box for a group of differences
  const calculateGroupBounds = (group) => {
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    
    group.forEach(diff => {
      // Extract x and y coordinates safely
      let x, y;
      
      if (diff.position) {
        if (typeof diff.position.x === 'number') {
          x = diff.position.x;
          y = diff.position.y;
        } else if (diff.position.pageNumber !== undefined) {
          x = diff.position.x;
          y = diff.position.y;
        } else {
          x = 0;
          y = 0;
        }
      } else {
        x = 0;
        y = 0;
      }
      
      const width = diff.bounds?.width || 0;
      const height = diff.bounds?.height || 0;
      
      minX = Math.min(minX, x);
      minY = Math.min(minY, y);
      maxX = Math.max(maxX, x + width);
      maxY = Math.max(maxY, y + height);
    });
    
    return {
      x: minX,
      y: minY,
      width: maxX - minX,
      height: maxY - minY,
      centerX: (minX + maxX) / 2,
      centerY: (minY + maxY) / 2
    };
  };
  
  // Count differences by type
  const countDifferencesByType = (group) => {
    return group.reduce((acc, diff) => {
      const type = diff.changeType || 'unknown';
      acc[type] = (acc[type] || 0) + 1;
      return acc;
    }, {});
  };
   
  // Draw annotation circle with counts
  const drawAnnotationCircle = (ctx, bounds, counts, source, zoom) => {
    // Use purple for annotations as shown in the image
    const circleColor = 'rgba(128, 0, 128, 0.3)';
    const strokeColor = 'rgba(128, 0, 128, 0.8)';
    const textColor = 'rgba(128, 0, 128, 1)';
    
    // Make circle larger for better visibility (increased radius)
    const scaledCenterX = bounds.centerX * zoom;
    const scaledCenterY = bounds.centerY * zoom;
    const scaledRadius = (Math.max(bounds.width, bounds.height) / 2 + 40) * zoom;
    
    // Make circle thicker
    ctx.beginPath();
    ctx.arc(scaledCenterX, scaledCenterY, scaledRadius, 0, 2 * Math.PI);
    ctx.fillStyle = circleColor;
    ctx.fill();
    ctx.strokeStyle = strokeColor;
    ctx.lineWidth = 3; // Thicker border
    ctx.stroke();
    
    // Prepare annotation text
    const lines = [];
    if (counts.deleted) lines.push(`${counts.deleted} element(s) removed`);
    if (counts.added) lines.push(`${counts.added} element(s) added`);
    if (counts.modified) lines.push(`${counts.modified} element(s) modified in style`);
    
    // Only add annotation text to the compare document (right side)
    if (source === 'compare' && lines.length > 0) {
      // Position text box near the circle using scaled values
      const textX = scaledCenterX + scaledRadius / 2;
      const textY = scaledCenterY - scaledRadius / 2;
      
      // Scale text properties by zoom but make font larger overall
      const baseFontSize = 14; // Increased base font size
      const baseLineHeight = 20; // Increased line height
      const basePadding = 12; // Increased padding

      const scaledFontSize = Math.max(12, baseFontSize * zoom);
      const scaledLineHeight = baseLineHeight * zoom;
      const scaledPadding = basePadding * zoom;
      
      // Calculate required width based on text content
      ctx.font = `${scaledFontSize}px Arial`;
      let maxTextWidth = 0;
      lines.forEach(line => {
        const metrics = ctx.measureText(line);
        maxTextWidth = Math.max(maxTextWidth, metrics.width);
      });
      
      const scaledTextWidth = maxTextWidth + scaledPadding * 2;
      const scaledTextHeight = lines.length * scaledLineHeight + scaledPadding * 2;
      
      // Draw text background with thicker border
      ctx.fillStyle = 'rgba(255, 255, 255, 0.9)';
      ctx.strokeStyle = strokeColor;
      ctx.lineWidth = Math.max(2, 2 * zoom); // Thicker border
      ctx.fillRect(textX, textY, scaledTextWidth, scaledTextHeight);
      ctx.strokeRect(textX, textY, scaledTextWidth, scaledTextHeight);
      
      // Draw text with larger font
      ctx.font = `${scaledFontSize}px Arial`;
      ctx.fillStyle = textColor;
      ctx.textAlign = 'left';
      
      lines.forEach((line, index) => {
        ctx.fillText(line, textX + scaledPadding, textY + scaledPadding + (index + 1) * scaledLineHeight);
      });
    }
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
    
    // Canvas width/height are now scaled by zoom, rect width/height are display size.
    // clickX/Y should represent coordinates within the scaled canvas.
    const scaleX = canvas.width / rect.width; 
    const scaleY = canvas.height / rect.height;
    const clickX = x * scaleX; 
    const clickY = y * scaleY;
    
    // Filter differences to only show those for the current page
    const filteredDifferences = differences.filter(diff => 
      !diff.pageNumber || diff.pageNumber === 0 || diff.pageNumber === pageDetails?.pageNumber
    );
    
    // Calculate positions for differences that don't have them
    const differencesWithPositions = filteredDifferences.map((diff, index) => {
      const { position, bounds } = calculatePositionAndBounds(diff, index, filteredDifferences.length, dimensions);
      return {
        ...diff,
        position,
        bounds
      };
    });
    
    // Find the clicked difference (compare click coords with scaled diff coords)
    const zoom = viewSettings?.zoom ?? 1;
    for (const diff of differencesWithPositions) {
      const diffX = (diff.position.x || 0) * zoom;
      const diffY = (diff.position.y || 0) * zoom;
      const diffWidth = (diff.bounds.width || 0) * zoom;
      const diffHeight = (diff.bounds.height || 0) * zoom;
      
      if (clickX >= diffX && clickX <= diffX + diffWidth &&
          clickY >= diffY && clickY <= diffY + diffHeight) {
        // Call the callback with the original difference object
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
    
    // Calculate mouse position relative to the canvas
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    // Canvas width/height are now scaled by zoom, rect width/height are display size.
    // mouseX/Y should represent coordinates within the scaled canvas.
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    const mouseX = x * scaleX;
    const mouseY = y * scaleY;
    
    // Filter differences to only show those for the current page
    const filteredDifferences = differences.filter(diff => 
      !diff.pageNumber || diff.pageNumber === 0 || diff.pageNumber === pageDetails?.pageNumber
    );
    
    // Calculate positions for differences that don't have them
    const differencesWithPositions = filteredDifferences.map((diff, index) => {
      const { position, bounds } = calculatePositionAndBounds(diff, index, filteredDifferences.length, dimensions);
      return {
        ...diff,
        position,
        bounds
      };
    });
    
    // Check if mouse is over a difference (compare mouse coords with scaled diff coords)
    let isOverDifference = false;
    const zoom = viewSettings?.zoom ?? 1;
    for (const diff of differencesWithPositions) {
      const diffX = (diff.position.x || 0) * zoom;
      const diffY = (diff.position.y || 0) * zoom;
      const diffWidth = (diff.bounds.width || 0) * zoom;
      const diffHeight = (diff.bounds.height || 0) * zoom;
      
      if (mouseX >= diffX && mouseX <= diffX + diffWidth &&
          mouseY >= diffY && mouseY <= diffY + diffHeight) {
        canvas.style.cursor = 'pointer';
        isOverDifference = true;
        break;
      }
    }
    
    if (!isOverDifference) {
      canvas.style.cursor = 'default';
    }
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
            top: tooltipInfo.y + 10
          }}
        >
          {tooltipInfo.diff.description || `${tooltipInfo.diff.type || 'Unknown'} ${tooltipInfo.diff.changeType || 'change'}`}
        </div>
      )}
    </div>
  );
};

export default EnhancedDiffHighlighter;