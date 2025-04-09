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
  
  // Handle image load errors
  const handleBaseImageError = () => {
    console.error("Failed to load base image:", baseImageUrl);
    setBaseImageLoaded(false);
  };
  
  const handleCompareImageError = () => {
    console.error("Failed to load compare image:", compareImageUrl);
    setCompareImageLoaded(false);
  };
  
  // Reset loaded state when URLs change
  useEffect(() => {
    setBaseImageLoaded(false);
  }, [baseImageUrl]);
  
  useEffect(() => {
    setCompareImageLoaded(false);
  }, [compareImageUrl]);
  
  // Draw highlights on canvas when images load or differences change
  useEffect(() => {
    console.log("pageDetails: " + pageDetails)
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
  
  // Function to extract or calculate position and bounds for differences
  const calculatePositionAndBounds = (diff, index, totalDiffs, dimensions) => {
    // If the difference has position and bounds, extract them correctly
    if (diff.position && diff.bounds) {
      // Handle nested position structure (with x, y inside position object)
      if (typeof diff.position.x === 'number' && typeof diff.position.y === 'number') {
        return {
          position: {
            x: diff.position.x,
            y: diff.position.y
          },
          bounds: diff.bounds
        };
      }
      
      // Handle flat position structure (x, y directly in position)
      if (diff.position.pageNumber !== undefined) {
        return {
          position: {
            x: diff.position.x,
            y: diff.position.y
          },
          bounds: diff.bounds
        };
      }
    }
    
    // Default width and height
    const defaultWidth = 100;
    const defaultHeight = 20;
    
    // Calculate position based on index
    // Distribute differences evenly across the page
    const rows = Math.ceil(Math.sqrt(totalDiffs));
    const cols = Math.ceil(totalDiffs / rows);
    
    const row = Math.floor(index / cols);
    const col = index % cols;
    
    const xSpacing = dimensions.width / cols;
    const ySpacing = dimensions.height / rows;
    
    // Add some randomness to make it look more natural
    const randomOffsetX = Math.random() * 20 - 10;
    const randomOffsetY = Math.random() * 20 - 10;
    
    const x = col * xSpacing + xSpacing / 4 + randomOffsetX;
    const y = row * ySpacing + ySpacing / 4 + randomOffsetY;
    
    // For text differences, try to use the text content to determine size
    let width = defaultWidth;
    let height = defaultHeight;
    
    if (diff.type === 'text' && (diff.baseText || diff.compareText)) {
      const text = diff.baseText || diff.compareText;
      width = Math.min(text.length * 8, dimensions.width / 2);
    }
    
    return {
      position: { x, y },
      bounds: { width, height }
    };
  };

  // Function to draw highlights on canvas
  const drawHighlights = (canvas, differences, dimensions, source, highlightMode, selectedDiff) => {
    const ctx = canvas.getContext('2d');
    
    // Set canvas dimensions to match the image
    canvas.width = dimensions.width;
    canvas.height = dimensions.height;
    
    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // Log differences for debugging
    console.log(`Drawing ${differences?.length || 0} differences for ${source}`);
    console.log('Differences data structure:', differences);
    
    // If no differences, draw some test differences
    if (!differences || differences.length === 0) {
      console.log(`No differences to draw for ${source}, adding test differences`);
      
      // Draw a test rectangle in each corner and the center
      const testDiffs = [
        { x: 50, y: 50, width: 100, height: 50, color: 'rgba(255, 0, 0, 0.5)', stroke: 'rgba(200, 0, 0, 1.0)' },
        { x: dimensions.width - 150, y: 50, width: 100, height: 50, color: 'rgba(0, 255, 0, 0.5)', stroke: 'rgba(0, 200, 0, 1.0)' },
        { x: 50, y: dimensions.height - 100, width: 100, height: 50, color: 'rgba(0, 0, 255, 0.5)', stroke: 'rgba(0, 0, 200, 1.0)' },
        { x: dimensions.width - 150, y: dimensions.height - 100, width: 100, height: 50, color: 'rgba(255, 165, 0, 0.5)', stroke: 'rgba(255, 140, 0, 1.0)' },
        { x: dimensions.width/2 - 50, y: dimensions.height/2 - 25, width: 100, height: 50, color: 'rgba(128, 0, 128, 0.5)', stroke: 'rgba(128, 0, 128, 1.0)' }
      ];
      
      // Draw test rectangles
      testDiffs.forEach(diff => {
        ctx.fillStyle = diff.color;
        ctx.strokeStyle = diff.stroke;
        ctx.lineWidth = 2;
        ctx.fillRect(diff.x, diff.y, diff.width, diff.height);
        ctx.strokeRect(diff.x, diff.y, diff.width, diff.height);
        
        // Add text
        ctx.font = '12px Arial';
        ctx.fillStyle = diff.stroke;
        ctx.fillText('Test Difference', diff.x + 5, diff.y + 25);
      });
      
      console.log(`Drew ${testDiffs.length} test differences for ${source}`);
      return;
    }
    
    // Filter differences to only show those for the current page
    const filteredDifferences = differences.filter(diff => 
      !diff.pageNumber || diff.pageNumber === 0 || diff.pageNumber === pageDetails?.pageNumber
    );
    
    console.log(`Filtered from ${differences.length} to ${filteredDifferences.length} differences for page ${pageDetails?.pageNumber}`);
    
    // Calculate positions for differences that don't have them
    const differencesWithPositions = filteredDifferences.map((diff, index) => {
      const { position, bounds } = calculatePositionAndBounds(diff, index, filteredDifferences.length, dimensions);
      return {
        ...diff,
        position,
        bounds
      };
    });
    
    // Group differences by area for annotation
    const groupedDiffs = groupDifferencesByArea(differencesWithPositions);
    
    // Draw each difference highlight
    differencesWithPositions.forEach(diff => {
      // Skip if doesn't match the highlight mode
      if (highlightMode !== 'all' && diff.type !== highlightMode) {
        return;
      }
      
      // Determine if this is the selected difference
      const isSelected = selectedDiff && selectedDiff.id === diff.id;
      
      // Get position and bounds
      const x = diff.position.x;
      const y = diff.position.y;
      const width = diff.bounds.width;
      const height = diff.bounds.height;
      
      // Determine colors based on difference type and change type
      let fillColor, strokeColor;
      
      switch (diff.changeType) {
        case 'added':
          fillColor = 'rgba(0, 255, 0, 0.5)';  // More visible green for added
          strokeColor = 'rgba(0, 200, 0, 1.0)';
          break;
        case 'deleted':
          fillColor = 'rgba(255, 0, 0, 0.5)';  // More visible red for deleted
          strokeColor = 'rgba(200, 0, 0, 1.0)';
          break;
        case 'modified':
          fillColor = 'rgba(255, 165, 0, 0.5)'; // More visible orange for modified
          strokeColor = 'rgba(255, 140, 0, 1.0)';
          break;
        default:
          fillColor = 'rgba(0, 0, 255, 0.5)';  // More visible blue for other changes
          strokeColor = 'rgba(0, 0, 200, 1.0)';
      }
      
      // Log the drawing of each difference for debugging
      console.log(`Drawing difference at (${x}, ${y}) with size ${width}x${height}, type: ${diff.type}, changeType: ${diff.changeType}`);
      
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
    
    // Draw group annotations after individual highlights
    drawGroupAnnotations(ctx, groupedDiffs, source);
  };
  
  // Group differences that are close to each other
  const groupDifferencesByArea = (differences) => {
    if (!differences || differences.length === 0) return [];
    
    // Clone the differences to avoid modifying the original
    const diffs = [...differences];
    const groups = [];
    
    // Simple clustering algorithm
    while (diffs.length > 0) {
      const current = diffs.shift();
      const group = [current];
      
      // Find all differences that are close to the current one
      for (let i = diffs.length - 1; i >= 0; i--) {
        const diff = diffs[i];
        if (areClose(current, diff, 100)) { // 100px threshold
          group.push(diff);
          diffs.splice(i, 1);
        }
      }
      
      // Only create groups with multiple differences
      if (group.length > 1) {
        groups.push(group);
      }
    }
    
    return groups;
  };
  
  // Check if two differences are close to each other
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
  const drawGroupAnnotations = (ctx, groups, source) => {
    groups.forEach(group => {
      // Calculate the bounding box for the group
      const bounds = calculateGroupBounds(group);
      
      // Count differences by type
      const counts = countDifferencesByType(group);
      
      // Draw annotation circle
      drawAnnotationCircle(ctx, bounds, counts, source);
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
  const drawAnnotationCircle = (ctx, bounds, counts, source) => {
    // Use purple for annotations as shown in the image
    const circleColor = 'rgba(128, 0, 128, 0.3)';
    const strokeColor = 'rgba(128, 0, 128, 0.8)';
    const textColor = 'rgba(128, 0, 128, 1)';
    
    // Draw circle around the group
    const radius = Math.max(bounds.width, bounds.height) / 2 + 20;
    ctx.beginPath();
    ctx.arc(bounds.centerX, bounds.centerY, radius, 0, 2 * Math.PI);
    ctx.fillStyle = circleColor;
    ctx.fill();
    ctx.strokeStyle = strokeColor;
    ctx.lineWidth = 2;
    ctx.stroke();
    
    // Prepare annotation text
    const lines = [];
    if (counts.deleted) lines.push(`${counts.deleted} element(s) removed`);
    if (counts.added) lines.push(`${counts.added} element(s) added`);
    if (counts.modified) lines.push(`${counts.modified} element(s) modified in style`);
    
    // Only add annotation text to the compare document (right side)
    if (source === 'compare' && lines.length > 0) {
      // Position text box near the circle
      const textX = bounds.centerX + radius / 2;
      const textY = bounds.centerY - radius / 2;
      
      // Draw text background
      const lineHeight = 16;
      const padding = 8;
      const textWidth = 220;
      const textHeight = lines.length * lineHeight + padding * 2;
      
      ctx.fillStyle = 'rgba(255, 255, 255, 0.9)';
      ctx.strokeStyle = strokeColor;
      ctx.lineWidth = 1;
      ctx.fillRect(textX, textY, textWidth, textHeight);
      ctx.strokeRect(textX, textY, textWidth, textHeight);
      
      // Draw text
      ctx.font = '12px Arial';
      ctx.fillStyle = textColor;
      ctx.textAlign = 'left';
      
      lines.forEach((line, index) => {
        ctx.fillText(line, textX + padding, textY + padding + (index + 1) * lineHeight);
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
    
    // Scale to account for canvas vs display size
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
    
    // Find the clicked difference
    for (const diff of differencesWithPositions) {
      const diffX = diff.position.x;
      const diffY = diff.position.y;
      const diffWidth = diff.bounds.width;
      const diffHeight = diff.bounds.height;
      
      if (clickX >= diffX && clickX <= diffX + diffWidth &&
          clickY >= diffY && clickY <= diffY + diffHeight) {
        // Call the callback with the clicked difference
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
    
    // Scale to account for canvas vs display size
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
    
    // Check if mouse is over a difference
    let isOverDifference = false;
    for (const diff of differencesWithPositions) {
      const diffX = diff.position.x;
      const diffY = diff.position.y;
      const diffWidth = diff.bounds.width;
      const diffHeight = diff.bounds.height;
      
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
          {tooltipInfo.diff.description || 
           `${tooltipInfo.diff.type} ${tooltipInfo.diff.changeType}`}
        </div>
      )}
    </div>
  );
};

export default EnhancedDiffHighlighter;
