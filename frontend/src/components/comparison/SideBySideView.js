import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import { usePreferences } from '../../context/PreferencesContext';
import { getDocumentPageDetails, getDocumentPage } from '../../services/api';
import EnhancedDiffHighlighter from './EnhancedDiffHighlighter';
import EnhancedDifferenceList from './EnhancedDifferenceList';
import DifferenceComparisonToolbar from './DifferenceComparisonToolbar';
import DifferenceLegend from './DifferenceLegend';
import ViewToolbar from './components/ViewToolbar';
import SideBySidePanel from './panels/SideBySidePanel'; // Keep import for potential future use or error states
import OverlayPanel from './panels/OverlayPanel';
import DifferencePanel from './panels/DifferencePanel';
import DifferenceViewer from './DifferenceViewer';
import Spinner from '../common/Spinner';
import './SideBySideView.css';

// Accept selectedPairIndexProp
const SideBySideView = ({ comparisonId, result, selectedPairIndexProp = 0 }) => { 
  // Reference to track if component is mounted
  const isMounted = useRef(true);
  const fetchAttempts = useRef(0);
  
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
  const [loading, setLoading] = useState(false); // Unified loading state
  const [error, setError] = useState(null);
  const [showDifferencePanel, setShowDifferencePanel] = useState(true);
  const [viewMode, setViewMode] = useState('sideBySide'); // 'sideBySide', 'overlay', 'difference'
  const [overlayOpacity, setOverlayOpacity] = useState(0.5);
  const [activeDifference, setActiveDifference] = useState(null);
  const [retryCount, setRetryCount] = useState(0);
  const [maxRetries] = useState(3);
  // Removed local selectedPairIndex state - use selectedPairIndexProp instead
  
  // Added for enhanced visual highlighting
  const [baseImageUrl, setBaseImageUrl] = useState(null);
  const [compareImageUrl, setCompareImageUrl] = useState(null);
  
  // Refs
  const baseContainerRef = useRef(null);
  const compareContainerRef = useRef(null);
  
  // Check if we're in smart comparison mode
  const isSmartComparisonMode = result && result.documentPairs && result.documentPairs.length > 0;
  
  // Set isMounted to false when component unmounts
  useEffect(() => {
    return () => {
      isMounted.current = false;
      
      // Clean up any blob URLs when unmounting
      if (baseImageUrl) URL.revokeObjectURL(baseImageUrl);
      if (compareImageUrl) URL.revokeObjectURL(compareImageUrl);
    };
  }, [baseImageUrl, compareImageUrl]);
  
  // Log the result once
  useEffect(() => {
    if (result && !fetchAttempts.current) {
      console.log("Comparison result structure:", result);
      fetchAttempts.current++;
      
      if (isSmartComparisonMode) {
        console.log(`Found ${result.documentPairs.length} document pairs`);
        // No need to set local index state here
      }
    }
  }, [result, isSmartComparisonMode]);
  
  // Calculate derived values
  let basePageCount = 0;
  let comparePageCount = 0;
  
  // Use selectedPairIndexProp here
  if (isSmartComparisonMode && result && result.documentPairs && result.documentPairs.length > selectedPairIndexProp) { 
    const pair = result.documentPairs[selectedPairIndexProp]; // Use prop
    if (pair) {
      basePageCount = pair.basePageCount || 0;
      comparePageCount = pair.comparePageCount || 0;
    }
  } else if (result) { // Fallback for non-smart mode (though this component is likely only used in smart mode now)
    basePageCount = result.basePageCount || 0;
    comparePageCount = result.comparePageCount || 0;
  }
  
  // Total pages is the maximum of base and compare for the *selected pair*
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
  
  // Fetch page details when selected page changes OR selected pair index changes
  useEffect(() => {
    const fetchPageDetails = async () => {
      // Ensure result and the specific pair exist before fetching
      if (!comparisonId || !state.selectedPage || !isMounted.current || !result?.documentPairs?.[selectedPairIndexProp]) {
          console.warn("Skipping fetchPageDetails due to missing data", { comparisonId, selectedPage: state.selectedPage, hasPair: !!result?.documentPairs?.[selectedPairIndexProp] });
          setLoading(false); // Ensure loading is off if we skip
          return;
      }
      
      try {
        setLoading(true);
        setError(null); // Clear previous errors
        setPageDetails(null); // Clear previous details
        setBaseImageUrl(null); // Clear previous images
        setCompareImageUrl(null);
        
        // Ensure we always use a valid page number (minimum 1)
        const pageNumber = Math.max(1, state.selectedPage);
        
        // Avoid excessive logging
        if (fetchAttempts.current < 5) {
          // Use prop in log
          console.log(`Fetching comparison details for ID: ${comparisonId}, page: ${pageNumber}, pairIndex: ${selectedPairIndexProp}`); 
          fetchAttempts.current++;
        }
        
        // Set a timeout to prevent infinite loading
        const timeoutPromise = new Promise((_, reject) => 
          setTimeout(() => reject(new Error('Request timed out')), 20000)
        );
        
        // Add a fallback promise that resolves with empty data after a longer timeout
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
            selectedPairIndexProp, // Use prop
            pageNumber,
            state.filters
          ),
          timeoutPromise,
          fallbackPromise
        ]);
        
        // Only update state if component is still mounted
        if (!isMounted.current) return;
        
        // Check if the response is valid
        if (!details) {
          throw new Error('Received empty response from server');
        }
        
        // Check if the page is out of bounds for the *current pair*
        const currentPair = result.documentPairs[selectedPairIndexProp];
        const pairTotalPages = Math.max(currentPair?.basePageCount || 0, currentPair?.comparePageCount || 0);
        if (pageNumber > pairTotalPages) {
             console.warn(`Requested page ${pageNumber} is out of bounds for pair ${selectedPairIndexProp} (max: ${pairTotalPages}). Resetting to page 1.`);
             setSelectedPage(1); // Reset to page 1 of the current pair
             setLoading(false);
             return; // Skip further processing for this invalid page request
        }

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
        
        // Ensure the response has the expected structure
        if (!details.baseDifferences) details.baseDifferences = [];
        if (!details.compareDifferences) details.compareDifferences = [];
        
        console.log('Received page details:', details);
        
        // Set details *first*
        setPageDetails(details);
        setRetryCount(0);

        // Fetch images *before* setting loading to false
        try {
          await fetchPageImages(); 
        } catch (imageError) {
           console.error("Error occurred during fetchPageImages:", imageError);
           // If image fetch fails, we still proceed to set loading false, 
           // the component will then render the fallback panel (or error message).
        } 
        
        // Set loading to false only after details AND image fetch attempt are complete
        if (isMounted.current) {
          setLoading(false);
        }
        
      } catch (err) {
        // Only update state if component is still mounted
        if (!isMounted.current) return;
        
        console.error('Error fetching page details:', err);
        
        // Handle different error types (ensure loading states are reset here too)
        if (err.message === 'Request timed out') {
          setError('Request timed out. The server may be overloaded or the comparison is too complex.');
        } else if (err.message && err.message.includes("still processing") && retryCount < maxRetries) {
          console.log(`Comparison still processing. Retry ${retryCount + 1}/${maxRetries} in 3 seconds...`);
          setError('Comparison details are still being processed. Please wait a moment...');
          
          // Try again after a delay
          setTimeout(() => {
            if (isMounted.current) {
              setRetryCount(prev => prev + 1);
              // Don't set loading false here, let the retry handle it
            }
          }, 3000);
          // Return early to prevent setting loading false immediately
          return; 
        } else {
          // For any other error, provide a clear message and fallback to empty data
            setError('Failed to load page comparison details. Please try navigating to another page or refreshing.');
            // Set empty page details to prevent UI from being stuck in loading state
            setPageDetails({
            baseDifferences: [],
            compareDifferences: [],
            error: err.message
          });
        }
        // Ensure loading is false on ANY error path that doesn't retry
        if (isMounted.current) {
            setLoading(false);
        }
      }
    };
    
    fetchPageDetails();
    // Use prop in dependency array
  }, [comparisonId, state.selectedPage, state.filters, retryCount, maxRetries, selectedPairIndexProp, setSelectedPage, result]); // Added result to dependencies

  // Fetch page images for enhanced highlighting using Promise.allSettled
  const fetchPageImages = async () => {
    // No need to set loadingImages here, main 'loading' covers it
    if (!comparisonId || !state.baseFile?.fileId || !state.compareFile?.fileId || !isMounted.current || !result?.documentPairs?.[selectedPairIndexProp]) return;

    // Clean up previous URLs immediately
    if (baseImageUrl) URL.revokeObjectURL(baseImageUrl);
    if (compareImageUrl) URL.revokeObjectURL(compareImageUrl);
    setBaseImageUrl(null);
    setCompareImageUrl(null);

    try {
      // Calculate base and compare page numbers
      let basePage, comparePage;
      // Use selectedPairIndexProp here
      if (isSmartComparisonMode && result.documentPairs.length > selectedPairIndexProp) { 
        const pair = result.documentPairs[selectedPairIndexProp]; // Use prop
        // Ensure pair exists before accessing properties
        if (!pair) {
          throw new Error(`Document pair at index ${selectedPairIndexProp} not found.`); // Use prop in error
        }
        // Page numbers within the *pair* context (1-based) need to map to absolute page numbers
        const relativePage = Math.max(1, state.selectedPage); 
        basePage = pair.baseStartPage + relativePage - 1;
        comparePage = pair.compareStartPage + relativePage - 1;
      } else { 
        // Fallback for non-smart mode (shouldn't happen if used correctly)
        basePage = state.selectedPage;
        comparePage = state.selectedPage;
      }

      // Ensure page numbers are valid (absolute)
      basePage = Math.max(1, basePage);
      comparePage = Math.max(1, comparePage);

      console.log(`Fetching images for absolute pages: Base ${basePage}, Compare ${comparePage}`);

      // Fetch images concurrently
      const [baseResult, compareResult] = await Promise.allSettled([
        getDocumentPage(state.baseFile.fileId, basePage),
        getDocumentPage(state.compareFile.fileId, comparePage)
      ]);

      if (!isMounted.current) return; // Check mount status again after async operations

      let newBaseUrl = null;
      let newCompareUrl = null;

      if (baseResult.status === 'fulfilled') {
        newBaseUrl = URL.createObjectURL(baseResult.value);
      } else {
        console.error('Error fetching base page image:', baseResult.reason);
        setError(prev => prev || 'Error loading base page image.'); // Set error state
      }

      if (compareResult.status === 'fulfilled') {
        newCompareUrl = URL.createObjectURL(compareResult.value);
      } else {
        console.error('Error fetching compare page image:', compareResult.reason);
        setError(prev => prev || 'Error loading compare page image.'); // Set error state
      }

      // Update state together after both promises settle
      setBaseImageUrl(newBaseUrl);
      setCompareImageUrl(newCompareUrl);

    } catch (error) {
      // Catch errors during setup or calculation
      console.error('Unexpected error in fetchPageImages setup:', error);
      setError(prev => prev || 'Unexpected error loading images.'); // Set error state
    }
    // No finally block needed here for loadingImages
  };

  // Handler for difference selection
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
  
  // Handler for synchronized scrolling
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
      console.error("Error checking page differences:", err);
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
  
  // Navigation function to go to a specific page
  const goToPage = useCallback((pageNum) => {
    if (pageNum >= 1 && pageNum <= totalPages) {
      setSelectedPage(pageNum);
    }
  }, [totalPages, setSelectedPage]);

  // If we're still loading result data (initial load)
  if (!result) {
    return (
      <div className="side-by-side-view-loading">
        <Spinner size="large" />
        <p>Loading comparison results...</p>
      </div>
    );
  }

  // Unified loading state check (covers details + image fetch attempt)
  if (loading) { 
    return (
      <div className="side-by-side-view-loading">
        <Spinner size="large" />
        <p>Loading page comparison...</p>
      </div>
    );
  }
  
  // Handle potential errors after loading attempt
  if (error) {
     return (
       <div className="side-by-side-view-error">
         <p>Error: {error}</p>
         <p>Please try selecting a different page or document pair.</p>
       </div>
     );
  }

  // Ensure pageDetails are available before rendering main view
  if (!pageDetails) {
     return (
       <div className="side-by-side-view-loading">
         <Spinner size="large" />
         <p>Initializing comparison view...</p>
       </div>
     );
  }
 

  return (
    <div className="side-by-side-view">
      {/* Use either the enhanced toolbar or original ViewToolbar */}
      <DifferenceComparisonToolbar
        differences={pageDetails}
        selectedDifference={state.selectedDifference}
        onSelectDifference={handleDifferenceSelect}
        currentPage={state.selectedPage}
        totalPages={totalPages} // total pages within the current pair
        onPageChange={goToPage}
        viewMode={viewMode}
        onViewModeChange={setViewMode}
        highlightMode={state.viewSettings?.highlightMode || 'all'}
        onHighlightModeChange={(mode) => updateViewSettings({ highlightMode: mode })}
      />
      
      <div className="comparison-container">
        <div className={`documents-container ${showDifferencePanel ? 'with-panel' : 'full-width'}`}>
          {viewMode === 'sideBySide' && (
            // Check if images are loaded AFTER main loading is complete
            baseImageUrl && compareImageUrl ? (
              // Render the main highlighter if images are ready
              <EnhancedDiffHighlighter
              result={result} // Pass full result
                pageDetails={pageDetails}
                baseImageUrl={baseImageUrl}
                compareImageUrl={compareImageUrl}
                viewSettings={state.viewSettings}
                onDifferenceSelect={handleDifferenceSelect}
                selectedDifference={state.selectedDifference}
                // Pass selectedPairIndexProp if needed by highlighter
                selectedPairIndex={selectedPairIndexProp} 
              />
            ) : (
              // Explicitly show an image loading state or error instead of fallback panel
              // This prevents SideBySidePanel/PDFRenderer from being called unnecessarily
              <div className="side-by-side-view-loading" style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                {error ? ( // Check if an error occurred during image fetch specifically
                  <span>Error loading page images. Please try again.</span>
                ) : (
                  // Otherwise, assume images are still loading (or failed silently)
                  <>
                    <Spinner size="medium" />
                    <p style={{ marginLeft: '10px' }}>Loading page images...</p>
                  </>
                )}
              </div>
              // Fallback SideBySidePanel removed to prevent incorrect rendering
            )
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
              selectedPairIndex={selectedPairIndexProp} // Use prop
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
              selectedPairIndex={selectedPairIndexProp} // Use prop
              isSmartMode={isSmartComparisonMode}
            />
          )}
        </div>
        
        {showDifferencePanel && (
          <div className="difference-panel">
            {/* Use the enhanced difference list instead of the original DifferenceViewer */}
            <EnhancedDifferenceList
              differences={pageDetails}
              onDifferenceSelect={handleDifferenceSelect}
              selectedDifference={state.selectedDifference}
            />
          </div>
        )}
        
        <button 
          className={`toggle-panel-button ${!showDifferencePanel ? 'hidden' : ''}`}
          onClick={toggleDifferencePanel}
          title={showDifferencePanel ? "Hide difference panel" : "Show difference panel"}
        >
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            {showDifferencePanel ? (
              <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6-6-6z" />
            ) : (
              <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
            )}
          </svg>
        </button>
      </div>
      
      {/* Add the difference legend at the bottom */}
      <DifferenceLegend differences={pageDetails} />
    </div>
  );
};

