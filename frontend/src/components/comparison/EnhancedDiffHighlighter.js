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
        height: baseImageRef.current.naturalHeight
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
        height: compareImageRef.current.naturalHeight
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
         viewSettings?.zoom ?? 1 // Pass zoom factor
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
   
   // Function to extract or calculate position and bounds for differences
   const calculatePositionAndBounds = (diff, index, totalDiffs, dimensions) => {
    // First, check if position and bounds are already correctly defined
    if (diff.position && diff.bounds && 
        typeof diff.position.x === 'number' && 
        typeof diff.position.y === 'number') {
      // Scale coordinates based on actual image dimensions
      return {
        position: {
          x: (diff.position.x / dimensions.originalWidth) * dimensions.width,
          y: (diff.position.y / dimensions.originalHeight) * dimensions.height
        },
        bounds: {
          width: (diff.bounds.width / dimensions.originalWidth) * dimensions.width,
          height: (diff.bounds.height / dimensions.originalHeight) * dimensions.height
        }
      };
    }
  
    // Fallback positioning if coordinates are not precise
    const rows = Math.ceil(Math.sqrt(totalDiffs));
    const cols = Math.ceil(totalDiffs / rows);
  
    const xSpacing = dimensions.width / cols;
    const ySpacing = dimensions.height / rows;
  
    const row = Math.floor(index / cols);
    const col = index % cols;
  
    return {
      position: { 
        x: col * xSpacing, 
        y: row * ySpacing 
      },
      bounds: { 
        width: xSpacing * 0.8, 
        height: ySpacing * 0.8 
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
      
       // Get position and bounds, scaled by zoom
       const x = (diff.position.x || 0) * zoom;
       const y = (diff.position.y || 0) * zoom;
       const width = (diff.bounds.width || 0) * zoom;
       const height = (diff.bounds.height || 0) * zoom;
      
      // Determine colors based on difference type and change type
      let fillColor, strokeColor;
      
      switch (diff.changeType) {
        case 'added':
          fillColor = 'rgba(0, 255, 0, 0.3)';  // More visible green for added
          strokeColor = 'rgba(0, 200, 0, 1.0)';
          break;
        case 'deleted':
          fillColor = 'rgba(255, 0, 0, 0.3)';  // More visible red for deleted
          strokeColor = 'rgba(200, 0, 0, 1.0)';
          break;
        case 'modified':
          fillColor = 'rgba(255, 165, 0, 0.3)'; // More visible orange for modified
          strokeColor = 'rgba(255, 140, 0, 1.0)';
          break;
        default:
          fillColor = 'rgba(0, 0, 255, 0.3)';  // More visible blue for other changes
          strokeColor = 'rgba(0, 0, 200, 1.0)';
      }
      
      // Enhance selected difference
      if (isSelected) {
        fillColor = fillColor.replace('0.3', '0.5');
         strokeColor = strokeColor.replace('1.0', '1.0'); // Keep stroke opaque
         ctx.lineWidth = Math.max(1, 3 * zoom); // Scale selected line width
       } else {
         ctx.lineWidth = Math.max(1, 1 * zoom); // Scale default line width
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
         
         const indicatorFontSize = Math.max(8, 12 * zoom); // Scale indicator font size
         ctx.font = `${indicatorFontSize}px Arial`;
         ctx.fillStyle = strokeColor;
         // Adjust text position slightly based on scaled font size for better centering
         ctx.fillText(indicator, x + (5 * zoom), y + indicatorFontSize + (2 * zoom)); 
      }
    });
    
     // Draw group annotations after individual highlights
     drawGroupAnnotations(ctx, groupedDiffs, source, zoom); // Pass zoom
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
   const drawGroupAnnotations = (ctx, groups, source, zoom) => { // Accept zoom
     groups.forEach(group => {
       // Calculate the bounding box for the group
       const bounds = calculateGroupBounds(group); // Uses unscaled coords
       
       // Count differences by type
       const counts = countDifferencesByType(group);
       
       // Draw annotation circle (scaling positions and radius)
       drawAnnotationCircle(ctx, bounds, counts, source, zoom); // Pass zoom
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
   const drawAnnotationCircle = (ctx, bounds, counts, source, zoom) => { // Accept zoom
     // Use purple for annotations as shown in the image
     const circleColor = 'rgba(128, 0, 128, 0.3)';
     const strokeColor = 'rgba(128, 0, 128, 0.8)';
     const textColor = 'rgba(128, 0, 128, 1)';
     
     // Scale center and radius
     const scaledCenterX = bounds.centerX * zoom;
     const scaledCenterY = bounds.centerY * zoom;
     const scaledRadius = (Math.max(bounds.width, bounds.height) / 2 + 20) * zoom;
     
     // Draw circle around the group using scaled values
     ctx.beginPath();
     ctx.arc(scaledCenterX, scaledCenterY, scaledRadius, 0, 2 * Math.PI);
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
       // Position text box near the circle using scaled values
       const textX = scaledCenterX + scaledRadius / 2;
       const textY = scaledCenterY - scaledRadius / 2;
       
       // Scale text properties by zoom
        const baseFontSize = 12; // Keep base font size fixed for now
        const baseLineHeight = 16;
        const basePadding = 8;

        const scaledFontSize = Math.max(8, baseFontSize * zoom); // Ensure minimum font size
        const scaledLineHeight = baseLineHeight * zoom;
        const scaledPadding = basePadding * zoom;
        
        // Calculate required width based on text content
        ctx.font = `${scaledFontSize}px Arial`; // Set font to measure text
        let maxTextWidth = 0;
        lines.forEach(line => {
          const metrics = ctx.measureText(line);
          maxTextWidth = Math.max(maxTextWidth, metrics.width);
        });
        
        const scaledTextWidth = maxTextWidth + scaledPadding * 2; // Width based on longest line + padding
        const scaledTextHeight = lines.length * scaledLineHeight + scaledPadding * 2;
        
        // Draw text background
        ctx.fillStyle = 'rgba(255, 255, 255, 0.9)';
       ctx.strokeStyle = strokeColor;
       ctx.lineWidth = Math.max(1, 1 * zoom); // Scale line width slightly, min 1px
       ctx.fillRect(textX, textY, scaledTextWidth, scaledTextHeight);
       ctx.strokeRect(textX, textY, scaledTextWidth, scaledTextHeight);
       
       // Draw text
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
     const dimensions = source === 'base' ? baseDimensions : compareDimensions; // Natural dimensions
     
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
       const diffX = (diff.position.x || 0) * zoom; // Scale diff coords for comparison
       const diffY = (diff.position.y || 0) * zoom;
       const diffWidth = (diff.bounds.width || 0) * zoom;
       const diffHeight = (diff.bounds.height || 0) * zoom;
       
       if (clickX >= diffX && clickX <= diffX + diffWidth &&
           clickY >= diffY && clickY <= diffY + diffHeight) {
         // Call the callback with the original (unscaled) difference object
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
     const dimensions = source === 'base' ? baseDimensions : compareDimensions; // Natural dimensions
     
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
       const diffX = (diff.position.x || 0) * zoom; // Scale diff coords for comparison
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
  
  // Generate direct image URLs from paths if needed
  const getDirectImageUrl = (path) => {
    if (!path) return null;
    
    // If it's already a blob URL, use it directly
    if (path.startsWith('blob:')) {
      return path;
    }
    
    // If it's a path to the API, make it a full URL
    if (path.startsWith('/api/')) {
      return path;
    }
    
    return null;
  };
  
  // Calculate effective image URLs
  const baseImageSrc = baseImageUrl || 
    (pageDetails && pageDetails.baseRenderedImagePath ? pageDetails.baseRenderedImagePath : null);
  
  const compareImageSrc = compareImageUrl || 
    (pageDetails && pageDetails.compareRenderedImagePath ? pageDetails.compareRenderedImagePath : null);

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
          {baseImageSrc ? (
            <>
              <img
                ref={baseImageRef}
                src={baseImageSrc}
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
          {compareImageSrc ? (
            <>
              <img
                ref={compareImageRef}
                src={compareImageSrc}
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
        </div>
      )}
    </div>
  );
};

export default EnhancedDiffHighlighter;
