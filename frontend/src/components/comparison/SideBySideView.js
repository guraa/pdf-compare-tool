import React, { useState, useEffect, useRef } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import DocumentMatchingView from './DocumentMatchingView';
import PDFRenderer from './PDFRenderer';
import DifferenceList from './DifferenceList';
import Spinner from '../common/Spinner';
import ZoomControls from './ZoomControls';
import { getDocumentPairs, getDocumentPairResult, getDocumentPageDetails } from '../../services/api';
import './SideBySideView.css';

const SideBySideView = ({ comparisonId }) => {
  // Refs
  const scrollContainerRef = useRef(null);
  const visiblePairIndicesRef = useRef([]);

  // Context
  const {
    state,
    setError,
    setLoading,
    setDocumentPairs,
    setSelectedDocumentPairIndex,
    setSelectedDifference
  } = useComparison();

  // State
  const [activeView, setActiveView] = useState('matching');
  const [pairDifferences, setPairDifferences] = useState({});
  const [currentVisibleDifferences, setCurrentVisibleDifferences] = useState([]);
  const [highlightMode, setHighlightMode] = useState('all');
  const [showDifferencePanel, setShowDifferencePanel] = useState(true);
  const [zoom, setZoom] = useState(0.4); // Start with a lower zoom level
  const [loadingPairs, setLoadingPairs] = useState(false);
  const [showUnpairedDocuments, setShowUnpairedDocuments] = useState(true);
  
  // New state for pair details
  const [currentPairResult, setCurrentPairResult] = useState(null);
  const [loadingPairResult, setLoadingPairResult] = useState(false);
  const [pageDifferences, setPageDifferences] = useState({});

  // Fetch document pairs when component mounts
  useEffect(() => {
    if (!comparisonId) return;

    const fetchDocumentPairs = async () => {
      try {
        setLoading(true);
        const pairs = await getDocumentPairs(comparisonId);
        
        if (pairs && pairs.length > 0) {
          console.log("Document pairs received:", pairs);
          
          // Enhance pairs with match flags
          const enhancedPairs = pairs.map(pair => ({
            ...pair,
            hasBaseDocument: pair.hasBaseDocument !== undefined ? pair.hasBaseDocument : 
                            (pair.baseStartPage > 0 && pair.baseEndPage > 0),
            hasCompareDocument: pair.hasCompareDocument !== undefined ? pair.hasCompareDocument : 
                              (pair.compareStartPage > 0 && pair.compareEndPage > 0),
            matched: pair.matched !== undefined ? pair.matched : 
                    (pair.baseStartPage > 0 && pair.baseEndPage > 0 && 
                    pair.compareStartPage > 0 && pair.compareEndPage > 0)
          }));
          
          setDocumentPairs(enhancedPairs);
          
          // Auto-select the first matched pair
          const matchedPairIndex = enhancedPairs.findIndex(pair => pair.matched);
          if (matchedPairIndex >= 0) {
            setSelectedDocumentPairIndex(matchedPairIndex);
          }
        }
      } catch (err) {
        console.error("Error fetching document pairs:", err);
        setError("Failed to load document pairs: " + err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchDocumentPairs();
  }, [comparisonId]);

  // Load pair details when selected pair changes
  useEffect(() => {
    if (!comparisonId || state.selectedDocumentPairIndex === undefined || activeView !== 'comparison') {
      return;
    }
    
    const fetchPairResult = async () => {
      try {
        setLoadingPairResult(true);
        console.log(`Fetching details for pair index ${state.selectedDocumentPairIndex}`);
        
        const result = await getDocumentPairResult(
          comparisonId,
          state.selectedDocumentPairIndex
        );
        
        console.log("Pair result:", result);
        setCurrentPairResult(result);
        
        // Preload first page differences for both base and compare
        if (result.pagePairIds && result.pagePairIds.length > 0) {
          const pagePairId = result.pagePairIds[0];
          
          // If we have differencesByPage data in the response, use it
          if (result.differencesByPage && result.differencesByPage[pagePairId]) {
            setPageDifferences(prev => ({
              ...prev,
              [pagePairId]: result.differencesByPage[pagePairId]
            }));
          } else {
            // Otherwise fetch page details
            await fetchPageDifferences(state.selectedDocumentPairIndex, 1);
          }
        }
      } catch (err) {
        console.error(`Error loading pair result:`, err);
        setError(`Failed to load comparison details: ${err.message}`);
      } finally {
        setLoadingPairResult(false);
      }
    };
    
    fetchPairResult();
  }, [comparisonId, state.selectedDocumentPairIndex, activeView]);

  // Function to fetch page differences
  const fetchPageDifferences = async (pairIndex, pageNumber) => {
    if (!comparisonId) return {};
    
    try {
      console.log(`Fetching differences for pair ${pairIndex}, page ${pageNumber}`);
      
      const response = await getDocumentPageDetails(
        comparisonId,
        pairIndex,
        pageNumber,
        {
          differenceTypes: ['text', 'font', 'image', 'style', 'metadata'],
          minSeverity: 'all'
        }
      );
      
      console.log(`Page differences for pair ${pairIndex}, page ${pageNumber}:`, response);
      
      // Store the differences by page pair ID
      const pagePairId = response.pagePairId || `pair-${pairIndex}-page-${pageNumber}`;
      
      setPageDifferences(prev => ({
        ...prev,
        [pagePairId]: {
          baseDifferences: response.baseDifferences || [],
          compareDifferences: response.compareDifferences || []
        }
      }));
      
      return {
        baseDifferences: response.baseDifferences || [],
        compareDifferences: response.compareDifferences || []
      };
    } catch (err) {
      console.error(`Error fetching page differences:`, err);
      return {
        baseDifferences: [],
        compareDifferences: []
      };
    }
  };

  // Handle selecting a document pair
  const handleSelectDocumentPair = (pairIndex) => {
    console.log("Selecting document pair:", pairIndex);
    setSelectedDocumentPairIndex(pairIndex);
    setActiveView('comparison');
    
    // Reset page differences when selecting a new pair
    setPageDifferences({});
    
    // Update visible pairs for difference panel
    visiblePairIndicesRef.current = [pairIndex];
  };

  // Get page differences - completely reworked with direct differencesByPage access
  const getPageDifferences = (pairIndex, pageNum, isBaseDocument) => {
    console.log(`Getting differences for pair ${pairIndex}, page ${pageNum}, isBase: ${isBaseDocument}`);
    
    // This is the main issue - we need to directly access the differencesByPage
    if (state.comparisonResult && state.comparisonResult.differencesByPage) {
      // Get all pagePairs
      const pagePairs = state.comparisonResult.pagePairs || [];
      
      if (pagePairs.length > 0) {
        // First find the relevant pagePair for the current page
        let pagePair = null;
        let pagePairId = null;
        
        // Log all pagePairs for debugging
        console.log("All pagePairs:", pagePairs);
        
        // Try to find the specific page pair for this page number
        for (const pair of pagePairs) {
          // Check if this pair includes the requested page
          if (isBaseDocument) {
            if (pair.basePageNumber === pageNum || 
               (pair.basePageStart <= pageNum && pair.basePageEnd >= pageNum)) {
              pagePair = pair;
              pagePairId = pair.id;
              break;
            }
          } else {
            if (pair.comparePageNumber === pageNum || 
               (pair.comparePageStart <= pageNum && pair.comparePageEnd >= pageNum)) {
              pagePair = pair;
              pagePairId = pair.id;
              break;
            }
          }
        }
        
        // If we didn't find a specific page pair, just use the first one
        // This is a fallback approach
        if (!pagePair) {
          // Try a different approach - use the page index
          pagePair = pagePairs[pageNum - 1] || pagePairs[0];
          if (pagePair) {
            pagePairId = pagePair.id;
          }
        }
        
        if (pagePairId) {
          // Now get all differences for this page pair
          const allDifferences = state.comparisonResult.differencesByPage[pagePairId] || [];
          console.log(`Found ${allDifferences.length} differences for pagePairId ${pagePairId}`);
          
          // IMPORTANT: Show ALL differences for now to help debug
          // We'll filter later once we confirm differences are showing
          return allDifferences;
        }
      }
    }
    
    // Get differences from our pageDifferences state if available
    const pagePairId = currentPairResult?.pagePairIds ? 
      currentPairResult.pagePairIds[pageNum - 1] : // Adjust for zero-based indexing
      `pair-${pairIndex}-page-${pageNum}`;
    
    if (pageDifferences[pagePairId]) {
      console.log(`Found cached differences for pagePairId ${pagePairId}`);
      if (isBaseDocument) {
        return pageDifferences[pagePairId].baseDifferences || [];
      } else {
        return pageDifferences[pagePairId].compareDifferences || [];
      }
    }
    
    // Last resort - fetch them directly
    console.log("No differences found, fetching from API...");
    fetchPageDifferences(pairIndex, pageNum);
    
    // Return empty array in the meantime
    return [];
  };

  // Update zoom
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

  // Get document pairs from context
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
    if (score >= 0.95) return '#4CAF50';
    if (score >= 0.85) return '#8BC34A';
    if (score >= 0.70) return '#CDDC39';
    if (score >= 0.50) return '#FFC107';
    if (score > 0) return '#FF9800';
    return '#F44336';
  };
  
  const renderDocumentPages = (pageRange, fileId, pairIndex, isBaseDocument, hasMatch) => {
    const pages = [];
    for (let i = pageRange.start; i <= pageRange.end; i++) {
      pages.push(i);
    }
    
    // Only apply highlights if this is a matched document
    const effectiveHighlightMode = !hasMatch ? 'none' : highlightMode;
    
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
                key={`pdf-renderer-${pairIndex}-${pageNum}`}
                fileId={fileId}
                page={pageNum}
                zoom={zoom}
                highlightMode={effectiveHighlightMode}
                differences={getPageDifferences(pairIndex, pageNum, isBaseDocument)}
                selectedDifference={state.selectedDifference}
                onDifferenceSelect={(diff) => setSelectedDifference({...diff, pairIndex, page: pageNum})}
                onZoomChange={handleZoomChange}
                isBaseDocument={isBaseDocument}
                loading={false}
              />
            </div>
          ))}
        </div>
      );
    }
    
    // Regular rendering for matched documents
    return (
      <div className="document-pages">
        {pages.map(pageNum => {
          // Get differences for this page
          const pageDiffs = getPageDifferences(pairIndex, pageNum, isBaseDocument);
          console.log(`[COORDINATE DEBUG] Page ${pageNum} - Raw differences from API:`, 
            pageDiffs.slice(0, 3).map(diff => ({
              id: diff.id,
              type: diff.type,
              baseX: diff.baseX,
              baseY: diff.baseY,
              compareX: diff.compareX,
              compareY: diff.compareY,
              position: diff.position,
              bounds: diff.bounds,
              text: diff.text?.substring(0, 20) || diff.baseText?.substring(0, 20) || '[No text]'
            }))
          );
          console.log(`Rendering ${isBaseDocument ? 'base' : 'compare'} page ${pageNum} with ${pageDiffs.length} differences`);
          
          // Debug - log the first difference to see its structure
          if (pageDiffs.length > 0) {
            console.log(`First difference example:`, pageDiffs[0]);
          }
          
          return (
            <div key={`page-${pageNum}`} className="document-page">
              <div className="page-number-indicator">
                Page {pageNum}
              </div>
              <PDFRenderer
                key={`pdf-renderer-${pairIndex}-${pageNum}`}
                fileId={fileId}
                page={pageNum}
                zoom={zoom}
                highlightMode={effectiveHighlightMode}
                differences={pageDiffs}
                selectedDifference={state.selectedDifference}
                onDifferenceSelect={(diff) => setSelectedDifference({...diff, pairIndex, page: pageNum})}
                onZoomChange={handleZoomChange}
                isBaseDocument={isBaseDocument}
                loading={false}
              />
              
              {/* Debug info - render difference count on the page */}
              {pageDiffs.length > 0 && (
                <div style={{
                  position: 'absolute', 
                  top: '40px', 
                  right: '8px',
                  background: 'red', 
                  color: 'white',
                  padding: '4px 8px',
                  borderRadius: '4px',
                  fontWeight: 'bold',
                  fontSize: '14px'
                }}>
                  {pageDiffs.length} diffs
                </div>
              )}
            </div>
          );
        })}
      </div>
    );
  };

  return (
    <div className="pdf-comparison-container">
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
            <span style={{marginLeft: '10px', color: '#FF9800'}}>
              {state.comparisonResult?.totalDifferences || 0} differences
            </span>
          </div>
        </div>
        
        <ZoomControls
          zoom={zoom}
          onZoomIn={() => handleZoomChange(Math.min(1.5, zoom + 0.1))}
          onZoomOut={() => handleZoomChange(Math.max(0.2, zoom - 0.1))}
          onZoomReset={() => handleZoomChange(0.5)}
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
        
        <div className="highlight-type-controls">
          <button 
            onClick={() => setHighlightMode('all')} 
            className={`highlight-button ${highlightMode === 'all' ? 'active' : ''}`}
          >
            Show All Highlights
          </button>
          <button 
            onClick={() => setHighlightMode('text')} 
            className={`highlight-button ${highlightMode === 'text' ? 'active' : ''}`}
          >
            Text Only
          </button>
          <button 
            onClick={() => setHighlightMode('none')} 
            className={`highlight-button ${highlightMode === 'none' ? 'active' : ''}`}
          >
            No Highlights
          </button>
        </div>
      </div>
      
      {/* Loading state for pair result */}
      {loadingPairResult && (
        <div className="loading-overlay">
          <Spinner size="medium" />
          <p>Loading comparison details...</p>
        </div>
      )}
      
      {/* Main content */}
      <div className="comparison-content">
        {/* Main scrollable area */}
        <div 
          className={`documents-scroll-container ${showDifferencePanel ? 'with-panel' : ''}`}
          ref={scrollContainerRef}
        >
          <div className="continuous-pdf-view">
            {/* Render the selected document pair */}
            {state.selectedDocumentPairIndex !== undefined && documentPairs[state.selectedDocumentPairIndex] && (
              <div 
                className="document-pair" 
                key={`pair-${state.selectedDocumentPairIndex}`}
                id={`document-pair-${state.selectedDocumentPairIndex}`}
              >
                {(() => {
                  const pairIndex = state.selectedDocumentPairIndex;
                  const pair = documentPairs[pairIndex];
                  
                  // Skip unmatched pairs if not showing unpaired documents
                  if (!pair.matched && !showUnpairedDocuments) return null;
                  
                  // For unmatched pairs, render them differently
                  if (!pair.matched) {
                    // Get whether it's base-only or compare-only
                    const isBaseOnly = pair.hasBaseDocument && !pair.hasCompareDocument;
                    
                    // Calculate page ranges
                    const basePageRange = isBaseOnly ? {
                      start: pair.baseStartPage,
                      end: pair.baseEndPage
                    } : { start: 0, end: 0 };
                    
                    const comparePageRange = !isBaseOnly ? {
                      start: pair.compareStartPage,
                      end: pair.compareEndPage
                    } : { start: 0, end: 0 };
                    
                    return (
                      <>
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
                                    pairIndex,
                                    false,
                                    false // No match
                                  )}
                                </div>
                              </div>
                            </>
                          )}
                        </div>
                      </>
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
                  
                  return (
                    <>
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
                    </>
                  );
                })()}
              </div>
            )}
          </div>
        </div>
        
        {/* Difference panel */}
        {showDifferencePanel && (
          <div className="difference-panel">
            <div className="difference-panel-header">
              <h3>Differences</h3>
              {state.comparisonResult && (
                <div className="difference-count">
                  {state.comparisonResult.totalDifferences || 0} differences found
                </div>
              )}
            </div>
            <DifferenceList
              result={{
                baseDifferences: state.comparisonResult?.differencesByPage 
                ? Object.values(state.comparisonResult.differencesByPage).flat() 
                : [],
                compareDifferences: []
              }}
              selectedDifference={state.selectedDifference}
              onDifferenceClick={(diff) => {
                setSelectedDifference(diff);
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
      
      {/* Footer with debug info */}
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
          </div>
          
          <div className="page-status">
            Viewing document pair {state.selectedDocumentPairIndex + 1} of {documentPairs.length}
          </div>
          
          {/* Debug info */}
          <div className="debug-info">
            {currentPairResult && (
              <span>Total Differences: {currentPairResult.totalDifferences || 0}</span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default SideBySideView;
