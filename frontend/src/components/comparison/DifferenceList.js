import React, { useState, useMemo, useCallback } from 'react';
import { usePreferences } from '../../context/PreferencesContext';
import './DifferenceList.css';

/**
 * Enhanced DifferenceList component - optimized to handle any difference format
 */
const DifferenceList = React.memo(({
  result, 
  onDifferenceClick, 
  selectedDifference 
}) => {
  // Get preferences 
  const { preferences } = usePreferences();
  
  // Component state
  const [searchTerm, setSearchTerm] = useState('');
  const [expandedTypes, setExpandedTypes] = useState({ text: true, image: true, font: true, style: true });
  const [typeFilters, setTypeFilters] = useState({ text: true, image: true, font: true, style: true });
  
  // Process differences with memoization
  const processedDifferences = useMemo(() => {
    console.log("Processing differences in DifferenceList", {
      baseDifferences: result?.baseDifferences?.length || 0,
      compareDifferences: result?.compareDifferences?.length || 0
    });
    
    // Skip if no result
    if (!result || (!result.baseDifferences?.length && !result.compareDifferences?.length)) {
      return {
        allDifferences: [],
        byType: { text: [], image: [], font: [], style: [] },
        filteredDifferences: [],
        differenceTypes: [],
        totalDifferences: 0
      };
    }
  
    // Combine base and compare differences
    const baseDiffs = result.baseDifferences || [];
    const compareDiffs = result.compareDifferences || [];
    
    // Use a Map to deduplicate differences by ID
    const differencesMap = new Map();
    
    // Add all differences to the map with unique IDs
    [...baseDiffs, ...compareDiffs].forEach(diff => {
      // Ensure there's an ID
      const id = diff.id || `diff-${Math.random().toString(36).substring(2, 11)}`;
      
      // Ensure there's a type
      if (!diff.type) {
        // Try to infer type from properties
        if (diff.imageData || diff.imageName) {
          diff.type = 'image';
        } else if (diff.fontName || diff.fontFamily) {
          diff.type = 'font';
        } else if (diff.styleName || diff.styleProperties) {
          diff.type = 'style';
        } else {
          diff.type = 'text'; // Default to text
        }
      }
      
      // Only add if not already in the map
      if (!differencesMap.has(id)) {
        differencesMap.set(id, { ...diff, id });
      }
    });
    
    // Convert to array and sort
    const allDifferences = Array.from(differencesMap.values()).sort((a, b) => {
      // Sort by page first
      const pageA = a.page || a.basePageNumber || a.comparePageNumber || 1;
      const pageB = b.page || b.basePageNumber || b.comparePageNumber || 1;
      
      if (pageA !== pageB) return pageA - pageB;
      
      // Then by position (if available)
      if (a.position?.y !== undefined && b.position?.y !== undefined) {
        return a.position.y - b.position.y;
      }
      
      if (a.baseY !== undefined && b.baseY !== undefined) {
        return a.baseY - b.baseY;
      }
      
      return 0;
    });
    
    // Filter differences based on searchTerm and typeFilters
    const filteredDifferences = allDifferences.filter(diff => {
      // Apply type filter first (faster)
      const type = diff.type || 'text';
      if (!typeFilters[type]) return false;
      
      // Then apply search if needed
      if (searchTerm.trim() === '') return true;
      
      const search = searchTerm.toLowerCase().trim();
      return (
        (diff.description && diff.description.toLowerCase().includes(search)) ||
        (diff.text && diff.text.toLowerCase().includes(search)) ||
        (diff.baseText && diff.baseText.toLowerCase().includes(search)) ||
        (diff.compareText && diff.compareText.toLowerCase().includes(search))
      );
    });
    
    // Group differences by type
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
        byType.text.push({...diff, type: 'text'});
      }
    });
    
    // Get all non-empty difference types
    const differenceTypes = Object.keys(byType).filter(type => byType[type].length > 0);
    
    console.log("Processed differences:", {
      total: allDifferences.length,
      filtered: filteredDifferences.length,
      byType: {
        text: byType.text.length,
        image: byType.image.length,
        font: byType.font.length,
        style: byType.style.length
      }
    });
    
    // Return everything needed for rendering
    return {
      allDifferences,
      byType,
      filteredDifferences,
      differenceTypes,
      totalDifferences: filteredDifferences.length
    };
  }, [result?.baseDifferences, result?.compareDifferences, searchTerm, typeFilters]);
  
  // Extract values from the memoized result
  const { 
    allDifferences, 
    byType, 
    filteredDifferences, 
    differenceTypes, 
    totalDifferences 
  } = processedDifferences;
  
  // Event handlers with proper memoization
  const handleSearchChange = useCallback((e) => {
    setSearchTerm(e.target.value);
  }, []);
  
  const handleClearSearch = useCallback(() => {
    setSearchTerm('');
  }, []);
  
  const toggleTypeFilter = useCallback((type) => {
    setTypeFilters(prev => ({
      ...prev,
      [type]: !prev[type]
    }));
  }, []);
  
  const toggleTypeExpand = useCallback((type) => {
    setExpandedTypes(prev => ({
      ...prev,
      [type]: !prev[type]
    }));
  }, []);
  
  // Memoized helper functions 
  const getTypeIcon = useCallback((type) => {
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
  }, []);
  
  const getTypeBadgeColor = useCallback((type) => {
    switch (type) {
      case 'text': return '#FF5252';
      case 'image': return '#2196F3';
      case 'font': return '#9C27B0';
      case 'style': return '#FF9800';
      default: return '#757575';
    }
  }, []);
  
  // Description formatter
  const formatDescription = useCallback((diff) => {
    if (diff.description) return diff.description;
    
    const truncateText = (text, maxLength) => {
      if (!text) return '';
      return text.length > maxLength ? text.substring(0, maxLength) + '...' : text;
    };
    
    switch (diff.type) {
      case 'text':
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
  }, []);

  // Render empty state
  if (totalDifferences === 0) {
    return (
      <div className="visual-difference-list">
        <div className="diff-list-search">
          <input 
            type="text" 
            placeholder="Search differences..." 
            value={searchTerm}
            onChange={handleSearchChange}
          />
          {searchTerm && (
            <button className="clear-search" onClick={handleClearSearch}>
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
          0 differences found
        </div>
        
        <div className="diff-items-container">
          <div className="no-differences">
            <p>No differences found in current view.</p>
            {searchTerm && <p>Try clearing your search or changing filters.</p>}
            {!searchTerm && result?.baseDifferences?.length === 0 && (
              <p>The documents appear to be identical.</p>
            )}
          </div>
        </div>
      </div>
    );
  }

  // Main render with differences
  return (
    <div className="visual-difference-list">
      <div className="diff-list-search">
        <input 
          type="text" 
          placeholder="Search differences..." 
          value={searchTerm}
          onChange={handleSearchChange}
        />
        {searchTerm && (
          <button className="clear-search" onClick={handleClearSearch}>
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
        {totalDifferences} {totalDifferences === 1 ? 'difference' : 'differences'} found
      </div>
      
      <div className="diff-items-container">
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
                  <span className="type-count">({byType[type].length})</span>
                </div>
                <div className="type-expand-icon">
                  <svg viewBox="0 0 24 24" style={{ transform: expandedTypes[type] ? 'rotate(180deg)' : 'none' }}>
                    <path d="M7 10l5 5 5-5z" />
                  </svg>
                </div>
              </div>
              
              {expandedTypes[type] && (
                <ul className="diff-items-list">
                  {byType[type].map(diff => (
                    <li 
                      key={diff.id}
                      className={`diff-item ${selectedDifference && selectedDifference.id === diff.id ? 'selected' : ''}`}
                      onClick={() => onDifferenceClick(diff)}
                    >
                      <div className="diff-item-content">
                        <div className="diff-page-indicator" title={`Page ${diff.page || diff.basePageNumber || diff.comparePageNumber || '?'}`}>
                          P{diff.page || diff.basePageNumber || diff.comparePageNumber || '?'}
                        </div>
                        <div className="diff-details">
                          <div className="diff-text">{formatDescription(diff)}</div>
                          {diff.severity && (
                            <div className={`diff-severity ${diff.severity}`}>
                              {diff.severity}
                            </div>
                          )}
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
      </div>
    </div>
  );
});

export default DifferenceList;