export default SideBySideView;
</final_file_content>

IMPORTANT: For any future changes to this file, use the final_file_content shown above as your reference. This content reflects the current state of the file, including any auto-formatting (e.g., if you used single quotes but the formatter converted them to double quotes). Always base your SEARCH/REPLACE operations on this final version to ensure accuracy.

<environment_details>
# VSCode Visible Files
frontend/src/components/comparison/SideBySideView.js

# VSCode Open Tabs
frontend/src/App.js
frontend/src/components/common/spinner.css
frontend/src/index.js
frontend/public/index.html
frontend/public/manifest.json
frontend/public/favicon.svg
frontend/scripts/generate-icons.sh
frontend/scripts/copy-pdfjs-worker.js
frontend/src/components/common/Spinner.js
frontend/src/components/common/ErrorBoundary.js
frontend/src/components/common/ErrorBoundary.css
frontend/public/robots.txt
frontend/src/context/PreferencesContext.js
frontend/src/components/results/ResultViewerError.js
frontend/src/components/results/ComparisonSummary.css
frontend/src/components/upload/UploadSection.js
frontend/src/components/results/DifferenceStats.js
frontend/src/components/results/DifferenceStats.css
frontend/src/components/results/ResultViewer.css
frontend/src/components/comparison/panels/OverlayPanel.js
frontend/src/components/comparison/panels/DifferencePanel.js
backend/src/main/java/guraa/pdfcompare/service/PageLevelComparisonSummary.java
backend/src/main/java/guraa/pdfcompare/service/PageDifference.java
backend/src/main/java/guraa/pdfcompare/service/PagePair.java
frontend/src/components/comparison/index.js
frontend/src/styles/global.css
frontend/src/components/comparison/PDFRenderer.css
frontend/src/components/comparison/DifferenceViewer.css
frontend/src/components/comparison/DocumentMatchingView.css
frontend/src/components/comparison/DifferenceList.css
frontend/src/components/layout/MainLayout.js
frontend/src/components/layout/Header.js
frontend/src/components/layout/Footer.js
frontend/src/components/layout/MainLayout.css
frontend/src/components/layout/Header.css
frontend/src/components/layout/Footer.css
frontend/src/components/upload/PDFUploader.cs
frontend/src/components/upload/UploadSection.css
frontend/src/components/comparison/ComparisonSettings.css
frontend/package.json
frontend/src/components/comparison/ComparisonSettings.js
frontend/src/components/results/ResultViewer.js
frontend/src/components/comparison/PDFRenderer.js
frontend/src/components/comparison/SmartComparisonContainer.js
frontend/src/components/comparison/SideBySideView.js
frontend/src/components/comparison/EnhancedDiffHighlighter.js
frontend/src/components/comparison/EnhancedDiffHighlighter.css
frontend/src/components/comparison/EnhancedDifferenceList.js
frontend/src/components/comparison/EnhancedDifferenceList.css
frontend/src/components/comparison/PDFComparisonView.js
frontend/src/components/comparison/PDFComparisonView,css
frontend/src/components/comparison/PDFComparisonApp.js
frontend/src/components/comparison/DifferenceLegend.js
frontend/src/components/comparison/DifferenceLegend.css
frontend/src/components/comparison/PDFComparisonApp.css
frontend/src/components/comparison/DifferenceComparisonToolbar.js
frontend/src/components/comparison/DifferenceComparisonToolbar.css
backend/src/main/java/guraa/pdfcompare/service/CustomPageDifference.java
backend/src/main/java/guraa/pdfcompare/service/FingerprintAdapter.java
backend/src/main/java/guraa/pdfcompare/service/PageLevelComparisonIntegrationService.java
backend/src/main/java/guraa/pdfcompare/service/PageLevelComparisonResult.java
backend/src/main/java/guraa/pdfcompare/comparison/TextDifference.java
backend/src/main/java/guraa/pdfcompare/core/FingerprintAdapter.java
backend/src/main/java/guraa/pdfcompare/service/PageMatchingComparisonService.java
backend/src/main/java/guraa/pdfcompare/comparison/TextDifferenceType.java
backend/src/main/java/guraa/pdfcompare/service/ReportGenerationService.java
backend/src/main/java/guraa/pdfcompare/service/PageLevelComparisonService.java
backend/src/main/java/guraa/pdfcompare/comparison/TextComparisonResult.java
backend/src/main/java/guraa/pdfcompare/service/PageComparisonResultAdapter.java
backend/src/main/java/guraa/pdfcompare/controller/EnhancedMatchingController.java
backend/src/main/java/guraa/pdfcompare/service/SmartDocumentComparisonService.java
backend/src/main/java/guraa/pdfcompare/service/EnhancedMatchingService.java
backend/src/main/java/guraa/pdfcompare/comparison/PageComparisonResult.java
backend/src/main/java/guraa/pdfcompare/service/PageComparisonResult.java
frontend/src/components/comparison/DifferenceViewer.js
frontend/src/context/ComparisonContext.js
frontend/src/components/comparison/SmartComparisonContainer.css
frontend/src/components/comparison/EnhancedDiffView.css
frontend/src/components/comparison/components/ViewToolbar.js
frontend/src/components/comparison/DifferenceList.js
frontend/src/components/comparison/panels/SideBySidePanel.js
backend/src/main/resources/application.properties
backend/src/main/java/guraa/pdfcompare/service/DocumentMatchingService.java
backend/src/main/java/guraa/pdfcompare/service/PageMatcher.java
backend/src/main/java/guraa/pdfcompare/service/ComparisonService.java
backend/src/main/java/guraa/pdfcompare/service/DifferenceDetectionService.java
frontend/src/components/comparison/DocumentMatchingView.js
frontend/src/components/comparison/SideBySideView.css
../../Users/GustavTullberg/AppData/Local/Programs/Microsoft VS Code/Untitled-1
backend/src/main/java/guraa/pdfcompare/util/SSIMCalculator.java
backend/src/main/java/guraa/pdfcompare/util/FontHandler.java
backend/src/main/java/guraa/pdfcompare/util/PdfProcessor.java
backend/src/main/java/guraa/pdfcompare/util/FontAnalyzer.java
frontend/src/components/results/ComparisonSummary.js
backend/src/main/java/guraa/pdfcompare/comparison/TextElementDifference.java
backend/src/main/java/guraa/pdfcompare/PDFComparisonEngine.java
backend/src/main/java/guraa/pdfcompare/core/SmartDocumentMatcher.java
backend/src/main/java/guraa/pdfcompare/core/DocumentMatchingStrategy.java
backend/src/main/java/guraa/pdfcompare/visual/EnhancedVisualMatcher.java
backend/src/main/java/guraa/pdfcompare/visual/VisualMatcher.java
backend/src/main/java/guraa/pdfcompare/service/EnhancedPageMatcher.java
backend/src/main/java/guraa/pdfcompare/comparison/ImageDifference.java
backend/src/main/java/guraa/pdfcompare/comparison/FontDifference.java
backend/src/main/java/guraa/pdfcompare/comparison/TextDifferenceItem.java
frontend/src/components/results/SummaryPanel.js
frontend/src/components/results/SummaryPanel.css

# Current Time
4/8/2025, 12:44:00 PM (Europe/Stockholm, UTC+2:00)

# Current Mode
ACT MODE
</environment_details>
