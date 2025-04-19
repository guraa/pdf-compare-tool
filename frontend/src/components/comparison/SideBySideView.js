import React, { useState, useEffect, useRef } from 'react';
import { useComparison } from '../../context/ComparisonContext';
import DocumentMatchingView from './DocumentMatchingView';
import PDFRenderer from './PDFRenderer';
import DifferenceList from './DifferenceList';
import Spinner from '../common/Spinner';
import ZoomControls from './ZoomControls';
import { getDocumentPairs } from '../../services/api';
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
  const [loadingPairs, setLoadingPairs] = useState([]);
  const [showUnpairedDocuments, setShowUnpairedDocuments] = useState(true);
  
  // Calibration parameters for coordinate mapping
  const calibrationParams = {
    xOffset: 0,
    yOffset: 16,
    scaleAdjustment: 1.0,
    flipY: true
  };

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


  

  // Handle selecting a document pair
  const handleSelectDocumentPair = (pairIndex) => {
    console.log("Selecting document pair:", pairIndex);
    setSelectedDocumentPairIndex(pairIndex);
    setActiveView('comparison');
    
    // Update visible pairs for difference panel
    visiblePairIndicesRef.current = [pairIndex];
    updateVisibleDifferences();
  };


  const getPageDifferences = (pairIndex, pageNum, isBaseDocument) => {
    // Get the correct pair ID
    const currentPair = documentPairs[pairIndex];
    if (!currentPair) return [];
    
    // For multi-page documents, we need the page pair ID
    const pagePairId = state.comparisonResult?.pagePairs?.[pairIndex]?.id;
    if (!pagePairId) return [];
    
    // Get all differences for this page pair
    const allDifferences = state.comparisonResult?.differencesByPage?.[pagePairId] || [];
    
    // Filter differences for the specific page
    return allDifferences.filter(diff => {
      if (isBaseDocument) {
        // For base document, check basePageNumber
        return (diff.basePageNumber || diff.page) === pageNum;
      } else {
        // For compare document, check comparePageNumber
        return (diff.comparePageNumber || diff.page) === pageNum;
      }
    });
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
    
    setCurrentVisibleDifferences(uniqueDiffs);
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
  
  const renderDocumentPages = (pageRange, fileId, pairData, pairIndex, isBaseDocument, hasMatch) => {
    const pages = [];
    for (let i = pageRange.start; i <= pageRange.end; i++) {
      pages.push(i);
    }
    
    
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
            pageMetadata={pairData}
            xOffsetAdjustment={0}
            yOffsetAdjustment={16}
            scaleAdjustment={1.0}
            flipY={true}
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
              highlightMode={effectiveHighlightMode}
              differences={isBaseDocument && pairData.baseDifferences ? 
                pairData.baseDifferences.filter(d => d.page === pageNum || d.basePageNumber === pageNum) : 
                pairData.compareDifferences ? pairData.compareDifferences.filter(d => d.page === pageNum || d.comparePageNumber === pageNum) : []
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
            />
          </div>
        ))}
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
                  
                  // Get differences for this pair (placeholder - normally from API)
                  const pairData = {
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
                    </>
                  );
                })()}
              </div>
            )}
          </div>
        </div>
        
        {/* Difference panel - simplified */}
        {showDifferencePanel && (
          <div className="difference-panel">
            <div className="difference-panel-header">
              <h3>Visible Differences</h3>
              <div className="difference-count">
                Select differences to highlight
              </div>
            </div>
            
            <DifferenceList
              result={{ 
                baseDifferences: currentVisibleDifferences, 
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
          </div>
          
          <div className="page-status">
            Viewing document pair {state.selectedDocumentPairIndex + 1} of {documentPairs.length}
          </div>
        </div>
      </div>
    </div>
  );
};

export default SideBySideView;