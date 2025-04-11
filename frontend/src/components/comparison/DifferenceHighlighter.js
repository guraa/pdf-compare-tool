import React, { useState, useEffect, useRef } from 'react';
import './DifferenceHighlighter.css';

/**
 * Enhanced DifferenceHighlighter component
 * Creates overlay highlights for differences between documents
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
  const [hoveredDifference, setHoveredDifference] = useState(null);
  
  // Draw the highlights whenever relevant props change
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !width || !height) return;
    
    // Set canvas dimensions
    canvas.width = width;
    canvas.height = height;
    
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, width, height);
    
    // Skip if highlighting is disabled
    if (highlightMode === 'none') return;
    
    // Filter differences based on highlight mode
    const visibleDifferences = differences.filter(diff => {
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
      
      // Check if this is the hovered difference
      const isHovered = hoveredDifference && hoveredDifference.id === diff.id;
      
      // Draw the highlight
      ctx.fillStyle = fillColor;
      ctx.strokeStyle = strokeColor;
      ctx.lineWidth = isSelected ? 3 : isHovered ? 2 : 1;
      
      // Draw highlight rectangle with slightly rounded corners
      ctx.beginPath();
      ctx.roundRect(x, y, w, h, 2);
      ctx.fill();
      ctx.stroke();
      
      // Add secondary border for selected difference
      if (isSelected) {
        // Draw outer glow effect for selected difference
        ctx.strokeStyle = 'rgba(255, 255, 0, 0.8)'; // Yellow for selection
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.roundRect(x - 3, y - 3, w + 6, h + 6, 4);
        ctx.stroke();
        
        // Add label for difference type if text is not too small
        if (w > 50 && h > 20) {
          const label = diff.type.charAt(0).toUpperCase() + diff.type.slice(1);
          ctx.fillStyle = strokeColor;
          ctx.font = '10px Arial';
          ctx.fillText(label, x + 4, y + 12);
        }
      }
      
      // Add hover effect
      if (isHovered && !isSelected) {
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.8)';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.roundRect(x - 2, y - 2, w + 4, h + 4, 3);
        ctx.stroke();
      }
    });
  }, [differences, zoom, highlightMode, selectedDifference, width, height, hoveredDifference]);
  
  // Helper function to find a difference at the given coordinates
  const findDifferenceAtCoordinates = (x, y) => {
    return differences.find(diff => {
      if (!diff.position || !diff.bounds || highlightMode === 'none') return false;
      
      // Skip if this difference type is filtered out
      if (highlightMode !== 'all' && diff.type !== highlightMode) return false;
      
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
    if (!onDifferenceClick || !differences.length || highlightMode === 'none') return;
    
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
  
  // Mouse move handler for tooltips and hover effects
  const handleMouseMove = (e) => {
    const canvas = canvasRef.current;
    // Only process if the canvas exists and we're showing highlights
    if (!canvas || highlightMode === 'none') return;
    
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    // Find if mouse is over any difference
    const hoveredDiff = findDifferenceAtCoordinates(x, y);
    
    // Update hover state
    setHoveredDifference(hoveredDiff);
    
    if (hoveredDiff) {
      canvas.style.cursor = 'pointer';
      
      // Call onMouseOver if provided
      if (typeof onMouseOver === 'function') {
        onMouseOver(hoveredDiff, e.clientX, e.clientY);
      }
    } else {
      canvas.style.cursor = 'default';
      
      // Call onMouseOut if provided
      if (typeof onMouseOut === 'function') {
        onMouseOut();
      }
    }
  };
  
  // Mouse leave handler
  const handleMouseLeave = () => {
    setHoveredDifference(null);
    
    if (typeof onMouseOut === 'function') {
      onMouseOut();
    }
  };
  
  return (
    <canvas
      ref={canvasRef}
      className={`difference-highlighter ${highlightMode === 'none' ? 'invisible' : ''}`}
      onClick={handleCanvasClick}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      width={width}
      height={height}
    />
  );
};

export default DifferenceHighlighter;