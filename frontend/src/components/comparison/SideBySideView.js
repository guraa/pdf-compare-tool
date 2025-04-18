import React, { useState, useEffect, useRef, memo } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import DocumentMatchingView from './DocumentMatchingView';
import PDFRenderer from './PDFRenderer';
import DifferenceList from './DifferenceList';
import Spinner from '../common/Spinner';
import ZoomControls from './ZoomControls';
import { getDocumentPairs, getComparisonResult } from '../../services/api';
import { processDifferences } from '../../utilities/differenceProcessor';
import './SideBySideView.css';

// Create a memoized DifferenceList for better performance
const MemoizedDifferenceList = memo(DifferenceList);

// Function to inject CSS directly to ensure highlight styles work
const injectHighlightCSS = () => {
  // Only inject once
  if (document.getElementById('pdf-highlight-styles')) {
    return;
  }
  
  // Create style element
  const style = document.createElement('style');
  style.id = 'pdf-highlight-styles';
  style.innerHTML = `
    /* Force highlight styles to be extra visible */
    .difference-highlight {
      position: absolute !important;
      border-radius: 4px !important;
      pointer-events: auto !important;
      z-index: 1000 !important;
      box-sizing: border-box !important;
      opacity: 0.7 !important;
      display: block !important;
    }
    
    .difference-highlight.text {
      background-color: rgba(255, 82, 82, 0.5) !important;
      border: 3px solid rgba(255, 82, 82, 1) !important;
    }
    
    .difference-highlight.image {
      background-color: rgba(33, 150, 243, 0.5) !important;
      border: 3px solid rgba(33, 150, 243, 1) !important;
    }
    
    .difference-highlight.font {
      background-color: rgba(156, 39, 176, 0.5) !important;
      border: 3px solid rgba(156, 39, 176, 1) !important;
    }
    
    .difference-highlight.style {
      background-color: rgba(255, 152, 0, 0.5) !important;
      border: 3px solid rgba(255, 152, 0, 1) !important;
    }
    
    .difference-highlight.added {
      background-color: rgba(76, 175, 80, 0.5) !important;
      border: 3px solid rgba(76, 175, 80, 1) !important;
    }
    
    .difference-highlight.deleted {
      background-color: rgba(244, 67, 54, 0.5) !important;
      border: 3px solid rgba(244, 67, 54, 1) !important;
    }
    
    .difference-highlight.modified {
      background-color: rgba(255, 152, 0, 0.5) !important;
      border: 3px solid rgba(255, 152, 0, 1) !important;
    }
    
    .difference-highlight.selected {
      border: 4px solid yellow !important;
      z-index: 1001 !important;
      box-shadow: 0 0 10px yellow !important;
    }
    
    .highlights-container {
      position: absolute !important;
      top: 0 !important;
      left: 0 !important;
      width: 100% !important;
      height: 100% !important;
      pointer-events: auto !important;
      z-index: 1000 !important;
      display: block !important;
    }
    
    .canvas-container {
      position: relative !important;
    }
    
    .pdf-image {
      z-index: 1 !important;
    }
  `;
  
  // Add to document head
  document.head.appendChild(style);
  console.log("Injected highlight CSS styles");
};

