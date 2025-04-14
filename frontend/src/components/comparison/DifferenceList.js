import React, { useState, useEffect } from 'react';
import { usePreferences } from '../../context/PreferencesContext';
import './DifferenceList.css';

/**
 * Enhanced DifferenceList component to show only visible differences
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
  const [expandedTypes, setExpandedTypes] = useState({ text: true, image: true, font: true, style: true });
  const [typeFilters, setTypeFilters] = useState({ text: true, image: true, font: true, style: true });
  
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
    
    // Group differences by page if the page property exists
    const byPage = {};
    combined.forEach(diff => {
      const page = diff.page || 1;
      if (!byPage[page]) {
        byPage[page] = [];
      }
      byPage[page].push(diff);
    });
    
    // Sort differences by page, then by position on the page
    const sortedByPage = Object.entries(byPage).sort(([pageA], [pageB]) => parseInt(pageA) - parseInt(pageB));
    
    const sortedCombined = [];
    sortedByPage.forEach(([page, diffs]) => {
      // Sort differences by position (top to bottom)
      const sortedPageDiffs = diffs.sort((a, b) => {
        // Get Y position (if available)
        const aY = a.position?.y || a.y || 0;
        const bY = b.position?.y || b.y || 0;
        
        // Sort by Y first
        if (aY !== bY) return aY - bY;
        
        // Then by X (left to right)
        const aX = a.position?.x || a.x || 0;
        const bX = b.position?.x || b.x || 0;
        return aX - bX;
      });
      
      sortedCombined.push(...sortedPageDiffs);
    });
    
    setAllDifferences(sortedCombined);
    applyFilters(sortedCombined, searchTerm, typeFilters);
  }, [result, searchTerm]);

  // Filter differences based on search and type filters
  const applyFilters = (diffs, search, filters = typeFilters) => {
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
    
    // Apply type filters
    if (filters) {
      filtered = filtered.filter(diff => filters[diff.type || 'text']);
    }
    
    setFilteredDifferences(filtered);
  };

  // Handle type filter toggle
  const toggleTypeFilter = (type) => {
    const newFilters = { ...typeFilters, [type]: !typeFilters[type] };
    setTypeFilters(newFilters);
    applyFilters(allDifferences, searchTerm, newFilters);
  };

  // Group differences by type
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

  // Toggle expansion of a difference type
  const toggleTypeExpand = (type) => {
    setExpandedTypes(prev => ({
      ...prev,
      [type]: !prev[type]
    }));
  };

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

  // Organize differences by type for the grouped view
  const differencesByType = getDifferencesByType();
  const differenceTypes = Object.keys(differencesByType).filter(type => differencesByType[type].length > 0);

  // Calculate total differences count
  const totalDifferences = filteredDifferences.length;

  // Render an organized list of differences grouped by type
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
      
      <div className="diff-filters">
        <div className="filter-label">Filter by type:</div>
        <div className="filter-buttons">
          {Object.keys(typeFilters).map(type => (
            <button
              key={`filter-${type}`}
              className={`filter-button ${typeFilters[type] ? 'active' : ''}`}
              style={{
                backgroundColor: typeFilters[type] ? getTypeBadgeColor(type) : 'transparent',
                color: typeFilters[type] ? 'white' : getTypeBadgeColor(type),
                borderColor: getTypeBadgeColor(type)
              }}
              onClick={() => toggleTypeFilter(type)}
            >
              {type.charAt(0).toUpperCase() + type.slice(1)}
            </button>
          ))}
        </div>
      </div>
      
      <div className="diff-count">
        {totalDifferences} {totalDifferences === 1 ? 'difference' : 'differences'} found in current view
      </div>
      
      <div className="diff-items-container">
        {totalDifferences === 0 ? (
          <div className="no-differences">
            <p>No differences found in current view.</p>
            {searchTerm && <p>Try clearing your search or changing filters.</p>}
          </div>
        ) : (
          <div className="diff-items-by-type">
            {differenceTypes.map(type => (
              <div key={`type-${type}`} className="diff-type-group">
                <div 
                  className="diff-type-header"
                  onClick={() => toggleTypeExpand(type)}
                >
                  <div className="type-icon" style={{ backgroundColor: getTypeBadgeColor(type) }}>
                    {getTypeIcon(type)}
                  </div>
                  <div className="type-label">
                    {type.charAt(0).toUpperCase() + type.slice(1)} 
                    <span className="type-count">({differencesByType[type].length})</span>
                  </div>
                  <div className="type-expand-icon">
                    <svg viewBox="0 0 24 24" style={{ transform: expandedTypes[type] ? 'rotate(180deg)' : 'none' }}>
                      <path d="M7 10l5 5 5-5z" />
                    </svg>
                  </div>
                </div>
                
                {expandedTypes[type] && (
                  <ul className="diff-items-list">
                    {differencesByType[type].map(diff => (
                      <li 
                        key={diff.id}
                        className={`diff-item ${selectedDifference && selectedDifference.id === diff.id ? 'selected' : ''}`}
                        onClick={() => onDifferenceClick(diff)}
                      >
                        <div className="diff-item-content">
                          <div className="diff-page-indicator" title={`Page ${diff.page || '?'}`}>
                            P{diff.page || '?'}
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
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default DifferenceList;