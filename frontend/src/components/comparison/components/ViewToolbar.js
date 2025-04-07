import React, { useState, useCallback, useRef } from 'react';
import { getDocumentPageDetails } from '../../../services/api';

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
  // State to track if we're currently checking for differences
  const [isCheckingDifferences, setIsCheckingDifferences] = useState(false);
  
  // Use refs to prevent excessive logging and memory usage
  const requestInProgress = useRef(false);
  const checkedPages = useRef({});

  // Navigation handlers
  const handlePreviousPage = useCallback(() => {
    if (currentPage > 1) {
      setSelectedPage(currentPage - 1);
    }
  }, [currentPage, setSelectedPage]);
  
  const handleNextPage = useCallback(() => {
    if (currentPage < totalPages) {
      setSelectedPage(currentPage + 1);
    }
  }, [currentPage, totalPages, setSelectedPage]);

  // Helper function to check if a page has differences (memoized with useCallback)
  const checkPageForDifferences = useCallback(async (comparisonId, pairIndex, pageNumber) => {
    // Avoid duplicate checks for the same page
    const cacheKey = `${comparisonId}-${pairIndex}-${pageNumber}`;
    if (checkedPages.current[cacheKey] !== undefined) {
      return checkedPages.current[cacheKey];
    }
    
    // Return early if a request is already in progress
    if (requestInProgress.current) {
      return { exists: false, hasDifferences: false, cacheHit: false };
    }
    
    try {
      requestInProgress.current = true;
      console.log(`Checking page ${pageNumber} for differences...`);
      
      const pageDetails = await getDocumentPageDetails(comparisonId, pairIndex, pageNumber);
      
      // Check for specific messages
      if (pageDetails && pageDetails.message) {
        // Skip pages that don't exist in the document pair
        if (pageDetails.message.includes("Page not found")) {
          console.log(`Page ${pageNumber} not found in document pair, skipping`);
          const result = { exists: false, hasDifferences: false };
          checkedPages.current[cacheKey] = result;
          return result;
        }
        
        // Handle pages that exist but don't have differences
        if (pageDetails.message.includes("Page exists but has no differences")) {
          console.log(`Page ${pageNumber} exists but has no differences`);
          const result = { exists: true, hasDifferences: false };
          checkedPages.current[cacheKey] = result;
          return result;
        }
      }
      
      // Check if the page has any differences
      const hasDifferencesOnPage = 
        (pageDetails.baseDifferences && pageDetails.baseDifferences.length > 0) ||
        (pageDetails.compareDifferences && pageDetails.compareDifferences.length > 0);
      
      console.log(`Page ${pageNumber} has differences: ${hasDifferencesOnPage}`);
      
      const result = { exists: true, hasDifferences: hasDifferencesOnPage };
      checkedPages.current[cacheKey] = result;
      return result;
    } catch (err) {
      console.error(`Error checking page ${pageNumber} for differences:`, err);
      return { exists: false, hasDifferences: false };
    } finally {
      requestInProgress.current = false;
    }
  }, []);

  // Go to the next page that has differences (memoized with useCallback)
  const handleNextDifferencePage = useCallback(async () => {
    if (!result || isCheckingDifferences) return;
    
    // Handle smart comparison mode differently
    if (result.documentPairs && result.documentPairs.length > 0) {
      try {
        setIsCheckingDifferences(true);
        
        // In smart mode, we need to check if the page exists in the current document pair
        const currentPair = result.documentPairs[0]; // Assuming we're working with the first pair
        const comparisonId = result.id;
        
        // Get the page counts for both documents
        const basePageCount = currentPair.basePageCount || 0;
        const comparePageCount = currentPair.comparePageCount || 0;
        const maxPage = Math.max(basePageCount, comparePageCount);
        
        // Safety check - don't try to navigate beyond the document
        if (maxPage <= 0) {
          console.warn("Document pair has no pages");
          setIsCheckingDifferences(false);
          return;
        }
        
        // If we're already at the last page, go back to page 1
        if (currentPage >= maxPage) {
          setSelectedPage(1);
          setIsCheckingDifferences(false);
          return;
        }
        
        console.log("Smart comparison mode: looking for next page with differences");
        
        // Only check a few pages ahead to prevent excessive API calls
        const pagesToCheck = Math.min(5, maxPage - currentPage);
        
        // Check pages from current+1 to current+pagesToCheck
        for (let i = 1; i <= pagesToCheck; i++) {
          const pageToCheck = currentPage + i;
          if (pageToCheck > maxPage) break;
          
          const result = await checkPageForDifferences(comparisonId, 0, pageToCheck);
          if (result.exists && result.hasDifferences) {
            console.log(`Found differences on page ${pageToCheck}, navigating there`);
            setSelectedPage(pageToCheck);
            setIsCheckingDifferences(false);
            return;
          }
        }
        
        // If we couldn't find any page with differences, just go to the next page
        console.log("No pages with differences found in our window, using simple navigation");
        let nextPage = currentPage + 1;
        if (nextPage > maxPage) {
          nextPage = 1;
        }
        
        setSelectedPage(nextPage);
        setIsCheckingDifferences(false);
      } catch (err) {
        console.error("Error navigating to next difference page:", err);
        setIsCheckingDifferences(false);
      }
      return;
    }
    
    // Original logic for non-smart mode
    if (!result.pageDifferences) return;
    
    // Find the next page with differences
    for (let i = currentPage; i < totalPages; i++) {
      const nextPage = result.pageDifferences.find(p => p.pageNumber === i + 1);
      if (nextPage && hasDifferences(nextPage)) {
        setSelectedPage(nextPage.pageNumber);
        return;
      }
    }
    
    // If we reached the end, start from the beginning
    for (let i = 0; i < currentPage - 1; i++) {
      const page = result.pageDifferences.find(p => p.pageNumber === i + 1);
      if (page && hasDifferences(page)) {
        setSelectedPage(page.pageNumber);
        return;
      }
    }
  }, [result, isCheckingDifferences, currentPage, totalPages, hasDifferences, setSelectedPage, checkPageForDifferences]);
  
  // Go to the previous page that has differences (memoized with useCallback)
  const handlePreviousDifferencePage = useCallback(async () => {
    if (!result || isCheckingDifferences) return;
    
    // Handle smart comparison mode differently
    if (result.documentPairs && result.documentPairs.length > 0) {
      try {
        setIsCheckingDifferences(true);
        
        // In smart mode, we need to check if the page exists in the current document pair
        const currentPair = result.documentPairs[0]; // Assuming we're working with the first pair
        const comparisonId = result.id;
        
        // Get the page counts for both documents
        const basePageCount = currentPair.basePageCount || 0;
        const comparePageCount = currentPair.comparePageCount || 0;
        const maxPage = Math.max(basePageCount, comparePageCount);
        
        // Safety check - don't try to navigate beyond the document
        if (maxPage <= 0) {
          console.warn("Document pair has no pages");
          setIsCheckingDifferences(false);
          return;
        }
        
        console.log("Smart comparison mode: looking for previous page with differences");
        
        // Only check a few pages back to prevent excessive API calls
        const pagesToCheck = Math.min(5, currentPage - 1);
        
        // Check pages from current-1 down 
        for (let i = 1; i <= pagesToCheck; i++) {
          const pageToCheck = currentPage - i;
          if (pageToCheck < 1) break;
          
          const result = await checkPageForDifferences(comparisonId, 0, pageToCheck);
          if (result.exists && result.hasDifferences) {
            console.log(`Found differences on page ${pageToCheck}, navigating there`);
            setSelectedPage(pageToCheck);
            setIsCheckingDifferences(false);
            return;
          }
        }
        
        // If we couldn't find any page with differences, just go to the previous page
        console.log("No pages with differences found in our window, using simple navigation");
        let prevPage = currentPage - 1;
        if (prevPage < 1) {
          prevPage = maxPage;
        }
        
        setSelectedPage(prevPage);
        setIsCheckingDifferences(false);
      } catch (err) {
        console.error("Error navigating to previous difference page:", err);
        setIsCheckingDifferences(false);
      }
      return;
    }
    
    // Original logic for non-smart mode
    if (!result.pageDifferences) return;
    
    // Find the previous page with differences
    for (let i = currentPage - 2; i >= 0; i--) {
      const page = result.pageDifferences.find(p => p.pageNumber === i + 1);
      if (page && hasDifferences(page)) {
        setSelectedPage(page.pageNumber);
        return;
      }
    }
    
    // If we reached the beginning, start from the end
    for (let i = totalPages - 1; i > currentPage; i--) {
      const page = result.pageDifferences.find(p => p.pageNumber === i + 1);
      if (page && hasDifferences(page)) {
        setSelectedPage(page.pageNumber);
        return;
      }
    }
  }, [result, isCheckingDifferences, currentPage, totalPages, hasDifferences, setSelectedPage, checkPageForDifferences]);
  
  // View settings handlers
  const handleZoomChange = useCallback((newZoom) => {
    updateViewSettings({ zoom: newZoom });
  }, [updateViewSettings]);
  
  const handleHighlightModeChange = useCallback((mode) => {
    updateViewSettings({ highlightMode: mode });
  }, [updateViewSettings]);
  
  const toggleViewMode = useCallback((mode) => {
    setViewMode(mode);
  }, [setViewMode]);
  
  const toggleSyncScroll = useCallback(() => {
    updateViewSettings({ syncScroll: !viewSettings?.syncScroll });
  }, [updateViewSettings, viewSettings]);
  
  const toggleShowChangesOnly = useCallback(() => {
    updateViewSettings({ showChangesOnly: !viewSettings?.showChangesOnly });
  }, [updateViewSettings, viewSettings]);

  return (
    <div className="view-toolbar">
      <div className="page-navigation">
        <button 
          className="nav-button"
          onClick={handlePreviousPage}
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
              if (page >= 1 && page <= totalPages) {
                setSelectedPage(page);
              }
            }}
            aria-label="Current page"
          />
          <span>/ {totalPages}</span>
        </div>
        
        <button 
          className="nav-button"
          onClick={handleNextPage}
          disabled={currentPage >= totalPages}
          title="Next page"
        >
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
          </svg>
        </button>
        
        <div className="diff-navigation">
          <button
            className="nav-button diff-nav-button"
            onClick={handlePreviousDifferencePage}
            title="Previous page with differences"
            disabled={isCheckingDifferences}
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M17.7 15.89L13.82 12l3.89-3.89c.39-.39.39-1.02 0-1.41-.39-.39-1.02-.39-1.41 0l-4.59 4.59c-.39.39-.39 1.02 0 1.41l4.59 4.59c.39.39 1.02.39 1.41 0 .38-.38.38-1.02-.01-1.4zM7 6c.55 0 1 .45 1 1v10c0 .55-.45 1-1 1s-1-.45-1-1V7c0-.55.45-1 1-1z" />
            </svg>
          </button>
          
          <div className="diff-count">
            <span className="diff-badge" title="Differences on this page">
              {differencesCount}
            </span>
          </div>
          
          <button
            className="nav-button diff-nav-button"
            onClick={handleNextDifferencePage}
            title="Next page with differences"
            disabled={isCheckingDifferences}
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M6.3 15.89l3.88-3.89-3.88-3.89c-.39-.39-.39-1.02 0-1.41.39-.39 1.02-.39 1.41 0l4.59 4.59c.39.39.39 1.02 0 1.41l-4.59 4.59c-.39.39-1.02.39-1.41 0-.38-.38-.38-1.02 0-1.4zM17 6c.55 0 1 .45 1 1v10c0 .55-.45 1-1 1s-1-.45-1-1V7c0-.55.45-1 1-1z" />
            </svg>
          </button>
        </div>
      </div>
      
      <div className="view-settings">
        <div className="view-mode-buttons">
          <button
            className={`view-mode-button ${viewMode === 'sideBySide' ? 'active' : ''}`}
            onClick={() => toggleViewMode('sideBySide')}
            title="Side by side view"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M3 5v14h18V5H3zm8 14H3V5h8v14zm10 0h-8V5h8v14z" />
            </svg>
          </button>
          
          <button
            className={`view-mode-button ${viewMode === 'overlay' ? 'active' : ''}`}
            onClick={() => toggleViewMode('overlay')}
            title="Overlay view"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M18 16h-2v-1h-1c-1.1 0-2-.9-2-2V9h5V8c0-1.1-.9-2-2-2h-6c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h5c1.1 0 2-.9 2-2v-1c0-.55.45-1 1-1s1 .45 1 1v1z" />
              <circle cx="17" cy="12" r="1" />
            </svg>
          </button>
          
          <button
            className={`view-mode-button ${viewMode === 'difference' ? 'active' : ''}`}
            onClick={() => toggleViewMode('difference')}
            title="Difference view"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14zm-7-2h2V7h-4v2h2z" />
            </svg>
          </button>
        </div>
        
        <div className="zoom-controls">
          <button 
            className="zoom-button"
            onClick={() => handleZoomChange(Math.max(0.25, (viewSettings?.zoom || 1) - 0.25))}
            title="Zoom Out"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14zM7 9h5v1H7z" />
            </svg>
          </button>
          
          <div className="zoom-level">
            {Math.round((viewSettings?.zoom || 1) * 100)}%
          </div>
          
          <button 
            className="zoom-button"
            onClick={() => handleZoomChange(Math.min(3, (viewSettings?.zoom || 1) + 0.25))}
            title="Zoom In"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z" />
              <path d="M12 10h-2v-2H9v2H7v1h2v2h1v-2h2z" />
            </svg>
          </button>
          
          <button 
            className="zoom-button reset"
            onClick={() => handleZoomChange(1)}
            title="Reset Zoom"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z" />
            </svg>
          </button>
        </div>
        
        <div className="highlight-controls">
          <select 
            id="highlightMode"
            value={viewSettings?.highlightMode || 'all'}
            onChange={(e) => handleHighlightModeChange(e.target.value)}
            aria-label="Highlight mode"
          >
            <option value="all">All Differences</option>
            <option value="text">Text Only</option>
            <option value="image">Images Only</option>
            <option value="font">Fonts Only</option>
            <option value="style">Styles Only</option>
            <option value="none">No Highlights</option>
          </select>
        </div>
        
        <div className="view-options">
          <div className="view-option">
            <input 
              type="checkbox" 
              id="syncScroll"
              checked={viewSettings?.syncScroll || false}
              onChange={toggleSyncScroll}
            />
            <label htmlFor="syncScroll">Sync Scrolling</label>
          </div>
          
          <div className="view-option">
            <input 
              type="checkbox" 
              id="showChangesOnly"
              checked={viewSettings?.showChangesOnly || false}
              onChange={toggleShowChangesOnly}
            />
            <label htmlFor="showChangesOnly">Show Changes Only</label>
          </div>
        </div>
        
        {viewMode === 'overlay' && (
          <div className="overlay-controls">
            <label htmlFor="overlayOpacity">Opacity:</label>
            <input
              type="range"
              id="overlayOpacity"
              min="0"
              max="1"
              step="0.05"
              value={overlayOpacity}
              onChange={(e) => setOverlayOpacity(parseFloat(e.target.value))}
            />
            <span>{Math.round(overlayOpacity * 100)}%</span>
          </div>
        )}
      </div>
      
      <div className="diff-navigation-buttons">
        <div className="diff-indicator">
          {activeDifference ? (
            <>
              <span className="diff-type-icon">
                {activeDifference.type?.charAt(0).toUpperCase() || 'D'}
              </span>
              <span className="diff-description">
                {activeDifference.description || `${activeDifference.type || 'Unknown'} difference`}
              </span>
            </>
          ) : (
            <span>No difference selected</span>
          )}
        </div>
      </div>
    </div>
  );
};

export default ViewToolbar;