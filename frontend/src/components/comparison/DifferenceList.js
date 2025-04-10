import React, { useState, useEffect } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import { usePreferences } from '../../context/PreferencesContext';
import './DifferenceList.css';

const DifferenceList = ({
  result, 
  onDifferenceClick, 
  selectedDifference 
}) => {
  const { preferences } = usePreferences();
  const [allDifferences, setAllDifferences] = useState([]);
  const [filteredDifferences, setFilteredDifferences] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [typeFilter, setTypeFilter] = useState('all');
  const [expandedCategories, setExpandedCategories] = useState({
    text: true,
    image: true,
    font: true,
    style: true,
    metadata: true
  });

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
    applyFilters(combined, searchTerm, typeFilter);
  }, [result, searchTerm, typeFilter]);

  const applyFilters = (diffs, search, type) => {
    if (!diffs || diffs.length === 0) {
      setFilteredDifferences([]);
      return;
    }
    
    let filtered = [...diffs];
    
    // Apply type filter
    if (type !== 'all') {
      filtered = filtered.filter(diff => diff.type === type);
    }
    
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
    
    setFilteredDifferences(filtered);
  };

  // Group differences by type
  const getDifferencesByType = () => {
    const byType = {
      text: [],
      image: [],
      font: [],
      style: [],
      metadata: []
    };
    
    filteredDifferences.forEach(diff => {
      const type = diff.type || 'metadata';
      if (byType[type]) {
        byType[type].push(diff);
      } else {
        // If we encounter an unknown type, add it to metadata
        byType.metadata.push(diff);
      }
    });
    
    return byType;
  };

  const differencesByType = getDifferencesByType();

  // Toggle expanded state for a category
  const toggleCategoryExpansion = (category) => {
    setExpandedCategories(prev => ({
      ...prev,
      [category]: !prev[category]
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
      case 'metadata':
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z" />
          </svg>
        );
      default:
        return (
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4zm6 0h4v-4h-4v4zm6-10v4h4V4h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z" />
          </svg>
        );
    }
  };

  // Get color by type
  const getTypeColor = (type) => {
    if (preferences?.differenceColors && preferences.differenceColors[type]) {
      return preferences.differenceColors[type];
    }
    
    switch (type) {
      case 'text':
        return '#FF5252';
      case 'image':
        return '#2196F3';
      case 'font':
        return '#9C27B0';
      case 'style':
        return '#FF9800';
      case 'metadata':
        return '#4CAF50';
      default:
        return '#757575';
    }
  };

  return (
    <div className="simplified-difference-list">
      <div className="difference-list-header">
        <div className="difference-search">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z" />
          </svg>
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
              title="Clear search"
            >
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
              </svg>
            </button>
          )}
        </div>
        
        <div className="difference-filter">
          <select 
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
          >
            <option value="all">All Types</option>
            <option value="text">Text</option>
            <option value="image">Images</option>
            <option value="font">Fonts</option>
            <option value="style">Styles</option>
            <option value="metadata">Metadata</option>
          </select>
        </div>
      </div>
      
      <div className="difference-summary">
        {filteredDifferences.length} difference{filteredDifferences.length !== 1 ? 's' : ''} found
      </div>
      
      <div className="difference-categories">
        {Object.entries(differencesByType).map(([type, diffs]) => 
          diffs.length > 0 ? (
            <div key={type} className={`category-group ${type}`}>
              <div 
                className="category-header"
                onClick={() => toggleCategoryExpansion(type)}
              >
                <div className="category-icon" style={{ backgroundColor: getTypeColor(type) }}>
                  {getTypeIcon(type)}
                </div>
                <div className="category-name">
                  {type.charAt(0).toUpperCase() + type.slice(1)}
                </div>
                <div className="category-count">{diffs.length}</div>
                <div className="category-toggle">
                  {expandedCategories[type] ? '▼' : '►'}
                </div>
              </div>
              
              {expandedCategories[type] && (
                <div className="category-items">
                  {diffs.map(diff => (
                    <div 
                      key={diff.id} 
                      className={`difference-item ${diff.id === selectedDifference?.id ? 'selected' : ''}`}
                      onClick={() => onDifferenceClick(diff)}
                    >
                      <div className="difference-source" title={`Found in ${diff.source} document`}>
                        {diff.source === 'base' ? 'B' : 'C'}
                      </div>
                      <div className="difference-content">
                        <div className="difference-description">
                          {diff.description || (
                            diff.type === 'text' ? (
                              <>
                                Base: "{diff.baseText || ''}" → Compare: "{diff.compareText || ''}"
                              </>
                            ) : diff.type === 'image' ? (
                              <>Image: {diff.imageName || 'Unnamed image'}</>
                            ) : diff.type === 'font' ? (
                              <>Font: {diff.fontName || 'Font difference'}</>
                            ) : diff.type === 'style' ? (
                              <>Style: {diff.styleName || (diff.text ? `"${diff.text}"` : 'Style difference')}</>
                            ) : 'Difference detected'
                          )}
                        </div>
                        {diff.bounds && (
                          <div className="difference-position">
                            x: {diff.position?.x || 0}, y: {diff.position?.y || 0}, w: {diff.bounds.width || 0}, h: {diff.bounds.height || 0}
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : null
        )}
      </div>
      
      {filteredDifferences.length === 0 && (
        <div className="no-differences">
          <p>No differences found on this page.</p>
        </div>
      )}
    </div>
  );
};
export default DifferenceList;