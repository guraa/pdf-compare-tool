import React, { useState, useEffect } from 'react';
import { usePreferences } from '../../context/PreferencesContext';
import './DifferenceList.css';

/**
 * DifferenceList component styled to match the reference images
 */
const DifferenceList = ({
  result, 
  onDifferenceClick, 
  selectedDifference 
}) => {
  const { preferences } = usePreferences();
  const [allDifferences, setAllDifferences] = useState([]);
  const [filteredDifferences, setFilteredDifferences] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  
  // Process differences when data changes
  useEffect(() => {
    if (!result) {
      setAllDifferences([]);
      setFilteredDifferences([]);
      return;
    }

    // Combine base and compare differences, ensuring no duplicates
    const baseDiffs = result.baseDifferences || [];
    const compareDiffs = result.compareDifferences || [];
    
    // Create a Map with ID as key to prevent duplicates
    const differencesMap = new Map();
    
    // Add base differences first
    baseDiffs.forEach(diff => {
      differencesMap.set(diff.id, { ...diff, source: 'base' });
    });
    
    // Add compare differences (will overwrite any duplicates from base)
    compareDiffs.forEach(diff => {
      // Only add if not already added from base
      if (!differencesMap.has(diff.id)) {
        differencesMap.set(diff.id, { ...diff, source: 'compare' });
      }
    });
    
    // Convert map to array
    const combined = Array.from(differencesMap.values());
    
    setAllDifferences(combined);
    applyFilters(combined, searchTerm);
  }, [result, searchTerm]);

  // Filter differences based on search
  const applyFilters = (diffs, search) => {
    if (!diffs || diffs.length === 0) {
      setFilteredDifferences([]);
      return;
    }
    
    let filtered = [...diffs];
    
    // Apply search filter
    if (search && search.trim() !== '') {
      const searchLower = search.toLowerCase().trim();
      filtered = filtered.filter(diff => 
        (diff.description && diff.description.toLowerCase().includes(searchLower)) ||
        (diff.text && diff.text.toLowerCase().includes(searchLower)) ||
        (diff.baseText && diff.baseText.toLowerCase().includes(searchLower)) ||
        (diff.compareText && diff.compareText.toLowerCase().includes(searchLower))
      );
    }
    
    // Group differences by type
    const byType = {};
    
    filtered.forEach(diff => {
      const type = diff.type || 'other';
      if (!byType[type]) {
        byType[type] = [];
      }
      byType[type].push(diff);
    });
    
    // Sort them in a specific order that matches the reference images
    const sortedFiltered = [];
    
    // Text differences first (most common)
    if (byType.text) {
      sortedFiltered.push(...byType.text);
    }
    
    // Image differences next
    if (byType.image) {
      sortedFiltered.push(...byType.image);
    }
    
    // Font differences
    if (byType.font) {
      sortedFiltered.push(...byType.font);
    }
    
    // Style differences
    if (byType.style) {
      sortedFiltered.push(...byType.style);
    }
    
    // All other difference types
    Object.keys(byType).forEach(type => {
      if (!['text', 'image', 'font', 'style'].includes(type)) {
        sortedFiltered.push(...byType[type]);
      }
    });
    
    setFilteredDifferences(sortedFiltered);
  };

  // Group differences by type for categories display
  const getDifferencesByType = () => {
    const byType = {
      text: [],
      image: [],
      font: [],
      style: []
    };
    
    filteredDifferences.forEach(diff => {
      const type = diff.type || 'text';
      if (byType[type]) {
        byType[type].push(diff);
      } else {
        // If we encounter an unknown type, add it to text for now
        byType.text.push({...diff, type: 'text'});
      }
    });
    
    return byType;
  };

  const differencesByType = getDifferencesByType();

  // Get icon for difference type
  const getTypeIcon = (type) => {
    switch (type) {
      case 'text':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M2.5 4v3h5v12h3V7h5V4h-13zm19 5h-9v3h3v7h3v-7h3V9z" />
          </svg>
        );
      case 'image':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z" />
          </svg>
        );
      case 'font':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M9.93 13.5h4.14L12 7.98zM20 2H4c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-4.05 16.5l-1.14-3H9.17l-1.12 3H5.96l5.11-13h1.86l5.11 13h-2.09z" />
          </svg>
        );
      case 'style':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M2.53 19.65l1.34.56v-9.03l-2.43 5.86c-.41 1.02.08 2.19 1.09 2.61zm19.5-3.7L17.07 3.98c-.31-.75-1.04-1.21-1.81-1.23-.26 0-.53.04-.79.15L7.1 5.95c-.75.31-1.21 1.03-1.23 1.8-.01.27.04.54.15.8l4.96 11.97c.31.76 1.05 1.22 1.83 1.23.26 0 .52-.05.77-.15l7.36-3.05c1.02-.42 1.51-1.59 1.09-2.6z" />
          </svg>
        );
      default:
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M2.5 4v3h5v12h3V7h5V4h-13zm19 5h-9v3h3v7h3v-7h3V9z" />
          </svg>
        );
    }
  };

  // Get color for difference type badge
  const getTypeBadgeColor = (type) => {
    switch (type) {
      case 'text':
        return '#FF5252';
      case 'image':
        return '#2196F3';
      case 'font':
        return '#9C27B0';
      case 'style':
        return '#FF9800';
      default:
        return '#757575';
    }
  };

  // Format difference description to match the reference images
  const formatDescription = (diff) => {
    if (diff.description) return diff.description;
    
    switch (diff.type) {
      case 'text':
        // In reference image, text differences show specific change patterns
        if (diff.baseText && diff.compareText) {
          return `Text changed from "${truncateText(diff.baseText, 15)}" to "${truncateText(diff.compareText, 15)}"`;
        } else if (!diff.baseText && diff.compareText) {
          return `Text added: "${truncateText(diff.compareText, 30)}"`;
        } else if (diff.baseText && !diff.compareText) {
          return `Text deleted: "${truncateText(diff.baseText, 30)}"`;
        } else if (diff.text) {
          return truncateText(diff.text, 30);
        }
        return 'Text difference';
        
      case 'image':
        if (diff.changeType === 'added') {
          return 'Image added';
        } else if (diff.changeType === 'deleted') {
          return 'Image deleted';
        } else if (diff.changeType === 'modified') {
          return 'Image changed';
        } else if (diff.imageName) {
          return `Image: ${diff.imageName}`;
        }
        return 'Image content differs';
        
      case 'font':
        if (diff.fontName) {
          return `Font changed to "${diff.fontName}"`;
        } else if (diff.text) {
          return `Font difference in "${truncateText(diff.text, 20)}"`;
        }
        return 'Font difference';
        
      case 'style':
        if (diff.styleName) {
          return `Style element ${diff.changeType || 'changed'}: "${diff.styleName}"`;
        } else if (diff.text) {
          return `Style changed in "${truncateText(diff.text, 20)}"`;
        }
        return 'Style difference';
        
      default:
        return 'Difference detected';
    }
  };
  
  // Helper function to truncate text
  const truncateText = (text, maxLength) => {
    if (!text) return '';
    return text.length > maxLength ? text.substring(0, maxLength) + '...' : text;
  };

  // Render the difference list to match the reference image
  return (
    <div className="visual-difference-list">
      <div className="diff-list-search">
        <input 
          type="text" 
          placeholder="Search differences..." 
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
        {searchTerm && (
          <button 
            className="clear-search" 
            onClick={() => setSearchTerm('')}
          >
            ×
          </button>
        )}
      </div>
      
      <div className="diff-types-header">
        <span>All Types</span>
        <svg className="expand-icon" viewBox="0 0 24 24">
          <path d="M7 10l5 5 5-5z" />
        </svg>
      </div>
      
      <div className="diff-count">
        {filteredDifferences.length} differences found
      </div>
      
      <div className="diff-items-container">
        {filteredDifferences.length === 0 ? (
          <div className="no-differences">
            <p>No differences found.</p>
            {searchTerm && <p>Try different search terms.</p>}
          </div>
        ) : (
          <ul className="diff-items-list">
            {filteredDifferences.map(diff => (
              <li 
                key={diff.id}
                className={`diff-item ${selectedDifference && selectedDifference.id === diff.id ? 'selected' : ''}`}
                onClick={() => onDifferenceClick(diff)}
              >
                <div className="diff-item-content">
                  <div className="diff-badge" style={{ backgroundColor: getTypeBadgeColor(diff.type) }}>
                    {diff.type.charAt(0).toUpperCase()}
                  </div>
                  <div className="diff-details">
                    <div className="diff-text">{formatDescription(diff)}</div>
                  </div>
                  <div className="diff-arrow">
                    <svg viewBox="0 0 24 24">
                      <path d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6-1.41-1.41z" />
                    </svg>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
};

export default DifferenceList;