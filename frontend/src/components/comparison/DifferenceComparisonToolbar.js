import React, { useState, useEffect, useCallback } from 'react';
import './DifferenceComparisonToolbar.css';

/**
 * Toolbar component for the difference comparison view
 * Provides controls for navigating through differences and visualizing them
 */
const DifferenceComparisonToolbar = ({
  differences,
  selectedDifference,
  onSelectDifference,
  currentPage,
  totalPages,
  onPageChange,
  viewMode,
  onViewModeChange,
  highlightMode,
  onHighlightModeChange
}) => {
  const [currentDiffIndex, setCurrentDiffIndex] = useState(-1);
  const [flattenedDiffs, setFlattenedDiffs] = useState([]);
  const [diffCount, setDiffCount] = useState(0);
  
  // Process differences when they change
  useEffect(() => {
    if (!differences) {
      setFlattenedDiffs([]);
      setDiffCount(0);
      return;
    }
    
    // Create flattened array of differences to navigate through
    const baseDiffs = differences.baseDifferences || [];
    const compareDiffs = differences.compareDifferences || [];
    
    // Remove duplicates by using a Map with the diff ID as key
    const uniqueDiffs = new Map();
    
    [...baseDiffs, ...compareDiffs].forEach(diff => {
      if (!uniqueDiffs.has(diff.id)) {
        uniqueDiffs.set(diff.id, diff);
      }
    });
    
    const diffs = Array.from(uniqueDiffs.values());
    setFlattenedDiffs(diffs);
    setDiffCount(diffs.length);
    
    // Update current diff index if we have a selected difference
    if (selectedDifference) {
      const index = diffs.findIndex(diff => diff.id === selectedDifference.id);
      setCurrentDiffIndex(index !== -1 ? index : -1);
    } else {
      setCurrentDiffIndex(-1);
    }
  }, [differences, selectedDifference]);
  
  // Handle navigation between differences
  const goToPrevDifference = useCallback(() => {
    if (flattenedDiffs.length === 0) return;
    
    const newIndex = currentDiffIndex <= 0 
      ? flattenedDiffs.length - 1 
      : currentDiffIndex - 1;
    
    setCurrentDiffIndex(newIndex);
    onSelectDifference(flattenedDiffs[newIndex]);
  }, [flattenedDiffs, currentDiffIndex, onSelectDifference]);
  
  const goToNextDifference = useCallback(() => {
    if (flattenedDiffs.length === 0) return;
    
    const newIndex = currentDiffIndex >= flattenedDiffs.length - 1 
      ? 0 
      : currentDiffIndex + 1;
    
    setCurrentDiffIndex(newIndex);
    onSelectDifference(flattenedDiffs[newIndex]);
  }, [flattenedDiffs, currentDiffIndex, onSelectDifference]);
  
  // Handle keyboard navigation
  useEffect(() => {
    const handleKeyDown = (e) => {
      // Don't capture key events if inside an input field
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
        return;
      }
      
      switch (e.key) {
        case 'ArrowRight':
          // Next page with Ctrl
          if (e.ctrlKey) {
            onPageChange(Math.min(currentPage + 1, totalPages));
          } else {
            // Next difference
            goToNextDifference();
          }
          break;
        case 'ArrowLeft':
          // Previous page with Ctrl
          if (e.ctrlKey) {
            onPageChange(Math.max(currentPage - 1, 1));
          } else {
            // Previous difference
            goToPrevDifference();
          }
          break;
        case 'PageUp':
          onPageChange(Math.max(currentPage - 1, 1));
          break;
        case 'PageDown':
          onPageChange(Math.min(currentPage + 1, totalPages));
          break;
        default:
          return;
      }
      
      e.preventDefault();
    };
    
    window.addEventListener('keydown', handleKeyDown);
    
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [currentPage, totalPages, onPageChange, goToNextDifference, goToPrevDifference]);
  
  // Format difference description for display
  const formatDiffDescription = useCallback((diff) => {
    if (!diff) return 'No difference selected';
    
    const typeMap = {
      text: 'Text',
      image: 'Image',
      font: 'Font',
      style: 'Style',
      structure: 'Structure'
    };
    
    const changeMap = {
      added: 'Added',
      deleted: 'Deleted',
      modified: 'Modified'
    };
    
    const type = typeMap[diff.type] || 'Unknown';
    const change = changeMap[diff.changeType] || '';
    
    return `${type} ${change}: ${diff.description || ''}`;
  }, []);

  return (
    <div className="difference-toolbar">
      <div className="diff-navigation-group">
        <button 
          className="diff-nav-button prev"
          onClick={goToPrevDifference}
          disabled={flattenedDiffs.length === 0}
          title="Previous difference"
        >
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M15.41 16.59L10.83 12l4.58-4.59L14 6l-6 6 6 6 1.41-1.41z" />
          </svg>
        </button>
        
        <div className="diff-counter" title="Current difference / Total differences">
          {flattenedDiffs.length > 0 ? 
            `${currentDiffIndex + 1} / ${flattenedDiffs.length}` : 
            `0 differences`
          }
        </div>
        
        <button 
          className="diff-nav-button next"
          onClick={goToNextDifference}
          disabled={flattenedDiffs.length === 0}
          title="Next difference"
        >
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6-1.41-1.41z" />
          </svg>
        </button>
      </div>
      
      <div className="diff-description" title={selectedDifference?.description || ''}>
        {formatDiffDescription(selectedDifference)}
      </div>
      
      <div className="view-controls-group">
        <div className="view-mode-selector">
          <button 
            className={`view-mode-button ${viewMode === 'sideBySide' ? 'active' : ''}`}
            onClick={() => onViewModeChange('sideBySide')}
            title="Side by side view"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M3 5v14h18V5H3zm8 14H3V5h8v14zm10 0h-8V5h8v14z" />
            </svg>
          </button>
          
          <button 
            className={`view-mode-button ${viewMode === 'overlay' ? 'active' : ''}`}
            onClick={() => onViewModeChange('overlay')}
            title="Overlay view"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M18 16h-2v-1h-1c-1.1 0-2-.9-2-2V9h5V8c0-1.1-.9-2-2-2h-6c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h5c1.1 0 2-.9 2-2v-1c0-.55.45-1 1-1s1 .45 1 1v1z" />
              <circle cx="17" cy="12" r="1" />
            </svg>
          </button>
          
          <button 
            className={`view-mode-button ${viewMode === 'changes' ? 'active' : ''}`}
            onClick={() => onViewModeChange('changes')}
            title="Changes only view"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14zm-7-2h2V7h-4v2h2z" />
            </svg>
          </button>
        </div>
        
        <div className="highlight-selector">
          <select 
            value={highlightMode}
            onChange={(e) => onHighlightModeChange(e.target.value)}
          >
            <option value="all">All Highlights</option>
            <option value="text">Text Only</option>
            <option value="image">Images Only</option>
            <option value="style">Styles Only</option>
            <option value="none">No Highlights</option>
          </select>
        </div>
        
        <div className="page-controls">
          <button 
            className="page-nav-button prev"
            onClick={() => onPageChange(Math.max(currentPage - 1, 1))}
            disabled={currentPage <= 1}
            title="Previous page"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
            </svg>
          </button>
          
          <div className="page-indicator">
            <input 
              type="number"
              min="1"
              max={totalPages}
              value={currentPage}
              onChange={(e) => {
                const page = parseInt(e.target.value);
                if (!isNaN(page) && page >= 1 && page <= totalPages) {
                  onPageChange(page);
                }
              }}
            />
            <span>/ {totalPages}</span>
          </div>
          
          <button 
            className="page-nav-button next"
            onClick={() => onPageChange(Math.min(currentPage + 1, totalPages))}
            disabled={currentPage >= totalPages}
            title="Next page"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  );
};

export default DifferenceComparisonToolbar;