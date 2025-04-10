/**
 * Utility functions for handling coordinate transformations between PDF and Canvas
 */

/**
 * Transforms PDF coordinates (origin at bottom-left) to Canvas coordinates (origin at top-left)
 * 
 * @param {number} x - X coordinate in PDF space
 * @param {number} y - Y coordinate in PDF space (from bottom)
 * @param {number} width - Width of the object
 * @param {number} height - Height of the object
 * @param {Object} dimensions - Image dimensions with properties:
 *   - naturalHeight: the natural height of the image
 *   - renderedHeight: the rendered height on screen
 *   - scaleX: the ratio of rendered width to natural width
 *   - scaleY: the ratio of rendered height to natural height
 * @param {number} zoom - Current zoom level (default: 1)
 * @returns {Object} Transformed coordinates for canvas
 */
export const pdfToCanvasCoordinates = (x, y, width, height, dimensions, zoom = 1) => {
  const { naturalHeight, renderedHeight, scaleX, scaleY } = dimensions;
  
  // Scale X coordinate
  const canvasX = x * scaleX * zoom;
  
  // For Y coordinate:
  // 1. In PDF: y measures distance from bottom of page to baseline of the object
  // 2. In Canvas: y measures distance from top of canvas to top of the object
  // So we need to invert the Y axis and adjust for the height of the object
  const canvasY = renderedHeight * zoom - (y + height) * scaleY * zoom;
  
  // Scale width and height
  const canvasWidth = width * scaleX * zoom;
  const canvasHeight = height * scaleY * zoom;
  
  return {
    x: canvasX,
    y: canvasY,
    width: canvasWidth,
    height: canvasHeight
  };
};

/**
 * Transforms Canvas coordinates (origin at top-left) to PDF coordinates (origin at bottom-left)
 * 
 * @param {number} canvasX - X coordinate in Canvas space
 * @param {number} canvasY - Y coordinate in Canvas space (from top)
 * @param {Object} dimensions - Image dimensions with properties:
 *   - naturalHeight: the natural height of the image
 *   - renderedHeight: the rendered height on screen
 *   - scaleX: the ratio of rendered width to natural width
 *   - scaleY: the ratio of rendered height to natural height
 * @param {number} zoom - Current zoom level (default: 1)
 * @returns {Object} Transformed coordinates for PDF space
 */
export const canvasToPdfCoordinates = (canvasX, canvasY, dimensions, zoom = 1) => {
  const { naturalHeight, renderedHeight, scaleX, scaleY } = dimensions;
  
  // Unscale X coordinate
  const pdfX = canvasX / (scaleX * zoom);
  
  // For Y coordinate: convert from top-down to bottom-up coordinate system
  const pdfY = (renderedHeight - canvasY / scaleY) / scaleY;
  
  return {
    x: pdfX,
    y: pdfY
  };
};

/**
 * Extract uniform coordinates from difference object regardless of the format
 * 
 * @param {Object} diff - Difference object from API
 * @returns {Object|null} Standardized coordinates or null if not available
 */
export const extractCoordinates = (diff) => {
  // Check if we have the direct properties (x, y, width, height)
  if (diff.x !== undefined && diff.y !== undefined && 
      diff.width !== undefined && diff.height !== undefined) {
    return {
      x: diff.x,
      y: diff.y,
      width: diff.width,
      height: diff.height
    };
  } 
  // Check if we have position and bounds objects
  else if (diff.position && diff.bounds) {
    return {
      x: diff.position.x || 0,
      y: diff.position.y || 0,
      width: diff.bounds.width || 0,
      height: diff.bounds.height || 0
    };
  }
  // Check if we have left/top/right/bottom properties
  else if (diff.left !== undefined && diff.top !== undefined && 
           diff.right !== undefined && diff.bottom !== undefined) {
    return {
      x: diff.left,
      y: diff.top,
      width: diff.right - diff.left,
      height: diff.bottom - diff.top
    };
  }
  
  // Return null if we couldn't extract coordinates
  return null;
};

/**
 * Checks if a point in PDF coordinates is within a difference's bounds
 * 
 * @param {number} pdfX - X coordinate in PDF space
 * @param {number} pdfY - Y coordinate in PDF space
 * @param {Object} diff - Difference object from API
 * @returns {boolean} True if point is within the difference's bounds
 */
export const isPointInDifference = (pdfX, pdfY, diff) => {
  const coords = extractCoordinates(diff);
  if (!coords) return false;
  
  const { x, y, width, height } = coords;
  
  return (
    pdfX >= x && 
    pdfX <= x + width && 
    pdfY >= y && 
    pdfY <= y + height
  );
};

/**
 * Get color for a difference based on its type and change type
 * 
 * @param {string} type - Difference type (text, image, font, style)
 * @param {string} changeType - Change type (added, deleted, modified)
 * @param {boolean} isStroke - Whether this is for stroke or fill
 * @param {boolean} isSelected - Whether this difference is selected
 * @returns {Object} Fill and stroke colors
 */
export const getDifferenceColors = (type, changeType, isSelected = false) => {
  let fillColor, strokeColor;
  
  // Determine by change type first
  switch (changeType) {
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
      // Fall back to type-based colors
      switch (type) {
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
  
  // Enhance colors for selected differences
  if (isSelected) {
    fillColor = fillColor.replace('0.3', '0.5');
    strokeColor = strokeColor.replace('0.8', '1.0');
  }
  
  return { fillColor, strokeColor };
};

/**
 * Get a visual indicator for the type of change
 * 
 * @param {string} changeType - The type of change (added, deleted, modified)
 * @returns {string} Character indicator for the change type
 */
export const getChangeTypeIndicator = (changeType) => {
  switch (changeType) {
    case 'added':
      return '+';
    case 'deleted':
      return '-';
    case 'modified':
      return '~';
    default:
      return '';
  }
};

export default {
  pdfToCanvasCoordinates,
  canvasToPdfCoordinates,
  extractCoordinates,
  isPointInDifference,
  getDifferenceColors,
  getChangeTypeIndicator
};