// Main component implementation with multi-page support and unmatched document handling
const SideBySideView = React.memo(({ comparisonId }) => {
  console.log("SideBySideView rendering with comparisonId:", comparisonId);

  // Refs to prevent dependency issues in effects
  const initialFetchDoneRef = useRef(false);
  const comparisonDataRef = useRef(null);
  const scrollContainerRef = useRef(null);
  const visiblePairIndicesRef = useRef([]);

  // Context
  const {
    state,
    setError,
    setLoading,
    setDocumentPairs,
    setSelectedDocumentPairIndex,
    setSelectedDifference,
    setComparisonResult
  } = useComparison();

  // State management
  const [activeView, setActiveView] = useState('matching');
  const [pairDifferences, setPairDifferences] = useState({});
  const [currentVisibleDifferences, setCurrentVisibleDifferences] = useState([]);
  const [highlightMode, setHighlightMode] = useState('all'); // Ensure this is 'all' not 'none'
  const [showDifferencePanel, setShowDifferencePanel] = useState(true);
  const [zoom, setZoom] = useState(0.75);
  const [loadingPairs, setLoadingPairs] = useState([]);
  const [viewKey, setViewKey] = useState(0);
  const [showUnpairedDocuments, setShowUnpairedDocuments] = useState(true);
  const [debugMode, setDebugMode] = useState(false);
  
  // Calibration parameters for proper coordinate mapping
  const calibrationParams = {
    xOffset: 0,  // Adjust if horizontal alignment is off
    yOffset: 16, // Vertical adjustment - increased for specific scale factor
    scaleAdjustment: 1.0, // Usually 1.0 is fine when using precise image dimensions
    flipY: true  // Keep this true for PDF coordinate system
  };

  // Inject CSS styles for highlights directly (bypassing CSS modules)
  useEffect(() => {
    injectHighlightCSS();
    
    // Check for highlight containers after render
    setTimeout(() => {
      const containers = document.querySelectorAll('.highlights-container');
      console.log(`Found ${containers.length} highlight containers`);
      
      containers.forEach((container, index) => {
        const style = window.getComputedStyle(container);
        console.log(`Container ${index} style:`, {
          display: style.display,
          position: style.position,
          zIndex: style.zIndex,
          top: style.top,
          left: style.left,
          width: style.width,
          height: style.height,
          pointerEvents: style.pointerEvents,
          visibility: style.visibility,
          opacity: style.opacity
        });
      });
    }, 1000);
  }, []);

  // Force highlightMode to 'all' if it's 'none'
  useEffect(() => {
    console.log(`Current highlight mode: ${highlightMode}`);
    
    if (highlightMode === 'none') {
      console.log("Highlight mode was 'none', changing to 'all'");
      setHighlightMode('all');
    }
  }, [highlightMode]);

  // Add global highlight mode setter for debugging
  useEffect(() => {
    window.setHighlightMode = (mode) => {
      console.log(`Setting highlight mode to: ${mode}`);
      setHighlightMode(mode);
      return `Highlight mode set to: ${mode}`;
    };
    
    return () => {
      delete window.setHighlightMode;
    };
  }, []);

  // Debug mode toggle with keyboard shortcut
  useEffect(() => {
    const handleKeyDown = (e) => {
      // Toggle debug mode with Ctrl+Shift+D
      if (e.ctrlKey && e.shiftKey && e.key === 'd') {
        setDebugMode(prev => !prev);
        console.log(`Debug mode ${!debugMode ? 'enabled' : 'disabled'}`);
      }
    };
    
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [debugMode]);

  // Initial fetch of comparison data
  useEffect(() => {
    if (!comparisonId || initialFetchDoneRef.current) return;

    const fetchData = async () => {
      try {
        initialFetchDoneRef.current = true;
        setLoading(true);

        console.log("Fetching comparison data...");
        const result = await getComparisonResult(comparisonId);
        
        if (result) {
          console.log("Comparison data received:", result);
          comparisonDataRef.current = result;
          setComparisonResult(result);

          // Process differences from the data
          if (result.differencesByPage) {
            const processedDifferences = processComparisonData(result);
            setPairDifferences(processedDifferences);
          }

          // Also fetch document pairs to get proper metadata
          const pairs = await getDocumentPairs(comparisonId);
          
          if (pairs && pairs.length > 0) {
            console.log("Document pairs received:", pairs);
            
            // Enhance pairs with match flags if not already present
            const enhancedPairs = pairs.map(pair => ({
              ...pair,
              hasBaseDocument: pair.hasBaseDocument !== undefined ? pair.hasBaseDocument : 
                               (pair.baseStartPage > 0 && pair.baseEndPage > 0),
              hasCompareDocument: pair.hasCompareDocument !== undefined ? pair.hasCompareDocument : 
                                 (pair.compareStartPage > 0 && pair.compareEndPage > 0),
              // If matched flag is not explicitly set, derive it
              matched: pair.matched !== undefined ? pair.matched : 
                      (pair.baseStartPage > 0 && pair.baseEndPage > 0 && 
                       pair.compareStartPage > 0 && pair.compareEndPage > 0)
            }));
            
            setDocumentPairs(enhancedPairs);
            
            // Auto-select the first matched pair
            const matchedPairIndex = enhancedPairs.findIndex(pair => pair.matched);
            const initialIndex = matchedPairIndex >= 0 ? matchedPairIndex : 0;
            
            setSelectedDocumentPairIndex(initialIndex);
            
            if (matchedPairIndex >= 0) {
              setActiveView('comparison');
              visiblePairIndicesRef.current = [matchedPairIndex];
              updateVisibleDifferences();
            }
          }
        }
      } catch (err) {
        console.error("Error fetching comparison data:", err);
        setError("Failed to load comparison: " + err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [comparisonId]); // Only dependency is comparisonId

  // Process the comparison data into the format needed for rendering
  const processComparisonData = (data) => {
    if (!data || !data.pagePairs || !data.differencesByPage) {
      return {};
    }

    const processedDifferences = {};
    
    // Process each page pair
    data.pagePairs.forEach((pair, index) => {
      const pageKey = pair.id;
      const differences = data.differencesByPage[pageKey] || [];
      
      if (differences.length > 0) {
        // Process differences to ensure proper typing
        const processedDiffs = processDifferences(differences);
        
        // Split differences for base and compare documents
        const baseDifferences = processedDiffs.map(diff => ({
          ...diff,
          id: `base-${diff.id || Math.random().toString(36).substr(2, 11)}`,
          page: diff.basePageNumber || 1
        }));
        
        const compareDifferences = processedDiffs.map(diff => ({
          ...diff,
          id: `compare-${diff.id || Math.random().toString(36).substr(2, 11)}`,
          page: diff.comparePageNumber || 1
        }));
        
        // Store differences for this pair with proper dimensions
        processedDifferences[index] = {
          baseDifferences,
          compareDifferences,
          // Standard PDF dimensions (adjust if your PDFs use different dimensions)
          baseWidth: 612,
          baseHeight: 792,
          compareWidth: 612,
          compareHeight: 792
        };
      } else {
        // Even for pairs with no differences, store empty arrays with dimensions
        processedDifferences[index] = {
          baseDifferences: [],
          compareDifferences: [],
          baseWidth: 612,
          baseHeight: 792,
          compareWidth: 612,
          compareHeight: 792
        };
      }
    });
    
    return processedDifferences;
  };
  
  // Helper function to filter differences for a specific page
  const filterDifferencesForPage = (differences, pageNum) => {
    if (!differences) return [];
    
    // Log the filtering process if debug mode is on
    if (debugMode) {
      console.log(`Filtering ${differences.length} differences for page ${pageNum}`);
    }
    
    return differences.filter(diff => {
      // Check which page property to use based on the difference format
      if (diff.page !== undefined) {
        return diff.page === pageNum;
      }
      
      if (diff.basePageNumber !== undefined || diff.comparePageNumber !== undefined) {
        const page = diff.basePageNumber || diff.comparePageNumber;
        return page === pageNum;
      }
      
      // Default to first page if no page information is available
      return pageNum === 1;
    });
  };
  
  // Function to render multiple pages for a document
  const renderDocumentPages = (pageRange, fileId, pairData, pairIndex, isBaseDocument, hasMatch) => {
    // Create an array of page numbers to render
    const pages = [];
    for (let i = pageRange.start; i <= pageRange.end; i++) {
      pages.push(i);
    }
    
    // For unmatched documents we still use no highlights, but for matched we force 'all'
    const effectiveHighlightMode = !hasMatch ? 'none' : highlightMode;
    
    console.log(`Rendering pages with highlight mode: ${effectiveHighlightMode}`);
    
    // If this is an unmatched document, show a clear indicator
    if (!hasMatch) {
      return (
        <div className="document-pages unmatched-document">
          <div className="unmatched-document-banner">
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
            </svg>
            <span>Only exists in {isBaseDocument ? 'base' : 'compare'} document</span>
          </div>
          
          {pages.map(pageNum => (
            <div key={`page-${pageNum}`} className="document-page">
              <div className="page-number-indicator">
                Page {pageNum}
              </div>
              <PDFRenderer
                fileId={fileId}
                page={pageNum}
                zoom={zoom}
                highlightMode="none" // No highlight mode for unmatched docs
                differences={[]} // No differences for unmatched docs
                selectedDifference={null}
                onDifferenceSelect={() => {}} // No difference selection for unmatched docs
                onZoomChange={handleZoomChange}
                isBaseDocument={isBaseDocument}
                loading={false}
                pageMetadata={pairData}
                xOffsetAdjustment={calibrationParams.xOffset}
                yOffsetAdjustment={calibrationParams.yOffset}
                scaleAdjustment={calibrationParams.scaleAdjustment}
                flipY={calibrationParams.flipY}
                debugMode={debugMode}
              />
            </div>
          ))}
        </div>
      );
    }
    
    // Regular rendering for matched documents
    return (
      <div className="document-pages">
        {pages.map(pageNum => (
          <div key={`page-${pageNum}`} className="document-page">
            <div className="page-number-indicator">
              Page {pageNum}
            </div>
            <PDFRenderer
              fileId={fileId}
              page={pageNum}
              zoom={zoom}
              highlightMode={effectiveHighlightMode} // Use the effective highlight mode
              differences={isBaseDocument ? 
                filterDifferencesForPage(pairData.baseDifferences, pageNum) : 
                filterDifferencesForPage(pairData.compareDifferences, pageNum)
              }
              selectedDifference={state.selectedDifference}
              onDifferenceSelect={(diff) => setSelectedDifference({...diff, pairIndex, page: pageNum})}
              onZoomChange={handleZoomChange}
              isBaseDocument={isBaseDocument}
              loading={false}
              pageMetadata={pairData}
              xOffsetAdjustment={calibrationParams.xOffset}
              yOffsetAdjustment={calibrationParams.yOffset}
              scaleAdjustment={calibrationParams.scaleAdjustment}
              flipY={calibrationParams.flipY}
              debugMode={debugMode}
            />
          </div>
        ))}
      </div>
    );
  };
  
  // Update visible differences based on visible pairs
  const updateVisibleDifferences = () => {
    if (!pairDifferences || Object.keys(pairDifferences).length === 0) return;
    
    // Get visible pairs
    const visibleIndices = visiblePairIndicesRef.current;
    if (visibleIndices.length === 0) return;
    
    // Collect differences from visible pairs
    let allDiffs = [];
    
    visibleIndices.forEach(pairIndex => {
      const pairData = pairDifferences[pairIndex];
      if (!pairData) return;
      
      const baseDiffs = (pairData.baseDifferences || []).map(diff => ({
        ...diff,
        pairIndex
      }));
      
      const compareDiffs = (pairData.compareDifferences || []).map(diff => ({
        ...diff,
        pairIndex
      }));
      
      allDiffs = [...allDiffs, ...baseDiffs, ...compareDiffs];
    });
    
    // Deduplicate by ID
    const uniqueDiffs = [];
    const diffIds = new Set();
    
    allDiffs.forEach(diff => {
      if (!diffIds.has(diff.id)) {
        diffIds.add(diff.id);
        uniqueDiffs.push(diff);
      }
    });
    
    console.log(`Found ${uniqueDiffs.length} visible differences`);
    setCurrentVisibleDifferences(uniqueDiffs);
  };

  // Handle scroll events to determine visible document pairs
  const handleScroll = () => {
    if (!scrollContainerRef.current) return;
    
    // Find visible document pairs
    const pairElements = document.querySelectorAll('.document-pair');
    if (!pairElements.length) return;
    
    const containerRect = scrollContainerRef.current.getBoundingClientRect();
    const visiblePairs = [];
    
    pairElements.forEach((element, index) => {
      const rect = element.getBoundingClientRect();
      
      // Check if element is visible
      if (rect.bottom >= containerRect.top && rect.top <= containerRect.bottom) {
        visiblePairs.push(index);
      }
    });
    
    // Only update if changed
    if (JSON.stringify(visiblePairs) !== JSON.stringify(visiblePairIndicesRef.current)) {
      visiblePairIndicesRef.current = visiblePairs;
      updateVisibleDifferences();
    }
  };

  // Set up scroll handler
  useEffect(() => {
    const scrollContainer = scrollContainerRef.current;
    if (!scrollContainer || activeView !== 'comparison') return;
    
    const throttledScrollHandler = () => {
      if (!window.requestAnimationFrame) {
        setTimeout(handleScroll, 100);
        return;
      }
      
      window.requestAnimationFrame(handleScroll);
    };
    
    scrollContainer.addEventListener('scroll', throttledScrollHandler);
    setTimeout(handleScroll, 100);
    
    return () => {
      scrollContainer.removeEventListener('scroll', throttledScrollHandler);
    };
  }, [activeView]);

  // Handle selecting a document pair
  const handleSelectDocumentPair = (pairIndex) => {
    console.log("Selecting document pair:", pairIndex);
    setSelectedDocumentPairIndex(pairIndex);
    setActiveView('comparison');
    
    // Force rerender to ensure UI updates properly
    setViewKey(prevKey => prevKey + 1);
    
    // Update visible pairs
    visiblePairIndicesRef.current = [pairIndex];
    updateVisibleDifferences();
    
    // Scroll to the selected pair
    setTimeout(() => {
      const pairElement = document.getElementById(`document-pair-${pairIndex}`);
      if (pairElement && scrollContainerRef.current) {
        pairElement.scrollIntoView({ behavior: 'smooth' });
      }
    }, 100);
  };

  // Handle zoom change
  const handleZoomChange = (newZoom) => {
    setZoom(newZoom);
  };

  // If showing the document matching view
  if (activeView === 'matching') {
    return (
      <DocumentMatchingView
        comparisonId={comparisonId}
        onSelectDocumentPair={handleSelectDocumentPair}
      />
    );
  }

  // Get all document pairs from context
  const documentPairs = state.documentPairs || [];

  // Show loading state if no document pairs
  if (documentPairs.length === 0) {
    return (
      <div className="loading-container">
        <Spinner size="large" />
        <p>Loading document comparisons...</p>
      </div>
    );
  }

  // Helper function to get color based on similarity score
  const getSimilarityColor = (score) => {
    if (score >= 0.95) return '#4CAF50'; // Green
    if (score >= 0.85) return '#8BC34A'; // Light Green
    if (score >= 0.70) return '#CDDC39'; // Lime
    if (score >= 0.50) return '#FFC107'; // Amber
    if (score > 0) return '#FF9800';     // Orange
    return '#F44336';                    // Red
  };
  
  return (
    <div className="pdf-comparison-container" key={viewKey}>
      {/* Header */}
      <div className="comparison-header">
        <button 
          className="back-button"
          onClick={() => setActiveView('matching')}
        >
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z" />
          </svg>
          Back to Document Matching
        </button>
        
        <div className="document-info">
          <h3>Document Comparison</h3>
          <div className="page-ranges">
            <span>{documentPairs.filter(p => p.matched).length} document pairs matched</span>
          </div>
        </div>
        
        <ZoomControls
          zoom={zoom}
          onZoomIn={() => handleZoomChange(Math.min(2.0, zoom + 0.25))}
          onZoomOut={() => handleZoomChange(Math.max(0.5, zoom - 0.25))}
          onZoomReset={() => handleZoomChange(1.0)}
        />
      </div>
      
      {/* Filter controls */}
      <div className="filter-controls">
        <label className="filter-checkbox">
          <input 
            type="checkbox" 
            checked={showUnpairedDocuments} 
            onChange={() => setShowUnpairedDocuments(!showUnpairedDocuments)}
          />
          Show unpaired documents
        </label>
        
        {/* Add direct buttons for highlight modes */}
        <div style={{ display: 'flex', marginLeft: '20px', gap: '10px' }}>
          <button 
            onClick={() => setHighlightMode('all')} 
            style={{
              padding: '4px 8px',
              backgroundColor: highlightMode === 'all' ? '#4CAF50' : '#cccccc',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer'
            }}
          >
            Show All Highlights
          </button>
          <button 
            onClick={() => setHighlightMode('text')} 
            style={{
              padding: '4px 8px',
              backgroundColor: highlightMode === 'text' ? '#4CAF50' : '#cccccc',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer'
            }}
          >
            Text Only
          </button>
          <button 
            onClick={() => setDebugMode(!debugMode)} 
            style={{
              padding: '4px 8px',
              backgroundColor: debugMode ? '#F44336' : '#cccccc',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer'
            }}
          >
            Debug Mode: {debugMode ? 'ON' : 'OFF'}
          </button>
        </div>
      </div>
      
      {/* Main content */}
      <div className="comparison-content">
        {/* Main scrollable area */}
        <div 
          className={`documents-scroll-container ${showDifferencePanel ? 'with-panel' : ''}`}
          ref={scrollContainerRef}
        >
          <div className="continuous-pdf-view">
            {/* Render all document pairs */}
            {documentPairs.map((pair, pairIndex) => {
              // Skip unmatched pairs if not showing unpaired documents
              if (!pair.matched && !showUnpairedDocuments) return null;
              
              // Get differences for this pair
              const pairData = pairDifferences[pairIndex] || {
                baseDifferences: [],
                compareDifferences: [],
                baseWidth: 612,
                baseHeight: 792,
                compareWidth: 612,
                compareHeight: 792
              };
              
              // For unmatched pairs, render them differently
              if (!pair.matched) {
                // Get whether it's base-only or compare-only
                const isBaseOnly = pair.hasBaseDocument && !pair.hasCompareDocument;
                const isCompareOnly = !pair.hasBaseDocument && pair.hasCompareDocument;
                
                // Calculate page ranges
                const basePageRange = isBaseOnly ? {
                  start: pair.baseStartPage,
                  end: pair.baseEndPage
                } : { start: 0, end: 0 };
                
                const comparePageRange = isCompareOnly ? {
                  start: pair.compareStartPage,
                  end: pair.compareEndPage
                } : { start: 0, end: 0 };
                
                return (
                  <div 
                    className="document-pair unmatched-pair" 
                    key={`pair-${pairIndex}`} 
                    id={`document-pair-${pairIndex}`}
                  >
                    <div className="pair-header unmatched">
                      <h3>Unmatched Document {pairIndex + 1}</h3>
                      <div className="match-status">
                        {isBaseOnly ? "Only in Base Document" : "Only in Compare Document"}
                      </div>
                    </div>
                    
                    <div className="page-content unmatched-content">
                      {isBaseOnly ? (
                        <>
                          <div className="page-side base-side">
                            <div className="document-header">
                              <h4>Base Document</h4>
                              <div className="page-info">
                                Pages {basePageRange.start} - {basePageRange.end}
                              </div>
                            </div>
                            
                            <div className="document-content">
                              {renderDocumentPages(
                                basePageRange,
                                state.baseFile.fileId,
                                pairData,
                                pairIndex,
                                true,
                                false // No match
                              )}
                            </div>
                          </div>
                          
                          <div className="page-side compare-side empty-side">
                            <div className="document-header">
                              <h4>Compare Document</h4>
                            </div>
                            
                            <div className="no-match-message">
                              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
                              </svg>
                              <p>No matching document found in comparison file</p>
                            </div>
                          </div>
                        </>
                      ) : (
                        <>
                          <div className="page-side base-side empty-side">
                            <div className="document-header">
                              <h4>Base Document</h4>
                            </div>
                            
                            <div className="no-match-message">
                              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
                              </svg>
                              <p>No matching document found in base file</p>
                            </div>
                          </div>
                          
                          <div className="page-side compare-side">
                            <div className="document-header">
                              <h4>Compare Document</h4>
                              <div className="page-info">
                                Pages {comparePageRange.start} - {comparePageRange.end}
                              </div>
                            </div>
                            
                            <div className="document-content">
                              {renderDocumentPages(
                                comparePageRange,
                                state.compareFile.fileId,
                                pairData,
                                pairIndex,
                                false,
                                false, // No match
                              )}
                            </div>
                          </div>
                        </>
                      )}
                    </div>
                  </div>
                );
              }
              
              // Regular matched pair - Calculate page ranges
              const basePageRange = {
                start: pair.baseStartPage,
                end: pair.baseEndPage
              };
              
              const comparePageRange = {
                start: pair.compareStartPage,
                end: pair.compareEndPage
              };
              
              const isLoading = loadingPairs.includes(pairIndex);
              
              return (
                <div 
                  className="document-pair" 
                  key={`pair-${pairIndex}`} 
                  id={`document-pair-${pairIndex}`}
                >
                  <div className="pair-header">
                    <h3>Document {pairIndex + 1}</h3>
                    {pair.similarityScore !== undefined && (
                      <div className="match-percentage" style={{
                        background: getSimilarityColor(pair.similarityScore),
                        color: 'white'
                      }}>
                        {Math.round(pair.similarityScore * 100)}% Match
                      </div>
                    )}
                  </div>
                  
                  <div className="page-content">
                    <div className="page-side base-side">
                      <div className="document-header">
                        <h4>Base Document</h4>
                        <div className="page-info">
                          Pages {basePageRange.start} - {basePageRange.end}
                        </div>
                      </div>
                      
                      <div className="document-content">
                        {state.baseFile?.fileId ? (
                          renderDocumentPages(
                            basePageRange,
                            state.baseFile.fileId,
                            pairData,
                            pairIndex,
                            true, // isBaseDocument
                            true  // Has match
                          )
                        ) : (
                          <div className="no-content">
                            <p>Base file not available</p>
                          </div>
                        )}
                      </div>
                    </div>
                    
                    <div className="page-side compare-side">
                      <div className="document-header">
                        <h4>Compare Document</h4>
                        <div className="page-info">
                          Pages {comparePageRange.start} - {comparePageRange.end}
                        </div>
                      </div>
                      
                      <div className="document-content">
                        {state.compareFile?.fileId ? (
                          renderDocumentPages(
                            comparePageRange,
                            state.compareFile.fileId,
                            pairData,
                            pairIndex,
                            false, // isBaseDocument
                            true  // Has match
                          )
                        ) : (
                          <div className="no-content">
                            <p>Compare file not available</p>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
        
        {/* Difference panel */}
        {showDifferencePanel && (
          <div className="difference-panel">
            <div className="difference-panel-header">
              <h3>Visible Differences</h3>
              <div className="difference-count">
                {currentVisibleDifferences.length} differences in view
              </div>
            </div>
            
            <MemoizedDifferenceList
              result={{ 
                baseDifferences: currentVisibleDifferences, 
                compareDifferences: [] // Already combined
              }}
              selectedDifference={state.selectedDifference}
              onDifferenceClick={(diff) => {
                console.log("Selected difference:", diff);
                setSelectedDifference(diff);
                
                // Scroll to the pair
                if (diff.pairIndex !== undefined) {
                  const pairElement = document.getElementById(`document-pair-${diff.pairIndex}`);
                  if (pairElement) {
                    pairElement.scrollIntoView({ behavior: 'smooth' });
                  }
                }
              }}
            />
          </div>
        )}
        
        {/* Toggle panel button */}
        <button 
          className={`toggle-panel-button ${showDifferencePanel ? '' : 'hidden'}`}
          onClick={() => setShowDifferencePanel(!showDifferencePanel)}
        >
          {showDifferencePanel ? '→' : '←'}
        </button>
      </div>
      
      {/* Footer */}
      <div className="comparison-footer">
        <div className="view-controls">
          <div className="highlight-controls">
            <label htmlFor="highlightSelect">Highlight: </label>
            <select 
              id="highlightSelect"
              value={highlightMode}
              onChange={(e) => setHighlightMode(e.target.value)}
            >
              <option value="all">All Differences</option>
              <option value="text">Text Only</option>
              <option value="image">Images Only</option>
              <option value="font">Fonts Only</option>
              <option value="style">Styles Only</option>
              <option value="none">No Highlights</option>
            </select>
            
            {/* Add a button to force highlight mode to 'all' */}
            <button 
              onClick={() => setHighlightMode('all')} 
              style={{
                marginLeft: '10px',
                padding: '4px 8px',
                backgroundColor: '#4CAF50',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer'
              }}
            >
              Force Highlights On
            </button>
          </div>
          
          <div className="page-status">
            Viewing {visiblePairIndicesRef.current.length} of {documentPairs.filter(p => p.matched).length} document pairs
          </div>
        </div>
      </div>
    </div>
  );
});

export default SideBySideView;