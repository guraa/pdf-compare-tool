import React from 'react';
import './ViewToolbar.css';

const ViewToolbar = ({ 
  viewMode,
  setViewMode,
  currentPage,
  totalPages,
  setSelectedPage,
  viewSettings,
  updateViewSettings,
  differencesCount,
  activeDifference,
  hasDifferences,
  result,
  overlayOpacity,
  setOverlayOpacity
}) => {
  // Page navigation
  const goToPrevPage = () => {
    if (currentPage > 1) {
      setSelectedPage(currentPage - 1);
    }
  };
  
  const goToNextPage = () => {
    if (currentPage < totalPages) {
      setSelectedPage(currentPage + 1);
    }
  };

  // Handle direct page input
  const handlePageChange = (e) => {
    const page = parseInt(e.target.value);
    if (!isNaN(page) && page >= 1 && page <= totalPages) {
      setSelectedPage(page);
    }
  };

  return (
    <div className="view-toolbar">
      <div className="toolbar-section">
        <div className="page-navigation">
          <button 
            className="nav-button prev"
            onClick={goToPrevPage}
            disabled={currentPage <= 1}
            title="Previous page"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
            </svg>
            <span>Prev</span>
          </button>
          
          <div className="page-selector">
            <input 
              type="number"
              min="1"
              max={totalPages}
              value={currentPage}
              onChange={handlePageChange}
              title="Current page"
            />
            <span>/ {totalPages}</span>
          </div>
          
          <button 
            className="nav-button next"
            onClick={goToNextPage}
            disabled={currentPage >= totalPages}
            title="Next page"
          >
            <span>Next</span>
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6-6-6z" />
            </svg>
          </button>
        </div>
        
        <div className="diff-counter">
          <span className="diff-badge" title="Differences on this page">
            {differencesCount}
          </span>
          <span className="diff-label">differences</span>
        </div>
      </div>
      
      <div className="toolbar-section">
        <div className="view-mode-selector">
          <button
            className={`view-mode-button ${viewMode === 'sideBySide' ? 'active' : ''}`}
            onClick={() => setViewMode('sideBySide')}
            title="Side by side view"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M3 5v14h18V5H3zm8 14H3V5h8v14zm10 0h-8V5h8v14z" />
            </svg>
          </button>
          
          <button
            className={`view-mode-button ${viewMode === 'overlay' ? 'active' : ''}`}
            onClick={() => setViewMode('overlay')}
            title="Overlay view"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M18 16h-2v-1h-1c-1.1 0-2-.9-2-2V9h5V8c0-1.1-.9-2-2-2h-6c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h5c1.1 0 2-.9 2-2v-1c0-.55.45-1 1-1s1 .45 1 1v1z" />
              <circle cx="17" cy="12" r="1" />
            </svg>
          </button>
          
          <button
            className={`view-mode-button ${viewMode === 'difference' ? 'active' : ''}`}
            onClick={() => setViewMode('difference')}
            title="Difference view"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14zm-7-2h2V7h-4v2h2z" />
            </svg>
          </button>
        </div>
        
        {viewMode === 'overlay' && (
          <div className="overlay-controls">
            <label htmlFor="overlay-opacity">Opacity:</label>
            <input
              id="overlay-opacity"
              type="range"
              min="0.1"
              max="1"
              step="0.1"
              value={overlayOpacity}
              onChange={(e) => setOverlayOpacity(parseFloat(e.target.value))}
            />
            <span className="opacity-value">{Math.round(overlayOpacity * 100)}%</span>
          </div>
        )}
      </div>
    </div>
  );
};

export default ViewToolbar;