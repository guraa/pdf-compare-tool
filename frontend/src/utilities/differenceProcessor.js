// utilities/differenceProcessor.js
// This utility helps ensure proper typing and categorization of differences

/**
 * Process raw difference data to ensure correct typing and categorization
 * @param {Object} difference - The raw difference object from the API
 * @returns {Object} - Enhanced difference object with proper typing
 */
export const processDifference = (difference) => {
    if (!difference) return null;
    
    // Create a copy to avoid mutating the original
    const processed = { ...difference };
    
    // Ensure there's a type field - infer from content if needed
    if (!processed.type) {
      // Try to infer the type from available data
      if (processed.imageName || processed.imageData) {
        processed.type = 'image';
      } else if (processed.fontName || processed.font) {
        processed.type = 'font';
      } else if (processed.styleName || (processed.styleProperties && Object.keys(processed.styleProperties).length > 0)) {
        processed.type = 'style';
      } else if (processed.baseText || processed.compareText || processed.text) {
        processed.type = 'text';
      } else {
        // Default to text for untyped differences
        processed.type = 'text';
      }
    }
    
    // Ensure there's a change type
    if (!processed.changeType) {
      if (processed.baseText && !processed.compareText) {
        processed.changeType = 'deleted';
      } else if (!processed.baseText && processed.compareText) {
        processed.changeType = 'added';
      } else if (processed.baseText && processed.compareText) {
        processed.changeType = 'modified';
      }
    }
    
    // Ensure dimensions are reasonable and non-zero
    if (processed.position && processed.bounds) {
      // Ensure width and height are non-zero
      if (!processed.bounds.width || processed.bounds.width < 2) {
        processed.bounds.width = 10; // Set a minimum width
      }
      
      if (!processed.bounds.height || processed.bounds.height < 2) {
        processed.bounds.height = 10; // Set a minimum height
      }
    }
    
    // Ensure there's an ID
    if (!processed.id) {
      processed.id = `diff-${Math.random().toString(36).substr(2, 9)}`;
    }
    
    return processed;
  };
  
  /**
   * Process a collection of differences to ensure proper typing
   * @param {Array} differences - Array of difference objects
   * @returns {Array} - Enhanced differences with proper typing
   */
  export const processDifferences = (differences) => {
    if (!Array.isArray(differences)) return [];
    
    return differences.map(processDifference).filter(Boolean);
  };
  
  /**
   * Group differences by type
   * @param {Array} differences - Array of difference objects
   * @returns {Object} - Object with differences grouped by type
   */
  export const groupDifferencesByType = (differences) => {
    const groups = {
      text: [],
      image: [],
      font: [],
      style: [],
      structure: []
    };
    
    if (!Array.isArray(differences)) return groups;
    
    differences.forEach(diff => {
      const processed = processDifference(diff);
      if (!processed) return;
      
      // Add to the appropriate group based on type
      if (groups[processed.type]) {
        groups[processed.type].push(processed);
      } else {
        // For unrecognized types, add to structure
        groups.structure.push(processed);
      }
    });
    
    return groups;
  };
  
  /**
   * Count differences by type
   * @param {Array} differences - Array of difference objects
   * @returns {Object} - Object with counts per type
   */
  export const countDifferencesByType = (differences) => {
    const groups = groupDifferencesByType(differences);
    
    return {
      text: groups.text.length,
      image: groups.image.length,
      font: groups.font.length,
      style: groups.style.length,
      structure: groups.structure.length,
      total: differences.length
    };
  };
  
  export default {
    processDifference,
    processDifferences,
    groupDifferencesByType,
    countDifferencesByType
  };