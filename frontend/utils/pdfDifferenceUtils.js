/**
 * Utility functions to support PDF difference highlighting
 */

// Default colors for different types of differences
const DEFAULT_COLORS = {
    text: {
      fill: 'rgba(255, 82, 82, 0.3)',
      stroke: 'rgba(255, 82, 82, 0.8)'
    },
    image: {
      fill: 'rgba(33, 150, 243, 0.3)',
      stroke: 'rgba(33, 150, 243, 0.8)'
    },
    font: {
      fill: 'rgba(156, 39, 176, 0.3)',
      stroke: 'rgba(156, 39, 176, 0.8)'
    },
    style: {
      fill: 'rgba(255, 152, 0, 0.3)',
      stroke: 'rgba(255, 152, 0, 0.8)'
    },
    metadata: {
      fill: 'rgba(0, 150, 136, 0.3)',
      stroke: 'rgba(0, 150, 136, 0.8)'
    },
    // Change types
    added: {
      fill: 'rgba(76, 175, 80, 0.3)',
      stroke: 'rgba(76, 175, 80, 0.8)'
    },
    deleted: {
      fill: 'rgba(244, 67, 54, 0.3)',
      stroke: 'rgba(244, 67, 54, 0.8)'
    },
    modified: {
      fill: 'rgba(255, 152, 0, 0.3)',
      stroke: 'rgba(255, 152, 0, 0.8)'
    },
    // Selection highlighting
    selected: {
      stroke: 'rgba(255, 255, 0, 0.8)',
      outlineWidth: 3
    }
  };
  
  /**
   * Draw difference highlights on a canvas
   * @param {Object} ctx - Canvas 2D context
   * @param {Array} differences - Array of difference objects
   * @param {number} zoom - Current zoom level
   * @param {Object} selectedDifference - Currently selected difference (if any)
   */
  export const drawDifferenceHighlights = (ctx, differences, zoom, selectedDifference = null) => {
    if (!ctx || !differences || !differences.length) return;
    
    // Clear the canvas first
    const canvas = ctx.canvas;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // Draw each difference
    differences.forEach(diff => {
      if (!diff.position || !diff.bounds) return;
      
      // Calculate zoomed coordinates
      const x = diff.position.x * zoom;
      const y = diff.position.y * zoom;
      const w = diff.bounds.width * zoom;
      const h = diff.bounds.height * zoom;
      
      // Determine colors based on difference type and change type
      let colors = DEFAULT_COLORS[diff.type] || DEFAULT_COLORS.text;
      
      // Override with change type if present
      if (diff.changeType && DEFAULT_COLORS[diff.changeType]) {
        colors = DEFAULT_COLORS[diff.changeType];
      }
      
      // Check if this is the selected difference
      const isSelected = selectedDifference && selectedDifference.id === diff.id;
      
      // Draw the highlight with slightly rounded corners
      ctx.fillStyle = colors.fill;
      ctx.strokeStyle = colors.stroke;
      ctx.lineWidth = isSelected ? 2 : 1;
      
      // Draw main highlight
      ctx.beginPath();
      ctx.roundRect(x, y, w, h, 3);
      ctx.fill();
      ctx.stroke();
      
      // Add selection highlight if selected
      if (isSelected) {
        const selectionColor = DEFAULT_COLORS.selected;
        ctx.strokeStyle = selectionColor.stroke;
        ctx.lineWidth = selectionColor.outlineWidth;
        ctx.beginPath();
        ctx.roundRect(x - 2, y - 2, w + 4, h + 4, 5);
        ctx.stroke();
        
        // Add a label for larger differences
        if (w > 50 && h > 20) {
          ctx.fillStyle = colors.stroke;
          ctx.font = '10px Arial';
          ctx.fillText(diff.type.charAt(0).toUpperCase() + diff.type.slice(1), x + 4, y + 12);
        }
      }
    });
  };
  
  /**
   * Create tooltip content for a difference
   * @param {Object} difference - The difference object
   * @returns {String} HTML content for the tooltip
   */
  export const createDifferenceTooltip = (difference) => {
    if (!difference) return '';
    
    const { type, changeType } = difference;
    let content = '';
    
    // Create title based on type and change type
    const title = `${type.charAt(0).toUpperCase() + type.slice(1)} ${changeType || 'Difference'}`;
    
    // Create content based on type
    switch (type) {
      case 'text':
        content = `
          <div class="tooltip-title">${title}</div>
          ${difference.baseText ? `<div class="base-text"><span class="label">Base:</span> ${difference.baseText}</div>` : ''}
          ${difference.compareText ? `<div class="compare-text"><span class="label">Compare:</span> ${difference.compareText}</div>` : ''}
          ${!difference.baseText && !difference.compareText && difference.text ? `<div>${difference.text}</div>` : ''}
        `;
        break;
        
      case 'image':
        content = `
          <div class="tooltip-title">${title}</div>
          ${difference.imageName ? `<div>Name: ${difference.imageName}</div>` : ''}
          ${difference.description ? `<div>${difference.description}</div>` : ''}
          ${difference.bounds ? `<div>Size: ${Math.round(difference.bounds.width)} x ${Math.round(difference.bounds.height)} px</div>` : ''}
        `;
        break;
        
      case 'font':
        content = `
          <div class="tooltip-title">${title}</div>
          ${difference.fontName ? `<div>Font: ${difference.fontName}</div>` : ''}
          ${difference.text ? `<div>Text: "${difference.text}"</div>` : ''}
        `;
        break;
        
      case 'style':
        content = `
          <div class="tooltip-title">${title}</div>
          ${difference.styleName ? `<div>Style: ${difference.styleName}</div>` : ''}
          ${difference.text ? `<div>Text: "${difference.text}"</div>` : ''}
        `;
        break;
        
      default:
        content = `
          <div class="tooltip-title">${title}</div>
          <div>${difference.description || 'No description available'}</div>
        `;
    }
    
    return content;
  };
  
  /**
   * Find a difference at the given coordinates
   * @param {Array} differences - Array of difference objects
   * @param {number} x - X coordinate
   * @param {number} y - Y coordinate
   * @param {number} zoom - Current zoom level
   * @returns {Object|null} The difference at the coordinates, or null if none
   */
  export const findDifferenceAtCoordinates = (differences, x, y, zoom) => {
    if (!differences || !differences.length) return null;
    
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
  
  export default {
    drawDifferenceHighlights,
    createDifferenceTooltip,
    findDifferenceAtCoordinates,
    DEFAULT_COLORS
  };