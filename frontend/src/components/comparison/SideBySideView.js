import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import DocumentMatchingView from './DocumentMatchingView';
import PDFRenderer from './PDFRenderer';
import SimplifiedDifferenceList from './DifferenceList';
import Spinner from '../common/Spinner';
import { getDocumentPairs, getDocumentPageDetails } from '../../services/api';
import './SideBySideView.css';

// Debug helper function
const debugPDFLoading = (enabled = true) => {
  if (!enabled) return;
  console.log('📋 PDF debugging enabled - Monitoring PDF loading');
};

// Enable debug logging for PDF loading
debugPDFLoading(true);

const SideBySideView = ({ comparisonId }) => {
  const isMounted = useRef(true);
  const fetchInProgress = useRef(false);
  
  const { 
    state, 
    setError,
    setLoading,
    setDocumentPairs,
    setSelectedDocumentPairIndex,
    setSelectedPage,
    setSelectedDifference,
    updateViewSettings
  } = useComparison();
  
  // Local state
  const [activeView, setActiveView] = useState('matching');
  const [currentPage, setCurrentPage] = useState(1);
  const [pageDetails, setPageDetails] = useState(null);
  const [baseDifferences, setBaseDifferences] = useState([]);
  const [compareDifferences, setCompareDifferences] = useState([]);
  const [loadingImages, setLoadingImages] = useState(false);
  const [highlightMode, setHighlightMode] = useState('all');
  const [showDifferencePanel, setShowDifferencePanel] = useState(true);
  
  // Zoom state (synchronized between both documents)
  const [zoom, setZoom] = useState(1.0);
  
  // Scroll synchronization
  const baseScrollRef = useRef(null);
  const compareScrollRef = useRef(null);
  const [syncedScroll, setSyncedScroll] = useState(true);
  const [scrolling, setScrolling] = useState(false);
  
  // Base and compare document dimensions
  const [baseDimensions, setBaseDimensions] = useState({ width: 0, height: 0 });
  const [compareDimensions, setCompareDimensions] = useState({ width: 0, height: 0 });

  // Safely extract selected pair from state
  const selectedPair = useMemo(() => {
    // Prioritize the selectedPair in state
    if (state.documentPairs && state.documentPairs.length > 0) {
      return state.documentPairs[state.selectedDocumentPairIndex || 0];
    }
    return null;
  }, [state.documentPairs, state.selectedDocumentPairIndex]);

  // Memoized max page count calculation
  const maxPageCount = useMemo(() => {
    if (!selectedPair) return 1;
    const basePages = selectedPair.baseEndPage - selectedPair.baseStartPage + 1;
    const comparePages = selectedPair.compareEndPage - selectedPair.compareStartPage + 1;
    return Math.max(basePages, comparePages);
  }, [selectedPair]);

  // Calculate actual page numbers for base and compare documents
  const basePageNumber = useMemo(() => {
    if (!selectedPair) return 1;
    return selectedPair.baseStartPage + currentPage - 1;
  }, [selectedPair, currentPage]);

  const comparePageNumber = useMemo(() => {
    if (!selectedPair) return 1;
    return selectedPair.compareStartPage + currentPage - 1;
  }, [selectedPair, currentPage]);

  // Handle page navigation
  const handlePreviousPage = () => {
    if (currentPage <= 1 || loadingImages) return;
    setCurrentPage(prev => Math.max(1, prev - 1));
  };

  const handleNextPage = () => {
    if (currentPage >= maxPageCount || loadingImages) return;
    setCurrentPage(prev => Math.min(maxPageCount, prev + 1));
  };

  // Go to a specific page number
  const handleGoToPage = (pageNum) => {
    if (pageNum < 1 || pageNum > maxPageCount || loadingImages) return;
    setCurrentPage(pageNum);
  };

  // Clean up on unmount
  useEffect(() => {
    return () => {
      isMounted.current = false;
    };
  }, []);

  // Effect to verify file data exists after pair is selected
  useEffect(() => {
    if (activeView === 'comparison' && selectedPair) {
      console.log('Selected pair:', selectedPair);
      console.log('Base file:', state.baseFile);
      console.log('Compare file:', state.compareFile);
      
      // Verify file IDs are available
      if (!state.baseFile?.fileId) {
        console.error('Base file ID is missing!');
        setError('Base document is unavailable. Please try uploading again.');
      }
      
      if (!state.compareFile?.fileId) {
        console.error('Compare file ID is missing!');
        setError('Comparison document is unavailable. Please try uploading again.');
      }
      
      // Log page numbers for debugging
      console.log(`Base page range: ${selectedPair.baseStartPage}-${selectedPair.baseEndPage}`);
      console.log(`Compare page range: ${selectedPair.compareStartPage}-${selectedPair.compareEndPage}`);
      console.log(`Current page index: ${currentPage}, Max pages: ${maxPageCount}`);
      console.log(`Actual base page: ${basePageNumber}, Actual compare page: ${comparePageNumber}`);
    }
  }, [activeView, selectedPair, state.baseFile, state.compareFile, currentPage, maxPageCount, basePageNumber, comparePageNumber, setError]);

  // Fetch document pairs on mount
  useEffect(() => {
    const fetchDocumentPairs = async () => {
      if (!comparisonId || fetchInProgress.current) return;
      
      try {
        fetchInProgress.current = true;
        setLoading(true);
        
        const pairs = await getDocumentPairs(comparisonId);
        
        if (pairs && pairs.length > 0) {
          // Set document pairs in context
          setDocumentPairs(pairs);
          
          // Find first matched pair
          const matchedPairIndex = pairs.findIndex(pair => pair.matched);
          const initialIndex = matchedPairIndex >= 0 ? matchedPairIndex : 0;
          
          // Set selected pair index
          setSelectedDocumentPairIndex(initialIndex);
          
          // Switch to comparison view if there's at least one matched pair
          if (matchedPairIndex >= 0) {
            setActiveView('comparison');
            
            // Reset to first page of document pair
            setCurrentPage(1);
          }
        } else {
          throw new Error("No document pairs returned");
        }
      } catch (err) {
        console.error("Error fetching document pairs:", err);
        setError("Failed to load document pairs: " + err.message);
      } finally {
        if (isMounted.current) {
          setLoading(false);
          fetchInProgress.current = false;
        }
      }
    };
    
    fetchDocumentPairs();
  }, [comparisonId, setLoading, setDocumentPairs, setSelectedDocumentPairIndex, setError]);

  // Fetch page details and images
  useEffect(() => {
    const fetchPageData = async () => {
      // Ensure we have all required data
      if (!comparisonId || !selectedPair || 
          !state.baseFile?.fileId || 
          !state.compareFile?.fileId ||
          activeView !== 'comparison') {
        return;
      }

      setLoadingImages(true);
      setPageDetails(null);
      setBaseDifferences([]);
      setCompareDifferences([]);

      try {
        // Fetch page details - use the actual page index within the document pair
        console.log(`Fetching page details for page index ${currentPage}`);
        
        const details = await getDocumentPageDetails(
          comparisonId,
          state.selectedDocumentPairIndex,
          currentPage,
          state.filters
        );

        // Process differences
        if (details) {
          console.log(`Received page details for page index ${currentPage}:`, details);
          setPageDetails(details);
          setBaseDifferences(details.baseDifferences || []);
          setCompareDifferences(details.compareDifferences || []);
        }
      } catch (error) {
        console.error("Error loading page data:", error);
        setError(`Failed to load page data: ${error.message}`);
      } finally {
        if (isMounted.current) {
          setLoadingImages(false);
        }
      }
    };

    fetchPageData();
  }, [
    comparisonId, 
    selectedPair, 
    currentPage, 
    state.selectedDocumentPairIndex, 
    state.baseFile?.fileId,
    state.compareFile?.fileId,
    state.filters,
    activeView,
    setError
  ]);

  // Handle zoom in
  const handleZoomIn = () => {
    setZoom(prevZoom => {
      const newZoom = Math.min(prevZoom + 0.25, 3.0);
      
      // Also update view settings in the context
      updateViewSettings({
        zoom: newZoom
      });
      
      return newZoom;
    });
  };

  // Handle zoom out
  const handleZoomOut = () => {
    setZoom(prevZoom => {
      const newZoom = Math.max(prevZoom - 0.25, 0.5);
      
      // Also update view settings in the context
      updateViewSettings({
        zoom: newZoom
      });
      
      return newZoom;
    });
  };

  // Handle zoom reset
  const handleZoomReset = () => {
    setZoom(1.0);
    updateViewSettings({
      zoom: 1.0
    });
  };

  // Handle synced scrolling between the two document views
  const handleScroll = useCallback((e) => {
    if (!syncedScroll || scrolling) return;

    const source = e.currentTarget;
    const target = source === baseScrollRef.current 
      ? compareScrollRef.current 
      : baseScrollRef.current;

    if (target && source) {
      setScrolling(true);
      
      // Calculate relative scroll positions for potentially different-sized documents
      const sourceScrollTop = source.scrollTop;
      const sourceScrollHeight = source.scrollHeight - source.clientHeight;
      const sourceScrollPercentage = sourceScrollHeight > 0 ? sourceScrollTop / sourceScrollHeight : 0;
      
      const targetScrollHeight = target.scrollHeight - target.clientHeight;
      const targetScrollTop = targetScrollHeight > 0 ? sourceScrollPercentage * targetScrollHeight : 0;
      
      // Apply the scroll position
      target.scrollTop = targetScrollTop;
      
      // Sync horizontal scroll similarly
      if (source.scrollWidth > source.clientWidth) {
        const sourceScrollLeft = source.scrollLeft;
        const sourceScrollWidth = source.scrollWidth - source.clientWidth;
        const sourceHorizontalScrollPercentage = sourceScrollWidth > 0 ? sourceScrollLeft / sourceScrollWidth : 0;
        
        const targetScrollWidth = target.scrollWidth - target.clientWidth;
        const targetScrollLeft = targetScrollWidth > 0 ? sourceHorizontalScrollPercentage * targetScrollWidth : 0;
        
        target.scrollLeft = targetScrollLeft;
      }
      
      // Reset scrolling flag after a short delay
      setTimeout(() => setScrolling(false), 50);
    }
  }, [syncedScroll, scrolling]);

  // Handle difference selection
  const handleDifferenceSelect = (diff) => {
    setSelectedDifference(diff);
    
    // Scroll to the difference if it's outside the viewport
    const scrollToSelectedDifference = (containerRef, diff, dimensions) => {
      if (!containerRef.current || !diff.position || !diff.bounds) return;
      
      const container = containerRef.current;
      const scrollMargin = 50; // Extra space around the difference
      
      // Calculate the difference position in the current zoom level
      const diffX = diff.position.x * zoom;
      const diffY = diff.position.y * zoom;
      const diffWidth = diff.bounds.width * zoom;
      const diffHeight = diff.bounds.height * zoom;
      
      // Get the current viewport boundaries
      const viewportTop = container.scrollTop;
      const viewportBottom = viewportTop + container.clientHeight;
      const viewportLeft = container.scrollLeft;
      const viewportRight = viewportLeft + container.clientWidth;
      
      // Check if the difference is outside the viewport
      if (diffY < viewportTop + scrollMargin) {
        // Diff is above the viewport
        container.scrollTop = Math.max(0, diffY - scrollMargin);
      } else if (diffY + diffHeight > viewportBottom - scrollMargin) {
        // Diff is below the viewport
        container.scrollTop = Math.min(
          container.scrollHeight - container.clientHeight,
          diffY + diffHeight - container.clientHeight + scrollMargin
        );
      }
      
      // Horizontal scrolling if needed
      if (diffX < viewportLeft + scrollMargin) {
        // Diff is to the left of viewport
        container.scrollLeft = Math.max(0, diffX - scrollMargin);
      } else if (diffX + diffWidth > viewportRight - scrollMargin) {
        // Diff is to the right of viewport
        container.scrollLeft = Math.min(
          container.scrollWidth - container.clientWidth,
          diffX + diffWidth - container.clientWidth + scrollMargin
        );
      }
    };
    
    // Find which document the difference belongs to
    const isBaseDifference = baseDifferences.some(d => d.id === diff.id);
    if (isBaseDifference) {
      scrollToSelectedDifference(baseScrollRef, diff, baseDimensions);
    } else {
      scrollToSelectedDifference(compareScrollRef, diff, compareDimensions);
    }
  };

  // Handle image loaded callback from PDFRenderer
  const handleBaseImageLoaded = (width, height) => {
    console.log(`Base image loaded with dimensions: ${width}x${height}`);
    setBaseDimensions({ width, height });
  };
  
  const handleCompareImageLoaded = (width, height) => {
    console.log(`Compare image loaded with dimensions: ${width}x${height}`);
    setCompareDimensions({ width, height });
  };

  // Toggle difference panel
  const toggleDifferencePanel = () => {
    setShowDifferencePanel(prev => !prev);
  };

  // Toggle sync scrolling
  const toggleSyncScrolling = () => {
    setSyncedScroll(prev => !prev);
    updateViewSettings({
      syncScroll: !syncedScroll
    });
  };

  // Back to document matching view
  const handleBackToMatching = () => {
    setActiveView('matching');
  };

  // Render for document matching view
  if (activeView === 'matching') {
    return (
      <DocumentMatchingView 
        comparisonId={comparisonId}
        onSelectDocumentPair={(pairIndex, pair) => {
          setSelectedDocumentPairIndex(pairIndex);
          setCurrentPage(1); // Reset to first page when selecting a new pair
          setActiveView('comparison');
        }}
      />
    );
  }

  // If no selected pair, show loading
  if (!selectedPair) {
    return (
      <div className="loading-container">
        <Spinner size="large" />
        <p>Loading document comparison...</p>
      </div>
    );
  }

  // Render comparison view
  return (
    <div className="pdf-comparison-container">
      <div className="comparison-header">
        <button className="back-button" onClick={handleBackToMatching}>
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z" />
          </svg>
          Back to Document List
        </button>
        
        <div className="document-info">
          <h3>Document Comparison</h3>
          <div className="page-ranges">
            <span className="base-range">
              Base: Pages {selectedPair.baseStartPage}-{selectedPair.baseEndPage}
            </span>
            <span className="separator">|</span>
            <span className="compare-range">
              Compare: Pages {selectedPair.compareStartPage}-{selectedPair.compareEndPage}
            </span>
            <span className="separator">|</span>
            <span className="current-page">
              Current: {currentPage} of {maxPageCount}
            </span>
          </div>
        </div>
        
        <div className="zoom-controls">
          <button onClick={handleZoomOut} title="Zoom Out">
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14zM7 9h5v1H7z"/>
            </svg>
          </button>
          
          <button onClick={handleZoomReset} title="Reset Zoom">
            {Math.round(zoom * 100)}%
          </button>
          
          <button onClick={handleZoomIn} title="Zoom In">
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
              <path d="M12 10h-2v-2h-1v2h-2v1h2v2h1v-2h2z"/>
            </svg>
          </button>
        </div>
      </div>
      
      <div className="comparison-content">
        <div className={`documents-container ${showDifferencePanel ? 'with-panel' : ''}`}>
          <div className="document-view base-document">
            <div className="document-header">
              <h4>Base Document</h4>
              <div className="page-info">
                Page {basePageNumber}
              </div>
            </div>
            <div 
              className="document-content"
              ref={baseScrollRef}
              onScroll={handleScroll}
            >
              {loadingImages ? (
                <Spinner size="medium" />
              ) : state.baseFile?.fileId ? (
                <PDFRenderer 
                  fileId={state.baseFile.fileId}
                  page={basePageNumber}
                  differences={baseDifferences}
                  highlightMode={highlightMode}
                  zoom={zoom}
                  selectedDifference={state.selectedDifference}
                  onDifferenceSelect={handleDifferenceSelect}
                  onImageLoaded={handleBaseImageLoaded}
                  onZoomChange={setZoom}
                />
              ) : (
                <div className="no-content">
                  <p>Base document unavailable. Please try uploading again.</p>
                </div>
              )}
            </div>
          </div>
          
          <div className="document-view compare-document">
            <div className="document-header">
              <h4>Compare Document</h4>
              <div className="page-info">
                Page {comparePageNumber}
              </div>
            </div>
            <div 
              className="document-content"
              ref={compareScrollRef}
              onScroll={handleScroll}
            >
              {loadingImages ? (
                <Spinner size="medium" />
              ) : state.compareFile?.fileId ? (
                <PDFRenderer 
                  fileId={state.compareFile.fileId}
                  page={comparePageNumber}
                  differences={compareDifferences}
                  highlightMode={highlightMode}
                  zoom={zoom}
                  selectedDifference={state.selectedDifference}
                  onDifferenceSelect={handleDifferenceSelect}
                  onImageLoaded={handleCompareImageLoaded}
                  onZoomChange={setZoom}
                />
              ) : (
                <div className="no-content">
                  <p>Compare document unavailable. Please try uploading again.</p>
                </div>
              )}
            </div>
          </div>
        </div>
        
        {showDifferencePanel && (
          <div className="difference-panel">
            <SimplifiedDifferenceList 
              result={pageDetails}
              onDifferenceClick={handleDifferenceSelect}
              selectedDifference={state.selectedDifference}
            />
          </div>
        )}
        
        <button 
          className={`toggle-panel-button ${!showDifferencePanel ? 'hidden' : ''}`}
          onClick={toggleDifferencePanel}
          title={showDifferencePanel ? 'Hide Differences' : 'Show Differences'}
        >
          {showDifferencePanel ? (
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6-6-6z" />
            </svg>
          ) : (
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12l4.58-4.59z" />
            </svg>
          )}
        </button>
      </div>
      
      <div className="comparison-footer">
        <div className="page-navigation">
          <button 
            onClick={handlePreviousPage}
            disabled={currentPage <= 1 || loadingImages}
          >
            Previous Page
          </button>
          
          <div className="page-indicator">
            Page {currentPage} of {maxPageCount}
          </div>
          
          <button 
            onClick={handleNextPage}
            disabled={currentPage >= maxPageCount || loadingImages}
          >
            Next Page
          </button>
        </div>
        
        <div className="view-controls">
          <div className="sync-scroll-control">
            <label>
              <input 
                type="checkbox" 
                checked={syncedScroll}
                onChange={toggleSyncScrolling}
              />
              Sync Scrolling
            </label>
          </div>
          
          <div className="highlight-controls">
            <select 
              value={highlightMode}
              onChange={(e) => setHighlightMode(e.target.value)}
            >
              <option value="all">All Differences</option>
              <option value="text">Text Only</option>
              <option value="image">Images Only</option>
              <option value="style">Styles Only</option>
              <option value="none">No Highlights</option>
            </select>
          </div>
          
          {selectedPair.similarityScore !== undefined && (
            <div className="similarity-info">
              Similarity: {Math.round(selectedPair.similarityScore * 100)}%
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default SideBySideView;