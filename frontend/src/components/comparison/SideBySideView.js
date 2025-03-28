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
        
        const details = await getComparisonDetails(
          comparisonId, 
          state.selectedPage,
          state.filters
        );
        
        setPageDetails(details);
        setLoading(false);
      } catch (err) {
        console.error('Error fetching page details:', err);
        setError('Failed to load page comparison details.');
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
  
  // Handlers for view settings
  const handleZoomChange = (newZoom) => {
    updateViewSettings({ zoom: newZoom });
  };
  
  const handleHighlightModeChange = (mode) => {
    updateViewSettings({ highlightMode: mode });
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
        <button onClick={() => window.location.reload()}>Retry</button>
      </div>
    );
  }

  return (
    <div className="side-by-side-view">
      <div className="view-controls">
        <div className="page-navigation">
          <button 
            className="nav-button"
            onClick={handlePreviousPage}
            disabled={state.selectedPage <= 1}
          >
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
            </svg>
            <span>Previous</span>
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
            />
            <span>of {totalPages}</span>
          </div>
          
          <button 
            className="nav-button"
            onClick={handleNextPage}
            disabled={state.selectedPage >= totalPages}
          >
            <span>Next</span>
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
            </svg>
          </button>
        </div>
        
        <div className="view-settings">
          <div className="zoom-controls">
            <button 
              className="zoom-button"
              onClick={() => handleZoomChange(Math.max(0.25, state.viewSettings.zoom - 0.25))}
              title="Zoom Out"
            >
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M19 13H5v-2h14v2z" />
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
                <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" />
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
            <label htmlFor="highlightMode">Highlight:</label>
            <select 
              id="highlightMode"
              value={state.viewSettings.highlightMode}
              onChange={(e) => handleHighlightModeChange(e.target.value)}
            >
              <option value="all">All Differences</option>
              <option value="text">Text Only</option>
              <option value="images">Images Only</option>
              <option value="fonts">Fonts Only</option>
              <option value="styles">Styles Only</option>
              <option value="none">No Highlights</option>
            </select>
          </div>
          
          <div className="view-options">
            <div className="sync-scroll-toggle">
              <input 
                type="checkbox" 
                id="syncScroll"
                checked={state.viewSettings.syncScroll}
                onChange={toggleSyncScroll}
              />
              <label htmlFor="syncScroll">Sync Scrolling</label>
            </div>
            
            <div className="changes-only-toggle">
              <input 
                type="checkbox" 
                id="showChangesOnly"
                checked={state.viewSettings.showChangesOnly}
                onChange={toggleShowChangesOnly}
              />
              <label htmlFor="showChangesOnly">Show Changes Only</label>
            </div>
          </div>
        </div>
      </div>
      
      <div className="comparison-container">
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
                />
              )}
            </div>
          </div>
        </div>
        
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