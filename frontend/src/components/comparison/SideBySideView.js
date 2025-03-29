import React, { useState, useEffect, useRef } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import { usePreferences } from '../../context/PreferencesContext';
import { getComparisonDetails } from '../../services/api';
import ViewToolbar from './components/ViewToolbar';
import SideBySidePanel from './panels/SideBySidePanel';
import OverlayPanel from './panels/OverlayPanel';
import DifferencePanel from './panels/DifferencePanel';
import DifferenceViewer from './DifferenceViewer';
import Spinner from '../common/Spinner';
import './SideBySideView.css';

const SideBySideView = ({ comparisonId, result }) => {
  const { 
    state, 
    setSelectedPage, 
    setSelectedDifference, 
    updateFilters,
    updateViewSettings
  } = useComparison();
  
  const { preferences } = usePreferences();
  
  // State variables
  const [pageDetails, setPageDetails] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [showDifferencePanel, setShowDifferencePanel] = useState(true);
  const [viewMode, setViewMode] = useState('sideBySide'); // 'sideBySide', 'overlay', 'difference'
  const [overlayOpacity, setOverlayOpacity] = useState(0.5);
  const [activeDifference, setActiveDifference] = useState(null);
  const [retryCount, setRetryCount] = useState(0);
  const [maxRetries] = useState(3);
  
  // Refs
  const baseContainerRef = useRef(null);
  const compareContainerRef = useRef(null);
  
  // Derived values
  const totalPages = Math.max(
    result?.basePageCount || 0,
    result?.comparePageCount || 0
  );

  useEffect(() => {
    // Set default view settings if not already set
    if (!state.viewSettings) {
      updateViewSettings({
        highlightMode: 'all',
        zoom: 1.0,
        syncScroll: true,
        showChangesOnly: false
      });
    }
    
    // Force re-initialization of selected page
    setSelectedPage(state.selectedPage || 1);
  }, []);
  
  // Check if result contains page differences
  useEffect(() => {
    // If result is loaded but no page differences, log error
    if (result && !result.pageDifferences) {
      console.error("Result does not contain pageDifferences array", result);
    } else if (result && result.pageDifferences) {
      console.log(`Result contains ${result.pageDifferences.length} page differences entries`);
    }
  }, [result]);
  
  // Load page details when selected page changes
  useEffect(() => {
    const fetchPageDetails = async () => {
      if (!comparisonId || !state.selectedPage) return;
      
      try {
        setLoading(true);
        setError(null);
        
        console.log(`Fetching comparison details for ID: ${comparisonId}, page: ${state.selectedPage}`);
        
        // Call the API function with proper filter formatting
        const details = await getComparisonDetails(
          comparisonId, 
          state.selectedPage,
          state.filters
        );
        
        console.log('Page details received:', details);
        
        // Check if we got any differences in the response
        if (details && details.baseDifferences && details.baseDifferences.length === 0 &&
            details.compareDifferences && details.compareDifferences.length === 0) {
          console.warn("No differences found in API response. This might be an issue with the backend API.");
        }
        
        setPageDetails(details);
        setLoading(false);
        setRetryCount(0);
      } catch (err) {
        console.error('Error fetching page details:', err);
        
        // Handle "still processing" error differently from other errors
        if (err.message === "Comparison still processing" && retryCount < maxRetries) {
          console.log(`Comparison still processing. Retry ${retryCount + 1}/${maxRetries} in 3 seconds...`);
          setError('Comparison details are still being processed. Please wait a moment...');
          setLoading(false);
          
          // Try again after a delay
          setTimeout(() => {
            setRetryCount(prev => prev + 1);
          }, 3000);
        } else {
          setError('Failed to load page comparison details: ' + (err.message || 'Unknown error'));
          setLoading(false);
        }
      }
    };
    
    fetchPageDetails();
  }, [comparisonId, state.selectedPage, state.filters, retryCount, maxRetries]);

  const checkForRealDifferences = (pageDetails) => {
    if (!pageDetails) return false;
    
    // Check base differences
    if (pageDetails.baseDifferences && pageDetails.baseDifferences.length > 0) {
      return true;
    }
    
    // Check compare differences
    if (pageDetails.compareDifferences && pageDetails.compareDifferences.length > 0) {
      return true;
    }
    
    return false;
  };
  

  // Handler for difference selection
  const handleDifferenceSelect = (difference) => {
    console.log("Difference selected:", difference);
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
      
      if (state.viewSettings?.syncScroll && compareContainerRef.current) {
        compareContainerRef.current.scrollTo({
          left: scrollLeft > 0 ? scrollLeft : 0,
          top: scrollTop > 0 ? scrollTop : 0,
          behavior: 'smooth'
        });
      }
    }
  };
  
  // Handler for synchronized scrolling
  const handleScroll = (event, source) => {
    if (!state.viewSettings?.syncScroll) return;
    
    const { scrollTop, scrollLeft } = event.target;
    
    if (source === 'base' && compareContainerRef.current) {
      compareContainerRef.current.scrollTop = scrollTop;
      compareContainerRef.current.scrollLeft = scrollLeft;
    } else if (source === 'compare' && baseContainerRef.current) {
      baseContainerRef.current.scrollTop = scrollTop;
      baseContainerRef.current.scrollLeft = scrollLeft;
    }
  };
  
  const toggleDifferencePanel = () => {
    setShowDifferencePanel(!showDifferencePanel);
  };
  
  // Helper function to check if a page has differences - modified to handle possible missing or corrupted data
  const hasDifferences = (page) => {
    if (!page) return false;
    
    let hasDiff = false;
    
    try {
      // Check for page existence differences
      if (page.onlyInBase || page.onlyInCompare) {
        hasDiff = true;
      }
      
      // Check for text differences
      if (page.textDifferences && 
          page.textDifferences.differences && 
          page.textDifferences.differences.length > 0) {
        hasDiff = true;
      }
      
      // Check for text element differences
      if (page.textElementDifferences && 
          page.textElementDifferences.length > 0) {
        hasDiff = true;
      }
      
      // Check for image differences
      if (page.imageDifferences && 
          page.imageDifferences.length > 0) {
        hasDiff = true;
      }
      
      // Check for font differences
      if (page.fontDifferences && 
          page.fontDifferences.length > 0) {
        hasDiff = true;
      }
    } catch (err) {
      console.error("Error checking page differences:", err, page);
      return false;
    }
    
    return hasDiff;
  };
  
  // Count differences for the active page
  const differencesCount = (() => {
    if (!pageDetails) return 0;
    
    try {
      // Account for duplicates by using a Set
      const uniqueIds = new Set([
        ...(pageDetails.baseDifferences || []).map(d => d.id),
        ...(pageDetails.compareDifferences || []).map(d => d.id)
      ]);
      
      return uniqueIds.size;
    } catch (err) {
      console.error("Error counting differences:", err);
      return 0;
    }
  })();

  // If result data is still loading and we don't have any comparison results yet
  if (!result) {
    return (
      <div className="side-by-side-view-loading">
        <Spinner size="large" />
        <p>Loading comparison results...</p>
      </div>
    );
  }

  // Loading state for page details
  if (loading && !pageDetails) {
    return (
      <div className="side-by-side-view-loading">
        <Spinner size="large" />
        <p>Loading page comparison...</p>
      </div>
    );
  }
  
  // Error state
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
          setRetryCount(0);
          setLoading(true);
        }}>Retry</button>
      </div>
    );
  }

  return (
    <div className="side-by-side-view">
      <ViewToolbar 
        viewMode={viewMode}
        setViewMode={setViewMode}
        currentPage={state.selectedPage}
        totalPages={totalPages}
        setSelectedPage={setSelectedPage}
        viewSettings={state.viewSettings}
        updateViewSettings={updateViewSettings}
        differencesCount={differencesCount}
        activeDifference={activeDifference}
        hasDifferences={hasDifferences}
        result={result}
        overlayOpacity={overlayOpacity}
        setOverlayOpacity={setOverlayOpacity}
      />
      
      <div className="comparison-container">
        {viewMode === 'sideBySide' && (
          <SideBySidePanel
            result={result}
            pageDetails={pageDetails}
            baseContainerRef={baseContainerRef}
            compareContainerRef={compareContainerRef}
            loading={loading}
            state={state}
            handleScroll={handleScroll}
            handleDifferenceSelect={handleDifferenceSelect}
            showDifferencePanel={showDifferencePanel}
          />
        )}
        
        {viewMode === 'overlay' && (
          <OverlayPanel
            result={result}
            pageDetails={pageDetails}
            loading={loading}
            state={state}
            handleDifferenceSelect={handleDifferenceSelect}
            showDifferencePanel={showDifferencePanel}
            overlayOpacity={overlayOpacity}
          />
        )}
        
        {viewMode === 'difference' && (
          <DifferencePanel
            result={result}
            pageDetails={pageDetails}
            baseContainerRef={baseContainerRef}
            loading={loading}
            state={state}
            handleDifferenceSelect={handleDifferenceSelect}
            showDifferencePanel={showDifferencePanel}
          />
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
        
        {differencesCount === 0 && !loading && pageDetails && (
          <div className="no-differences-warning">
            <p>No differences found on this page. 
              Try checking other pages or review the Overview tab for a summary of all differences.</p>
          </div>
        )}
        
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