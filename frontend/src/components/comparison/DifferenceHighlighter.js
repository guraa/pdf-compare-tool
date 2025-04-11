import React, { useEffect, useRef } from 'react';
import './DifferenceHighlighter.css';

/**
 * DifferenceHighlighter component - Creates overlay highlights for differences
 */
const DifferenceHighlighter = ({ 
  differences = [], 
  zoom = 1,
  highlightMode = 'all',
  selectedDifference = null,
  onDifferenceClick,
  width,
  height,
  onMouseOver,
  onMouseOut
}) => {
  const canvasRef = useRef(null);
  
  // Draw the highlights whenever relevant props change
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !width || !height) return;
    
    // Set canvas dimensions
    canvas.width = width;
    canvas.height = height;
    
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, width, height);
    
    // Filter differences based on highlight mode
    const visibleDifferences = differences.filter(diff => {
      if (highlightMode === 'none') return false;
      if (highlightMode === 'all') return true;
      return diff.type === highlightMode;
    });
    
    // Draw each difference
    visibleDifferences.forEach(diff => {
      if (!diff.position || !diff.bounds) return;
      
      // Calculate zoomed coordinates
      const x = diff.position.x * zoom;
      const y = diff.position.y * zoom;
      const w = diff.bounds.width * zoom;
      const h = diff.bounds.height * zoom;
      
      // Choose color based on difference type and change type
      let fillColor = 'rgba(255, 0, 0, 0.3)'; // Default red
      let strokeColor = 'rgba(255, 0, 0, 0.8)';
      
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
          // Keep default colors
      }
      
      // Adjust for change type if available
      if (diff.changeType) {
        switch (diff.changeType) {
          case 'added':
            fillColor = 'rgba(76, 175, 80, 0.3)'; // Green for added
            strokeColor = 'rgba(76, 175, 80, 0.8)';
            break;
          case 'deleted':
            fillColor = 'rgba(244, 67, 54, 0.3)'; // Red for deleted
            strokeColor = 'rgba(244, 67, 54, 0.8)';
            break;
          case 'modified':
            fillColor = 'rgba(255, 152, 0, 0.3)'; // Orange for modified
            strokeColor = 'rgba(255, 152, 0, 0.8)';
            break;
          default:
            // Keep type-based colors
        }
      }
      
      // Check if this is the selected difference
      const isSelected = selectedDifference && selectedDifference.id === diff.id;
      
      // Draw the highlight
      ctx.fillStyle = fillColor;
      ctx.strokeStyle = strokeColor;
      ctx.lineWidth = isSelected ? 3 : 1;
      
      // Draw rectangle
      ctx.fillRect(x, y, w, h);
      ctx.strokeRect(x, y, w, h);
      
      // Add border animation for selected difference
      if (isSelected) {
        ctx.strokeStyle = 'rgba(255, 255, 0, 0.8)'; // Yellow for selection
        ctx.lineWidth = 2;
        ctx.strokeRect(x - 2, y - 2, w + 4, h + 4);
      }
    });
  }, [differences, zoom, highlightMode, selectedDifference, width, height]);
  
  // Helper function to find a difference at the given coordinates
  const findDifferenceAtCoordinates = (x, y) => {
    return differences.find(diff => {
      if (!diff.position || !diff.bounds) return false;
      
      // Calculate zoomed coordinates for comparison
      const diffX = diff.position.x * zoom;
      const diffY = diff.position.y * zoom;
      const diffWidth = diff.bounds.width * zoom;
      const diffHeight = diff.bounds.height * zoom;
      
      // Check if coordinates are within the difference bounds
      return (
        x >= diffX && 
        x <= diffX + diffWidth &&
        y >= diffY && 
        y <= diffY + diffHeight
      );
    });
  };
  
  // Handle click on the canvas to select a difference
  const handleCanvasClick = (e) => {
    if (!onDifferenceClick || !differences.length) return;
    
    // Get click coordinates relative to the canvas
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    // Find if the click intersects with any difference
    const clickedDiff = findDifferenceAtCoordinates(x, y);
    
    if (clickedDiff) {
      onDifferenceClick(clickedDiff);
    }
  };
  
  // Mouse move handler for tooltips
  const handleMouseMove = (e) => {
    const canvas = canvasRef.current;
    // Only process if the canvas exists and we have an onMouseOver handler
    if (!canvas || typeof onMouseOver !== 'function') return;
    
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    // Find if mouse is over any difference
    const hoveredDiff = findDifferenceAtCoordinates(x, y);
    
    if (hoveredDiff) {
      canvas.style.cursor = 'pointer';
      onMouseOver(hoveredDiff, e.clientX, e.clientY);
    } else {
      canvas.style.cursor = 'default';
      if (typeof onMouseOut === 'function') {
        onMouseOut();
      }
    }
  };
  
  // Mouse leave handler
  const handleMouseLeave = () => {
    if (typeof onMouseOut === 'function') {
      onMouseOut();
    }
  };
  
  return (
    <canvas
      ref={canvasRef}
      className="difference-highlighter"
      onClick={handleCanvasClick}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      width={width}
      height={height}
    />
  );
};

export default DifferenceHighlighter;