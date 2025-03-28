import React, { useState, useEffect, useRef } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import { usePreferences } from '../../context/PreferencesContext';
import { getDocumentPage, getComparisonDetails } from '../../services/api';
import PDFRenderer from './PDFRenderer';
import DifferenceViewer from './DifferenceViewer';
import Spinner from '../common/Spinner';
import './SideBySideView.css';

const SideBySideView = ({ comparisonId, result }) => {
  const { 
    state, 
    setSelectedPage, 
    setSelectedDifference, 
    updateViewSettings 
  } = useComparison();
  
  const { preferences } = usePreferences();
  
  const [pageDetails, setPageDetails] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [showDifferencePanel, setShowDifferencePanel] = useState(true);
  const [viewMode, setViewMode] = useState('sideBySide'); // 'sideBySide', 'overlay', 'difference'
  const [overlayOpacity, setOverlayOpacity] = useState(0.5);
  const [activeDifference, setActiveDifference] = useState(null);
  
  const baseContainerRef = useRef(null);
  const compareContainerRef = useRef(null);
  
  const totalPages = Math.max(
    result?.basePageCount || 0,
    result?.comparePageCount || 0
  );
  
  // Load page details when selected page changes
  useEffect(() => {
    const fetchPageDetails = async () => {
      if (!comparisonId || !state.selectedPage) return;
      
      try {
        setLoading(true);
        setError(null);
        
        // Call the updated API function with proper filter formatting
        const details = await getComparisonDetails(
          comparisonId, 
          state.selectedPage,
          state.filters
        );
        
        setPageDetails(details);
        setLoading(false);
      } catch (err) {
        console.error('Error fetching page details:', err);
        
        // Handle "still processing" error differently from other errors
        if (err.message === "Comparison still processing") {
          setError('Comparison details are still being processed. Please wait a moment...');
        } else {
          setError('Failed to load page comparison details: ' + (err.message || 'Unknown error'));
        }
        
        setLoading(false);
      }
    };
    
    fetchPageDetails();
  }, [comparisonId, state.selectedPage, state.filters]);
  
  // Handlers for page navigation
  const handlePreviousPage = () => {
    if (state.selectedPage > 1) {
      setSelectedPage(state.selectedPage - 1);
    }
  };
  
  const handleNextPage = () => {
    if (state.selectedPage < totalPages) {
      setSelectedPage(state.selectedPage + 1);
    }
  };
  
  // Go to the next page that has differences
  const handleNextDifferencePage = () => {
    if (!result || !result.pageDifferences) return;
    
    // Find the next page with differences
    for (let i = state.selectedPage; i < totalPages; i++) {
      const nextPageIndex = i; // 0-based index
      if (nextPageIndex >= result.pageDifferences.length) break;
      
      const page = result.pageDifferences[nextPageIndex];
      if (hasDifferences(page) && page.pageNumber > state.selectedPage) {
        setSelectedPage(page.pageNumber);
        return;
      }
    }
    
    // If we reached the end, start from the beginning
    for (let i = 0; i < state.selectedPage - 1; i++) {
      const page = result.pageDifferences[i];
      if (hasDifferences(page)) {
        setSelectedPage(page.pageNumber);
        return;
      }
    }
  };
  
  // Go to the previous page that has differences
  const handlePreviousDifferencePage = () => {
    if (!result || !result.pageDifferences) return;
    
    // Find the previous page with differences
    for (let i = state.selectedPage - 2; i >= 0; i--) {
      const page = result.pageDifferences[i];
      if (hasDifferences(page)) {
        setSelectedPage(page.pageNumber);
        return;
      }
    }
    
    // If we reached the beginning, start from the end
    for (let i = result.pageDifferences.length - 1; i >= 0; i--) {
      const page = result.pageDifferences[i];
      if (hasDifferences(page) && page.pageNumber > state.selectedPage) {
        setSelectedPage(page.pageNumber);
        return;
      }
    }
  };
  
  // Helper function to check if a page has differences
  const hasDifferences = (page) => {
    if (!page) return false;
    
    return (
      page.onlyInBase ||
      page.onlyInCompare ||
      (page.textDifferences && page.textDifferences.differences && page.textDifferences.differences.length > 0) ||
      (page.textElementDifferences && page.textElementDifferences.length > 0) ||
      (page.imageDifferences && page.imageDifferences.length > 0) ||
      (page.fontDifferences && page.fontDifferences.length > 0)
    );
  };
  
  // Handlers for view settings
  const handleZoomChange = (newZoom) => {
    updateViewSettings({ zoom: newZoom });
  };
  
  const handleHighlightModeChange = (mode) => {
    updateViewSettings({ highlightMode: mode });
  };
  
  const toggleViewMode = (mode) => {
    setViewMode(mode);
  };
  
  const toggleSyncScroll = () => {
    updateViewSettings({ syncScroll: !state.viewSettings.syncScroll });
  };
  
  const toggleShowChangesOnly = () => {
    updateViewSettings({ showChangesOnly: !state.viewSettings.showChangesOnly });
  };
  
  // Handler for synchronized scrolling
  const handleScroll = (event, source) => {
    if (!state.viewSettings.syncScroll) return;
    
    const { scrollTop, scrollLeft } = event.target;
    
    if (source === 'base' && compareContainerRef.current) {
      compareContainerRef.current.scrollTop = scrollTop;
      compareContainerRef.current.scrollLeft = scrollLeft;
    } else if (source === 'compare' && baseContainerRef.current) {
      baseContainerRef.current.scrollTop = scrollTop;
      baseContainerRef.current.scrollLeft = scrollLeft;
    }
  };
  
  // Handler for difference selection
  const handleDifferenceSelect = (difference) => {
    setSelectedDifference(difference);
    setActiveDifference(difference);
    
    // Scroll to the difference location if available
    if (difference.position && baseContainerRef.current && compareContainerRef.current) {
      const { x, y } = difference.position;
      
      // Calculate scroll position (center the difference in the viewport)
      const container = baseContainerRef.current;
      const containerWidth = container.clientWidth;
      const containerHeight = container.clientHeight;
      
      const scrollLeft = x - containerWidth / 2;
      const scrollTop = y - containerHeight / 2;
      
      // Apply scrolling
      baseContainerRef.current.scrollTo({
        left: scrollLeft > 0 ? scrollLeft : 0,
        top: scrollTop > 0 ? scrollTop : 0,
        behavior: 'smooth'
      });
      
      if (state.viewSettings.syncScroll && compareContainerRef.current) {
        compareContainerRef.current.scrollTo({
          left: scrollLeft > 0 ? scrollLeft : 0,
          top: scrollTop > 0 ? scrollTop : 0,
          behavior: 'smooth'
        });
      }
    }
  };
  
  const toggleDifferencePanel = () => {
    setShowDifferencePanel(!showDifferencePanel);
  };
  
  // Handle difference navigation (next/previous)
  const navigateToNextDifference = () => {
    if (!pageDetails) return;
    
    const allDifferences = [
      ...(pageDetails.baseDifferences || []),
      ...(pageDetails.compareDifferences || []).filter(diff => 
        !pageDetails.baseDifferences.some(baseDiff => baseDiff.id === diff.id)
      )
    ];
    
    if (allDifferences.length === 0) return;
    
    let currentIndex = -1;
    if (activeDifference) {
      currentIndex = allDifferences.findIndex(diff => diff.id === activeDifference.id);
    }
    
    const nextIndex = (currentIndex + 1) % allDifferences.length;
    handleDifferenceSelect(allDifferences[nextIndex]);
  };
  
  const navigateToPreviousDifference = () => {
    if (!pageDetails) return;
    
    const allDifferences = [
      ...(pageDetails.baseDifferences || []),
      ...(pageDetails.compareDifferences || []).filter(diff => 
        !pageDetails.baseDifferences.some(baseDiff => baseDiff.id === diff.id)
      )
    ];
    
    if (allDifferences.length === 0) return;
    
    let currentIndex = -1;
    if (activeDifference) {
      currentIndex = allDifferences.findIndex(diff => diff.id === activeDifference.id);
    }
    
    const prevIndex = currentIndex <= 0 ? allDifferences.length - 1 : currentIndex - 1;
    handleDifferenceSelect(allDifferences[prevIndex]);
  };
  
  if (loading && !pageDetails) {
    return (
      <div className="side-by-side-view-loading">
        <Spinner size="large" />
        <p>Loading page comparison...</p>
      </div>
    );
  }
  
  if (error) {
    return (
      <div className="side-by-side-view-error">
        <div className="error-icon">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
          </svg>
        </div>
        <h3>Error Loading Page Comparison</h3>
        <p>{error}</p>
        <button onClick={() => {
          setError(null);
          setLoading(true);
          // Try again after a short delay
          setTimeout(() => {
            getComparisonDetails(comparisonId, state.selectedPage, state.filters)
              .then(details => {
                setPageDetails(details);
                setLoading(false);
              })
              .catch(err => {
                console.error('Error retrying page details:', err);
                setError('Failed to load page comparison details after retry. Please try again later.');
                setLoading(false);
              });
          }, 1000);
        }}>Retry</button>
      </div>
    );
  }

  // Count differences for the active page
  const differencesCount = (() => {
    if (!pageDetails) return 0;
    
    const baseDiffs = pageDetails.baseDifferences?.length || 0;
    const compareDiffs = pageDetails.compareDifferences?.length || 0;
    
    // Account for duplicates
    const uniqueIds = new Set([
      ...(pageDetails.baseDifferences || []).map(d => d.id),
      ...(pageDetails.compareDifferences || []).map(d => d.id)
    ]);
    
    return uniqueIds.size;
  })();

  return (
    <div className="side-by-side-view">
      <div className="view-toolbar">
        <div className="page-navigation">
          <button 
            className="nav-button"
            onClick={handlePreviousPage}
            disabled={state.selectedPage <= 1}
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
              value={state.selectedPage}
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
            disabled={state.selectedPage >= totalPages}
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
              onClick={() => handleZoomChange(Math.max(0.25, state.viewSettings.zoom - 0.25))}
              title="Zoom Out"
            >
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14zM7 9h5v1H7z" />
              </svg>
            </button>
            
            <div className="zoom-level">
              {Math.round(state.viewSettings.zoom * 100)}%
            </div>
            
            <button 
              className="zoom-button"
              onClick={() => handleZoomChange(Math.min(3, state.viewSettings.zoom + 0.25))}
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
              value={state.viewSettings.highlightMode}
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
                checked={state.viewSettings.syncScroll}
                onChange={toggleSyncScroll}
              />
              <label htmlFor="syncScroll">Sync Scrolling</label>
            </div>
            
            <div className="view-option">
              <input 
                type="checkbox" 
                id="showChangesOnly"
                checked={state.viewSettings.showChangesOnly}
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
          <button
            className="nav-button"
            onClick={navigateToPreviousDifference}
            disabled={differencesCount === 0}
            title="Previous difference"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M14 7l-5 5 5 5V7z" />
            </svg>
          </button>
          
          <div className="diff-indicator">
            {activeDifference ? (
              <>
                <span className="diff-type-icon">
                  {activeDifference.type.charAt(0).toUpperCase()}
                </span>
                <span className="diff-description">
                  {activeDifference.description || `${activeDifference.type} difference`}
                </span>
              </>
            ) : (
              <span>No difference selected</span>
            )}
          </div>
          
          <button
            className="nav-button"
            onClick={navigateToNextDifference}
            disabled={differencesCount === 0}
            title="Next difference"
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M10 17l5-5-5-5v10z" />
            </svg>
          </button>
        </div>
      </div>
      
      <div className="comparison-container">
        {viewMode === 'sideBySide' && (
          <div className={`documents-container ${showDifferencePanel ? 'with-panel' : 'full-width'}`}>
            <div className="document-viewer base-document">
              <div className="document-header">
                <h3>Base Document</h3>
                <div className="document-info">
                  {result.basePageCount < state.selectedPage ? (
                    <span className="page-missing">Page {state.selectedPage} does not exist</span>
                  ) : null}
                </div>
              </div>
              
              <div 
                ref={baseContainerRef}
                className="document-content"
                onScroll={(e) => handleScroll(e, 'base')}
              >
                {result.basePageCount >= state.selectedPage && (
                  <PDFRenderer 
                    fileId={state.baseFile?.fileId}
                    page={state.selectedPage}
                    zoom={state.viewSettings.zoom}
                    highlightMode={state.viewSettings.highlightMode}
                    differences={pageDetails?.baseDifferences || []}
                    selectedDifference={state.selectedDifference}
                    onDifferenceSelect={handleDifferenceSelect}
                    loading={loading}
                    interactive={true}
                  />
                )}
              </div>
            </div>
            
            <div className="document-viewer compare-document">
              <div className="document-header">
                <h3>Comparison Document</h3>
                <div className="document-info">
                  {result.comparePageCount < state.selectedPage ? (
                    <span className="page-missing">Page {state.selectedPage} does not exist</span>
                  ) : null}
                </div>
              </div>
              
              <div 
                ref={compareContainerRef}
                className="document-content"
                onScroll={(e) => handleScroll(e, 'compare')}
              >
                {result.comparePageCount >= state.selectedPage && (
                  <PDFRenderer 
                    fileId={state.compareFile?.fileId}
                    page={state.selectedPage}
                    zoom={state.viewSettings.zoom}
                    highlightMode={state.viewSettings.highlightMode}
                    differences={pageDetails?.compareDifferences || []}
                    selectedDifference={state.selectedDifference}
                    onDifferenceSelect={handleDifferenceSelect}
                    loading={loading}
                    interactive={true}
                  />
                )}
              </div>
            </div>
          </div>
        )}
        
        {viewMode === 'overlay' && (
          <div className={`overlay-container ${showDifferencePanel ? 'with-panel' : 'full-width'}`}>
            <div className="document-viewer overlay-viewer">
              <div className="document-header">
                <h3>Overlay View</h3>
                <div className="overlay-legend">
                  <span className="base-legend">Base Document</span>
                  <span className="compare-legend">Comparison Document</span>
                </div>
              </div>
              
              <div className="overlay-content" ref={baseContainerRef}>
                <div className="base-layer">
                  {result.basePageCount >= state.selectedPage && (
                    <PDFRenderer 
                      fileId={state.baseFile?.fileId}
                      page={state.selectedPage}
                      zoom={state.viewSettings.zoom}
                      highlightMode={state.viewSettings.highlightMode}
                      differences={pageDetails?.baseDifferences || []}
                      selectedDifference={state.selectedDifference}
                      onDifferenceSelect={handleDifferenceSelect}
                      loading={loading}
                      interactive={true}
                    />
                  )}
                </div>
                
                <div 
                  className="compare-layer" 
                  style={{ opacity: overlayOpacity }}
                >
                  {result.comparePageCount >= state.selectedPage && (
                    <PDFRenderer 
                      fileId={state.compareFile?.fileId}
                      page={state.selectedPage}
                      zoom={state.viewSettings.zoom}
                      highlightMode={state.viewSettings.highlightMode}
                      differences={pageDetails?.compareDifferences || []}
                      selectedDifference={state.selectedDifference}
                      onDifferenceSelect={handleDifferenceSelect}
                      loading={loading}
                      interactive={true}
                    />
                  )}
                </div>
              </div>
            </div>
          </div>
        )}
        
        {viewMode === 'difference' && (
          <div className={`difference-only-container ${showDifferencePanel ? 'with-panel' : 'full-width'}`}>
            <div className="document-viewer difference-only-viewer">
              <div className="document-header">
                <h3>Difference View</h3>
                <div className="difference-legend">
                  <span className="added-legend">Added</span>
                  <span className="deleted-legend">Deleted</span>
                  <span className="modified-legend">Modified</span>
                </div>
              </div>
              
              <div className="difference-content" ref={baseContainerRef}>
                <div className="difference-renderer">
                  {result.basePageCount >= state.selectedPage && result.comparePageCount >= state.selectedPage && (
                    <div className="difference-view">
                      <div className="added-content">
                        {/* Elements only in comparison document */}
                        {pageDetails?.compareDifferences
                          ?.filter(diff => diff.changeType === 'added')
                          .map(diff => (
                            <div 
                              key={diff.id}
                              className="diff-item added"
                              style={{
                                top: diff.position?.y,
                                left: diff.position?.x,
                                width: diff.bounds?.width,
                                height: diff.bounds?.height
                              }}
                              onClick={() => handleDifferenceSelect(diff)}
                            >
                              <div className="diff-content">{diff.text || diff.description}</div>
                            </div>
                          ))}
                      </div>
                      
                      <div className="deleted-content">
                        {/* Elements only in base document */}
                        {pageDetails?.baseDifferences
                          ?.filter(diff => diff.changeType === 'deleted')
                          .map(diff => (
                            <div 
                              key={diff.id}
                              className="diff-item deleted"
                              style={{
                                top: diff.position?.y,
                                left: diff.position?.x,
                                width: diff.bounds?.width,
                                height: diff.bounds?.height
                              }}
                              onClick={() => handleDifferenceSelect(diff)}
                            >
                              <div className="diff-content">{diff.text || diff.description}</div>
                            </div>
                          ))}
                      </div>
                      
                      <div className="modified-content">
                        {/* Modified elements */}
                        {pageDetails?.baseDifferences
                          ?.filter(diff => diff.changeType === 'modified')
                          .map(diff => (
                            <div 
                              key={diff.id}
                              className="diff-item modified"
                              style={{
                                top: diff.position?.y,
                                left: diff.position?.x,
                                width: diff.bounds?.width,
                                height: diff.bounds?.height
                              }}
                              onClick={() => handleDifferenceSelect(diff)}
                            >
                              <div className="diff-content">
                                <div className="old-value">{diff.baseText || diff.description}</div>
                                <div className="new-value">{diff.compareText}</div>
                              </div>
                            </div>
                          ))}
                      </div>
                      
                      {/* Base document canvas for reference */}
                      <div className="reference-layer">
                        <PDFRenderer 
                          fileId={state.baseFile?.fileId}
                          page={state.selectedPage}
                          zoom={state.viewSettings.zoom}
                          highlightMode="none"
                          differences={[]}
                          onDifferenceSelect={() => {}}
                          loading={loading}
                          interactive={false}
                          opacity={0.3}
                        />
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}
        
        <button 
          className="toggle-panel-button"
          onClick={toggleDifferencePanel}
          title={showDifferencePanel ? "Hide difference panel" : "Show difference panel"}
        >
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            {showDifferencePanel ? (
              <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6-6-6z" />
            ) : (
              <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12l4.58-4.59z" />
            )}
          </svg>
        </button>
        
        {showDifferencePanel && (
          <DifferenceViewer 
            pageDetails={pageDetails}
            selectedDifference={state.selectedDifference}
            onDifferenceSelect={handleDifferenceSelect}
            loading={loading}
          />
        )}
      </div>
    </div>
  );
};

export default SideBySideView;