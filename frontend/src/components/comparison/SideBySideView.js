import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import { usePreferences } from '../../context/PreferencesContext';
import { getDocumentPageDetails } from '../../services/api';
import ViewToolbar from './components/ViewToolbar';
import SideBySidePanel from './panels/SideBySidePanel';
import OverlayPanel from './panels/OverlayPanel';
import DifferencePanel from './panels/DifferencePanel';
import DifferenceViewer from './DifferenceViewer';
import Spinner from '../common/Spinner';
import './SideBySideView.css';

const SideBySideView = ({ comparisonId, result }) => {
  // Avoid excessive re-renders by using useRef for logging control
  const hasLoggedResult = useRef(false);
  const fetchAttempts = useRef(0);
  const isComponentMounted = useRef(true);
  
  const { 
    state, 
    setSelectedPage, 
    setSelectedDifference,
    updateFilters,
    updateViewSettings
  } = useComparison();
  
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
  const [selectedPairIndex, setSelectedPairIndex] = useState(0);
  
  // Refs
  const baseContainerRef = useRef(null);
  const compareContainerRef = useRef(null);
  
  // Check if we're in smart comparison mode - don't trigger error if pageDifferences is missing
  const isSmartComparisonMode = result && result.documentPairs && result.documentPairs.length > 0;
  
  // Log the result once
  useEffect(() => {
    if (result && !hasLoggedResult.current) {
      console.log("Comparison result structure:", result);
      hasLoggedResult.current = true;
      
      if (isSmartComparisonMode) {
        console.log(`Found ${result.documentPairs.length} document pairs`);
        // Initialize with the first pair
        if (result.documentPairs.length > 0) {
          setSelectedPairIndex(0);
        }
      }
    }
    
    // Cleanup function to prevent memory leaks
    return () => {
      isComponentMounted.current = false;
    };
  }, [result, isSmartComparisonMode]);
  
  // Calculate derived values
  let basePageCount = 0;
  let comparePageCount = 0;
  
  if (isSmartComparisonMode && result && result.documentPairs && result.documentPairs.length > 0) {
    const pair = result.documentPairs[selectedPairIndex];
    if (pair) {
      basePageCount = pair.basePageCount || 0;
      comparePageCount = pair.comparePageCount || 0;
    }
  } else if (result) {
    basePageCount = result.basePageCount || 0;
    comparePageCount = result.comparePageCount || 0;
  }
  
  // Total pages is the maximum of base and compare
  const totalPages = Math.max(basePageCount, comparePageCount);
  
  // Ensure we don't try to access pages beyond the document pair's page count
  useEffect(() => {
    if (state.selectedPage > totalPages && totalPages > 0) {
      setSelectedPage(1);
    }
  }, [state.selectedPage, totalPages, setSelectedPage]);
  
  // Ensure we have a valid selected page
  useEffect(() => {
    if (!state.selectedPage || state.selectedPage < 1) {
      setSelectedPage(1);
    }
  }, [state.selectedPage, setSelectedPage]);
  
  // Memoize fetchPageDetails to avoid recreation on every render
  const fetchPageDetails = useCallback(async () => {
    if (!comparisonId || !state.selectedPage || !isComponentMounted.current) return;
    
    try {
      setLoading(true);
      setError(null);
      
      // Ensure we always use a valid page number (minimum 1)
      const pageNumber = Math.max(1, state.selectedPage);
      
      // Avoid excessive logging
      if (fetchAttempts.current < 5) {
        console.log(`Fetching comparison details for ID: ${comparisonId}, page: ${pageNumber}, pairIndex: ${selectedPairIndex}`);
        fetchAttempts.current++;
      }
      
      // Set a timeout to prevent infinite loading
      const timeoutPromise = new Promise((_, reject) => 
        setTimeout(() => reject(new Error('Request timed out')), 20000)
      );
      
      // Add a fallback promise that resolves with empty data after a longer timeout
      // This ensures we always get something to display even if the backend is completely unresponsive
      const fallbackPromise = new Promise((resolve) => {
        setTimeout(() => {
          console.warn('Using fallback empty data due to backend unresponsiveness');
          resolve({
            baseDifferences: [],
            compareDifferences: [],
            message: "Backend may be unresponsive. Showing empty comparison view.",
            fallback: true
          });
        }, 30000); // 30 seconds fallback timeout
      });
      
      // Race between the actual request, the timeout, and the fallback
      const details = await Promise.race([
        getDocumentPageDetails(
          comparisonId,
          selectedPairIndex,
          pageNumber,
          state.filters
        ),
        timeoutPromise,
        fallbackPromise
      ]);
      
      // Only update state if component is still mounted
      if (!isComponentMounted.current) return;
      
      // Check if the response is valid
      if (!details) {
        throw new Error('Received empty response from server');
      }
      
      // Check if the page is out of bounds
      if (details && details.message && details.message.includes("Page not found")) {
        console.warn("Page not found in document pair:", details.message);
        
        // If we have a maxPage value, navigate to the last valid page
        if (details.maxPage && details.maxPage > 0) {
          console.log(`Navigating to max page: ${details.maxPage}`);
          setSelectedPage(details.maxPage);
          setLoading(false);
          return;
        }
        
        // Otherwise, show an error
        setError(`${details.message}. Please navigate to a valid page.`);
        setLoading(false);
        return;
      }
      
      // Check if we got any differences in the response
      if (details && details.baseDifferences && details.baseDifferences.length === 0 &&
          details.compareDifferences && details.compareDifferences.length === 0) {
        console.warn("No differences found in API response for this page.");
      }
      
      // Ensure the response has the expected structure
      if (!details.baseDifferences) details.baseDifferences = [];
      if (!details.compareDifferences) details.compareDifferences = [];
      
      console.log('Received page details:', details);
      
      setPageDetails(details);
      setLoading(false);
      setRetryCount(0);
    } catch (err) {
      // Only update state if component is still mounted
      if (!isComponentMounted.current) return;
      
      console.error('Error fetching page details:', err);
      
      // Handle different error types
      if (err.message === 'Request timed out') {
        setError('Request timed out. The server may be overloaded or the comparison is too complex.');
        setLoading(false);
      } else if (err.message && err.message.includes("still processing") && retryCount < maxRetries) {
        console.log(`Comparison still processing. Retry ${retryCount + 1}/${maxRetries} in 3 seconds...`);
        setError('Comparison details are still being processed. Please wait a moment...');
        setLoading(false);
        
        // Try again after a delay
        setTimeout(() => {
          if (isComponentMounted.current) {
            setRetryCount(prev => prev + 1);
          }
        }, 3000);
      } else {
        // For any other error, provide a clear message and fallback to empty data
        setError('Failed to load page comparison details. Please try navigating to another page or refreshing.');
        setLoading(false);
        
        // Set empty page details to prevent UI from being stuck in loading state
        setPageDetails({
          baseDifferences: [],
          compareDifferences: [],
          error: err.message
        });
      }
    }
  }, [comparisonId, state.selectedPage, state.filters, retryCount, maxRetries, selectedPairIndex, setSelectedPage]);

  // Fetch page details when selected page changes
  useEffect(() => {
    fetchPageDetails();
  }, [fetchPageDetails]);

  // Handler for difference selection - memoized to prevent recreation on every render
  const handleDifferenceSelect = useCallback((difference) => {
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
  }, [setSelectedDifference, state.viewSettings]);
  
  // Handler for synchronized scrolling - memoized to prevent recreation on every render
  const handleScroll = useCallback((event, source) => {
    if (!state.viewSettings?.syncScroll) return;
    
    const { scrollTop, scrollLeft } = event.target;
    
    if (source === 'base' && compareContainerRef.current) {
      compareContainerRef.current.scrollTop = scrollTop;
      compareContainerRef.current.scrollLeft = scrollLeft;
    } else if (source === 'compare' && baseContainerRef.current) {
      baseContainerRef.current.scrollTop = scrollTop;
      baseContainerRef.current.scrollLeft = scrollLeft;
    }
  }, [state.viewSettings]);
  
  const toggleDifferencePanel = useCallback(() => {
    setShowDifferencePanel(!showDifferencePanel);
  }, [showDifferencePanel]);
  
  // Helper function to check if a page has differences
  const hasDifferences = useCallback((page) => {
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
  }, []);
  
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

  // If we're still loading result data
  if (!result) {
    return (
      <div className="side-by-side-view-loading">
        <Spinner size="large" />
        <p>Loading comparison results...</p>
      </div>
    );
  }
  
  // If this is a fallback result, show a warning
  if (result.fallback) {
    console.warn("Using fallback result object for SideBySideView");
  }
  
  // Create a notification component for fallback mode
  const FallbackNotification = () => {
    if (!result.fallback) return null;
    
    return (
      <div className="fallback-notification">
        <div className="fallback-icon">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
          </svg>
        </div>
        <div className="fallback-message">
          <p>The backend may be unresponsive. Showing a limited view with reduced functionality.</p>
          <p>Try refreshing the page if the comparison doesn't appear correctly.</p>
        </div>
      </div>
    );
  };

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
      {result.fallback && <FallbackNotification />}
      
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
            selectedPairIndex={selectedPairIndex}
            isSmartMode={isSmartComparisonMode}
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
            selectedPairIndex={selectedPairIndex}
            isSmartMode={isSmartComparisonMode}
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
            selectedPairIndex={selectedPairIndex}
            isSmartMode={isSmartComparisonMode}
          />
        )}
        
        {viewMode !== 'enhanced' && (
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
        )}
        
        {differencesCount === 0 && !loading && pageDetails && (() => {
          // Calculate if pages exist using the same logic as SideBySidePanel
          let selectedPage = state.selectedPage || 1;
          
          // Get the document pair if in smart mode
          const documentPair = isSmartComparisonMode && result.documentPairs && result.documentPairs.length > 0 ? 
            result.documentPairs[selectedPairIndex] : null;
          
          // Calculate if pages exist
          const basePageExists = isSmartComparisonMode && documentPair ? 
            (selectedPage <= (documentPair.baseEndPage - documentPair.baseStartPage + 1)) :
            (result && result.basePageCount >= selectedPage);
            
          const comparePageExists = isSmartComparisonMode && documentPair ? 
            (selectedPage <= (documentPair.compareEndPage - documentPair.compareStartPage + 1)) :
            (result && result.comparePageCount >= selectedPage);
          
          // Only show the "No differences found" message if both pages exist
          // If one page exists and the other doesn't, there's an implicit difference
          return (basePageExists && comparePageExists) ? (
            <div className="no-differences-warning">
              <p>No differences found on this page. 
                Try checking other pages or review the Overview tab for a summary of all differences.</p>
            </div>
          ) : null;
        })()}
        
        {showDifferencePanel && viewMode !== 'enhanced' && pageDetails && (
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
